package home.someoneshome.model.protocol

import home.someoneshome.model.Intent
import home.someoneshome.model.MarkerId
import home.someoneshome.model.Seat
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * **The upward wire: what a phone may ask for, and what it is structurally unable to say.**
 *
 * `RoundWireTest`'s mirror. That file's central assertion is that a message addressed to one seat
 * cannot tell you which seat — proved by rendering the same effect for two recipients and demanding
 * identical bytes. This file's is the same property pointing the other way: **the same bytes
 * arriving on two connections build two different requests**, because the frame contributes nothing
 * at all to who is asking.
 */
class IntentWireTest {

    /**
     * The field names an actor could ride in. `target` is deliberately not among them: a vote's
     * target is **content**, drawn from a ballot the house sent this phone and published live to
     * the couch (D-117). The rule is not *no seats on this wire*, it is *no seat that says who is
     * asking*.
     */
    private val FORBIDDEN = listOf("actor", "seat", "voter", "caller", "sender", "from")

    private val marker = MarkerId("CARD-11")

    /**
     * Every member of [IntentMessage], once.
     *
     * Kept honest by [requestFor] below, which is exhaustive over [Intent] and therefore stops
     * compiling the moment a request exists that nothing here names.
     */
    private val everyMessage: List<IntentMessage> = listOf(
        IntentMessage.ArmRevoke,
        // Both Tier-2 targets, and the self-surge is a member of this list on purpose: a message
        // naming the sender's own seat has to survive the wire unchanged, because self-targeting
        // is required (gdd.md:393) and there is nothing on the frame that marks it as one.
        IntentMessage.FireSurge(Seat(2)),
        IntentMessage.FireSurge(Seat(0)),
        IntentMessage.FireIsolate(marker),
        IntentMessage.BeginSubroutine(marker),
        IntentMessage.CallMeeting,
        IntentMessage.CheckIn,
        IntentMessage.DeclareReadyToVote,
        IntentMessage.SelectVote(Seat(2)),
        IntentMessage.SelectVote(null),
        IntentMessage.LockVote,
        IntentMessage.HandOverEntry(marker, listOf(3, 1, 4)),
    )

    /**
     * **The list above, closed against the type it is meant to cover.**
     *
     * Exhaustive over [Intent] on purpose: a request that exists and is named nowhere here is a
     * compile error rather than a case this file quietly stops checking. It is the reverse of
     * [IntentMessage.attributedTo], and running the two against each other is what proves neither
     * of them lost anything on the way.
     */
    private fun requestFor(intent: Intent): IntentMessage = when (intent) {
        is Intent.ArmRevoke -> IntentMessage.ArmRevoke
        is Intent.FireSurge -> IntentMessage.FireSurge(intent.target)
        is Intent.FireIsolate -> IntentMessage.FireIsolate(intent.target)
        is Intent.FireSpoof -> IntentMessage.FireSpoof(intent.room)
        is Intent.BeginSubroutine -> IntentMessage.BeginSubroutine(intent.marker)
        is Intent.CallMeeting -> IntentMessage.CallMeeting
        is Intent.CheckIn -> IntentMessage.CheckIn
        is Intent.DeclareReadyToVote -> IntentMessage.DeclareReadyToVote
        is Intent.SelectVote -> IntentMessage.SelectVote(intent.target)
        is Intent.LockVote -> IntentMessage.LockVote
        is Intent.HandOverEntry -> IntentMessage.HandOverEntry(intent.marker, intent.entered)
    }

    // ---- 1 · The frame cannot say who sent it ------------------------------------------------------

    /**
     * **The one assertion this whole channel exists to be able to make.**
     *
     * The same bytes, decoded once and attributed twice, are two different requests — and the only
     * thing that differed was the connection they were handed to. There is no argument
     * [IntentMessage.attributedTo] can be passed that a client chose, so the forge is not
     * discouraged, it is unrepresentable.
     *
     * A screen keeping its hands clean is a convention somebody can break in one edit. A frame with
     * nowhere to put a seat is not.
     */
    @Test
    fun theSameBytesFromTwoConnectionsAreTwoDifferentRequests() {
        for (message in everyMessage) {
            val bytes = RoundWire.encode(RoundBody.Act(seq = 1, intent = message))
            val decoded = RoundWire.decodeOrNull(bytes)
            assertTrue(decoded is RoundBody.Act, "an Act did not survive the wire: $bytes")

            val fromSeatFour = decoded.intent.attributedTo(Seat(4))
            val fromSeatOne = decoded.intent.attributedTo(Seat(1))

            assertNotEquals(
                fromSeatFour, fromSeatOne,
                "identical bytes built the same request for two different connections — " +
                    "something in $message is deciding who is asking",
            )
            assertEquals(Seat(4), actorOf(fromSeatFour))
            assertEquals(Seat(1), actorOf(fromSeatOne))
        }
    }

