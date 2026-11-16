package home.someoneshome.platform

import home.someoneshome.model.ContactToken
import home.someoneshome.model.ContactWindow
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.set
import platform.CoreBluetooth.CBAdvertisementDataServiceUUIDsKey
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBCentralManagerOptionShowPowerAlertKey
import platform.CoreBluetooth.CBManagerStatePoweredOff
import platform.CoreBluetooth.CBManagerStatePoweredOn
import platform.CoreBluetooth.CBManagerStateResetting
import platform.CoreBluetooth.CBManagerStateUnauthorized
import platform.CoreBluetooth.CBManagerStateUnsupported
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBPeripheralManager
import platform.CoreBluetooth.CBPeripheralManagerDelegateProtocol
import platform.CoreBluetooth.CBPeripheralManagerOptionShowPowerAlertKey
import platform.CoreBluetooth.CBUUID
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSNumber
import platform.Foundation.dataWithBytes
import platform.Foundation.getBytes
import platform.darwin.DISPATCH_SOURCE_TYPE_TIMER
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_resume
import platform.darwin.dispatch_source_create
import platform.darwin.dispatch_source_set_event_handler
import platform.darwin.dispatch_source_set_timer
import platform.darwin.dispatch_sync
import platform.darwin.dispatch_time

/**
 * **iOS: the contact radio on CoreBluetooth** (D-218). A [SwitchboardRadio] makes every decision;
 * this file is the managers, their delegates and one queue.
 *
 * ### One queue, and everything on it
 *
 * Both managers deliver their callbacks on a serial queue of the radio's own, and every call the
 * app makes ([advertise], [scan], [silence]) is put on the same queue, so the switchboard and its
 * ring are only ever touched from one thread and need no lock. [drain] is the one synchronous hop:
 * once a beat, the uploader waits for the queue to hand the ring over. Nothing here runs on the main
 * thread, which is the lamp's.
 *
 * ### Rule 7: what a callback costs
 *
 * Every CoreBluetooth callback into Kotlin/Native allocates before a line of this file runs: the
 * bridge wraps the manager, the peripheral, the advertisement dictionary and the RSSI number, and
 * reading the dictionary wraps the UUID list, the UUIDs and their data. That is several hundred
 * bytes a callback (D-218 estimates 300, at most 500) and none of it can be avoided from Kotlin. What
 * this file adds is nothing: the dictionary key is read once, the token's bytes are copied into a
 * buffer allocated once, and the ring the switchboard keeps is fixed. **So the radio's allocation
 * rate is its callback rate**, and the scan cycle ([ContactAdvertisement.SCAN_CYCLE_MILLIS]) is what
 * bounds that: at most four reports a second per phone heard.
 *
 * ### Rule 6: nothing here can reach the player
 *
 * Both managers are built with the system's power alert switched off, which is a dialog iOS would
 * otherwise put over the lamp the moment Bluetooth is off. An outage goes to the device log and to
 * the switchboard's `outage`, and nowhere else.
 */
actual fun bluetoothContactRadio(log: (String) -> Unit): ContactRadio = IosContactRadio(log)

private class IosContactRadio(private val log: (String) -> Unit) : ContactRadio {

    private val queue = dispatch_queue_create("home.someoneshome.contact-radio", null)
    private val hardware = CoreBluetoothHardware(queue, log)
    private val board = SwitchboardRadio(hardware, log).also { hardware.board = it }

    override fun advertise(token: ContactToken) {
        // The window and the wall clock are logged so two phones' logs can be laid side by side:
        // both should name the same window within a beat of each other (the device check, D-218).
        val now = wallClockMillis()
        log("ADVERTISE ${token.hex.take(8)} · WINDOW ${ContactWindow.containing(now).index} · WALL $now")
        dispatch_async(queue) { board.advertise(token) }
    }

    override fun scan(on: Boolean) {
        log(if (on) "SCAN ON" else "SCAN OFF — ADVERTISING ONLY FROM HERE")
        dispatch_async(queue) { board.scan(on) }
    }

    override fun drain(): List<RadioReading> {
        var out: List<RadioReading> = emptyList()
        dispatch_sync(queue) { out = board.drain() }
        if (out.isNotEmpty()) {
            log("HEARD ${out.size} — " + out.joinToString(" · ") { "${it.token.hex.take(8)} ${it.rssi}" })
        }
        return out
    }

    override fun silence() {
        log("SILENCE — NOT ADVERTISING, NOT SCANNING")
        dispatch_async(queue) { board.silence() }
    }
}

/**
 * **The five [RadioHardware] calls, in CoreBluetooth**, and the callbacks back into [board]. Every
 * method here runs on [queue].
 */
