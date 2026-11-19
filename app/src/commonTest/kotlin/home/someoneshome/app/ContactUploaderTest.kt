package home.someoneshome.app

import home.someoneshome.model.ContactSeed
import home.someoneshome.model.ContactToken
import home.someoneshome.model.ContactTokens
import home.someoneshome.model.ContactWindow
import home.someoneshome.model.Haptic
import home.someoneshome.model.RadioCause
import home.someoneshome.model.WinRoute
import home.someoneshome.model.Winner
import home.someoneshome.model.protocol.RoundBody
import home.someoneshome.model.protocol.RoundMessage
import home.someoneshome.model.protocol.RoundWire
import home.someoneshome.model.protocol.SightingUpload
import home.someoneshome.platform.ContactRadio
import home.someoneshome.platform.RadioReading
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **The phone's half of the handshake, alone: one upload a beat at one size, and R8** (D-217).
 *
 * A radio that always hears a stranger at contact range, so that what the uploader does with a
 * reading is visible in every upload: a living phone reports it, an out phone never does.
 */
class ContactUploaderTest {

    private class LoudRadio : ContactRadio {
        val advertised = mutableListOf<ContactToken>()
        var scanning = false
        var silenced = 0
        val stranger = ContactToken("ffeeddccbbaa99887766554433221100")
        override fun advertise(token: ContactToken) { advertised += token }
        override fun scan(on: Boolean) { scanning = on }
        override fun drain(): List<RadioReading> = listOf(RadioReading(stranger, -31))
        override fun silence() { silenced++; scanning = false }
        var faults = 0
        var cause = RadioCause.Person
        override fun faultBegan(): RadioCause? = if (faults > 0) cause.also { faults-- } else null
    }

    private val seed = ContactSeed("0123456789abcdef0123456789abcdef")

    private fun rig(window: () -> ContactWindow = { ContactWindow(10) }): Triple<ContactUploader, LoudRadio, MutableList<RoundBody.Sighted>> {
        val radio = LoudRadio()
        val sent = mutableListOf<RoundBody.Sighted>()
        return Triple(ContactUploader(radio, window) { sent += it }, radio, sent)
    }

    private fun heardIn(body: RoundBody.Sighted, token: ContactToken): Boolean =
        SightingUpload.unpack(body)!!.any { it.token == token }

    @Test
    fun nothingGoesUpBeforeTheSeedAndOneUploadAtOneSizeGoesUpEveryBeatAfter() {
        val (up, radio, sent) = rig()
        repeat(3) { up.beat() }
        assertEquals(0, sent.size, "a phone uploaded before it was in a round")
        up.heard(RoundMessage.ContactSeedIssued(seed))
        assertTrue(radio.scanning, "a living phone in a round is not scanning")
        repeat(5) { up.beat() }
        assertEquals(5, sent.size)
        assertTrue(sent.all { it.slots.length == SightingUpload.BODY_CHARS })
        assertTrue(sent.all { heardIn(it, radio.stranger) }, "a living phone did not report what it heard")
    }

    /** R8, for both ways out: the cadence and the size hold, scanning stops, nothing heard goes up. */
    @Test
    fun anOutPhoneKeepsUploadingOnTheBeatAtOneSizeAndReportsNothing() {
        for (out in listOf<RoundMessage>(RoundMessage.RevokeLanded.Buzzing, RoundMessage.RestrainedTakeover(Haptic.Long))) {
            val (up, radio, sent) = rig()
            up.heard(RoundMessage.ContactSeedIssued(seed))
            up.beat()
            up.heard(out)
            assertFalse(radio.scanning, "$out: an out phone kept scanning (D-008)")
            repeat(4) { up.beat() }
            assertEquals(5, sent.size, "$out: an out phone stopped uploading on the beat (R8)")
            assertTrue(sent.all { it.slots.length == SightingUpload.BODY_CHARS }, "$out: the size moved")
            assertTrue(heardIn(sent.first(), radio.stranger), "the living upload did not carry the reading")
            assertTrue(sent.drop(1).none { heardIn(it, radio.stranger) }, "$out: an out phone reported what its radio held")
            assertTrue(up.isOut)
        }
    }

    @Test
    fun theAdvertisedTokenRotatesWithTheWindowAndOutPhonesKeepAdvertising() {
        var w = 10L
        val (up, radio, _) = rig(window = { ContactWindow(w) })
        up.heard(RoundMessage.ContactSeedIssued(seed))
        up.beat(); up.beat()
        up.heard(RoundMessage.RevokeLanded.Buzzing)
        w = 11L
        up.beat()
        assertEquals(
            listOf(ContactTokens.of(seed, ContactWindow(10)), ContactTokens.of(seed, ContactWindow(11))),
            radio.advertised,
            "the token did not rotate once per window, or an out phone stopped advertising",
        )
    }

