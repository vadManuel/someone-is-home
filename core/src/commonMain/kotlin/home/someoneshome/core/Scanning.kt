package home.someoneshome.core

import home.someoneshome.model.EntityId
import home.someoneshome.model.Event
import home.someoneshome.model.HouseMap
import home.someoneshome.model.MarkerCard
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.OpenSubroutine
import home.someoneshome.model.OrderEntry
import home.someoneshome.model.Seat
import home.someoneshome.model.SubroutineKind
import home.someoneshome.model.Tick

/*
 * **What a scan is, in a round.**
 *
 * Two halves, and they sit on opposite sides of `reduce`.
 *
 * `routeScan` is ABOVE the rules, beside `armingFor`: a piece of paper arrives and something has
 * to decide what kind of fact it is. That decision needs the home's map, which the rules have
 * never held and must not start holding -- the round knows cards, and `HouseMap` is what turns a
 * card into a place.
 *
 * `instanceFor` is INSIDE them: it is the question the house asks, drawn from the round's own seed
 * every time a scan opens a Subroutine, so it replays from the recording exactly as the arming
 * draw does.
 */

/**
 * **What one scanned card turns into** (D-121, D-124, D-072).
 *
 * Three cases and no fourth. The split lives here rather than in `reduce` because two of the three
 * are not events at all, and an enum of reasons handed to the rules would have made the routing a
 * branch inside a client-visible path — which is the shape rule 1 spends its whole length on.
 */
sealed interface ScanRouting {

    /**
     * An ordinary registered card, and the Terminal with it. The rules resolve `(seat, card)`.
     *
     * **The Terminal routes here too, and the rules answer it as the station** (D-199). It is a
     * registered card and the scan is one event either way; what turns it into the station is
     * that the arming event names the T card, so `reduce` recognises it by id and answers with the
     * station's own effect. No fourth case here: the routing decides what kind of fact the paper
     * is, and a scan of the T card is the same fact as any scan -- *this seat read this card*.
     */
    data class Work(val event: Event.MarkerScanned) : ScanRouting

    /**
     * **The meeting card, and it is never work** (D-121).
     *
     * *A meeting is called by standing at this card.* The caller's scan is their check-in, so this
     * is a single event and there is no second one. A meeting card that resolved through the work
     * plane would answer NOTHING FOR YOU HERE and the party would never be called — the failure
     * would present as *the meeting card is broken* rather than as a routing bug.
     */
    data class Meeting(val event: Event.MeetingCalled) : ScanRouting

    /**
     * **Paper that is not part of this home. No event, and no report to anybody** (D-071, D-072).
     *
     * The client says so plainly — the one refusal allowed to be specific, because it is a fact
     * about a piece of paper rather than about a player — and **nothing else happens**. No count at
     * the end of the round, no notice at the next meeting: either would turn the app into an
     * arbiter of a player's claim, letting the room verify testimony. Unreported it becomes
     * material instead, which is the decision rather than an omission.
     *
     * It is an object with no event precisely so that this stays impossible to route into the
     * rules by accident.
     */
    data object Unregistered : ScanRouting
}

/**
 * **Which of the three a scanned card is**, decided against the home's map and nothing else.
 *
 * Above the rules for [armingFor]'s reason: `reduce` stays total and never learns that a house has
 * a shape, and the branch is provably outside every client-visible path rather than reviewed as
 * being outside one.
 *
 * The meeting card is asked about first, because it is the one card whose *identity* changes what
 * the scan means. Everything else registered is work, and the difference between an ordinary
 * marker holding work for you and one holding none is the rules' to answer, not this function's —
 * that is D-124's whole point, and a routing that pre-answered it would have to know the work
 * order, which would put the answer key one call away from the map.
 */
fun routeScan(at: Tick, actor: Seat, card: MarkerCard, map: HouseMap): ScanRouting = when {
    map.meeting?.card?.id == card.id ->
        ScanRouting.Meeting(Event.MeetingCalled(at, actor, MeetingTrigger.MeetingCard))

    map.terminal?.card?.id == card.id || map.registrationOf(card.id) != null ->
        ScanRouting.Work(Event.MarkerScanned(at, actor, card.id))

    else -> ScanRouting.Unregistered
}

/**
 * **The question the house asks, drawn fresh by every scan** (D-139, D-140, E-L3-2).
 *
 * D-139 and D-140 both rule the same architecture: *the house sends the parameters when the scan
 * opens the Subroutine, the client renders the motion deterministically from them, the tap is the
 * entry, and the house grades it.* No authored motion on the wire, no effect grows a member, no
 * schema row for a picture. This is that draw.
 *
 * ### [id] is what makes a re-scan a different question
 *
 * The mint is seeded, recorded and monotonic (rule 4), so **the second scan of a card cannot draw
 * what the first drew** and a replay of the same recording draws exactly what it drew before. The
 * tempting alternative — seed off the seat and the entry — is stable across re-scans by
 * construction, which is precisely the thing D-139 forbids: *a retry is a fresh judgment rather
 * than a second run at a picture the player has already memorised.*
 *
 * A clock reading would have worked too and is worse: two events at one tick draw one question,
 * and the resolution of the tick would silently become a game rule.
 *
 * ### Both halves come out together, and the answer never leaves this module
 *
 * [OpenSubroutine.parameters] is the question and [OpenSubroutine.expected] is the answer to *that*
 * question. They are drawn from one stream and stored on one instance because they are one fact:
 * an answer that outlived its parameters would be the house grading a picture it no longer shows.
 *
 * **The draw knows what it is dealing** (E-L4-3, closed). It was three numbers out of one range
 * with no idea what they meant, which could deal a Sniff with two groups of the same size or a
 * Drift whose dot is in plain sight at the buzz — instances the clients refuse by name. Now
 * [drawParameters] deals each kind's own shape inside each kind's own bounds and [answerTo] answers
 * the question it just dealt, so **an instance a client would refuse cannot be dealt.**
 *
 * It does not read `Role`. D-109 grades every entry for real, for both roles, by the same rule, and
 * a question drawn per role would put the game's central secret into the answer key.
 */
internal fun instanceFor(
    seed: Long,
    id: EntityId,
    seat: Seat,
    entry: OrderEntry,
    /**
     * What the entry opens as, taken from `AssignedWork.opensAs` by the caller.
     *
     * Passed in rather than read off [entry] because not every kind of assigned work opens as a
     * Subroutine — the Array Wipe circuit does not — and a function that had to answer *what if it
     * is not one* would answer it by inventing something. `WorkOrder.openAt` returns only entries a
     * scan opens, so by the time this is called the question has already been settled once.
     */
    kind: SubroutineKind,
    marker: MarkerId,
): OpenSubroutine {
    val draw = Draw(seed + id.value * INSTANCE_STRIDE, DOMAIN_INSTANCE)
    val parameters = drawParameters(kind, draw)
    return OpenSubroutine(
        seat = seat,
        entry = entry.index,
        subroutine = kind,
        parameters = parameters,
        expected = answerTo(kind, parameters),
        armedAt = marker,
    )
}

private const val DOMAIN_INSTANCE = 8L
private const val INSTANCE_STRIDE = 0x2545F4914F6CDD1DL
