package home.someoneshome.core

import home.someoneshome.model.ContactSeed
import home.someoneshome.model.ContactToken
import home.someoneshome.model.ContactTokens
import home.someoneshome.model.ContactWindow
import home.someoneshome.model.Seat
import kotlin.test.Test
import kotlin.test.assertEquals

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

    /** The launcher's fixed seed. Changing it means changing `round.sh` and the Swift tests too. */
    private val launcherSeed = 20261116L

    @Test
    fun `the launcher's seed gives these seat seeds`() {
        assertEquals(ContactSeed("7995882527fa09a3ce75db487404b23a"), contactSeedFor(launcherSeed, Seat(0)))
        assertEquals(ContactSeed("130f00039e6f9efdc337d82ba3bf7b59"), contactSeedFor(launcherSeed, Seat(1)))
        assertEquals(ContactSeed("fc274ca75d79c8f78e2e496afa36a637"), contactSeedFor(launcherSeed, Seat(2)))
    }

    @Test
    fun `and those seats advertise these tokens`() {
        val one = contactSeedFor(launcherSeed, Seat(1))
        assertEquals(ContactToken("8c714648964924c65201ff4a48e23f37"), token(one, 0L))
        assertEquals(ContactToken("bb3b6d5dcbbc59c87787b1959b3a982a"), token(one, 1L))
        assertEquals(ContactToken("16dd923b17fd4239b701611561bfb7a2"), token(one, 117_888_000L))
        val zero = contactSeedFor(launcherSeed, Seat(0))
        assertEquals(ContactToken("0aa2b4e3ff3c4b96c60062568d758520"), token(zero, 117_888_000L))
    }

    /** A negative round seed, so the Swift side's signed big-endian key is pinned as well. */
    @Test
    fun `a negative round seed keys the same way`() {
        val one = contactSeedFor(-20261116L, Seat(1))
        assertEquals(ContactSeed("d9c79a2954673537a3a6a6bb392f3010"), one)
        assertEquals(ContactToken("d929b7cf1bc04ef329dd518e9970508d"), token(one, 117_888_000L))
    }

    /**
     * **At three seats the launcher's seed deals the Insider role to seat 0, the host phone.**
     *
     * The bench's scripted Revoke (`RoundCheat.theRevoke`) targets the first living plain Resident
     * other than the Insider, so this is what puts the Revoke on seat 1, a Simulator, and leaves the
     * one real phone living and scanning. The Mac then advertises as seat 1 and the phone's report
     * of it is the check. Any other dealing would Revoke the phone itself, which stops its scan.
     */
    @Test
    fun `the launcher's seed deals seat 0 the Insider role at three seats`() {
        assertEquals(listOf(Seat(0)), insidersFor(launcherSeed, (0 until 3).map { Seat(it) }, null))
    }
}