    @Test
    fun theRoundEndingStopsTheUploadsAndSilencesTheRadio() {
        val (up, radio, sent) = rig()
        up.heard(RoundMessage.ContactSeedIssued(seed))
        up.beat()
        up.heard(RoundMessage.RoundEnded(Winner.Residents, WinRoute.SystemIntegrityCleared, Haptic.Short, integrity = 60))
        repeat(3) { up.beat() }
        assertEquals(1, sent.size, "a phone kept uploading after the round ended")
        assertEquals(1, radio.silenced)
    }

    /**
     * **T2: a fault goes up once, on the beat after its onset, with its cause, from a living phone
     * and an out one alike, and never outside a round** (D-222; D-223). **GUARD: before arming,
     * nothing goes up**, so nothing can be announced.
     */
    @Test
    fun aRadioFaultGoesUpOnceOnTheBeatAfterItBeganInARoundOnly() {
        for (out in listOf(false, true)) for (cause in RadioCause.entries) {
            val radio = LoudRadio()
            radio.cause = cause
            val faults = mutableListOf<RadioCause>()
            val up = ContactUploader(radio, { ContactWindow(10) }, send = { }, faulted = { faults += it.cause })
            radio.faults = 1
            up.beat()
            assertEquals(emptyList(), faults, "a fault went up before the phone was in a round")
            up.heard(RoundMessage.ContactSeedIssued(seed))
            if (out) up.heard(RoundMessage.RevokeLanded.Buzzing)
            repeat(3) { up.beat() }
            assertEquals(listOf(cause), faults, "out=$out, $cause: one onset did not go up exactly once, as itself")
        }
    }

    // ---- D-229: leaving the foreground mid-round (D-187) ------------------------------------------

    /** An uploader whose reports are collected, over a radio that only faults when told to. */
    private fun evader(): Triple<ContactUploader, LoudRadio, MutableList<RadioCause>> {
        val radio = LoudRadio()
        val faults = mutableListOf<RadioCause>()
        val up = ContactUploader(radio, { ContactWindow(10) }, send = { }, faulted = { faults += it.cause })
        return Triple(up, radio, faults)
    }

    /**
     * **GUARD: a phone that leaves the foreground mid-round tells the house once, as a person's act,
     * on the first beat after it is back, and not before** (D-187, D-229). Living and out alike, as a
     * radio fault is: the body is the one every phone sends.
     */
    @Test
    fun leavingTheForegroundMidRoundGoesUpOnceAsAPersonsActWhenThePhoneIsBack() {
        for (out in listOf(false, true)) {
            val (up, _, faults) = evader()
            up.heard(RoundMessage.ContactSeedIssued(seed))
            if (out) up.heard(RoundMessage.RevokeLanded.Buzzing)
            up.beat()
            assertTrue(up.leftTheForeground(), "out=$out: leaving in a round did not count")
            up.beat()
            assertEquals(emptyList(), faults, "out=$out: the report went up while the phone was still away")
            assertTrue(up.backInTheForeground(), "out=$out: coming back owed nothing")
            repeat(4) { up.beat() }
            assertEquals(listOf(RadioCause.Person), faults, "out=$out: one leave did not go up exactly once, as a person's")
        }
    }

    /**
     * **GUARD: once per onset.** Leaving and coming back several times between two beats is one
     * report; leaving again after it went up is a second onset.
     */
    @Test
    fun aLeaveIsReportedOncePerOnsetHoweverOftenThePhoneFlicksAway() {
        val (up, _, faults) = evader()
        up.heard(RoundMessage.ContactSeedIssued(seed))
        repeat(3) {
            up.leftTheForeground()
            up.leftTheForeground()
            up.backInTheForeground()
        }
        assertFalse(up.backInTheForeground(), "a second return owed a second report for one onset")
        repeat(3) { up.beat() }
        assertEquals(listOf(RadioCause.Person), faults, "several flicks before a beat were not one report")
        up.leftTheForeground()
        up.backInTheForeground()
        up.beat()
        assertEquals(listOf(RadioCause.Person, RadioCause.Person), faults, "a second leave after the first went up was not a second onset")
    }

    /** **GUARD: before arming, nothing is owed** — not then, and not once the round arms. */
    @Test
    fun leavingTheForegroundBeforeArmingIsNothing() {
        val (up, _, faults) = evader()
        assertFalse(up.leftTheForeground(), "a leave in the lobby counted")
        assertFalse(up.backInTheForeground(), "a return in the lobby owed a report")
        up.leftTheForeground()
        up.heard(RoundMessage.ContactSeedIssued(seed))
        assertFalse(up.backInTheForeground(), "a leave from the lobby was carried into the round")
        repeat(3) { up.beat() }
        assertEquals(emptyList(), faults, "a phone that left before arming was reported")
    }

