package home.someoneshome.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import home.someoneshome.model.Effect
import home.someoneshome.model.EgressType
import home.someoneshome.model.Haptic
import home.someoneshome.model.HapticStep
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MeetingTrigger
import home.someoneshome.model.Seat
import home.someoneshome.platform.Haptics
import home.someoneshome.ui.Amber
import home.someoneshome.ui.Label
import home.someoneshome.ui.LocalPanelInsets
import home.someoneshome.ui.SubroutineModel
import home.someoneshome.ui.tap
import home.someoneshome.ui.u
import kotlinx.coroutines.delay

/**
 * **The haptic bench: every buzz this game has, with a finger on it.**
 *
 * A phone's motor is the one output in this app that cannot be looked at. A screenshot proves a
 * screen; a log line proves the engine was asked and did not refuse; **only a hand proves a
 * buzz.** So this surface exists to put the two halves of the verification within reach of one
 * person: the rows drive the real effects through the real [HouseBuzzer] into the real motor, the
 * log says what the engine did, and the phone is on the table for whoever picks it up.
 *
 * ### The rows build effects, which makes them the house's stand-in and nothing better
 *
 * `FlowHost` has `standingInForTheHouse` for the same reason and carries the same caveat: **there
 * is no authority attached to this build**, so the `Haptic` on each effect below was written down
 * here rather than constructed by `Rules.kt` in the same reduction that constructed the push.
 * That is precisely the arrangement D-134 forbids in play — and it is why this file is in the
 * cheat source set, absent from the compilation of a release build rather than switched off in
 * one. What it proves is the half below the effect: *given an effect carrying a kind, does this
 * phone buzz that kind.* Which kind rides which push is `core`'s, and `core` has its own tests.
 *
 * ### The five are named because a closed set nobody can enumerate is not closed
 *
 * D-135 reserves the long haptic for five events. They are the first five rows, in the ruling's
 * own order, so *are all five here* is a question a person can answer by looking rather than by
 * reading `Rules.kt`.
 */
class HapticCheat(private val haptics: Haptics) {

    private val buzzer = HouseBuzzer(haptics)

    /** Newest first, and mirrored to stdout so the same line is in the device log. */
    val log = mutableStateListOf<String>()

    /** One row of the bench: what to tap, and what the house would have been saying. */
    class Row(val label: String, val detail: String, val fire: () -> Unit)

    private val seat = Seat(0)

    /**
     * **D-135's five, then the Short, then the two Subroutine scripts.**
     *
     * The Egress effect carries two node names because coordination is required and nobody may
     * speak; the ids are the seeded deck's, so a phone showing `SEED001` is a phone somebody
     * standing next to it can follow.
     */
    val rows: List<Row> = listOf(
        Row("EGRESS", "LONG · D-135 #1 · EgressOpened") {
            heard(
                Effect.EgressOpened(
                    seat = seat,
                    type = EgressType.Beacon,
                    nodes = listOf(MarkerId("SEED001"), MarkerId("SEED002")),
                    remaining = 180,
                    haptic = Haptic.Long,
                ),
            )
        },
        Row("INCOMING CALL", "LONG · D-135 #2 · MeetingOpened") {
            heard(Effect.MeetingOpened(seat, MeetingTrigger.MeetingCard, Haptic.Long))
        },
        Row("STAND AND WALK IN", "LONG · D-135 #3 · StandAndWalkIn") {
            heard(Effect.StandAndWalkIn(seat, Haptic.Long))
        },
        Row("RESTRAINED TAKEOVER", "LONG · D-135 #4 · RestrainedTakeover") {
            heard(Effect.RestrainedTakeover(seat, Haptic.Long))
        },
        Row("LIGHTS OUT", "LONG · D-135 #5 · MeetingEnded") {
            heard(Effect.MeetingEnded(Haptic.Long))
        },
        Row("OPENING MESSAGE", "SHORT · the ordinary buzz · OpeningMessage") {
            heard(Effect.OpeningMessage(seat, Haptic.Short))
        },
        // The couch's walk-in is the same effect at the other kind. Side by side with the row
        // above it, because *the haptic differs by round-state and never by role* is the one
        // sentence on StandAndWalkIn that a hand can actually check.
        Row("COUCH WALK-IN", "SHORT · the same effect, the other kind") {
            heard(Effect.StandAndWalkIn(seat, Haptic.Short))
        },
        Row("SNIFF", "SCRIPT · ${SubroutineModel.SNIFF_PARAMETERS} · two groups and a pause") {
            script("SNIFF", SubroutineModel.SNIFF.script)
        },
        Row("DRIFT", "SCRIPT · the wait, then one short buzz") {
            script("DRIFT", SubroutineModel.DRIFT.script)
        },
        Row("STOP", "silence — whatever is playing, cut") {
            note("STOP")
            haptics.stop()
        },
    )

