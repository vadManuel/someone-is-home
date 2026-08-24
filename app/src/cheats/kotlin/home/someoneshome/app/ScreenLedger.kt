package home.someoneshome.app

import home.someoneshome.platform.monotonicNanos
import home.someoneshome.ui.FlowModel
import home.someoneshome.ui.PanelState
import kotlinx.coroutines.delay

/**
 * **What this seat's screen says — the question P3's rehearsal could not answer.**
 *
 * That unit ran a whole round over the real transport and its central finding was a sentence about
 * screens it could only make one way round: *every distinct screen a client stood on, whole round:
 * LOBBY*. It knew that because a client's log printed the screen name in two or three places by
 * accident. There was no instrument. R1 and C3 routed the kinds that move a screen, and the claim
 * those units make — the round is visible now — is a claim about **five phones at once**, which is
 * exactly the shape no single-process test can check and no log written for another purpose can
 * either.
 *
 * ### It reads the model the composition reads, and that is the whole of its honesty
 *
 * [LobbyCheat]'s discipline one wire down: every field below comes out of the same [FlowModel] the
 * screens draw from, so a line saying `ON Vote · voted 3 OF 4` is the text on the glass rather than
 * a second opinion about it. It forms nothing, derives nothing and asks the house nothing — a
 * ledger that computed what the screen *ought* to say would agree with itself for the whole round
 * and be worth nothing.
 *
 * **A log is still not a photograph**, which is why this unit also takes pictures. What this can do
 * that a photograph cannot is be there at every beat on all five seats at once.
 *
 * ### What it will not print
 *
 * No one line, no reveal text, no house body — [LobbyCheat] and [RoundClient] both refuse those and
 * the reasons are theirs. Lengths and counts stand in for all three: the two failures worth telling
 * apart on a screen are *nothing arrived* and *something arrived and is wrong*, and a count
 * separates them without putting anybody's confession in a file on a Mac. **Names are printed**,
 * because the house sends the roster to every phone (D-115) and the strip draws it — a name in this
 * log is on five screens already.
 */
class ScreenLedger(
    private val flow: FlowModel,
    private val note: (String) -> Unit,
) {

    /** The last reading printed, so an idle phone does not print a line a second forever. */
    private var last: String = ""

    /**
     * **This seat's screen, in one line.**
     *
     * The screen first, because it is the fact every assertion in this rehearsal is about, and the
     * fields after it in the order a player's eye would find them. Null fields are printed as `-`
     * rather than omitted: *the house has said nothing* and *the house said nothing about this*
     * are the same fact here, and a line whose shape changed with its content could not be diffed
     * across five seats.
     */
    fun reading(): String {
        val s: PanelState = flow.state
        val counts = flow.meeting.counts
        return buildString {
            append("ON ${s.screen.name.uppercase()}")
            append(" · ROLE ${s.role.name.uppercase()}")
            append(" · OUT ${s.outBy?.name?.uppercase() ?: "-"}")
            append(" · VERDICT ${s.verdict?.name?.uppercase() ?: "-"}")
            append(" · WORK ${s.current?.subroutine?.label ?: "-"}")
            append(" · ORDER ${s.order?.size ?: "-"}")
            append(" · METER ${s.integrityPercent?.let { "$it%" } ?: "-"}")
            append(" · SEATS ${s.seats ?: "-"}")
            append(" · SECS ${s.secondsLeft ?: "-"}")
            append(" · PHASE ${s.meetingPhase?.name?.uppercase() ?: "-"}")
            append(" · CALLER ${s.caller ?: "-"}")
            append(" · GATE ${counts.present}/${counts.seats}")
            append(" · READY ${counts.ready}/${counts.living}")
            append(" · VOTED ${counts.voted}/${counts.living}")
            // The couch's live ballot is a count here for a different reason from the reveal's: it
            // is lawful on the screen that draws it and this line is drawn on all five, so a ledger
            // that printed it would publish the ghosts' view into every seat's log and the
            // differential below would read a leak that never happened.
            append(" · BALLOTS ${s.ballots?.size ?: "-"}")
            // The result, as the living get it: the participation count and whether a name came
            // with it. **Both halves reach every phone**, so unlike the row above there is nothing
            // here a differential could mistake for a leak — and a ledger that printed only
            // *arrived* could not tell a result of 6 OF 6 from one the screen invented.
            append(" · TALLY ${s.tally?.let { "${it.voted}/${it.living}" } ?: "-"}")
            append(" · EGRESS ${s.egressType ?: "-"}")
            append(" · NODES ${s.egressNodes?.size ?: "-"}")
            append(" · RUNNING ${s.egressRunning}")
            append(" · HELD ${s.egressHeld}")
            append(" · BEAT ${s.beatHeld?.toString()?.uppercase() ?: "-"}")
            append(" · JAM ${s.staticJam?.let { "${it.seconds}S" } ?: "-"}")
            append(" · REVEALED ${s.revealed?.size ?: "-"}")
            append(" · SIGNOFF ${s.signOff?.length ?: "-"}")
        }
    }

    /**
     * **A monotonic stamp, in milliseconds, on every line this class prints.**
     *
     * Without one there is no way to say what seat 3 was showing *when* seat 0 announced a beat, and
     * that sentence is the entire subject of this rehearsal. Wall clocks would not do it — five
     * processes reading five `NSDate`s is five clocks — but `mach_absolute_time` is the **host
     * kernel's**, and five simulator processes on one Mac are five processes on one kernel. So the
     * stamps really are comparable across the seats, which is the one property that makes a beat
     * table possible at all.
     *
     * On five phones in a house it would not be, and the transport already carries the answer for
     * that day: `TransportFrame.TimeProbe`/`TimeMark` exist to put the host's clock on a client.
     * Written down here rather than used, because nothing in this build reads them yet.
     */
    private fun stamped(line: String): String = "@${monotonicNanos() / 1_000_000L} $line"

    /** Print the reading if it moved. Cheap enough to call at whatever rate. */
    fun sample() {
        val now = reading()
        if (now != last) {
            note(stamped(now))
            last = now
        }
    }

    /**
     * **A named moment in the script, printed whether or not the screen moved.**
     *
     * The beats are what the assertions are written against, and a beat that printed nothing
     * because the screen happened not to have changed would be a beat with no evidence at it —
     * which on the seats that are *supposed* not to move is the entry that matters most.
     */
    fun beat(name: String) {
        val now = reading()
        note(stamped("BEAT $name — $now"))
        last = now
    }

    /**
     * Watch for as long as the phone is up.
     *
     * Called on the composition's scope, which is the main thread — [PanelState] is Compose state
     * and reading it from a socket's thread is the same mistake [RoundOverTransport]'s queue exists
     * to prevent, one direction over.
     */
    suspend fun watch() {
        while (true) {
            sample()
            delay(STEP_MILLIS)
        }
    }

    private companion object {
        /** Fast enough to catch a phase the house opened and closed, idle enough to be free. */
        const val STEP_MILLIS = 250L
    }
}