    /**
     * **GUARD: after the round, nothing is owed** — a leave after the end, and a leave whose return
     * comes after the end, alike.
     */
    @Test
    fun leavingTheForegroundAfterTheRoundOrAcrossItsEndIsNothing() {
        val ended = RoundMessage.RoundEnded(Winner.Residents, WinRoute.SystemIntegrityCleared, Haptic.Short, integrity = 60)
        val (after, _, afterFaults) = evader()
        after.heard(RoundMessage.ContactSeedIssued(seed))
        after.heard(ended)
        assertFalse(after.leftTheForeground(), "a leave after the round counted")
        after.backInTheForeground()
        repeat(2) { after.beat() }
        assertEquals(emptyList(), afterFaults, "a phone that left after the round was reported")

        val (across, _, acrossFaults) = evader()
        across.heard(RoundMessage.ContactSeedIssued(seed))
        across.leftTheForeground()
        across.heard(ended)
        assertFalse(across.backInTheForeground(), "a leave was carried past the round's end")
        across.heard(RoundMessage.ContactSeedIssued(ContactSeed("fedcba9876543210fedcba9876543210")))
        repeat(2) { across.beat() }
        assertEquals(emptyList(), acrossFaults, "a leave in the last round was reported in the next")

        // A new round from the app's side (`roundEnded`, a leave or NEW ROUND) forgets it too.
        val (fresh, _, freshFaults) = evader()
        fresh.heard(RoundMessage.ContactSeedIssued(seed))
        fresh.leftTheForeground()
        fresh.backInTheForeground()
        fresh.roundEnded()
        fresh.heard(RoundMessage.ContactSeedIssued(seed))
        repeat(2) { fresh.beat() }
        assertEquals(emptyList(), freshFaults, "a report owed in one round went up in the next")
    }

    /**
     * **GUARD: a reset is still a reset, and one beat sends one body.** A radio that resets with no
     * leave is the phone's own failure and goes up as such (D-223); on the beat a phone comes back,
     * the radio's onset is folded into the person's report rather than sent beside it.
     */
    @Test
    fun aResetAloneStaysTheDevicesAndABeatAfterAReturnSendsOneBody() {
        val (alone, aloneRadio, aloneFaults) = evader()
        aloneRadio.cause = RadioCause.Device
        alone.heard(RoundMessage.ContactSeedIssued(seed))
        aloneRadio.faults = 1
        repeat(2) { alone.beat() }
        assertEquals(listOf(RadioCause.Device), aloneFaults, "a reset with nobody leaving went up as something else")

        val (both, bothRadio, bothFaults) = evader()
        bothRadio.cause = RadioCause.Device
        both.heard(RoundMessage.ContactSeedIssued(seed))
        both.leftTheForeground()
        both.backInTheForeground()
        bothRadio.faults = 1
        repeat(3) { both.beat() }
        assertEquals(listOf(RadioCause.Person), bothFaults, "a return and a radio onset on one beat were not one person's report")
    }

    /**
     * **GUARD: a phone that relaunches into its seat is not named** (D-036). A crash is not a
     * person's act: the fresh process hears the round's seed on its resume, and owes nothing.
     */
    @Test
    fun aPhoneThatRelaunchesIntoItsSeatOwesNothing() {
        val (up, _, faults) = evader()
        up.heard(RoundMessage.ContactSeedIssued(seed))
        assertFalse(up.backInTheForeground(), "a relaunch's first foreground owed a report")
        repeat(3) { up.beat() }
        assertEquals(emptyList(), faults, "a phone that relaunched into its seat was reported")
    }

    /**
     * **GUARD: the upward body is one shape of one length**, the same bytes from every phone whatever
     * it is, and the same length whichever side of D-187's line it reports (D-223). A cause whose
     * serial name was longer would let a reader of the Wi-Fi tell a reset from a switch-off by size.
     */
    @Test
    fun theFaultBodyIsOneFixedLength() {
        val onset = "0123456789abcdef"
        assertEquals("{\"type\":\"radioFaulted\",\"cause\":\"person\",\"onset\":\"$onset\"}", RoundWire.encode(RoundBody.RadioFaulted(RadioCause.Person, onset)))
        assertEquals("{\"type\":\"radioFaulted\",\"cause\":\"device\",\"onset\":\"$onset\"}", RoundWire.encode(RoundBody.RadioFaulted(RadioCause.Device, onset)))
        // Every onset a phone names is the same width, so its length says nothing either (D-245).
        val lengths = RadioCause.entries.flatMap { cause ->
            List(50) { RoundWire.encode(RoundBody.RadioFaulted(cause, freshOnset())).length }
        }.toSet()
        assertEquals(1, lengths.size, "the fault body's length depends on its cause or its onset: $lengths")
        assertEquals(null, RoundWire.decodeOrNull("{\"type\":\"radioFaulted\",\"cause\":\"person\",\"onset\":\"12\"}"), "an onset of another width was read")
    }

