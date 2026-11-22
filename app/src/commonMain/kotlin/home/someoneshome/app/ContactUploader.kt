package home.someoneshome.app

import home.someoneshome.model.ContactSeed
import home.someoneshome.model.ContactTokens
import home.someoneshome.model.ContactWindow
import home.someoneshome.model.DevicePermission
import home.someoneshome.model.RadioCause
import home.someoneshome.model.outranking
import home.someoneshome.model.Sighting
import home.someoneshome.model.protocol.OccupancyBeat
import home.someoneshome.model.protocol.RangingCodec
import home.someoneshome.model.protocol.RangingHandle
import home.someoneshome.model.protocol.RangingWire
import home.someoneshome.model.protocol.MainViewField
import home.someoneshome.model.protocol.ReportSlot
import home.someoneshome.model.protocol.SpareKey
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.model.protocol.SightingUpload
import home.someoneshome.model.protocol.UploadProbe
import home.someoneshome.platform.ContactRadio
import home.someoneshome.platform.NoRanging
import home.someoneshome.platform.RangingRadio
import home.someoneshome.platform.secureRandomBytes

/**
 * **The phone's half of the contact handshake: advertise this window's token, and say what was
 * heard, once a beat, at one size** (D-212, D-217).
 *
 * It holds three facts and every one of them came down this phone's own socket: the seed the house
 * issued it at arming (`ContactSeedIssued`), whether the house has put it out (`RevokeLanded`,
 * `RestrainedTakeover`), and whether the round is over (`RoundEnded`). **It never holds a role or an
 * armed state, and it could not**: nothing it is told carries either, and nothing it sends depends
 * on anything but those three facts and what the radio heard (D-212 ruling 5).
 *
 * ### One upload per beat, from every phone in a round, at one size
 *
 * [beat] is called on `SightingUpload.CADENCE_MILLIS` by whoever owns the clock. Every call in a
 * round sends exactly one `RoundBody.Sighted`, packed to `SightingUpload.BODY_CHARS` whatever was
 * heard (D-212 ruling 3). Each upload also carries this beat's request slot ([ask], D-263): every
 * request the player makes in a round rides here rather than as a frame of its own.
 *
 * ### R8: a phone that is out keeps uploading, and uploads nothing heard
 *
 * The owner's 2026-11-15 ruling 8. A Revoked or Restrained phone stops scanning and keeps
 * advertising (D-008), and **keeps uploading on the same cadence**, with nothing in it but filler. A
 * phone that fell silent when it went out would tell the Wi-Fi who is out, and a Revoke is the one
 * fact the house keeps until somebody finds the body of it. What the radio buffered is drained and
 * dropped every beat, so a reading taken a moment before the house's push cannot ride out later.
 *
 * ### A radio fault goes up on the beat after it begins (T2, D-222; D-223)
 *
 * The radio reports an onset once, with its cause ([ContactRadio.faultBegan]), and so does the UWB
 * radio ([RangingRadio.faultBegan], D-303: Nearby Interaction refused is the person's, the session
 * limit or a failed session the device's); the next beat sends
 * one `RoundBody.RadioFaulted` carrying that cause and the onset's name, the same from every phone
 * and the same length whichever the cause. Before arming and after the round nothing is beaten, so
 * nothing is sent.
 *
 * ### A phone that leaves the foreground mid-round says so once, when it is back (D-187, D-229)
 *
 * Locking the phone or leaving the app is D-187's evasion: with no background modes (D-221) the app
 * is suspended, so its radio falls silent and its socket with it. [leftTheForeground] marks the
 * onset, in a round only; [backInTheForeground] makes the report due; the next beat sends it as the
 * same `RoundBody.RadioFaulted` with [RadioCause.Person] that a Bluetooth switch-off sends (D-223),
 * because that is what it is: this phone's radio down by its owner's hand. One body, so one length,
 * from every phone whatever it is, the host's own seat included. However many times the phone leaves
 * and returns between two beats, it is one report; and the radio's own onset on that beat is folded
 * into it, so one beat never sends two. Before arming, after the round, and on a phone that relaunched
 * into its seat after a crash in the foreground (a crash is not a person's act, D-036), nothing is
 * owed.
 *
 * ### A permission switched off is told apart from leaving the app (D-310)
 *
 * The owner's 2026-11-20 ruling: only a permission switched off counts toward closing the house, and
 * leaving the app never does. Both reach the house as this one body, told apart by its cause alone.
 * [RadioCause.Permission] is sent where a radio says its permission was withdrawn (Bluetooth
 * unauthorized, Nearby Interaction not allowed: every phone was seated with both GRANTED, D-309), and
 * where [refusedGrants], read every beat, has a grant switched off that was not switched off at
 * arming. Every grant is changed in Settings and going there is leaving the app, so a trip to
 * Settings is a leave whose report, if it is not sent yet, becomes a permission's on the first beat
 * that reads the grant off; one already sent is followed by a permission's report of its own. iOS
 * ends the app for some grants (Camera, Motion), so what this phone has already reported switched
 * off this round is kept beside the owed reports, and a relaunch reads it back rather than telling
 * the house again (D-311, the overnight review's finding 1). One body of one length either way
 * (D-217), and what decides the cause stays inside this phone.
 *
 * ### One report a grant, whoever heard it (D-311)
 *
 * The owner's 2026-11-21 rulings: the Bluetooth radio turned off counts exactly as a permission
 * switched off, and a phone that reaches a round missing one is named (no grant is excused for
 * being off at arming any more). One switch-off can be heard three ways, by the contact radio, by
 * the ranging radio and by the grants read, so each is keyed by its grant's bit ([reported]) and
 * goes up once: a second report of the same switch-off after the warning would close the house for
 * something the room was already warned about.
 *
 * ### Every report is kept until the house says it has it (D-245)
 *
 * A report goes up the socket this phone has, and that socket may be defunct without the phone knowing
 * yet: a guest's connection that failed while it was suspended takes the frame with it
 * (`TransportClient` is at-most-once). And the process may end under it: iOS ends an app whose
 * Camera or Motion grant is withdrawn in Settings, which is a person's leave. So each onset is a
 * report with its own random name (`RoundBody.RadioFaulted.onset`), **kept** from the moment it is
 * owed until the house's `RoundBody.FaultHeard` names it, and **sent again on every seating** until
 * then, one body a beat. The house hears each onset once however often it arrives. A report owed at
 * a leave is written to [kept] (beside the seat token) at the leave itself, before the phone is
 * suspended, so a relaunched process reads it back when the round's seed arrives on its resume and
 * owes it still. A crash in the foreground writes nothing, so D-036 holds.
 *
 * ### UWB rides the same beat (U6, D-300)
 *
 * Every upload also carries this phone's ranging report ([ranging]): its capacity, its idle
 * sessions' tokens and, for each pair it ranges, the nearest distance this second, sealed under the
 * seed to one width. The house's answer carries the plan back, and [planned] applies it: stops
 * first, then starts. **A phone that is out keeps ranging and keeps reporting its distances**, unlike
 * its Bluetooth sightings (R8): a Revoked phone must stay measurable for the report, and a phone
 * that dropped its readings would leave the handles it was dealt unanswered in its uploads, a
 * difference anybody holding its seed could read. Every rule reads the distances (D-301, D-308).
 *
 * ### Why the one early exit here is not rule 1's
 *
 * [beat] sends nothing before a seed has arrived, which is before arming and after the round has
 * ended: every phone is in that state at the same moments and for the same reason, so its absence
 * tells nobody anything about anybody. Inside a round it never declines.
 */
