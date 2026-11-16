package home.someoneshome.core

import home.someoneshome.model.ContactSeed
import home.someoneshome.model.ContactToken
import home.someoneshome.model.ContactTokens
import home.someoneshome.model.ContactWindow
import home.someoneshome.model.Event
import home.someoneshome.model.GameState
import home.someoneshome.model.Seat
import home.someoneshome.model.Tick
import home.someoneshome.model.WinRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * **The values the Mac's BLE peer (`tools/ble-peer`) is pinned to, and the one fact the one-phone
 * launcher (`tools/one-phone/round.sh`) leans on.**
 *
 * The peer is a Swift CoreBluetooth tool that advertises a seat's token from the Mac, so the one
 * phone the owner has can hear something shaped exactly like another phone. It derives the seat's
 * seed and the window's token in Swift, from CryptoKit, and its own tests assert the values below.
 * If this file and `tools/ble-peer/Tests` ever disagree, the Mac is advertising tokens the house
 * cannot resolve, and a check run against it would fail for a reason that is not the phone's.
 *
 * Every value was computed a third time in Python's `hmac`, as [ContactSeedTest]'s are.
 */
class OnePhoneVectorsTest {

    private fun token(seed: ContactSeed, window: Long): ContactToken = ContactTokens.of(seed, ContactWindow(window))

    /**
     * The launcher's fixed seed. Changing it means changing `round.sh`, the README and the Swift
     * tests too.
     *
     * **20261150, not 20261116** (D-228). 20261116 dealt the Insider role to seat 0 at three seats
     * and to seat 2 at four, where the bench's Revoke would have landed on the phone itself. Of the
     * seeds from 20261116 to 20261160, 20261150 is the first that deals seat 0 at both counts, so
     * `--seats 3` and `--seats 4` play under one seed.
     */
    private val launcherSeed = 20261150L

    @Test
    fun `the launcher's seed gives these seat seeds`() {
        assertEquals(ContactSeed("f79b6f331bf6d28bbdbeddd74aaa18a2"), contactSeedFor(launcherSeed, Seat(0)))
        assertEquals(ContactSeed("aed69e6fc764d486ab051d7fdc760ad4"), contactSeedFor(launcherSeed, Seat(1)))
        assertEquals(ContactSeed("4f06e585f8ab672c59d4a161b987199f"), contactSeedFor(launcherSeed, Seat(2)))
        assertEquals(ContactSeed("8f12a2ae4efbc0960d06c55f87d91269"), contactSeedFor(launcherSeed, Seat(3)))
    }

    @Test
    fun `and those seats advertise these tokens`() {
        val one = contactSeedFor(launcherSeed, Seat(1))
        assertEquals(ContactToken("f7b01e60a5f1b47206a3c79d535a4b69"), token(one, 0L))
        assertEquals(ContactToken("33cec58d2d012e756c02b98c965fab5f"), token(one, 1L))
        assertEquals(ContactToken("615f52c14cb48655c67bbefca08afd6d"), token(one, 117_888_000L))
        val zero = contactSeedFor(launcherSeed, Seat(0))
        assertEquals(ContactToken("c8246bc64c3355d2ffce2b55a82f6380"), token(zero, 117_888_000L))
    }

    /** A negative round seed, so the Swift side's signed big-endian key is pinned as well. */
    @Test
    fun `a negative round seed keys the same way`() {
        val one = contactSeedFor(-20261116L, Seat(1))
        assertEquals(ContactSeed("d9c79a2954673537a3a6a6bb392f3010"), one)
        assertEquals(ContactToken("d929b7cf1bc04ef329dd518e9970508d"), token(one, 117_888_000L))
    }

    /**
     * **At three seats and at four, the launcher's seed deals the Insider role to seat 0, the host
     * phone, and to nobody else.**
     *
     * The bench's scripted Revoke (`RoundCheat.theRevoke`) targets the first living plain Resident
     * other than the Insider, so this is what puts the Revoke on seat 1, a Simulator, and leaves the
     * one real phone living and scanning. The Mac then advertises as seat 1 and the phone's report
     * of it is the check. Any other dealing would Revoke the phone itself, which stops its scan.
     */
    @Test
    fun `the launcher's seed deals seat 0 the Insider role at three and four seats`() {
        assertEquals(listOf(Seat(0)), insidersFor(launcherSeed, seats(3), null))
        assertEquals(listOf(Seat(0)), insidersFor(launcherSeed, seats(4), null))
    }

    /**
     * **At four seats the bench's Revoke lands on seat 1, and the round goes on** (D-228).
     *
     * At three seats one Revoke leaves one plain Resident against one Insider, which is parity
     * (D-131): the round ends on the spot, and the report and the meeting after it cannot be
     * reached. At four it leaves two against one, so the report and the meeting are played. The
     * target is picked exactly as `RoundCheat.theRevoke` picks it.
     */
    @Test
    fun `at four seats the bench Revokes seat 1 and the round goes on`() {
        val state = GameState.armedRound(
            seed = launcherSeed,
            seats = seats(4),
            insiders = insidersFor(launcherSeed, seats(4), null),
            systemIntegrity = 30,
        )
        val insider = state.livingInsiders.first()
        val target = state.livingPlainResidents.first { it.index != insider.index }
        assertEquals(Seat(1), target, "the bench's Revoke would not land on the first Simulator")

        val afterTheRevoke = state.copy(revoked = listOf(target), newlyRevoked = listOf(target))
        assertNull(
            outcomeOf(afterTheRevoke, Event.RevokeArmed(Tick(0), insider)),
            "one Revoke at four seats ended the round, so no report or meeting can follow it",
        )
        // And the same at three seats does end it, which is why the rig needs the fourth seat.
        val three = GameState.armedRound(launcherSeed, seats(3), insidersFor(launcherSeed, seats(3), null), 30)
        assertEquals(
            WinRoute.Parity,
            outcomeOf(three.copy(revoked = listOf(Seat(1))), Event.RevokeArmed(Tick(0), Seat(0)))?.by,
            "the three-seat round no longer ends at the first Revoke; the reason for the fourth seat is stale",
        )
    }

    private fun seats(n: Int): List<Seat> = (0 until n).map { Seat(it) }
}
