package home.someoneshome.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * **Where the phone is, as the house's report asks it** (D-307, the owner's 2026-11-20 rulings).
 *
 * *In order to report a player you must not be in a minigame. You must just be in the main view
 * (that being first or second page).* The phone sends [isOn] in every upload (`MainViewField`), the
 * house starts no hold and keeps none running on an upload that says it is off, and the report
 * screen is only ever entered from where [takesOverFrom] names.
 *
 * The owner's three clarifications, the same day:
 *
 * 1. **The lantern is not the main view.** The game's own lock screen ([ScreenId.Lock],
 *    [ScreenId.LockNotify], lamp on, SLIDE TO OPEN) starts and runs no report; the person slides
 *    to page 1 or 2 first.
 * 2. **The Egress alert dropped over page 1 or 2 is the main view**, and so is the alert over the
 *    report screen. The opening text and a quiet text over page 1 are the same arrangement (a
 *    banner over the page, nothing open), so they count too.
 * 3. **Control Centre or a notification pulled down over the app does not stop a report.** The
 *    phone does not watch for it. A phone the OS has suspended (an iOS lock, the side button, a
 *    swipe home) sends nothing at all, and the house's gap rule (D-301) ends its hold.
 *
 * **Only the screen is asked**, never the role: page 1 and page 2 are the same pages for a Resident
 * and an Insider (D-142), and so is every activity (rule 8).
 */
object MainView {

    /**
     * **Page 1 or 2, a banner over page 1, or the report screen that took one over**, and nothing
     * else: no Subroutine, scan, briefing, Egress widget, meeting, list, picker or lantern.
     */
    fun isOn(state: PanelState): Boolean = when (state.screen) {
        ScreenId.Home, ScreenId.Page2, ScreenId.Report -> true
        ScreenId.Banner, ScreenId.Notify, ScreenId.Quiet -> true
        else -> false
    }

    /**
     * **The page a report screen arriving now would take over**: [ScreenId.Home] or
     * [ScreenId.Page2], or null where no report screen may be shown. A banner over page 1 is
     * page 1, and is left behind with the banner, as any arrival leaves one.
     */
    fun takesOverFrom(state: PanelState): ScreenId? = when {
        !isOn(state) -> null
        state.screen == ScreenId.Page2 -> ScreenId.Page2
        state.screen == ScreenId.Report -> null
        else -> ScreenId.Home
    }
}

/**
 * **How full the report circle is, 0 to 1** (D-307), and deliberately not a field of [PanelState]:
 * it moves every frame for two seconds, and a [PanelState] copy every frame is a recomposition and
 * an allocation every frame, on a phone that is standing next to a Revoked one and may be touched
 * itself (rule 7). One float, read only inside the circle's draw lambda, so a frame's change costs a
 * draw and nothing above it: [LampState]'s arrangement.
 *
 * Written by [FlowModel.reportRead] from the phone's own clock against the moment the house's slot
 * says the hold began (D-243, D-269's discipline), and nowhere else. Every render that does not
 * hand one in gets an empty circle.
 */
class ReportCircle {
    val fill: MutableFloatState = mutableFloatStateOf(0f)
}

/** The report circle's test tag. */
const val REPORT_CIRCLE: String = "report-circle"

/**
 * **The report circle's stroke: Jam's ring** (`JamRow`'s 3), the one ring this module already draws
 * at a reading size. Its diameter is the springboard's content: the panel's [DESIGN_WIDTH] less the
 * pages' 20 either side ([REPORT_SIDE]), so 320 on the design's canvas, or the height left under
 * the status strip when that is less.
 */
private val REPORT_STROKE = 3.dp

/** The springboard pages' own side, top and bottom padding, so the circle sits where the page did. */
private val REPORT_SIDE = 20.dp
private val REPORT_TOP = 18.dp
private val REPORT_BOTTOM = 16.dp

/**
 * **The report screen: the circle, and nothing else** (D-307, the owner's 2026-11-20 rulings).
 *
 * *Reporting takes over the screen and shows the circle filling.* No words and no buttons. Drawn on
 * the springboard's own ground at the springboard's own light: the panel's black, the track in
 * [Ramp.Line] and the fill in [Ramp.Ink], the two inks page 1 already draws its lines and its words
 * in (Jam's two rings are the same pair). No new light level, colour or type style. The fill starts
 * at the top and runs clockwise, a step of nothing: it is read off [circle] in the draw lambda.
 *
 * **Identical for both roles**: it takes no [PanelVals], so there is no role for it to read.
 */
@Composable
fun ReportScreen(circle: ReportCircle) {
    Box(
        Modifier.fillMaxSize().padding(start = REPORT_SIDE, end = REPORT_SIDE, top = REPORT_TOP, bottom = REPORT_BOTTOM),
    ) {
        Canvas(Modifier.fillMaxSize().testTag(REPORT_CIRCLE)) {
            val stroke = REPORT_STROKE.toPx()
            val diameter = minOf(size.width, size.height) - stroke
            val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            val arc = Size(diameter, diameter)
            drawArc(Ramp.Line, 0f, 360f, false, topLeft, arc, style = Stroke(stroke))
            val fill = circle.fill.floatValue.coerceIn(0f, 1f)
            if (fill > 0f) drawArc(Ramp.Ink, -90f, 360f * fill, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Butt))
        }
    }
}
