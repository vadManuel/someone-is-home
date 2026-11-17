package home.someoneshome.app

import home.someoneshome.model.OneLineHeld
import home.someoneshome.model.Seat
import home.someoneshome.model.SeatNamed
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **A real LIGHTS OUT never reads the bench's seed** (R4, D-240).
 *
 * The bug: `CheatRoot` answered the host's press with `RoundCheat.seed()`, which read
 * `SOMEONES_HOME_ROUND_SEED` on every launch. `round.sh` sets it by default and its README names the
 * seat that seed makes the Insider. So the guard is on the press itself, [lightsOutPressed], with the
 * launch environment handed in as a recorder: on a launch with no bench switch the seed switch is
 * **never asked for**, and the round is armed with a seed that is not the named one. A bench launch
 * (the scripted host, or `--by-hand`) still arms the named seed, which is what the one-phone rig needs.
 */
class LightsOutSeedTest {

    private val seats = List(5) { Seat(it) }

    private val inputs = ArmingInputs(
        seats = seats,
        names = seats.mapIndexed { i, s -> SeatNamed(s, "NAME$i") },
        oneLines = seats.mapIndexed { i, s -> OneLineHeld(s, "line $i") },
        chosenInsiders = 1,
        voteWindowSeconds = 45,
        readyToArm = true,
        home = BenchHome.home,
    )

    /** One launch environment, and every name the press asked it for. */
    private class Launch(private val env: Map<String, String>) {
        val asked = mutableListOf<String>()
        fun read(name: String): String? {
            asked += name
            return env[name]
        }
    }

    /** The host's press, as `CheatRoot` makes it, on a launch holding [env]. */
    private fun press(env: Map<String, String>): Pair<RoundAuthority, Launch> = runBlocking {
        val launch = Launch(env)
        val house = RoundAuthority(inputs = { inputs }, emit = { _, _ -> }, note = {})
        assertTrue(lightsOutPressed(house, hosting = true, note = {}, read = launch::read), "the press armed nothing")
        house to launch
    }

    /**
     * **GUARD (R4): the seed alone, as `round.sh` sets it, is never read by a press.** A phone a
     * person plays draws for real, whatever its launch carried.
     */
    @Test
    fun aLightsOutWithoutTheRoundSwitchNeverReadsTheSeedSwitch() {
        for (env in listOf(mapOf(RoundCheat.SEED_SWITCH to NAMED.toString()), emptyMap())) {
            val (house, launch) = press(env)
            assertFalse(
                RoundCheat.SEED_SWITCH in launch.asked,
                "a press with no bench switch asked for the seed switch: ${launch.asked} (launch $env)",
            )
            assertNotEquals(NAMED, house.state.seed, "a press with no bench switch armed the named seed (launch $env)")
            assertNull(RoundCheat.benchSeed(launch::read), "the house's log would name seats on a real draw (launch $env)")
        }
    }

    /** The rig keeps its fixed seed: the scripted host and the by-hand host are both bench launches. */
    @Test
    fun aBenchLaunchStillArmsTheNamedSeed() {
        for (bench in listOf(RoundCheat.ROUND_SWITCH, RoundCheat.BY_HAND_SWITCH)) {
            val (house, launch) = press(mapOf(bench to "1", RoundCheat.SEED_SWITCH to NAMED.toString()))
            assertEquals(NAMED, house.state.seed, "a $bench launch did not arm the seed it named")
            assertEquals(NAMED, RoundCheat.benchSeed(launch::read), "a $bench launch would not name seats in the log")
        }
    }

    private companion object {
        const val NAMED = 20261150L
    }
}
