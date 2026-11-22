package home.someoneshome.app

import home.someoneshome.core.reduce
import home.someoneshome.model.Balance
import home.someoneshome.model.CellRect
import home.someoneshome.model.Effect
import home.someoneshome.model.Event
import home.someoneshome.model.Floor
import home.someoneshome.model.GameState
import home.someoneshome.model.HouseMap
import home.someoneshome.model.HousePlan
import home.someoneshome.model.MarkerCard
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MarkerShapes
import home.someoneshome.model.Meeting
import home.someoneshome.model.MeetingPhase
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.OneLineHeld
import home.someoneshome.model.PlanRoom
import home.someoneshome.model.Registration
import home.someoneshome.model.Room
import home.someoneshome.model.SavedHome
import home.someoneshome.model.Seat
import home.someoneshome.model.SeatNamed
import home.someoneshome.model.Tick
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The host's DISCUSSION, EGRESS TIMER and REVOKE COOLDOWN rows, from the desk to the round**
 * (the owner's 2026-11-21 ruling, D-317; D-037).
 *
 * The VOTING row's road, walked for the other three: the host's press lands on the desk beside
 * this phone ([LobbyHouse]), the desk is read once at LIGHTS OUT ([LobbyHouse.armingInputs]), the
 * house arms off that reading ([RoundAuthority]), and from then on nothing the desk does moves the
 * round. In `commonTest` for [HostArmingTest]'s reason: what it tests ships.
 */
class LobbySettingsArmingTest {

    private val seats = List(5) { Seat(it) }
    private val rooms = listOf("PORCH", "PANTRY", "SNUG", "BOOT ROOM", "LOFT")
    private val home = SavedHome(
        name = "THE PLACE ON GIBSON STREET",
        plan = HousePlan.of(
            listOf(
                Floor(
                    name = "GROUND",
                    rooms = rooms.mapIndexed { i, name -> PlanRoom(Room(name), listOf(CellRect(x = i, y = 0, width = 1, height = 1))) },
                ),
            ),
        ),
        map = HouseMap.of(
            registrations = List(10) { i ->
                Registration(MarkerCard(1, MarkerShapes.registrable[i], MarkerId("GIBSN-" + ('A' + i))), Room(rooms[i / 2]))
            },
            terminal = Registration(MarkerCard(1, MarkerShapes.TERMINAL, MarkerId("GIBSN-T")), Room(rooms.first())),
            meeting = Registration(MarkerCard(1, MarkerShapes.MEETING, MarkerId("GIBSN-U")), Room(rooms[2])),
        ),
    )

    /** What the desk read at LIGHTS OUT, with the host's three rows as [discussion], [egress] and [revoke]. */
    private fun inputs(discussion: Int, egress: Int, revoke: Int) = ArmingInputs(
        seats = seats,
        names = seats.mapIndexed { i, s -> SeatNamed(s, "NAME$i") },
        oneLines = seats.map { OneLineHeld(it, "i borrowed the ladder ${it.index}") },
        chosenInsiders = 1,
        voteWindowSeconds = Balance.VOTE_WINDOW_SECONDS,
        discussionSeconds = discussion,
        egressTimerSeconds = egress,
        revokeCooldownSeconds = revoke,
        readyToArm = true,
        home = home,
    )

    /** **The desk holds the design's numbers until the host moves a row, and hands on what it holds.** */
    @Test
    fun theDeskHandsTheHostsThreeRowsToTheArming() {
        val house = LobbyHouse(CoroutineScope(Dispatchers.Unconfined))
        val untouched = house.armingInputs()
        assertEquals(Balance.DISCUSSION_SECONDS, untouched.discussionSeconds, "an untouched desk held a talk length of its own")
        assertEquals(Balance.EGRESS_TIMER_SECONDS, untouched.egressTimerSeconds, "an untouched desk held an Egress timer of its own")
        assertEquals(Balance.REVOKE_COOLDOWN_SECONDS, untouched.revokeCooldownSeconds, "an untouched desk held a Revoke cooldown of its own")

        house.setDiscussion(60)
        house.setEgressTimer(60)
        house.setRevokeCooldown(30)
        val moved = house.armingInputs()
        assertEquals(60, moved.discussionSeconds, "the DISCUSSION row never reached the arming")
        assertEquals(60, moved.egressTimerSeconds, "the EGRESS TIMER row never reached the arming")
        assertEquals(30, moved.revokeCooldownSeconds, "the REVOKE COOLDOWN row never reached the arming")
    }

    /**
     * **Each locks at arming** (D-037): the house arms with what the desk held, and the desk moving
     * afterwards moves nothing on the running round — *or whoever runs the server can shorten their
     * own Revoke cooldown mid-round while holding an armed token*.
     */
    @Test
    fun eachSettingLocksAtArming() = runBlocking<Unit> {
        var held = inputs(discussion = 60, egress = 60, revoke = 30)
        val pushed = mutableListOf<Effect>()
        val authority = RoundAuthority(inputs = { held }, emit = { effect: Effect, _: GameState -> pushed += effect }, note = {})
        assertTrue(authority.arm(SEED), "the round did not arm")

        fun assertLocked(moment: String) {
            assertEquals(60, authority.state.discussionSeconds, "the talk's length moved $moment")
            assertEquals(60, authority.state.egressTimerSeconds, "the Egress timer moved $moment")
            assertEquals(30, authority.state.revokeCooldownSeconds, "the Revoke cooldown moved $moment")
        }
        assertLocked("at arming")
        assertEquals(
            listOf(30L * Balance.TICKS_PER_SECOND),
            pushed.filterIsInstance<Effect.OpeningMessage>().map { it.revokeCooldown }.distinct(),
            "LIGHTS OUT told the phones a cooldown the host did not set",
        )

        // The desk moves after LIGHTS OUT; the round does not.
        held = inputs(discussion = 15, egress = 15, revoke = 5)
        authority.at(5)
        assertLocked("after the desk moved mid-round")
    }

    /**
     * **The house's clock closes the talk on the locked length, not the default's ninety** (D-317).
     * [HouseClockTest]'s arrangement: elapsed time handed in, a second at a time.
     */
    @Test
    fun theHouseClosesTheTalkOnTheLockedLength() {
        val armed = reduce(
            GameState.EMPTY,
            Event.RoundArmed(Tick(0), seed = 1L, seats = seats, insiders = listOf(Seat(1)), discussionSeconds = 60),
        ).state
        val talking = armed.copy(meeting = Meeting.called(Seat(0), MeetingTrigger.MeetingCard).copy(phase = MeetingPhase.Discussion))
        val second = 1_000_000_000L
        var clock = HouseClock.started()
        val due = mutableListOf<Event>()
        fun run(seconds: Int) = repeat(seconds * Balance.TICKS_PER_SECOND.toInt()) {
            val turned = clock.turned(second / Balance.TICKS_PER_SECOND, talking)
            clock = turned.next
            due += turned.due
        }
        run(59)
        assertEquals(emptyList(), due, "a 60 s talk closed before sixty seconds")
        run(2)
        assertEquals(1, due.count { it is Event.DiscussionClosed }, "a 60 s talk did not close at sixty seconds, so the house ran its own ninety")
    }

    private companion object {
        const val SEED = 20261121L
    }
}
