package home.someoneshome.platform.transport

/**
 * The service type on the local network. Part of the wire contract in spirit: two builds that
 * disagree on this string cannot find each other, so it changes with [home.someoneshome.model.protocol.TRANSPORT_PROTOCOL]
 * ceremony, not casually.
 */
const val DISCOVERY_SERVICE_TYPE: String = "_someones-home._tcp."

/**
 * **Why an advertisement never went out.**
 *
 * Two members and not the OS's error list, because the host is told this and a host cannot act on
 * a number. The split is the one that changes what they do next: rename the home, or look at the
 * network.
 */
enum class AdvertiseFailure {

    /** A home of this name is already on the air here. Bonjour's collision, [BONJOUR_COLLISION]. */
    NameAlreadyOnTheAir,

    /** The advertisement was refused for any other reason. */
    Refused,
}

/**
 * Bonjour's collision code, as seen on two phones in one house on 2026-08-21 (E-D3-2).
 *
 * `NSNetServicesCollisionError`. Written as a number rather than reached for through Foundation
 * so that the mapping below is `commonMain` and can be tested without a network stack under it.
 */
const val BONJOUR_COLLISION: Int = -72001

/** What the host is told, from whatever the OS refused with. An absent code is still a refusal. */
fun advertiseFailureOf(code: Int?): AdvertiseFailure =
    if (code == BONJOUR_COLLISION) AdvertiseFailure.NameAlreadyOnTheAir else AdvertiseFailure.Refused

/**
 * DNS-SD's `kDNSServiceErr_PolicyDenied`: the code a browse is expected to fail with when Local
 * Network is off (D-309).
 *
 * ⚠️ **Provisional until a phone shows it.** Whether `NSNetServiceBrowser` reports this number in
 * `NSNetServicesErrorCode` or folds it into one of its own `NSNetServicesErrorCode` values is not
 * known here, so nothing branches on it: every search failure reads as LOCAL NETWORK IS OFF, and the
 * code is logged ([searchFailureLine]) for the phone test to settle.
 */
const val DNS_SD_POLICY_DENIED: Int = -65570

/** The log line for a refused search: the code, and whether it is [DNS_SD_POLICY_DENIED]. */
fun searchFailureLine(code: Int?, details: String): String =
    "SEARCH FAILED code=${code ?: "none"}" +
        (if (code == DNS_SD_POLICY_DENIED) " (POLICY DENIED: LOCAL NETWORK IS OFF)" else " (READ AS LOCAL NETWORK IS OFF, PROVISIONAL)") +
        " $details"

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
 * ### Publishing has two ends and both of them are callbacks
 *
 * [onAir] fires when the service is really published and [onFailed] when it is really refused —
 * exactly one of them, and neither at `start()`. A caller that treated `start()` as success would
 * be a lobby that goes up whether or not anybody can find it, which is E-D3-2: on 2026-08-21 a
 * second house called THE BUNGALOW was refused with [BONJOUR_COLLISION], was **never published at
 * all**, and its host sat in a one-seat lobby with no reason given.
 *
 * Both classes must be started from the main thread — the iOS actual schedules on the main run
 * loop, and callbacks arrive there.
 */
expect class HostAdvertiser(
    name: String,
    port: Int,
    onEvent: (String) -> Unit,
    onAir: () -> Unit = {},
    onFailed: (AdvertiseFailure) -> Unit = {},
) {
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
    /**
     * **The OS refused the search** (D-309), with its error code when one could be read. The search
     * is over; [stop] and [start] to look again. Every refusal is read as Local Network off; see
     * [DNS_SD_POLICY_DENIED].
     */
    onRefused: (code: Int?) -> Unit = {},
) {
    fun start()
    fun stop()
}
