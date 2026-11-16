package home.someoneshome.platform

import home.someoneshome.model.ContactToken
import home.someoneshome.model.ContactTokens
import home.someoneshome.model.RadioCause
import home.someoneshome.model.protocol.SightingUpload

/*
 * **The real contact radio, everything about it that is not CoreBluetooth** (D-218).
 *
 * The iOS actual (`BluetoothRadio.ios.kt`) is a thin layer: it builds the managers, turns their
 * callbacks into the four calls on [SwitchboardRadio], and turns [RadioHardware]'s five calls into
 * CoreBluetooth. Every decision the radio makes is here, in common code, behind that seam, because
 * the Simulator has no Bluetooth and this is the only way any of it is tested before a phone.
 */

/**
 * **What goes on the air, and how often the scan is turned over** (D-218).
 *
 * The advertisement is two service UUIDs and nothing else: [SERVICE_UUID], which is the same on
 * every phone in every round, and this window's token carried whole as a 128-bit UUID. No local
 * name, no seat, no role, no armed bit (D-212 ruling 5). The token is the only thing in it that
 * differs between phones, and it rotates every window.
 *
 * ### Why the service UUID is 16 bits
 *
 * iOS gives a foreground app 28 bytes of advertisement and accepts only service UUIDs and a local
 * name. A 128-bit list costs 2 + 16n bytes, so two 128-bit UUIDs (34 bytes) do not fit and iOS puts
 * the one that does not fit into its overflow area, where only a phone scanning for that exact UUID
 * finds it: a token nobody knows in advance would be unreadable. A 16-bit list costs 4, so a
 * 16-bit service UUID and a 128-bit token fit in 22.
 *
 * ⚠️ **[SERVICE_UUID] is a placeholder and must be settled before release.** 16-bit UUIDs belong to
 * the Bluetooth SIG; this one is unassigned, not ours. It is fine for a playtest in a living room
 * and not fine on the App Store. The alternatives are a SIG member UUID, or a 128-bit service UUID
 * with a shorter token beside it (which moves the truncation into `ContactTokens.of`, as
 * `ContactToken`'s own KDoc anticipates). Both change what the radio emits, so both are the owner's.
 */
object ContactAdvertisement {

    /** The game's service UUID, as CoreBluetooth spells a 16-bit one. Placeholder: see above. */
    const val SERVICE_UUID: String = "5348"

    /** The token is carried whole: one 128-bit UUID is sixteen bytes. */
    const val TOKEN_BYTES: Int = ContactTokens.TOKEN_BYTES

    /**
     * **The scan is stopped and started again this often, with duplicate filtering on** (D-218).
     *
     * Every callback CoreBluetooth makes into Kotlin/Native allocates (rule 7), so the callback rate
     * is the radio's allocation rate, and with duplicates allowed that rate is the OS's, not ours.
     * With duplicates filtered, iOS reports each phone once per scan; turning the scan over every
     * 250 ms reports each phone at most four times a second, which bounds the rate at 4 × peers
     * whatever the OS does. The scan is never off for longer than the restart, so a living phone
     * still scans continuously (D-212 ruling 3).
     *
     * ⚠️ One named value so that unit 8's on-device measurement changes one line. If a device shows a
     * phone reported once and never again, iOS is not resetting its filter on a restart, and the
     * fallback is duplicates on with no cycle: the reports come at the OS's rate, which is more
     * allocation and less latency.
     */
    const val SCAN_CYCLE_MILLIS: Long = 250L

    /** How many cycles between the radio's tally lines in the device log: every ten seconds. */
    const val TALLY_CYCLES: Int = 40
}

/** A CoreBluetooth manager's state, as the radio needs to know it. */
enum class RadioPower {
    /** Not yet answered. The state every manager starts in; not an outage. */
    Unknown,
    On,
    Off,
    Unauthorized,
    Resetting,
    Unsupported,
}

/** Which of the two managers spoke. The advertiser is the peripheral, the scanner the central. */
enum class RadioSide { Advertiser, Scanner }

/**
 * **The CoreBluetooth layer, as the switchboard drives it.** Five calls, all made on the radio's
 * own queue. The iOS actual implements it; the tests implement it with a recorder.
 */
interface RadioHardware {

    /**
     * Build the managers. Called once, at the first [SwitchboardRadio.advertise] or
     * `scan(true)`, which the uploader makes only once the house has issued a seed at arming: **no
     * manager exists before arming** (D-067), so nothing is on the air in the lobby and no
     * Bluetooth prompt can be raised there.
     */
    fun wake()

    /**
     * Replace whatever is advertised with [token] ([ContactAdvertisement.TOKEN_BYTES] bytes) under
     * [ContactAdvertisement.SERVICE_UUID], and nothing else. Only called while the advertiser is on.
     */
    fun advertise(token: ByteArray)