    // ---- D-245: a report is kept until the house answers it --------------------------------------------

    /** An uploader over [kept], its reports collected whole. */
    private fun keeper(kept: SeatStore, radio: LoudRadio = LoudRadio()): Pair<ContactUploader, MutableList<RoundBody.RadioFaulted>> {
        val reports = mutableListOf<RoundBody.RadioFaulted>()
        return ContactUploader(radio, { ContactWindow(10) }, faulted = { reports += it }, kept = kept, send = { }) to reports
    }

    /**
     * **GUARD (D-245): an unanswered report goes up again on every new connection, the same onset,
     * and stops once the house answers.** One body a beat, and never twice on one connection.
     */
    @Test
    fun anUnansweredReportGoesUpAgainOnEachNewConnectionUntilTheHouseAnswers() {
        val (up, reports) = keeper(MemorySeat())
        up.heard(RoundMessage.ContactSeedIssued(seed))
        up.leftTheForeground()
        up.backInTheForeground()
        repeat(3) { up.beat() }
        assertEquals(1, reports.size, "one report did not go up once on one connection")
        val onset = reports.single().onset
        up.reseated()
        repeat(3) { up.beat() }
        assertEquals(listOf(onset, onset), reports.map { it.onset }, "an unanswered report did not go up again, as itself, on the new connection")
        up.acknowledged(onset)
        up.reseated()
        repeat(3) { up.beat() }
        assertEquals(2, reports.size, "an answered report went up again")
        assertEquals(0, up.owing, "an answered report is still owed")
    }

    /** **GUARD (D-245): each onset has its own name**, so the house can tell a second onset from a repeat. */
    @Test
    fun twoOnsetsHaveTwoNames() {
        val (up, reports) = keeper(MemorySeat())
        up.heard(RoundMessage.ContactSeedIssued(seed))
        repeat(2) {
            up.leftTheForeground()
            up.backInTheForeground()
            up.beat()
        }
        assertEquals(2, reports.map { it.onset }.toSet().size, "two onsets went up under one name: $reports")
    }

    /**
     * **GUARD (D-245): a report owed at a leave outlives the process.** The leave is kept beside the
     * token before the phone comes back; a fresh uploader over the same store (the relaunch) hears
     * the round's seed on its resume and owes the same onset; the answer clears the store. A report
     * kept for another round is forgotten, not sent.
     */
    @Test
    fun aReportOwedAtALeaveOutlivesTheProcess() {
        val store = MemorySeat()
        val (before, beforeReports) = keeper(store)
        before.heard(RoundMessage.ContactSeedIssued(seed))
        before.beat()
        before.leftTheForeground()
        // iOS ends the process here: nothing more of `before` runs.
        assertTrue(store.owedReports() != null, "the leave was not kept before the phone was suspended")
        assertEquals(emptyList(), beforeReports, "the report went up while the phone was away")

        val (after, afterReports) = keeper(store)
        after.beat()
        assertEquals(emptyList(), afterReports, "a relaunch sent a report before it held the round's seed")
        after.heard(RoundMessage.ContactSeedIssued(seed))
        repeat(3) { after.beat() }
        assertEquals(listOf(RadioCause.Person), afterReports.map { it.cause }, "the relaunched phone did not owe its leave, once, as a person's")
        after.acknowledged(afterReports.single().onset)
        assertEquals(null, store.owedReports(), "an answered report was still kept beside the token")

        // Kept in one round, read back in another: forgotten.
        val (old, _) = keeper(store)
        old.heard(RoundMessage.ContactSeedIssued(seed))
        old.leftTheForeground()
        val (next, nextReports) = keeper(store)
        next.heard(RoundMessage.ContactSeedIssued(ContactSeed("fedcba9876543210fedcba9876543210")))
        repeat(3) { next.beat() }
        assertEquals(emptyList(), nextReports, "a report kept in the last round went up in the next")
        assertEquals(null, store.owedReports(), "a report kept for another round was not forgotten")
    }

    /** **GUARD (D-245, D-036): a crash in the foreground keeps nothing**, so its relaunch owes nothing. */
    @Test
    fun aCrashInTheForegroundKeepsNothing() {
        val store = MemorySeat()
        val (before, _) = keeper(store)
        before.heard(RoundMessage.ContactSeedIssued(seed))
        repeat(3) { before.beat() }
        assertEquals(null, store.owedReports(), "a phone that never left kept a report")
        val (after, afterReports) = keeper(store)
        after.heard(RoundMessage.ContactSeedIssued(seed))
        repeat(3) { after.beat() }
        assertEquals(emptyList(), afterReports, "a phone that crashed in the foreground was reported on its relaunch")
    }
}
