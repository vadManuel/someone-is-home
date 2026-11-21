package home.someoneshome.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **What JOIN A HOME says under NETWORKS NEARBY, and every way it moves** (D-309).
 *
 * The three captions are the owner's words: LISTENING FOR A HOME ON THIS NETWORK while looking, NO
 * HOME ON THIS NETWORK after a look of [LobbyModel.NO_HOME_AFTER_MILLIS] that found nothing, and
 * LOCAL NETWORK IS OFF when the OS refuses the search. The radio is a [ScriptedHomeFinder] and the
 * screen's clock is [LobbyModel.lookedFor], called as the screen's timer calls it.
 */
class JoinListeningTest {

    private val bungalow = NearbyHome("THE BUNGALOW", "192.168.1.24", 47747)

    private fun looking(): Pair<LobbyModel, ScriptedHomeFinder> {
        val finder = ScriptedHomeFinder()
        val lobby = LobbyModel(finder = finder)
        lobby.look()
        return lobby to finder
    }

    @Test
    fun aLookStartsAtListening() {
        val (lobby, _) = looking()
        assertEquals(Listening.Looking, lobby.listening, "a look that has just begun does not say it is listening")
    }

    @Test
    fun aLookThatFoundNothingForTenSecondsSaysNoHome() {
        val (lobby, _) = looking()
        lobby.lookedFor(lobby.looks)
        assertEquals(Listening.NothingFound, lobby.listening, "ten seconds of nothing did not say NO HOME ON THIS NETWORK")
    }

    @Test
    fun aHomeThatAnswersAfterNoHomeIsListed() {
        val (lobby, finder) = looking()
        lobby.lookedFor(lobby.looks)
        finder.answers(bungalow)
        assertEquals(listOf(bungalow), lobby.nearby, "a home that answered after NO HOME was not listed")
        assertEquals(Listening.Looking, lobby.listening, "a home answered and the phone still says NO HOME")
    }

    @Test
    fun aRefusedSearchSaysLocalNetworkIsOff() {
        val (lobby, finder) = looking()
        finder.refuses()
        assertEquals(Listening.Off, lobby.listening, "a refused search did not say LOCAL NETWORK IS OFF")
        lobby.lookedFor(lobby.looks)
        assertEquals(Listening.Off, lobby.listening, "the timer of a refused look turned OFF into NO HOME")
    }

    @Test
    fun offHoldsAcrossARestartThatIsRefusedAgain() {
        val (lobby, finder) = looking()
        finder.refuses()
        lobby.lookAgain()
        assertEquals(2, finder.starts, "coming back did not look again")
        assertEquals(Listening.Off, lobby.listening, "OFF flickered back to LISTENING on the restart")
        finder.refuses()
        lobby.lookedFor(lobby.looks)
        assertEquals(Listening.Off, lobby.listening, "a phone still refused stopped saying OFF")
    }

    @Test
    fun offClearsWhenTheRestartedSearchRunsUnrefused() {
        val (lobby, finder) = looking()
        finder.refuses()
        lobby.lookAgain()
        lobby.lookedFor(lobby.looks)
        assertEquals(
            Listening.NothingFound, lobby.listening,
            "Local Network was switched on and the look ran ten seconds unrefused, and the phone still says OFF",
        )
    }

    @Test
    fun offClearsWhenAHomeAnswers() {
        val (lobby, finder) = looking()
        finder.refuses()
        lobby.lookAgain()
        finder.answers(bungalow)
        assertEquals(Listening.Looking, lobby.listening, "a home answered and the phone still says OFF")
        assertEquals(listOf(bungalow), lobby.nearby, "the home that answered was not listed")
    }

    @Test
    fun aRestartRestartsTheCount() {
        val (lobby, _) = looking()
        val first = lobby.looks
        lobby.lookAgain()
        lobby.lookedFor(first)
        assertEquals(Listening.Looking, lobby.listening, "the first look's timer said NO HOME over the restarted look")
        lobby.lookedFor(lobby.looks)
        assertEquals(Listening.NothingFound, lobby.listening, "the restarted look's own timer did nothing")
    }
}