    fun stopAdvertising()

    /** Scan for [ContactAdvertisement.SERVICE_UUID] only. Only called while the scanner is on. */
    fun startScan()

    fun stopScan()
}

/**
 * **What one window heard, coalesced into a fixed number of slots: one per token, the strongest
 * reading kept** (D-218, rule 7).
 *
 * A fixed table rather than a list: [heard] runs once per CoreBluetooth callback and allocates
 * nothing. The arrays are sized once, at [capacity], which is the upload's slot count, because the
 * upload carries no more than that and the strongest are the ones that decide a contact.
 *
 * - **One slot per token.** A phone heard forty times between two drains is one reading, at its
 *   strongest.
 * - **A reading of zero or more is no reading.** iOS reports 127 when it has none. It is kept only
 *   when nothing better was heard for that token, so it can never displace a real one, and the
 *   house drops it (D-213's ceiling) the way it would have dropped it anyway.
 * - **Full, the weakest gives way to a stronger newcomer**, and a newcomer no stronger than the
 *   weakest held is the one refused. Order is first heard, oldest first.
 *
 * Not thread-safe: the iOS radio confines it to its own queue.
 */
class SightingRing(val capacity: Int = SightingUpload.SLOTS) {

    private val tokens = ByteArray(capacity * WIDTH)
    private val rssis = IntArray(capacity)

    /** Slots in use since the last [drain]. */
    var size: Int = 0
        private set

    /** Readings folded into a token already held. For the tally. */
    var coalesced: Long = 0L
        private set

    /** Readings lost to a full table, whichever side of the comparison lost. For the tally. */
    var displaced: Long = 0L
        private set

    /** One reading: the first [WIDTH] bytes of [token], at [rssi]. Allocates nothing. */
    fun heard(token: ByteArray, rssi: Int) {
        val at = indexOf(token)
        if (at >= 0) {
            coalesced++
            if (strength(rssi) > strength(rssis[at])) rssis[at] = rssi
            return
        }
        if (size < capacity) {
            write(size, token, rssi)
            size++
            return
        }
        displaced++
        val weakest = weakest()
        if (strength(rssi) <= strength(rssis[weakest])) return
        tokens.copyInto(tokens, weakest * WIDTH, (weakest + 1) * WIDTH, size * WIDTH)
        rssis.copyInto(rssis, weakest, weakest + 1, size)
        write(size - 1, token, rssi)
    }

    /** Everything held, oldest first, and the table emptied. The one allocation, once a beat. */
    fun drain(): List<RadioReading> {
        val out = ArrayList<RadioReading>(size)
        for (i in 0 until size) out.add(RadioReading(ContactToken(hexOf(i)), rssis[i]))
        size = 0
        return out
    }

    /** Forget everything held. */
    fun clear() {
        size = 0
    }

    private fun indexOf(token: ByteArray): Int {
        for (i in 0 until size) {
            var same = true
            for (j in 0 until WIDTH) {
                if (tokens[i * WIDTH + j] != token[j]) {
                    same = false
                    break
                }
            }
            if (same) return i
        }
        return -1
    }

    private fun weakest(): Int {
        var at = 0
        for (i in 1 until size) if (strength(rssis[i]) < strength(rssis[at])) at = i
        return at
    }

    private fun write(slot: Int, token: ByteArray, rssi: Int) {
        token.copyInto(tokens, slot * WIDTH, 0, WIDTH)
        rssis[slot] = rssi
    }

    private fun hexOf(slot: Int): String {
        val chars = CharArray(WIDTH * 2)
        for (j in 0 until WIDTH) {
            val v = tokens[slot * WIDTH + j].toInt() and 0xff
            chars[2 * j] = HEX[v ushr 4]
            chars[2 * j + 1] = HEX[v and 0xf]
        }
        return chars.concatToString()
    }

    private companion object {
        const val WIDTH = ContactAdvertisement.TOKEN_BYTES
        const val HEX = "0123456789abcdef"

        /** A reading of zero or more is no reading (iOS's 127): weaker than any real one. */
        fun strength(rssi: Int): Int = if (rssi >= 0) Int.MIN_VALUE else rssi
    }
}

