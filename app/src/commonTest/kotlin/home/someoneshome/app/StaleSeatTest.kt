package home.someoneshome.app

import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.LobbyBody
import home.someoneshome.model.protocol.LobbyWire
import home.someoneshome.model.protocol.SeatToken
import home.someoneshome.model.protocol.TransportFrame
import home.someoneshome.model.protocol.TransportRefusal
import home.someoneshome.platform.NoIdleTimer
import home.someoneshome.platform.ScreenWake
import home.someoneshome.platform.monotonicNanos
import home.someoneshome.platform.transport.LobbyDesk
import home.someoneshome.platform.transport.SeatLedger
import home.someoneshome.platform.transport.TransportHost
import home.someoneshome.ui.LobbyModel
import home.someoneshome.ui.NearbyHome
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **The second evening: a phone presents the seat it stored, and this house never issued it**
 * (E-D3-1).
 *
 * A real socket, because the thing under test is a handshake. On 2026-08-21 the same build was
 * launched twice against a fresh house and **nothing seated** — not the client and not the host's
 * own loopback client — for ninety seconds, with the server up and both screens saying nothing at
 * all. The refusal was correct (a house that never minted a token must not honour it, D-087) and
 * the silence was the bug: rule 6 exactly inverted, quiet to the player *and* to the authority.
 *
 * The ruling is **seamless**: the phone drops the stale token and joins fresh by itself, and the
 * player sees no message and no state the eye could catch. So what is asserted here is a lobby
 * that fills, a token that was replaced by one this house minted, and a screen that was never
 * given anything to draw except counts — plus the record, at the host's end, that a connection
 * was turned away.
 *
 * ### It drives [LobbyModel] rather than the link
 *
 * The invisibility is the ruling, and the only place invisibility means anything is the state a
 * screen draws from. Asserting it against the link would be asserting it one layer below where it
 * has to hold.
 */
class StaleSeatTest {

    private fun nowMillis(): Long = monotonicNanos() / 1_000_000

    /** [SeatStore] with no filesystem under it: what this phone kept, and what it threw away. */
    private class Kept(private var token: String?) : SeatStore {
        val forgotten = mutableListOf<String>()
        override fun stored(): String? = token
        override fun keep(token: String, hostAddress: String) {
            this.token = token
        }
        override fun forget() {
            token?.let { forgotten += it }
            token = null
        }
    }

    /** The token this phone is carrying in from an evening that is over. */
    private val stale = "a-token-no-house-here-ever-minted"