    /**
     * **Every row, once, in order, with a gap wide enough to tell them apart.**
     *
     * The finger a unit running overnight does not have. It drives the same rows a person taps —
     * the same effects, the same [HouseBuzzer], the same motor — so what lands in the device log
     * is what a tap would have put there. It cannot tell anybody what the phone *felt* like, and
     * nothing in this file pretends otherwise.
     *
     * The gap is the whole reason this is a sweep rather than ten calls: the motor replaces
     * rather than queues (D-156's arrangement), so ten buzzes with no gap would be one buzz and a
     * log full of lines saying so.
     */
    suspend fun sweep() {
        note("SWEEP — ${rows.size} row(s), ${GAP_MILLIS}ms apart")
        rows.forEach { row ->
            row.fire()
            delay(GAP_MILLIS)
        }
        note("SWEEP DONE — only a hand proves a buzz")
    }

    private fun heard(effect: Effect) {
        note("${effect::class.simpleName} → ${effect.haptic()}")
        buzzer.heard(effect)
    }

    private fun script(what: String, steps: List<HapticStep>) {
        val buzzes = steps.count { it is HapticStep.Buzz }
        note("$what script → $buzzes buzz(es), ${steps.size} step(s)")
        haptics.play(steps)
    }

    private fun note(line: String) {
        // Both places on purpose. The screen is for the person holding the phone; stdout is the
        // device log, which is the only evidence a unit with no hand on the phone can produce.
        println("[haptic-bench] $line")
        log.add(0, line)
        while (log.size > LOG_LINES) log.removeAt(log.size - 1)
    }

    companion object {

        /** The environment variable `devicectl` sets to make the bench drive itself once. */
        const val SWEEP_SWITCH = "SOMEONES_HOME_HAPTIC_SWEEP"

        /** Long enough that the Long has finished and been felt as a separate thing. */
        private const val GAP_MILLIS = 1500L

        private const val LOG_LINES = 12
    }
}

/** The bench, drawn. Rows in D-135's order, then the log the device console also carries. */
@Composable
fun HapticCheatScreen(cheat: HapticCheat) {
    val insets = LocalPanelInsets.current
    Column(
        Modifier.fillMaxSize().background(Amber.Black)
            .padding(top = insets.top + 6.u, bottom = insets.bottom + 18.u)
            .padding(horizontal = 14.u)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(3.u),
    ) {
        Label("${BuildVariant.MARKER} — HAPTICS", size = 8.0, color = Amber.Bright, tracking = 0.16)
        Label("ONLY A HAND PROVES A BUZZ", size = 6.0, color = Amber.Dim, tracking = 0.2)
        for (row in cheat.rows) {
            Column(
                Modifier.fillMaxWidth().border(1.u, Amber.Faint).tap(row.fire)
                    .padding(horizontal = 6.u, vertical = 3.u),
                verticalArrangement = Arrangement.spacedBy(1.u),
            ) {
                Label(row.label, size = 7.0, color = Amber.Bright, tracking = 0.08)
                Label(row.detail, size = 5.5, color = Amber.Dim)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 3.u)) {
            Label("LOG", size = 6.5, color = Amber.Dim, tracking = 0.2)
        }
        for (line in cheat.log) {
            Label(line, size = 6.0, color = Amber.Dim)
        }
    }
}