/**
 * **The contact radio's decisions, over any [RadioHardware]** (D-218).
 *
 * It holds what the uploader asked for — a token to advertise, whether to scan — and what the two
 * managers last said about their power, and it puts the hardware wherever both allow. So a phone
 * whose Bluetooth comes back after a reset is advertising and scanning again without anybody
 * asking twice, and a phone told to stop scanning stays stopped through every reset there is.
 *
 * **It has no opinion on which phone it is in** (D-217). Scanning follows the last `scan` call and
 * nothing else; the uploader makes that call off the house's pushes. **A Revoked or Restrained phone
 * advertises and never scans** (D-008, story 9.4): told `scan(false)`, nothing here starts the
 * scanner again until `scan(true)`, and readings already in flight are dropped on arrival.
 *
 * ### Outages: silent to the player, loud to the house (rule 6; T2, D-222; D-223)
 *
 * A manager going Off, Unauthorized, Resetting or Unsupported is reported to [outages] and to the
 * device log, and the lamp, the screen and the haptics never hear of it. **The house is told once
 * per fault onset**: the radio is *down* while either manager is in one of those four, and the moment
 * it goes from up to down is one onset, however many callbacks follow (both managers report a
 * Bluetooth switch-off, and a reset passes through Resetting and Off). [faultBegan] hands that
 * onset to the uploader once, which sends it up as `RoundBody.RadioFaulted`. Both managers back On is
 * up again, so a second switch-off is a second onset. A radio still down when a new round arms is
 * an onset in that round.
 *
 * **The onset says which side of D-187's line it is on, decided once, at the onset** ([causeOf]):
 * Off or Unauthorized is a person's act, Resetting or Unsupported is the phone's own. The cause holds
 * for the whole outage, so a reset that passes through Off is still a reset and is never named.
 * ⚠️ Limit: a manager whose reset callbacks arrive after the other has already come back up would
 * start a second onset, and if that one opened on Off it would read as a person's. The device check
 * cannot induce a reset on demand, so this is reasoned rather than observed.
 *
 * Not thread-safe: every call is made on the radio's own queue on iOS, and on the test's thread in
 * the tests.
 */
class SwitchboardRadio(
    private val hardware: RadioHardware,
    private val log: (String) -> Unit = {},
    private val outages: (RadioSide, RadioPower) -> Unit = { _, _ -> },
) : ContactRadio {

    private val ring = SightingRing()
    private val token = ByteArray(ContactAdvertisement.TOKEN_BYTES)
    private var hasToken = false
    private var scanning = false
    private var awake = false
    private var advertiser = RadioPower.Unknown
    private var scanner = RadioPower.Unknown

    /** Whether either manager is down, as last assessed. See the class KDoc. */
    private var down = false

    /** A fault began, and why, and the uploader has not collected it yet. */
    private var began: RadioCause? = null

    private var cycles = 0
    private var callbacks = 0L
    private var drained = 0L

    /** Whether the managers have been built. False until the first advertise or `scan(true)`. */
    val woken: Boolean get() = awake

    override fun advertise(token: ContactToken) {
        wake()
        val hex = token.hex
        for (j in 0 until ContactAdvertisement.TOKEN_BYTES) {
            this.token[j] = ((digit(hex[2 * j]) shl 4) or digit(hex[2 * j + 1])).toByte()
        }
        hasToken = true
        if (advertiser == RadioPower.On) hardware.advertise(this.token)
        assess()
    }

    override fun scan(on: Boolean) {
        if (on) wake()
        if (on == scanning) return
        scanning = on
        if (!on) ring.clear()
        if (scanner != RadioPower.On) return
        if (on) hardware.startScan() else hardware.stopScan()
    }

    override fun drain(): List<RadioReading> {
        val out = ring.drain()
        drained += out.size
        return out
    }

    override fun silence() {
        hasToken = false
        scanning = false
        ring.clear()
        // The round is over and so is what it was told. A radio still down at the next arming is a
        // fault of that round, found again by the first advertise.
        down = false
        began = null
        if (!awake) return
        if (advertiser == RadioPower.On) hardware.stopAdvertising()
        if (scanner == RadioPower.On) hardware.stopScan()
    }

    override fun faultBegan(): RadioCause? = began.also { began = null }

    /** The advertiser's power changed. On, it takes up whatever was asked of it. */
    fun advertiserPower(power: RadioPower) {
        if (power == advertiser) return
        advertiser = power
        assess()
        if (power == RadioPower.On) {
            if (hasToken) hardware.advertise(token)
        } else {
            outage(RadioSide.Advertiser, power)
        }
    }

    /** The scanner's power changed. On, it scans if it was asked to. */
    fun scannerPower(power: RadioPower) {
        if (power == scanner) return
        scanner = power
        assess()
        if (power == RadioPower.On) {
            if (scanning) hardware.startScan()
        } else {
            outage(RadioSide.Scanner, power)
        }
    }

    /**
     * **One reading, from the scanner's callback.** [token] is read and not kept; allocates nothing.
     * Dropped unless this phone is scanning, so a reading in flight when the house put this phone
     * out never reaches an upload.
     */
    fun sighted(token: ByteArray, rssi: Int) {
        if (!scanning) return
        callbacks++
        ring.heard(token, rssi)
    }

    /** **The scan cycle's tick** ([ContactAdvertisement.SCAN_CYCLE_MILLIS]): turn the scan over. */
    fun cycle() {
        if (scanning && scanner == RadioPower.On) {
            hardware.stopScan()
            hardware.startScan()
        }
        if (++cycles % ContactAdvertisement.TALLY_CYCLES == 0 && awake) tally()
    }

    /**
     * **One fault onset, however many callbacks** (T2, D-222). Down is either manager Off,
     * Unauthorized, Resetting or Unsupported; Unknown is not yet answered and is not down. Only a
     * round's radio reports: before the first advertise of a round there is nobody to tell.
     */
    private fun assess() {
        val now = isDown(advertiser) || isDown(scanner)
        if (now && !down && hasToken) {
            val cause = causeOf(advertiser, scanner)
            began = cause
            log(
                "FAULT — THE RADIO WENT DOWN (ADVERTISER $advertiser · SCANNER $scanner) · " +
                    "${cause.name.uppercase()} · THE HOUSE IS TOLD ONCE",
            )
        }
        down = now && hasToken
    }

    private fun wake() {
        if (awake) return
        awake = true
        hardware.wake()
    }

    /**
     * **The one place a radio outage is reported** (rule 6). To the device log and to [outages], and
     * never to the lamp, a screen or a buzz.
     *
     * The house hears of it through [faultBegan], once per onset (T2, D-222), not from here: this
     * is called once per manager per change.
     */
    private fun outage(side: RadioSide, power: RadioPower) {
        if (power == RadioPower.Unknown) return
        log("OUTAGE — ${side.name.uppercase()} ${power.name.uppercase()}")
        outages(side, power)
    }

    /**
     * Every ten seconds, in the device log: the callback rate is the radio's allocation rate.
     *
     * TODO(unit 8): `AllocationBudget`'s counter is not wired here. It counts the whole process, it
     * only advances when a collection runs, and reading it allocates a `GCInfo`, so sampled from this
     * queue it would cost more than the radio and still not say what the radio spent. The radio's own
     * share is callbacks × bytes per callback, and unit 8 measures the second factor on a phone.
     */
    private fun tally() {
        val seconds = ContactAdvertisement.SCAN_CYCLE_MILLIS * ContactAdvertisement.TALLY_CYCLES / 1_000
        log(
            "TALLY ${seconds} S — CALLBACKS $callbacks · KEPT $drained · COALESCED ${ring.coalesced} · " +
                "DISPLACED ${ring.displaced} · SCANNING ${if (scanning) "YES" else "NO"} · " +
                "ADVERTISING ${if (hasToken) "YES" else "NO"} · ADVERTISER $advertiser · SCANNER $scanner",
        )
        callbacks = 0L
        drained = 0L
    }

    private companion object {
        fun digit(c: Char): Int = if (c <= '9') c - '0' else c - 'a' + 10

        fun isDown(power: RadioPower): Boolean = when (power) {
            RadioPower.Off, RadioPower.Unauthorized, RadioPower.Resetting, RadioPower.Unsupported -> true
            RadioPower.Unknown, RadioPower.On -> false
        }
    }
}