    @Test
    fun aSeatTokenThisHouseNeverIssuedIsDroppedAndThePhoneSeatsItselfFresh() = runBlocking {
        var minted = 0
        val ledger = SeatLedger(List(4) { Seat(it) }, mint = { SeatToken("tk-${minted++}") })
        val desk = LobbyDesk()
        val refused = Channel<TransportRefusal>(Channel.UNLIMITED)
        lateinit var host: TransportHost

        suspend fun publish() {
            val body = TransportFrame.Carry(LobbyWire.encode(desk.standing()))
            for (seat in host.connectedSeats()) runCatching { host.send(seat, body) }
        }

        val published = Channel<Unit>(Channel.UNLIMITED)
        host = TransportHost(
            ledger,
            nowMillis = ::nowMillis,
            onFrame = { seat, frame ->
                val body = (frame as? TransportFrame.Carry)?.body?.let(LobbyWire::decodeOrNull)
                if (body is LobbyBody.Naming) {
                    desk.named(seat, body.name)
                    published.trySend(Unit)
                }
            },
            onSeated = { desk.seated(it); published.trySend(Unit) },
            onLost = { desk.left(it); published.trySend(Unit) },
            onRefused = { refused.trySend(it) },
        )

        val port = host.start(0)
        val log = mutableListOf<String>()
        val kept = Kept(stale)
        val link = LobbyOverTransport(this, onEvent = { log += it }, seat = kept, wake = ScreenWake(NoIdleTimer))
        val model = LobbyModel(link = link)
        model.nameResident("ELLIOT")
        try {
            withTimeout(30_000) {
                // The host's own bookkeeping loop, which is what a house does: something changed,
                // tell everybody how it stands.
                val pump = launch {
                    while (true) {
                        published.receive()
                        publish()
                    }
                }
                model.attachTo(NearbyHome("THE BUNGALOW", "127.0.0.1", port))

                // **The whole assertion is that this arrives at all.** With the stale token
                // presented and nothing done about the refusal, it never does: the phone holds a
                // seat this house has never heard of and waits for a house that will never answer.
                val seen = mutableListOf<LobbyBody.Standing>()
                while (model.residents != listOf("ELLIOT")) {
                    assertNull(
                        model.refusal,
                        "the player was shown something on the way to a seat: ${model.refusal}",
                    )
                    seen += model.standing
                    delay(50)
                }
                pump.cancel()

                assertEquals(1, model.standing.joined, "the lobby filled with somebody else")
                assertTrue(
                    seen.all {
                        it == LobbyBody.Standing(joined = 0, linesIn = 0) ||
                            it == LobbyBody.Standing(joined = 1, linesIn = 0, names = listOf(""))
                    },
                    "the screen was given a state that is neither empty nor this lobby: $seen",
                )
                assertNull(model.refusal, "a seated phone is holding a refusal")
            }

            // ---- The token: the stale one is gone, and a real one took its place ------------
            assertEquals(
                listOf(stale), kept.forgotten,
                "the token this house refused is still on the phone, ready to be refused again",
            )
            assertEquals(
                "tk-0", kept.stored(),
                "the phone did not store the seat this house actually gave it",
            )

            // ---- The record: loud to the authority, at both ends ---------------------------
            //
            // `tryReceive`, and never a bare `receive`. The first version of this line was a
            // `receive()` outside any timeout, so a build where the house recorded nothing left
            // the test **hanging instead of failing** — which the injection that removes
            // `onRefused` found by running for ten minutes and reporting nothing at all. A guard
            // that hangs is a guard that will one day be stopped by a CI timeout and read as
            // infrastructure. Everything above has already waited for the seating, so by here the
            // refusal has either been recorded or it never will be.
            assertEquals(
                TransportRefusal.UnknownToken, refused.tryReceive().getOrNull(),
                "the house turned a connection away and recorded nothing",
            )
            assertNotNull(
                log.firstOrNull {
                    it.contains("REFUSED") && it.contains(TransportRefusal.UnknownToken.name)
                },
                "this phone was refused and said so nowhere: $log",
            )
            assertFalse(
                log.any { it.contains(stale) },
                "the log recited the token it was recording the refusal of: $log",
            )
        } finally {
            link.leave()
            host.stop()
        }
    }

    /**
     * **The rejoin happens once, and the second answer is final** — the fuse.
     *
     * A stale token walking into a house that has already armed: the token is refused, the phone
     * starts over as a stranger exactly as it is now ruled to, and the locked round refuses the
     * stranger too. **Two knocks and then silence.** Without the fuse this is a phone rejoining
     * forever against a door it has been told is shut, with the host's log filling up one refusal
     * at a time — a retry loop is a worse failure than the silence it replaced, and it would take
     * an evening to notice.
     *
     * The seconds of quiet at the end are the assertion. A third refusal is what failure looks
     * like here and nothing else would show it.
     */
    @Test
    fun theFreshJoinIsTriedOnceAndARefusedStrangerStopsThere() = runBlocking {
        val ledger = SeatLedger(List(2) { Seat(it) }, mint = { SeatToken("tk") })
        // A channel and not a list: these arrive on Ktor's worker threads, and a `MutableList`
        // written from there and read here is the flake nobody reproduces (LobbyLoopbackTest's
        // rule, one layer up).
        val refused = Channel<TransportRefusal>(Channel.UNLIMITED)
        val host = TransportHost(
            ledger,
            nowMillis = ::nowMillis,
            onFrame = { _, _ -> },
            onRefused = { refused.trySend(it) },
        )
        val port = host.start(0)
        val kept = Kept(stale)
        val link = LobbyOverTransport(this, seat = kept, wake = ScreenWake(NoIdleTimer))
        val refusals = mutableListOf<TransportRefusal>()
        try {
            ledger.lock()
            link.join(NearbyHome("THE BUNGALOW", "127.0.0.1", port), "ELLIOT") {}
            withTimeout(30_000) {
                while (refusals.size < 2) {
                    refused.tryReceive().getOrNull()?.let { refusals += it }
                    delay(50)
                }
            }
            // Long enough for a third to arrive if anything were still knocking. The retry this
            // guards against is paced by PRESEAT_RETRY_MILLIS, which is half of it.
            delay(1_000)
            generateSequence { refused.tryReceive().getOrNull() }.forEach { refusals += it }
            assertEquals(
                listOf(TransportRefusal.UnknownToken, TransportRefusal.RoundLocked), refusals,
                "the phone did not stop at the second refusal",
            )
            assertNull(kept.stored(), "the refused token is still on the phone")
        } finally {
            link.leave()
            host.stop()
        }
    }
}
