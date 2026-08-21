package home.someoneshome.platform

import home.someoneshome.model.Seat
import home.someoneshome.model.protocol.LobbyBody
import home.someoneshome.model.protocol.LobbyWire
import home.someoneshome.platform.transport.LobbyDesk
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Seen by the house only. Deleted when the round ends.** Both promises, against the actual
 * filesystem of the actual phone.
 *
 * The screen where a player types their one line makes those two promises in the host's own
 * words, and a player is being asked for something real on the strength of them. Every other
 * guard in this repo is about what a *client* is told; this one is about what the *host's own
 * phone* keeps, which nobody can check by looking at a screen and which would survive the
 * evening, the round, and the uninstall-shaped conversation afterwards.
 *
 * ### It reads the real Documents directory, because that is where everything durable is
 *
 * Every store this app has — the saved homes, the house plan, the house map, the seat token, the
 * host address — writes into Documents (deliberately: Caches and tmp are deleted under storage
 * pressure, and a house evaporating because a phone got full is fifteen minutes lost). So a sweep
 * of that one directory is a sweep of everything the app can durably keep. There is no second
 * place to look, and if one is ever added this test is the thing that has to be told about it.
 *
 * ### The positive control is what makes the sweep honest
 *
 * A test that reads no files passes for the same reason a test that reads all of them does. So it
 * first writes a canary through a real store and asserts the sweep **finds** it. An instrument
 * that cannot see a string it was just handed proves nothing about the string it did not find.
 */
class OneLineNeverPersistedTest {

    private val secrets = listOf(
        "i still have priya's spare key",
        "i read the group chat i was removed from",
        "i have never watched the film we all say is our favourite",
    )

    @BeforeTest fun start() = clearSavedHomes()
    @AfterTest fun finish() = clearSavedHomes()

    /** Every readable file under Documents, by path, with its text. */
    private fun everythingThisPhoneKeeps(): Map<String, String> {
        val directory = documentsDirectory()
        val subpaths = NSFileManager.defaultManager.subpathsAtPath(directory).orEmpty()
        return subpaths.filterIsInstance<String>().mapNotNull { relative ->
            val text = NSString.stringWithContentsOfFile(
                path = "$directory/$relative",
                encoding = NSUTF8StringEncoding,
                error = null,
            ) as String?
            text?.let { relative to it }
        }.toMap()
    }

    @Test
    fun `nothing a player typed reaches this phone's storage`() {
        // --- the positive control ------------------------------------------------------------
        val canary = "canary-4f2b-the-sweep-can-read-files"
        saveSavedHomes("someone-is-home/saved-homes/1\nH $canary\n")
        assertTrue(
            everythingThisPhoneKeeps().values.any { canary in it },
            "the sweep could not find a string it had just written — it proves nothing below",
        )

        // --- the whole lobby, for real --------------------------------------------------------
        val desk = LobbyDesk()
        val seats = List(secrets.size) { Seat(it) }
        seats.forEach(desk::seated)
        desk.setInsiders(1)
        seats.forEachIndexed { i, seat ->
            // The client's half too: the line's one authorised exit is this body, and encoding it
            // is the moment a well-meaning cache would be tempted into existence.
            val onTheWire = LobbyWire.encode(LobbyBody.Handover(secrets[i]))
            val arrived = LobbyWire.decodeOrNull(onTheWire) as LobbyBody.Handover
            desk.handedOver(seat, arrived.line)
        }
        val standing = LobbyWire.encode(desk.standing())

        // --- and the phone knows none of it ---------------------------------------------------
        val kept = everythingThisPhoneKeeps()
        for (secret in secrets) {
            for ((path, text) in kept) {
                assertTrue(secret !in text, "a one line was written to $path")
            }
            assertTrue(secret !in standing, "a one line went out in the standing: $standing")
            // Fragments count. The first four words of somebody's line identify it completely in
            // a room of six people who all know each other.
            for (word in secret.split(" ").filter { it.length > 4 }) {
                for ((path, text) in kept) {
                    assertTrue(word !in text, "'$word' from a one line was written to $path")
                }
            }
        }

        // --- deleted when the round ends ------------------------------------------------------
        desk.roundEnded()
        for (seat in seats) assertNull(desk.lineOf(seat), "a line survived the round")
        for ((path, text) in everythingThisPhoneKeeps()) {
            for (secret in secrets) assertTrue(secret !in text, "the round ended and $path still has it")
        }
    }
}