/**
 * **D-187's line, drawn on the radio: was this outage a person's act, or the phone's own?** (D-223)
 *
 * Asked once, at the onset, of whichever managers are down then. Off or Unauthorized is
 * [RadioCause.Person]: Bluetooth switched off, or its permission withdrawn, and every grant was
 * given at setup (D-161), so one missing in a round is something somebody did. Resetting or
 * Unsupported is [RadioCause.Device], and **the phone's own failure wins** when the two managers
 * disagree, because naming a player whose phone failed is the injustice D-187 exists to prevent and
 * missing one evasion is not. On and Unknown are not down and say nothing.
 */
fun causeOf(advertiser: RadioPower, scanner: RadioPower): RadioCause {
    // Exhaustive, so a state the OS adds later has to be put on one side of the line by somebody.
    fun failed(power: RadioPower) = when (power) {
        RadioPower.Resetting, RadioPower.Unsupported -> true
        RadioPower.Off, RadioPower.Unauthorized, RadioPower.On, RadioPower.Unknown -> false
    }
    return if (failed(advertiser) || failed(scanner)) RadioCause.Device else RadioCause.Person
}

/**
 * **This phone's contact radio** (D-218). On iOS, CoreBluetooth behind a [SwitchboardRadio], on a
 * queue of its own. Building one puts nothing on the air: the managers are built at the first
 * advertise or `scan(true)`, which is arming (D-067).
 *
 * [log] is the device log and only the device log (stdout, which `devicectl --console` carries).
 * Never a screen: a line about the radio on a screen is a screen that changed for a reason the
 * house did not author (rules 5 and 6).
 */
expect fun bluetoothContactRadio(log: (String) -> Unit): ContactRadio
