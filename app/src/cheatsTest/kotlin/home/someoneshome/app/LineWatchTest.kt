package home.someoneshome.app

import home.someoneshome.model.protocol.LobbyBody
import home.someoneshome.model.protocol.LobbyWire

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **The leak probe's own positive control.**
 *
 * It lives in `src/cheatsTest` and not in `commonTest`, which is not filing: release does not
 * compile the cheat source set, so a test for a cheat written into `commonTest` is an unresolved
 * reference in the one variant that must build without them. `variantSweepRelease` refused this
 * file exactly once and the directory is the answer.
 *
 * `LineWatch` reported a clean two-phone evening before anything had established that it *can*
 * report a dirty one, which is the standing U6's never-persisted sweep refused to accept for
 * itself: a test that reads no files passes for the same reason a test that reads all of them
 * does. The device injection answered it once, on a phone, with a build that no longer exists;
 * this is the part of that answer that stays in the repository.
 *
 * The bodies here are built by [LobbyWire] rather than typed out as strings, so the test cannot
 * quietly stop describing the wire the day the wire changes shape.
 */
class LineWatchTest {

    private class Heard {
        val lines = mutableListOf<String>()
        val note: (String) -> Unit = { lines += it }
        val alarms: List<String> get() = lines.filter { it.startsWith("!!!") }
    }

    private val line = "i still have priya's spare key"

    @Test
    fun aLobbyThatOnlySendsStandingsSetsNothingOff() {
        val heard = Heard()
        val watch = LineWatch(heard.note)
        watch.mine(line)
        repeat(3) {
            watch.saw(LobbyWire.encode(LobbyBody.Standing(joined = 2, linesIn = 2, names = listOf("ELLIOT", "PRIYA"))))
        }
        assertEquals(emptyList(), heard.alarms, "a standing set the leak alarm off")
        assertTrue(
            watch.verdict().contains("3 FRAME(S)") && watch.verdict().contains("0 CARRIED"),
            "the verdict did not report three clean frames: ${watch.verdict()}",
        )
    }

    /** The injected bug, as a test: the host echoing a handover back down to the clients. */
    @Test
    fun aHandoverComingBackDownIsCaughtAndNamed() {
        val heard = Heard()
        val watch = LineWatch(heard.note)
        watch.mine(line)
        watch.saw(LobbyWire.encode(LobbyBody.Standing(joined = 1, linesIn = 1, names = listOf("ELLIOT"))))
        watch.saw(LobbyWire.encode(LobbyBody.Handover(line)))
        assertEquals(1, heard.alarms.size, "the echoed handover did not set the alarm off")
        assertTrue(heard.alarms.single().contains("HANDOVER"), heard.alarms.single())
        assertTrue(
            heard.alarms.single().contains("CARRIED THIS PHONE'S LINE=true"),
            heard.alarms.single(),
        )
        assertTrue(watch.verdict().contains("1 CARRIED"), watch.verdict())
    }

    /**
     * **The alarm never prints the line, and this is the assertion that keeps it that way.**
     *
     * A leak detector that logs what it caught writes the thing it is guarding into a file the
     * first time it works. Everything this class emits is checked against the text, including the
     * verdict, because the verdict is the line that gets printed most often.
     */
    @Test
    fun nothingItSaysEverContainsTheLine() {
        val heard = Heard()
        val watch = LineWatch(heard.note)
        watch.mine(line)
        watch.saw(LobbyWire.encode(LobbyBody.Handover(line)))
        watch.saw("something that is not a lobby body at all")
        for (said in heard.lines + watch.verdict()) {
            assertFalse(said.contains(line), "the probe printed the line it is guarding: $said")
            // Fragments too: the first four words of somebody's line identify it completely in a
            // room of six people who all know each other — U6's reason, one layer down.
            assertFalse(said.contains("i still have priya"), "the probe printed part of the line: $said")
        }
    }

    /**
     * A body the wire cannot read is named rather than dropped.
     *
     * A decoder that let an unrecognised body fall through would hide exactly the frame worth
     * seeing — the one that arrived by a route nobody meant to open.
     */
    @Test
    fun anUndecodableBodyIsNamedRatherThanIgnored() {
        val heard = Heard()
        val watch = LineWatch(heard.note)
        watch.mine(line)
        watch.saw("{\"not\":\"a lobby body\"}")
        assertEquals(1, heard.alarms.size, "an unreadable body passed without a word")
        assertTrue(heard.alarms.single().contains("UNDECODABLE"), heard.alarms.single())
    }

    /**
     * **Zero carriers out of zero frames is not a pass**, and the verdict has to make that
     * visible — it is the answer a phone that never connected gives, and the one this probe is
     * most likely to produce by accident.
     */
    @Test
    fun aPhoneThatNeverConnectedReportsNoFramesRatherThanACleanRun() {
        val watch = LineWatch {}
        watch.mine(line)
        assertTrue(watch.verdict().contains("0 FRAME(S)"), watch.verdict())
    }

    /**
     * **A verdict read while frames are still landing does not throw** (H5 of the 2026-11-17 hunt,
     * D-252).
     *
     * The host of the five-seat hunt failed of a `ConcurrentModificationException` inside [LineWatch.verdict]:
     * the lobby bench read the tally on its own coroutine while frames appended to it on another.
     * Two threads, one writing and one reading, for as long as the writer runs; then every frame is
     * accounted for.
     */
    @Test
    fun aVerdictReadWhileFramesLandNeitherThrowsNorLosesAFrame() = runBlocking {
        val watch = LineWatch {}
        watch.mine(line)
        val standing = LobbyWire.encode(LobbyBody.Standing(joined = 2, linesIn = 2, names = listOf("ELLIOT", "PRIYA")))
        val ended = LobbyWire.encode(LobbyBody.RoundEnded)
        val frames = 20_000
        val writer = async(Dispatchers.Default) {
            repeat(frames) { watch.saw(if (it % 2 == 0) standing else ended) }
        }
        val reader = async(Dispatchers.Default) {
            var reads = 0
            while (!writer.isCompleted) {
                watch.verdict()
                reads++
            }
            reads
        }
        writer.await()
        assertTrue(reader.await() > 0, "the reader never overlapped the writer, so this proved nothing")
        assertTrue(
            watch.verdict().startsWith("INBOUND — $frames FRAME(S) {STANDING=${frames / 2}, ROUND ENDED=${frames / 2}}"),
            "a frame went missing under the lock: ${watch.verdict()}",
        )
    }
}