    /**
     * **No type on the upward wire has a field an actor could ride in.**
     *
     * Read off the **serial descriptor**, which is the type as the wire sees it, and the reason it
     * is read that way is worth keeping: the first version of this test swept the rendered bytes
     * only, and an injection walked straight through it. Adding `actor: Seat? = null` to a member
     * left the bytes untouched — `encodeDefaults` is off, so a defaulted null renders nothing — 
     * while the type had grown exactly the field this file exists to forbid, and a client that set
     * it would have been attributed to somebody else.
     *
     * The forbidden names are the ones [Intent] and `Event` actually use for attribution — `actor`,
     * `seat`, `voter`, `caller` — plus the two a well-meaning protocol change would reach for.
     */
    @Test
    fun noMessageCanSayWhoSentIt() {
        val named = fieldsOf(IntentMessage.serializer().descriptor).filter { it in FORBIDDEN }
        assertEquals(
            emptyList(), named,
            "the upward wire has a field a client could name a seat in: $named",
        )

        // **The rendered bytes as well, and with defaults written out.** The descriptor above is
        // the real guard; this is the second instrument, and it exists because the first version of
        // this test was ONLY the rendering — which an injection walked straight through. A nullable
        // `actor` defaulting to null is omitted by the encoder, so the bytes were clean while the
        // type had grown exactly the field this file forbids. `encodeDefaults` is what closes that,
        // and the two checks now fail for different reasons rather than for the same one.
        val loud = Json { encodeDefaults = true }
        for (message in everyMessage) {
            val bytes = loud.encodeToString(
                RoundBody.serializer(),
                RoundBody.Act(seq = 7, intent = message),
            )
            for (name in FORBIDDEN) {
                assertTrue(
                    !bytes.contains("\"$name\""),
                    "an upward frame carries a '$name' field, which is a client naming a seat: $bytes",
                )
            }
        }
    }

    /**
     * Every field name reachable from [descriptor], following nested types.
     *
     * Reads the **type** rather than a rendering, which is the whole difference between a guard
     * that survives an encoder setting and one that does not. Cycles are cut on serial name, which
     * a sealed hierarchy nested inside itself would otherwise walk forever.
     */
    private fun fieldsOf(
        descriptor: SerialDescriptor,
        seen: MutableSet<String> = mutableSetOf(),
        found: MutableList<String> = mutableListOf(),
    ): List<String> {
        if (!seen.add(descriptor.serialName)) return found
        for (i in 0 until descriptor.elementsCount) {
            found += descriptor.getElementName(i)
            fieldsOf(descriptor.getElementDescriptor(i), seen, found)
        }
        return found
    }

    /**
     * **A forged frame that names a seat does not decode, so it never reaches a desk at all.**
     *
     * The positive control for the test above: [RoundWire]'s decoder is strict, so a body carrying a
     * field the type does not have is refused rather than quietly stripped. Both halves matter — a
     * decoder that ignored unknown keys would also be safe here, because the desk stamps the
     * connection's seat regardless, but *this* is the stronger of the two behaviours and it is worth
     * knowing which one is in force.
     *
     * The refusal is a `null`, which the host's `onFrame` treats as *not a round body* and leaves
     * alone. A forged frame therefore changes nothing and takes nobody's connection down with it.
     */
    @Test
    fun aFrameThatTriesToNameASeatIsRefusedRatherThanStripped() {
        val honest = RoundWire.encode(RoundBody.Act(seq = 1, intent = IntentMessage.CheckIn))
        assertTrue(RoundWire.decodeOrNull(honest) is RoundBody.Act, "the honest frame did not decode")

        // The forge, written the way somebody would write it: the real frame with a seat added.
        val forged = honest.replace(
            """"intent":{"type":"CheckIn"}""",
            """"intent":{"type":"CheckIn","actor":3}""",
        )
        assertNotEquals(honest, forged, "the forge did not change the frame, so it proved nothing")
        assertEquals(
            null, RoundWire.decodeOrNull(forged),
            "a frame claiming a seat was accepted: $forged",
        )
    }

    // ---- 2 · One request, one message, nothing lost ------------------------------------------------

    /** Every message is exactly the request it names, both ways round. */
    @Test
    fun everyMessageIsTheRequestItNames() {
        for (message in everyMessage) {
            assertEquals(
                message, requestFor(message.attributedTo(Seat(0))),
                "a request lost or gained something on the way through the wire type",
            )
        }
    }

    /** Every message survives the wire, and the sequence with it. */
    @Test
    fun everyMessageSurvivesTheWire() {
        for (message in everyMessage) {
            val body = RoundBody.Act(seq = 12, intent = message)
            assertEquals(
                body, RoundWire.decodeOrNull(RoundWire.encode(body)),
                "an Act did not round-trip",
            )
        }
    }

    /**
     * **The three directions stay three, and a body only ever travels its own way.**
     *
     * A `Push` decoded as an `Act` would be a client's request being taken for the house's word;
     * the discriminators are what keep them apart, and this is the assertion that notices if two
     * ever collide.
     */
    @Test
    fun theThreeBodiesAreThreeDifferentThings() {
        val bodies = listOf(
            RoundBody.Push(seq = 1, message = RoundMessage.LampSet(0)),
            RoundBody.Gap(held = 1),
            RoundBody.Act(seq = 1, intent = IntentMessage.CheckIn),
        )
        val encoded = bodies.map(RoundWire::encode)
        assertEquals(encoded.size, encoded.toSet().size, "two upward bodies render the same bytes")
        for ((body, bytes) in bodies.zip(encoded)) {
            assertEquals(body, RoundWire.decodeOrNull(bytes), "a body decoded as something else")
        }
    }

    /** The attribution, read back out — the test's own accessor, never the wire's. */
    private fun actorOf(intent: Intent): Seat = when (intent) {
        is Intent.ArmRevoke -> intent.actor
        is Intent.FireSurge -> intent.actor
        is Intent.FireIsolate -> intent.actor
        is Intent.FireSpoof -> intent.actor
        is Intent.BeginSubroutine -> intent.actor
        is Intent.CallMeeting -> intent.actor
        is Intent.CheckIn -> intent.actor
        is Intent.DeclareReadyToVote -> intent.actor
        is Intent.SelectVote -> intent.actor
        is Intent.LockVote -> intent.actor
        is Intent.HandOverEntry -> intent.actor
    }
}
