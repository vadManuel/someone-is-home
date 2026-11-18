package home.someoneshome.app

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.copyAndClose
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * **One phone's network, which a test can take away and give back** (V1, D-267).
 *
 * A loopback relay between one phone and the house: the phone dials [port], and every connection it
 * makes is piped byte for byte to the house's own port. [unplug] closes every connection it is
 * carrying and stops listening, so the house sees that seat's socket close (the desk drops it) and
 * the phone's reconnects are refused, as a phone out of range or suspended with its socket defunct
 * would be. [plug] listens on the same port again, and the phone's next reconnect reaches the house
 * with its seat token. Nothing above the socket is faked: the websocket, the ledger, the desk and
 * both ends' code are the real ones.
 */
class Unplug(private val scope: CoroutineScope, private val housePort: Int) {

    // Touched only on [scope], which in every test is the one thread `runBlocking` runs on.

    private val selector = SelectorManager(Dispatchers.IO)
    private var server: ServerSocket? = null
    private var accepting: Job? = null
    private val carried = mutableListOf<Socket>()

    /** The port the phone dials. Fixed by the first [plug], and kept. */
    var port: Int = 0
        private set

    /** Listen, on the port this relay had before or a fresh one, and carry what arrives. */
    suspend fun plug() {
        val listening = aSocket(selector).tcp().bind("127.0.0.1", port) { reuseAddress = true }
        server = listening
        port = (listening.localAddress as InetSocketAddress).port
        accepting = scope.launch {
            while (true) {
                val phone = try {
                    listening.accept()
                } catch (_: Throwable) {
                    return@launch
                }
                val house = aSocket(selector).tcp().connect("127.0.0.1", housePort)
                carried += phone
                carried += house
                val up = phone.openReadChannel()
                val down = house.openReadChannel()
                val toHouse = house.openWriteChannel(autoFlush = true)
                val toPhone = phone.openWriteChannel(autoFlush = true)
                scope.launch { runCatching { up.copyAndClose(toHouse) }; runCatching { house.close() } }
                scope.launch { runCatching { down.copyAndClose(toPhone) }; runCatching { phone.close() } }
            }
        }
    }

    /** Close every connection this relay is carrying, and stop listening. */
    fun unplug() {
        accepting?.cancel()
        server?.close()
        server = null
        val gone = carried.toList().also { carried.clear() }
        gone.forEach { runCatching { it.close() } }
    }
}