class ContactUploader(
    private val radio: ContactRadio,
    /** The window this phone is in now. The wall clock in production (`wallClockMillis`). */
    private val windowNow: () -> ContactWindow,
    /**
     * **This phone's radio went down: say so, once, and why** (T2, D-222; D-223). Up this phone's
     * own socket as the one fixed body `RoundBody.RadioFaulted(cause, onset)`. Called on the beat
     * after the radio reports an onset, in a round only, from every phone alike, out or not; and
     * again on each new connection until the house answers (D-245).
     */
    private val faulted: (RoundBody.RadioFaulted) -> Unit = {},
    /**
     * **Where the reports this phone owes are kept across the process** (D-245): beside the seat
     * token. [DeviceSeat] on a phone; an object of its own, which fails with this one, by default.
     */
    private val kept: SeatStore = MemorySeat(),
    /** A fresh onset's random name, `RoundBody.RadioFaulted.ONSET_CHARS` hex characters. */
    private val onsetName: () -> String = ::freshOnset,
    /** This phone's log, and only its log: a report read back, or a store that would not write. */
    private val onEvent: (String) -> Unit = {},
    /**
     * **This beat's request slot** (D-263): `SlotRequests.slot` on a phone, read once per upload, so
     * every request this phone makes in a round rides inside the upload it was going to send anyway.
     * An empty slot by default, for a construction with nothing to ask.
     */
    private val ask: () -> String = { SightingUpload.EMPTY_ASK },
    /**
     * **This beat's clock probe** (D-295): `UploadClock.stamp` on a phone, drawn once per upload,
     * so every upload is one round trip on the house's clock. No probe by default, for a
     * construction with no clock to refresh; the width is the same either way.
     */
    private val probe: () -> String = { UploadProbe.NONE },
    /**
     * **This phone's UWB radio** (U6, D-300): its pool's capacity and spare tokens and its nearest
     * distances go up in every upload, and the house's plan comes back to it. [NoRanging] by
     * default, which reports nothing at the same width.
     */
    private val ranging: RangingRadio = NoRanging,
    /** A fresh nonce for each upload's ranging report: `RangingWire.NONCE_BYTES` secure random bytes. */
    private val rangingNonce: () -> ByteArray = { secureRandomBytes(RangingWire.NONCE_BYTES) },
    /**
     * **Whether this phone is on the main view, now** (D-307): `FlowModel.onMainView` on a phone,
     * asked once per upload and sent as its one-character field (`MainViewField`), so the house's
     * report hold can start and run only while the person is on springboard page 1 or 2 (or the
     * report screen). Off by default, for a construction with no screens:
     * such a phone starts no hold, which is the direction a forgotten wire fails in.
     */
    private val mainView: () -> Boolean = { false },
    /**
     * **The grants that read switched off now, one bit a row** (D-310): `Permissions.refusedGrants`
     * on a phone, read at arming and once a beat. None by default, for a construction with no OS
     * behind it.
     */
    private val refusedGrants: () -> Int = { 0 },
    /** Up this phone's own socket. */
    private val send: (RoundBody.Sighted) -> Unit,
) {
    private var seed: ContactSeed? = null
    private var out = false
    private var uploads = 0L
    private var advertising: ContactWindow? = null

    /** This phone left the foreground in this round and has not come back yet (D-229). */
    private var away = false

    /**
     * **The grants already reported switched off this round, one bit a grant** (D-310, D-311), so
     * each switch-off is one report whichever source heard it. **Kept beside the owed reports**
     * (the overnight review's finding 1): a relaunch reads it back, so a grant still off that was
     * reported before the process ended is not reported again, and a leave after it stays a leave.
     */
    private var reported = 0

    /** Beats left before a bit a radio set may be cleared by the grants read (D-311). One a grant; allocated once. */
    private val hold = IntArray(DevicePermission.entries.size)

    /**
     * **One onset the house has not yet said it has** (D-245). [due] is false while this phone is
     * still away from the leave it reports; [sentOn] is the connection it last went up on, and it is
     * sent again on each new one until the house answers.
     */
    private class Owed(val onset: String, var cause: RadioCause, var due: Boolean, var sentOn: Int = NEVER_SENT)

    /** Every report owed, oldest first. Written to [kept] whenever it changes. */
    private val owed = ArrayList<Owed>()

    /** Which connection this phone is on: one more on every seating. */
    private var connection = 0

    /** Whether this phone is uploading at all: a seed has arrived and the round has not ended. */
    val inRound: Boolean get() = seed != null

    /** Whether the house has put this phone out, as far as this phone has been told. */
    val isOut: Boolean get() = out

    /** How many reports this phone owes the house and has not had answered. For tests and the log. */
    val owing: Int get() = owed.size

    /**
     * **A push, read for the three facts this holds.** Called with every message the inbox applies,
     * after the inbox has decided it is new, so a re-assertion of an old seed changes nothing.
     */
    fun heard(message: RoundMessage) {
        when (message) {
            is RoundMessage.ContactSeedIssued -> {
                seed = message.seed
                out = false
                uploads = 0L
                report = ReportSlot.Idle
                advertising = null
                away = false
                // A relaunched process hears its round's seed again on its resume, and reads back
                // what it owed in that round. Any other seed is a new round, and nothing is owed.
                owed.clear()
                owed += restore(message.seed)
                // **What was already reported this round** (D-311): what this phone kept beside its
                // owed reports, if a relaunch is reading them back, or nothing at a fresh arming. No
                // grant is excused for being off at arming: one that is goes up on the first beat.
                reported = keptGrants ?: 0
                hold.fill(0)
                if (owed.isNotEmpty()) onEvent("${owed.size} REPORT(S) OWED FROM BEFORE THIS PROCESS — THE NEXT BEATS TELL THE HOUSE (D-245)")
                radio.scan(true)
                ranging.open()
            }
            // **Out, by the house's word and by nothing else.** Stop listening; keep advertising
            // (D-008), so a living phone can still find this one and report it.
            is RoundMessage.RevokeLanded, is RoundMessage.RestrainedTakeover -> {
                out = true
                radio.scan(false)
            }
            is RoundMessage.RoundEnded -> roundEnded()
            // **The house closed on a permission switched off again** (D-310): the round is over on
            // this phone exactly as at an ending.
            is RoundMessage.HouseClosedOnPermission -> roundEnded()
            else -> Unit
        }
    }

    /** **One beat of the fixed cadence**: rotate the token if the window turned, then upload once. */
    fun beat() {
        val key = seed ?: return
        val window = windowNow()
        if (advertising != window) {
            radio.advertise(ContactTokens.of(key, window))
            advertising = window
        }
        val readings = radio.drain()
        val heard = if (out) emptyList() else readings.map { Sighting(it.token, it.rssi, window) }
        // **And whether the person is on the main view** (D-307), asked of the panel at this beat.
        val view = MainViewField.of(mainView())
        send(SightingUpload.pack(key, window, uploads++, heard, ask(), probe(), rangingReport(key), view))
        // **Rule 6: silent here, loud at the house** (T2, D-222). The radio reports each fault once,
        // at its onset; nothing on this phone's screen, lamp or motor hears of it. A return from
        // leaving the foreground is a person's act and wins the beat (D-229): the radio's onset is
        // folded into a report not yet sent, and one beat never sends two bodies.
        //
        // **A switch-off is keyed by the grant it is about, one report a grant** (D-311, the
        // overnight review's findings 1 and 4). Three sources can say the same switch-off: the
        // contact radio (Bluetooth's permission withdrawn, or the radio turned off), the ranging
        // radio (NEARBY DEVICES not allowed), and the grants read ([refusedGrants], which reads the
        // Bluetooth radio turned off as its row refused). Each is the bit of its grant, and a bit
        // already in [reported] is not reported again, on this beat, a later one, or after a relaunch.
        val bluetooth = radio.faultBegan()
        val uwb = ranging.faultBegan()
        var fresh = 0
        var failed: RadioCause? = null
        if (bluetooth == RadioCause.Permission) fresh = fresh or BLUETOOTH else if (bluetooth != null) failed = bluetooth
        if (uwb == RadioCause.Permission) fresh = fresh or NEARBY else if (uwb != null) failed = failed?.outranking(uwb) ?: uwb
        val read = refusedGrants()
        val before = reported
        // **A grant read back on is free to be switched off again** (D-310: back on and off again is
        // another switch-off), except a bit a radio set in the last [HOLD_BEATS] beats, which the
        // grants read may not have caught up with yet, and NEARBY DEVICES', which no read in a round
        // can see (D-309 keeps every probe off a round): that one is once a round.
        var held = NEARBY
        for (i in hold.indices) {
            if (hold[i] > 0) {
                hold[i]--
                held = held or (1 shl i)
            }
        }
        reported = reported and (read or held)
        val newly = (fresh or read) and reported.inv()
        for (i in hold.indices) if ((fresh and newly) and (1 shl i) != 0) hold[i] = HOLD_BEATS
        reported = reported or newly
        failed?.let { cause -> fold(cause) }
        // **A grant switched off is a permission's onset** (D-310, D-311): folded into a report not
        // yet sent (the leave that was the trip to Settings), or owed as one of its own. A grant
        // already off when the round armed counts too (D-311: no phone enters a round missing one,
        // and one that does is named; D-310's provisional item 3 overruled).
        if (newly != 0) fold(RadioCause.Permission)
        if (reported != before) keep()
        // **The oldest report this connection has not carried, and only one** (D-245): a new onset,
        // or one that went up a connection since replaced and was never answered.
        val next = owed.firstOrNull { it.due && it.sentOn != connection } ?: return
        next.sentOn = connection
        faulted(RoundBody.RadioFaulted(next.cause, next.onset))
    }

    /**
     * **One onset this beat, folded or owed** (D-229, D-303, D-310). Into a report not yet sent if
     * there is one, where **the stronger act wins it** (`RadioCause.outranking`): a permission over
     * a person's other act, which wins over a device fault that happened to coincide and must not
     * hide an evasion D-187 names. Otherwise owed as a report of its own. One beat never sends two.
     */
    private fun fold(cause: RadioCause) {
        val unsent = owed.lastOrNull { it.due && it.sentOn == NEVER_SENT }
        if (unsent != null) {
            unsent.cause = unsent.cause.outranking(cause)
        } else {
            owe(Owed(onsetName(), cause, due = !away))
        }
        keep()
    }

    /**
     * **This beat's ranging report, sealed** (D-300): what the radio says now, out or not. Whatever
     * the radio hands over past the wire's width is cut, never a longer field.
     */
    private fun rangingReport(key: ContactSeed): String {
        val capacity = ranging.capacity().coerceIn(0, RangingWire.MAX_PEERS)
        // At most three idle sessions offered a second (D-300): a phone that has more offers the
        // rest on the seconds after.
        val spares = ranging.spares().distinctBy { it.key }.take(RangingWire.TOKEN_SLOTS)
        val readings = ranging.drain().distinctBy { it.handle }.take(RangingWire.MAX_PEERS)
        return codec.packReport(key, rangingNonce(), capacity, spares, readings)
    }

    /** **This phone's ranging codec**: its buffers and its seed's keys, held (rule 7, D-300). */
    private val codec = RangingCodec()

    /**
     * **The report's slot, as the house last said it in a plan that opened** (D-301): idle, held for
     * so long, or cancelled. Every phone reads one off every answer, at one width; a phone holding a
     * Revoked seat within report reach reads [ReportSlot.Pending]. Idle before a seed and after the
     * round. The report screen draws it (D-307): `RoundOverTransport` hands it to the panel off every
     * answer.
     */
    var report: ReportSlot = ReportSlot.Idle
        private set

    /**
     * **The house's plan for this second** (`RoundBody.UploadHeard.plan`, D-300), applied to the
     * radio: every stop, then every start; a keep changes nothing. A plan that does not open under
     * this phone's seed (a blank, another round's, a field damaged on the way) is nothing, and the
     * radio goes on as it was. Before a seed and after the round there is no plan to apply.
     */
    fun planned(plan: String) {
        val key = seed ?: return
        if (!codec.openPlan(key, plan)) return
        // **The report's slot** (D-301): what the house says this phone's report circle is doing.
        // Read off every plan that opens, out or not; the report screen draws it (D-307).
        report = ReportSlot.of(codec.planReportKind, codec.planReportMillis) ?: ReportSlot.Idle
        for (i in 0 until codec.planSteps) {
            if (codec.planKind(i) == RangingWire.STEP_STOP) ranging.stop(RangingHandle(codec.planHandle(i)))
        }
        for (i in 0 until codec.planSteps) {
            if (codec.planKind(i) == RangingWire.STEP_START) {
                ranging.start(RangingHandle(codec.planHandle(i)), SpareKey(codec.planOwn(i)), codec.planPeer(i))
            }
        }
    }

    /**
     * **What this phone may draw of the map this beat** (`RoundBody.UploadHeard.map`, D-316): the
     * field opened under this phone's seed. A field that does not open (a blank, another round's, a
     * field damaged on the way), or no seed yet, is nothing: the empty frame, never a partial map.
     */
    fun mapped(field: String): OccupancyBeat {
        val key = seed ?: return OccupancyBeat.Nothing
        return codec.openMap(key, field) ?: OccupancyBeat.Nothing
    }

    /**
     * **The house has the report of [onset]** (`RoundBody.FaultHeard`, D-245): it is no longer kept,
     * here or beside the token. An answer for an onset this phone does not hold (answered twice, or
     * from before a new round) is nothing.
     */
    fun acknowledged(onset: String) {
        if (owed.removeAll { it.onset == onset }) keep()
    }

    /**
     * **This phone is seated on a new connection** (D-245). Whatever it owes and the house has not
     * answered may have failed with the last one, so each goes up again, one a beat, and the house
     * hears each onset once.
     */
    fun reseated() {
        connection++
    }

    /**
     * **This phone left the foreground: the onset of D-187's evasion, if it is in a round** (D-229).
     * True when it counted. Before arming and after the round there is no round to evade, and
     * nothing is marked.
     *
     * **A phone that is out marks it too, and that is rule 1** (R7, D-242). Whether a seat that is
     * out is named is the house's question, and the house answers no (`onTheNotice`). A phone that
     * stayed quiet on its return because it was out would put one frame fewer on the Wi-Fi than a
     * living phone that did the same thing, and the missing frame would say who is out.
     */
    fun leftTheForeground(): Boolean {
        if (seed == null) return false
        away = true
        // Flicks between two beats are one onset: a report not yet sent takes this leave too.
        val unsent = owed.lastOrNull { it.sentOn == NEVER_SENT }
        if (unsent != null) {
            // A person's act at least; a permission already owed stays one (D-310).
            unsent.cause = unsent.cause.outranking(RadioCause.Person)
            unsent.due = false
        } else {
            owe(Owed(onsetName(), RadioCause.Person, due = false))
        }
        // **Kept now, before the phone is suspended** (D-245): iOS may end this process before it
        // ever comes back, and the relaunch reads this back.
        keep()
        return true
    }

    /**
     * **This phone is back: the next beat tells the house, once** (D-229). True when a report is
     * now owed. A return with no onset in this round owes nothing.
     */
    fun backInTheForeground(): Boolean {
        if (!away) return false
        away = false
        owed.forEach { it.due = true }
        return true
    }

    /**
     * A new round is coming: forget this one's seed, whether it put this phone out, and stop the
     * radio. **Out goes with the round** (D-259): it was cleared only by the next round's seed, so a
     * phone Revoked in one round said it was out of the round all through the lobby before the next.
     */
    fun roundEnded() {
        seed = null
        advertising = null
        out = false
        uploads = 0L
        report = ReportSlot.Idle
        forgetTheRound()
        radio.silence()
        ranging.silence()
    }

    /** This round is over for this phone: nothing is owed in it, here or in [kept]. */
    private fun forgetTheRound() {
        away = false
        reported = 0
        hold.fill(0)
        owed.clear()
        try {
            kept.forgetOwedReports()
        } catch (failed: IllegalStateException) {
            onEvent("THE OWED REPORTS COULD NOT BE FORGOTTEN — ${failed.message}")
        }
    }

    /** One more report owed. Bounded: past [OWED_MOST] the oldest is let go, and the log says so. */
    private fun owe(report: Owed) {
        if (owed.size >= OWED_MOST) {
            owed.removeAt(0)
            onEvent("MORE THAN $OWED_MOST REPORTS OWED — THE OLDEST IS LET GO")
        }
        owed += report
    }

    /**
     * **[owed], written to [kept]**: the round's fingerprint, then one line per report. A store that
     * would not write is said in this phone's log (rule 6); the report is still owed in memory.
     */
    private fun keep() {
        val key = seed ?: return
        try {
            // Kept while anything is owed **or anything has been reported** this round (D-311): the
            // switch-offs already told are what a relaunch must not tell again.
            if (owed.isEmpty() && reported == 0) {
                kept.forgetOwedReports()
            } else {
                val lines = listOf(fingerprintOf(key), "$GRANTS $reported") + owed.map { "${it.onset} ${it.cause.name.lowercase()}" }
                kept.keepOwedReports(lines.joinToString(LINE))
            }
        } catch (failed: IllegalStateException) {
            onEvent("A REPORT OWED COULD NOT BE KEPT — IT FAILS WITH THIS PROCESS (${failed.message})")
        }
    }

    /**
     * **What [kept] holds for the round of [key], or nothing.** Anything kept for another round, or
     * unreadable, is forgotten. Every report read back is due and has gone up no connection of this
     * process.
     */
    private fun restore(key: ContactSeed): List<Owed> {
        val text = try {
            kept.owedReports()
        } catch (_: IllegalStateException) {
            null
        }
        val lines = text?.split(LINE).orEmpty()
        // **The grants at arming, as kept** (D-310): the second line, when this round wrote one.
        keptGrants = if (lines.firstOrNull() == fingerprintOf(key)) {
            lines.getOrNull(1)?.takeIf { it.startsWith("$GRANTS ") }?.substringAfter(' ')?.toIntOrNull()
        } else {
            null
        }
        val reports = if (lines.firstOrNull() == fingerprintOf(key)) {
            lines.drop(1).mapNotNull { line ->
                val parts = line.split(' ')
                val onset = parts.first()
                val cause = RadioCause.entries.firstOrNull { it.name.lowercase() == parts.getOrNull(1) }
                if (cause == null || !isOnset(onset)) null else Owed(onset, cause, due = true)
            }.takeLast(OWED_MOST)
        } else {
            emptyList()
        }
        if (reports.isEmpty() && (keptGrants ?: 0) == 0 && text != null) {
            try {
                kept.forgetOwedReports()
            } catch (_: IllegalStateException) {
                // Read back as nothing either way; the next keep overwrites it.
            }
        }
        return reports
    }

    /** The grants already reported this round, read back by the last [restore], or null when nothing for this round was kept (D-311). */
    private var keptGrants: Int? = null

    private companion object {
        const val NEVER_SENT = -1
        const val LINE = "\n"

        /**
         * The kept line that holds the grants already reported this round (D-311; D-310 kept the
         * grants at arming here). Not an onset's name, so no report reads it.
         */
        const val GRANTS = "grants"

        /** BLUETOOTH's bit: the contact radio's switch-offs are about this grant (D-311). */
        val BLUETOOTH = 1 shl DevicePermission.Bluetooth.ordinal

        /** NEARBY DEVICES' bit: the ranging radio's refusal is about this grant (D-311). */
        val NEARBY = 1 shl DevicePermission.NearbyDevices.ordinal

        /**
         * **Beats a radio's switch-off holds its grant's bit before the grants read may clear it**
         * (D-311): the two hear one switch-off on two queues, and the read can be a beat behind.
         */
        const val HOLD_BEATS = 2

        /** Reports one phone may owe at once. A phone that owes this many is not reaching the house. */
        const val OWED_MOST = 8

        /**
         * The window whose token names a round in [kept]: one no phone is ever in, so the name is
         * never on the air. One-way, like every token (D-212): it does not give back the seed.
         */
        val STORE_WINDOW = ContactWindow(-1)

        fun fingerprintOf(key: ContactSeed): String = ContactTokens.of(key, STORE_WINDOW).hex

        fun isOnset(text: String): Boolean =
            text.length == RoundBody.RadioFaulted.ONSET_CHARS && text.all { it in '0'..'9' || it in 'a'..'f' }
    }
}

/** A fresh onset's name: secure random bytes, as lowercase hex (D-245). Never a counter or a time. */
fun freshOnset(): String = buildString(RoundBody.RadioFaulted.ONSET_CHARS) {
    for (b in secureRandomBytes(RoundBody.RadioFaulted.ONSET_CHARS / 2)) {
        val v = b.toInt() and 0xff
        append(HEX[v shr 4])
        append(HEX[v and 0x0f])
    }
}

private const val HEX = "0123456789abcdef"
