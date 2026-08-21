package home.someoneshome.platform.transport

import kotlinx.cinterop.pointed
import kotlinx.cinterop.reinterpret
import platform.Foundation.NSData
import platform.Foundation.NSNetService
import platform.Foundation.NSNetServiceBrowser
import platform.Foundation.NSNetServiceBrowserDelegateProtocol
import platform.Foundation.NSNetServiceDelegateProtocol
import platform.Foundation.NSRunLoop
import platform.Foundation.NSRunLoopCommonModes
import platform.darwin.NSObject
import platform.posix.AF_INET
import platform.posix.sockaddr
import platform.posix.sockaddr_in

/**
 * Bonjour, via NSNetService.
 *
 * NSNetService is deprecated in favour of Network.framework's NWListener/NWBrowser and still
 * fully functional; it is used here because its Kotlin surface is a fraction of the C API's.
 * The day it actually goes, this file is the entire migration and the `expect` side does not
 * move. Browsing on iOS 14+ silently finds NOTHING unless the service type is listed under
 * `NSBonjourServices` in Info.plist — a fail-closed OS behaviour worth knowing when a browser
 * runs forever in silence.
 *
 * ### Every failure callback is implemented, and that is the lesson of the first device run
 *
 * The first version wired only the success paths. On phones, a publish that is refused and a
 * search that is denied both report through delegate methods — `didNotPublish`, `didNotSearch`
 * — and with those unimplemented the whole feature fails as *silence*: no error, no log line,
 * two people staring at phones that say nothing. [onEvent] exists so the failure is loud at the
 * surface that started the operation.
 */
actual class HostAdvertiser actual constructor(
    name: String,
    port: Int,
    private val onEvent: (String) -> Unit,
) {

    private val service = NSNetService(
        domain = "local.",
        type = DISCOVERY_SERVICE_TYPE,
        name = name,
        port = port,
    )

    private val listener = object : NSObject(), NSNetServiceDelegateProtocol {
        override fun netServiceDidPublish(sender: NSNetService) {
            onEvent("ADVERTISED AS '${sender.name}'")
        }

        override fun netService(sender: NSNetService, didNotPublish: Map<Any?, *>) {
            onEvent("ADVERTISE FAILED $didNotPublish")
        }
    }

    actual fun start() {
        service.delegate = listener
        service.scheduleInRunLoop(NSRunLoop.mainRunLoop, NSRunLoopCommonModes)
        service.publish()
    }

    actual fun stop() {
        service.stop()
    }
}

actual class HostBrowser actual constructor(
    private val onFound: (name: String, address: String, port: Int) -> Unit,
    private val onEvent: (String) -> Unit,
) {

    private val browser = NSNetServiceBrowser()

    /** Services being resolved are retained here — NSNetServiceBrowser does not hold them. */
    private val resolving = mutableListOf<NSNetService>()

    private val listener = object : NSObject(), NSNetServiceBrowserDelegateProtocol, NSNetServiceDelegateProtocol {

        override fun netServiceBrowser(
            browser: NSNetServiceBrowser,
            didFindService: NSNetService,
            moreComing: Boolean,
        ) {
            didFindService.delegate = this
            resolving.add(didFindService)
            didFindService.resolveWithTimeout(5.0)
        }

        override fun netServiceBrowser(
            browser: NSNetServiceBrowser,
            didNotSearch: Map<Any?, *>,
        ) {
            onEvent("SEARCH FAILED $didNotSearch")
        }

        override fun netServiceDidResolveAddress(sender: NSNetService) {
            resolving.remove(sender)
            val candidates = sender.addresses?.mapNotNull { ipv4Of(it as NSData) } ?: return
            // The address list's order is not a preference. The first simulator run resolved
            // itself to 169.254.x.x — link-local, useless off its own segment — with the real
            // address sitting behind it. Routable first; link-local only when it is all there is.
            val address = candidates.firstOrNull { !it.startsWith("169.254.") }
                ?: candidates.firstOrNull()
                ?: return
            onFound(sender.name, address, sender.port.toInt())
        }

        override fun netService(sender: NSNetService, didNotResolve: Map<Any?, *>) {
            resolving.remove(sender)
            onEvent("RESOLVE FAILED FOR '${sender.name}' $didNotResolve")
        }
    }

    actual fun start() {
        browser.delegate = listener
        browser.scheduleInRunLoop(NSRunLoop.mainRunLoop, NSRunLoopCommonModes)
        browser.searchForServicesOfType(DISCOVERY_SERVICE_TYPE, inDomain = "local.")
    }

    actual fun stop() {
        browser.stop()
    }
}

/** The dotted-quad of an AF_INET sockaddr, or null for anything else (v6 is skipped, not parsed). */
private fun ipv4Of(data: NSData): String? {
    val bytes = data.bytes ?: return null
    if (data.length.toInt() < 8) return null
    val family = bytes.reinterpret<sockaddr>().pointed.sa_family.toInt()
    if (family != AF_INET) return null
    val ip = bytes.reinterpret<sockaddr_in>().pointed.sin_addr.s_addr
    return listOf(
        ip and 0xffu,
        (ip shr 8) and 0xffu,
        (ip shr 16) and 0xffu,
        (ip shr 24) and 0xffu,
    ).joinToString(".")
}
