package home.someoneshome.app

import home.someoneshome.ui.FlowModel
import home.someoneshome.ui.ScreenId

/**
 * **The screen answer to a house this phone cannot reach** (H4 of the 2026-11-17 hunt, D-253).
 *
 * [LobbyOverTransport] reports that the house has been out of reach too long, and that it is back;
 * it cannot reach a screen, so the app root hands both reports here. `DisconnectScreen` existed and
 * nothing walked to it: when the host's phone failed, the guests stayed on whatever they were looking
 * at, a lobby listing a host who was gone among it.
 *
 * **Lost** walks to [ScreenId.Disconnect] and remembers where this phone was. **Back** walks to that
 * screen again, and only if the phone is still on the disconnect screen; whatever the house then
 * re-pushes for the time it was gone (D-097) lands on top of it. The lamp is not touched here: the
 * disconnect screen holds the last authorised state (G3), which is `DisconnectScreen`'s own rule.
 *
 * Every call is made on the composition's dispatcher ([LobbyOverTransport] launches them on its
 * scope), which is the one thread a screen is written from.
 */
class HouseGone(private val panel: () -> FlowModel?) {

    /** Where this phone stood when the house went, or null while the house is reachable. */
    private var before: ScreenId? = null

    fun lost() {
        val flow = panel() ?: return
        if (flow.state.screen == ScreenId.Disconnect) return
        before = flow.state.screen
        flow.push(ScreenId.Disconnect)
    }

    fun back() {
        val flow = panel() ?: return
        val to = before ?: return
        before = null
        if (flow.state.screen == ScreenId.Disconnect) flow.push(to)
    }
}
