package home.someoneshome.platform.transport

/**
 * The service type on the local network. Part of the wire contract in spirit: two builds that
 * disagree on this string cannot find each other, so it changes with [home.someoneshome.model.protocol.TRANSPORT_PROTOCOL]
 * ceremony, not casually.
 */
const val DISCOVERY_SERVICE_TYPE: String = "_someones-home._tcp."

/**
 * D1's discovery half: mDNS, the one platform-specific piece of the transport.
 *
 * The host advertises; a phone browses; nobody types an IP in a dark house. This also closes
 * D-094 properly — a relaunched phone's stored address (the interim) can be wrong after a DHCP
 * lease change, but the advertised service is wherever the host actually is right now.
 *
 * ### The names are data on the air
 *
 * The instance [name] is broadcast to everyone on the network, before any permission the app
 * controls. It must never carry anything about a round: no roles, no seats, no state. A house
 * name a host chose is fine; anything the rules produced is not.
 *
 * Both classes must be started from the main thread — the iOS actual schedules on the main run
 * loop, and callbacks arrive there.
 */
expect class HostAdvertiser(name: String, port: Int, onEvent: (String) -> Unit) {
    fun start()
    fun stop()
}

/**
 * Finds advertised hosts. [onFound] fires once per resolved service with its instance name,
 * IPv4 address and port — the name is passed so a caller can tell the host it wants from a
 * neighbour's, and so tests can ignore services that are not theirs.
 */
expect class HostBrowser(
    onFound: (name: String, address: String, port: Int) -> Unit,
    /** Publish/search/resolve failures, in words. Silence was the first device run's only symptom. */
    onEvent: (String) -> Unit,
) {
    fun start()
    fun stop()
}