private class CoreBluetoothHardware(
    private val queue: platform.darwin.dispatch_queue_t,
    private val log: (String) -> Unit,
) : RadioHardware {

    lateinit var board: SwitchboardRadio

    private var central: CBCentralManager? = null
    private var peripheral: CBPeripheralManager? = null

    /** Held so the scan cycle's timer is never collected; it runs on [queue] from [wake] onwards. */
    private var timer: platform.darwin.dispatch_source_t = null

    private val service: CBUUID = CBUUID.UUIDWithString(ContactAdvertisement.SERVICE_UUID)
    private val services: List<CBUUID> = listOf(service)

    /** Read once: every read of a CoreBluetooth string constant is a new Kotlin string. */
    private val serviceKey: String = CBAdvertisementDataServiceUUIDsKey

    /** The callback's scratch, native and Kotlin, allocated once for the life of the process. */
    private val heardNative = nativeHeap.allocArray<ByteVar>(ContactAdvertisement.TOKEN_BYTES)
    private val heard = ByteArray(ContactAdvertisement.TOKEN_BYTES)
    private val advertisedNative = nativeHeap.allocArray<ByteVar>(ContactAdvertisement.TOKEN_BYTES)

    /** CoreBluetooth holds its delegates weakly, so these are held here for the radio's lifetime. */
    private val scannerDelegate = object : NSObject(), CBCentralManagerDelegateProtocol {
        override fun centralManagerDidUpdateState(central: CBCentralManager) {
            board.scannerPower(powerOf(central.state))
        }

        override fun centralManager(
            central: CBCentralManager,
            didDiscoverPeripheral: CBPeripheral,
            advertisementData: Map<Any?, *>,
            RSSI: NSNumber,
        ) = discovered(advertisementData, RSSI)
    }

    private val advertiserDelegate = object : NSObject(), CBPeripheralManagerDelegateProtocol {
        override fun peripheralManagerDidUpdateState(peripheral: CBPeripheralManager) {
            board.advertiserPower(powerOf(peripheral.state))
        }

        override fun peripheralManagerDidStartAdvertising(peripheral: CBPeripheralManager, error: NSError?) {
            log(if (error == null) "ADVERTISING" else "ADVERTISING FAILED — ${error.localizedDescription}")
        }
    }

    override fun wake() {
        log("WAKE — THE MANAGERS ARE BUILT (ARMING)")
        central = CBCentralManager(scannerDelegate, queue, mapOf(CBCentralManagerOptionShowPowerAlertKey to false))
        peripheral = CBPeripheralManager(
            advertiserDelegate, queue, mapOf(CBPeripheralManagerOptionShowPowerAlertKey to false),
        )
        val cycle = ContactAdvertisement.SCAN_CYCLE_MILLIS * NANOS_PER_MILLI
        timer = dispatch_source_create(DISPATCH_SOURCE_TYPE_TIMER, 0u, 0u, queue)?.also { t ->
            dispatch_source_set_timer(t, dispatch_time(DISPATCH_TIME_NOW, cycle), cycle.toULong(), LEEWAY_NANOS)
            dispatch_source_set_event_handler(t) { board.cycle() }
            dispatch_resume(t)
        }
    }

    override fun advertise(token: ByteArray) {
        val manager = peripheral ?: return
        if (manager.isAdvertising) manager.stopAdvertising()
        for (j in 0 until ContactAdvertisement.TOKEN_BYTES) advertisedNative[j] = token[j]
        val data = NSData.dataWithBytes(advertisedNative, ContactAdvertisement.TOKEN_BYTES.toULong())
        // Two service UUIDs and nothing else: no local name key, so iOS sends none (D-212, D-218).
        manager.startAdvertising(
            mapOf(CBAdvertisementDataServiceUUIDsKey to listOf(service, CBUUID.UUIDWithData(data))),
        )
    }

    override fun stopAdvertising() {
        peripheral?.stopAdvertising()
    }

    /** Duplicates filtered (the default with no options), so each phone is reported once per cycle. */
    override fun startScan() {
        central?.scanForPeripheralsWithServices(services, null)
    }

    override fun stopScan() {
        central?.stopScan()
    }

    /**
     * **The hot path, once per report.** Finds the one 16-byte UUID among the advertised services,
     * copies it into the preallocated buffer and hands it over. No collection, no string, no
     * iterator is made here; what is allocated is the bridge's (see the file's KDoc).
     */
    private fun discovered(advertisementData: Map<Any?, *>, rssi: NSNumber) {
        val uuids = advertisementData[serviceKey] as? List<*> ?: return
        val count = uuids.size
        for (i in 0 until count) {
            val uuid = uuids[i] as? CBUUID ?: continue
            val data = uuid.data
            if (data.length != ContactAdvertisement.TOKEN_BYTES.toULong()) continue
            data.getBytes(heardNative, ContactAdvertisement.TOKEN_BYTES.toULong())
            for (j in 0 until ContactAdvertisement.TOKEN_BYTES) heard[j] = heardNative[j]
            board.sighted(heard, rssi.intValue)
            return
        }
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L

        /** The cycle may run this late; it is a scan restart, not a frame. */
        const val LEEWAY_NANOS = 25_000_000uL

        fun powerOf(state: Long): RadioPower = when (state) {
            CBManagerStatePoweredOn -> RadioPower.On
            CBManagerStatePoweredOff -> RadioPower.Off
            CBManagerStateUnauthorized -> RadioPower.Unauthorized
            CBManagerStateResetting -> RadioPower.Resetting
            CBManagerStateUnsupported -> RadioPower.Unsupported
            else -> RadioPower.Unknown
        }
    }
}
