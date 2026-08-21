package home.someoneshome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The status bar — the one piece of chrome on screen for nearly the whole round.
 *
 * It carries the perimeter as a **glyph**, so the fact is stated once rather than written out in
 * words on thirty screens, and so a revoked player's device says exactly what everyone else's
 * says. The signal bars go dead the moment the round starts, because from then on the house owns
 * the network — a fiction and a rule at the same time.
 *
 * **Nothing here is role-dependent.** If a value in this function ever needs to consult the role,
 * that is the bug, not the feature.
 */
@Composable
fun StatusBar(vals: PanelVals, bandHeight: Dp = STATUS_BAR_HEIGHT, sideInset: Dp = 0.u) {
    Row(
        Modifier.fillMaxWidth().height(maxOf(bandHeight, STATUS_BAR_HEIGHT))
            // The rule spans the full width; only the GLYPHS are held off the curve. A divider
            // that stopped short of each edge would read as a shorter bar, not a safer one.
            .edgeLine(PanelSide.Bottom, vals.edge)
            .padding(horizontal = 6.u + sideInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(LEFT_ZONE),
            horizontalArrangement = Arrangement.spacedBy(5.u),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SignalBars(on = vals.signalOn, off = vals.signalOff)
            ReceptionGlyph(vals.receptionInk)

            Label(
                vals.carrier,
                size = 7.0, color = vals.dim, tracking = 0.06,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }

        // THE MIDDLE IS EMPTY, ON EVERY DEVICE. Not "empty where there is an Island" -- the row
        // is three zones and the middle is never used, so the layout owes nothing to hardware it
        // cannot measure. Compose reports that a cutout EXISTS (displayCutout top=62) and nothing
        // whatever about how wide it is, so any rule keyed on the pill's geometry would be a
        // guess at Apple's numbers that silently goes wrong on the next phone.
        //
        // Reserved a little wider than the pill needs: the Island is about 32% of a 393pt panel,
        // and this holds 36% clear.
        Spacer(Modifier.weight(MIDDLE_ZONE))

        Row(
            Modifier.weight(RIGHT_ZONE),
            horizontalArrangement = Arrangement.spacedBy(5.u, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The iris follows the panel's ink rather than being pinned to full intensity. The
            // design hardcodes #FFC759 here, which left it the single brightest element on the
            // very screens that dim everything else -- and a 7-unit lit ring is exactly the kind
            // of thing that carries across a dark room when the text does not.
            if (vals.armedGlyph) PerimeterGlyph(ring = vals.ink, core = vals.ink)
            if (vals.disarmedGlyph) PerimeterGlyph(ring = vals.dim, core = vals.edge)

            if (vals.lockChip) {
                Box(Modifier.border(1.u, Amber.Dim).padding(horizontal = 2.u)) {
                    Label("LOCK", size = 7.0, color = Amber.Bright, tracking = 0.06)
                }
            }

            Readout(vals.clock, size = 11.0, color = vals.ink)
            Battery(ink = vals.ink, edge = vals.dim)
        }
    }
}

/**
 * The status row's three zones: content at each end, nothing in the middle.
 *
 * Weights rather than widths, so the reserve scales with the panel instead of being a point count
 * that happens to be right on one phone.
 *
 * **Deliberately lopsided.** The right zone holds a clock and a battery and wants about 16% of
 * the panel; the left holds two glyphs AND the carrier, which is a word. Splitting the sides
 * evenly looked tidy and truncated UNREGISTERED to `UNREGISTER…` — a game-critical string
 * clipped to make room for space the right side was not using.
 *
 * **Sized so the middle is provably clear, not tuned until this pill happened to fit.** An
 * earlier pass widened the left zone until UNREGISTERED stopped ellipsizing, which pushed its
 * last character under the pill's left edge — trading a visible truncation for an invisible one.
 * Measured: the carrier has about 60 design units before the pill and UNREGISTERED wants 60.3.
 *
 * So REVOKED and RESTRAINED — the distinction D-078 exists to keep legible — fit whole with room
 * to spare, and UNREGISTERED ellipsizes. That is the right one to lose: it is the fallback for a
 * missing cause, chosen because it "asserts only that you are out", and `UNREGISTER…` still
 * asserts exactly that. Clipping it under a cutout would not.
 */
private const val LEFT_ZONE = 0.30f
private const val MIDDLE_ZONE = 0.44f
private const val RIGHT_ZONE = 0.26f

/** Three bars, and after arming all three are spent. The house is the network now. */
@Composable
private fun SignalBars(on: Color, off: Color) {
    Row(
        Modifier.height(7.u),
        horizontalArrangement = Arrangement.spacedBy(1.u),
        verticalAlignment = Alignment.Bottom,
    ) {
        Block(2.u, 3.u, on)
        Block(2.u, 5.u, on)
        Block(2.u, 7.u, off)
    }
}

/** The inverted-triangle reception mark, built from three rules the way the panel would. */
@Composable
private fun ReceptionGlyph(ink: Color) {
    Column(
        Modifier.width(7.u),
        verticalArrangement = Arrangement.spacedBy(1.u),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Block(7.u, 1.u, ink)
        Block(5.u, 1.u, ink)
        Block(1.u, 1.u, ink)
    }
}

/**
 * The perimeter iris: a lit ring with a lit core when armed, the same iris drained when not.
 *
 * One symbol in two intensities rather than two symbols, so "the perimeter came down" reads as a
 * state change on something the player has been looking at all evening.
 */
@Composable
private fun PerimeterGlyph(ring: Color, core: Color) {
    Box(Modifier.size(7.u).border(1.u, ring, CircleShape), contentAlignment = Alignment.Center) {
        Box(Modifier.size(3.u).background(core))
    }
}

@Composable
private fun Battery(ink: Color, edge: Color) {
    Row(Modifier.width(11.u).height(6.u).border(1.u, edge).padding(1.u)) {
        Box(Modifier.weight(1f).fillMaxHeight().background(ink))
        Box(Modifier.weight(1f).fillMaxHeight().background(ink))
        Box(Modifier.weight(1f).fillMaxHeight())
    }
}

/**
 * The device screen: ground, status bar, and the content area every screen fills.
 *
 * The bone ground is painted *behind* everything rather than as the column's background, because
 * it is a property of the device being unarmed rather than of any one screen's layout — the same
 * reason the design paints it as an absolutely-positioned fill.
 *
 * ### The dim, and why it is not styling
 *
 * When [overlay] is present the whole panel — **status bar included** — drops to
 * [NOTIFIED_DIM], and the overlay draws at full intensity on top. Two jobs at once:
 *
 * 1. It makes the banner the only bright thing, so it is noticed.
 * 2. **It is a notification channel that works when the screen cannot be seen at all.** A phone
 *    held as a lamp faces away from its owner. They cannot read a banner, but they can see the
 *    light in the room drop. The buzz says something arrived; the dim confirms it.
 *
 * Which makes this a **lamp change**, and lamp changes are game state (project rule 5). Two
 * consequences that outlive this file:
 *
 * - The dim has to arrive as an authored effect from the rules, not as a UI transition, and it
 *   has to be a step rather than a fade. A ramp nobody authored is a signal nobody authored.
 * - **Every banner must go to everyone at once.** A lamp dimming is world-observable, so a
 *   notification addressed to fewer than all players is a beacon pointing at whoever got it. If
 *   a per-player notification is ever wanted, it cannot use this.
 *
 * ### The panel makes room for the banner rather than being covered by it
 *
 * The banner used to be drawn straight over the top of whatever the screen had put there, which on
 * the springboard is the System Integrity meter: the bar came out cut in half, with FROZEN UNTIL
 * NEXT HOUSE MEETING under a bright block. Half a widget reads as a rendering fault rather than as
 * something on top of something, and the meter is the Residents' only number.
 *
 * So the content is inset by whatever the banner turns out to be tall, measured rather than
 * guessed — a notification is one line or three depending on what the house has to say, and a
 * reserved constant would be wrong for two of those. **The inset does not track the swipe**: the
 * banner slides up under the finger and the room it left stays open until it is actually gone, so
 * dismissing a notification does not drag the whole screen up with it.
 */
@Composable
fun PanelFrame(
    vals: PanelVals,
    modifier: Modifier = Modifier,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // THE BACKGROUNDS FILL THE SCREEN; ONLY THE CONTENT INSETS. Padding the whole panel would
    // put a black bar above a lit screen, and on a phone held as a lamp that is a reduction in
    // emitted light the core never authored -- rule 5. So the black base and the light-field
    // below still run edge to edge, and the Dynamic Island sits on bare light.
    val insets = LocalPanelInsets.current
    val safeTop = insets.top
    val safeBottom = insets.bottom

    // THE SCREEN'S CORNERS ARE ROUND, and the status row is the one thing that lives up in them.
    // iOS reports NO horizontal safe-area inset in portrait — measured, not assumed — so nothing
    // in the top band is held off the curve by the system, and the row's own 6u is not enough.
    val sideInset = insets.side
    val statusHeight = if (vals.statusVisible) STATUS_BAR_HEIGHT else 0.u

    // THE ROW OCCUPIES THE WHOLE BAND THE ISLAND SITS IN, rather than being a 17u strip placed
    // somewhere inside it. Its glyphs centre vertically, so they come out level with the Island
    // the way the system's own bar does — and its bottom rule lands exactly on the safe-area
    // boundary, CLEAR of the Island, instead of being crossed by the pill halfway along.
    //
    // Both earlier attempts got this wrong in visible ways: flush to y=0 put the row above the
    // Island in the corner radius, and centring a 17u strip put the rule behind it.
    val statusBand = if (vals.statusVisible) maxOf(safeTop, statusHeight) else 0.u
    val contentTop = if (vals.statusVisible) 0.u else safeTop

    // How tall the banner turned out to be, measured at layout. Read back as room for the content
    // only while there IS an overlay, so a banner that leaves does not leave its hole behind.
    var bannerHeight by remember { mutableStateOf(0.dp) }
    val bannerRoom = if (overlay != null) bannerHeight else 0.dp
    val density = LocalDensity.current

    Box(modifier.fillMaxSize().background(Amber.Black)) {
        Box(
            Modifier.fillMaxSize()
                .then(if (overlay != null) Modifier.alpha(NOTIFIED_DIM) else Modifier)
        ) {
            if (vals.isPre) Box(Modifier.fillMaxSize().background(Amber.Bone))

            Column(Modifier.fillMaxSize()) {
                // The status row STAYS in the Island's band, which is what the row was already
                // shaped for: signal at the left ear, clock and battery at the right, the way
                // the system's own bar is laid out. Moving it down would waste the one strip of
                // screen the Island has already taken.
                if (vals.statusVisible) StatusBar(vals, bandHeight = statusBand, sideInset = sideInset)
                // BOTTOM AS WELL AS TOP. The home indicator takes 34pt and the boot screen's
                // UPTIME line, progress bar and STARTING label were sitting under it — fixing
                // only the Island moved the collision to the other end of the panel.
                Column(
                    Modifier.fillMaxWidth().weight(1f)
                        .padding(top = contentTop + bannerRoom, bottom = safeBottom),
                    content = content,
                )
            }
        }
        if (overlay != null) {
            // Below the status bar, not over it. The bar is the one thing that stays put when
            // something arrives -- it is how the player confirms the perimeter and the clock are
            // still what they were, which is exactly the reassurance a takeover would remove.
            //
            // CLIPPED, because the banner MOVES. A swipe-up dismiss tracks the finger, and a
            // banner that slid over the status row on its way out would take the one fixed thing
            // on the screen with it. Clipping here rather than capping the travel in the banner
            // keeps the rule where the rule is: this box is the region an overlay may occupy.
            Box(
                Modifier.fillMaxSize()
                    .padding(top = statusBand + contentTop, bottom = safeBottom)
                    .clipToBounds()
            ) {
                // Measured on a wrapper rather than asked of the banner, so the overlay slot stays
                // a `BoxScope` lambda that knows nothing about the frame it is drawn in.
                Box(
                    Modifier.fillMaxWidth()
                        .onSizeChanged { bannerHeight = with(density) { it.height.toDp() } }
                ) {
                    overlay(this)
                }
            }
        }

        // LAST, AND OVER EVERYTHING INCLUDING THE BANNER. The design puts it on the screen
        // element rather than inside the luminous layer, so it is not dimmed by a notification
        // and does not move with an overlay -- see [ScanLines].
        ScanLines()
    }
}

/**
 * **The glass: the horizontal banding of the panel this device is pretending to be.**
 *
 * A transcription, not a treatment. The design's device mockup
 * (`_bmad-output/brainstorming/brainstorm-engine-selection-2026-08-15/artifacts/someone-is-home-device.html:53`)
 * draws it on `.screen::after` as
 *
 * ```css
 * repeating-linear-gradient(0deg, rgba(0,0,0,.32) 0 1px, transparent 1px 3px)
 * ```
 *
 * — one unit of 32% black in every three, black-on-transparent, over the whole panel. Those are
 * the numbers below and they are not rounded, adjusted or "tuned for the phone": the mockup's
 * screen is about 312 CSS pixels wide against this canvas's 300 units, so a CSS pixel there and a
 * design unit here are the same thing to within four percent. That correspondence is the entire
 * reason the port is written in design units, and it is what makes this a copy rather than an
 * impression of one.
 *
 * ### Why it belongs to the frame and not to a screen
 *
 * The design attaches it to the *screen element*, above every screen and outside the layer that
 * dims — so a banner does not brighten it, a light-field screen does not switch it off, and the
 * two inverted screens get it like everything else. It is the surface, not the content, which is
 * how a real panel behaves and why there is exactly one of these in the app.
 *
 * ### The three rules it has to not break
 *
 * - **Rule 5, the lamp is a pure function of state.** This never varies: same alpha, same period,
 *   every screen, every frame, every role. It is a constant attenuation of about 11% of emitted
 *   light, and a constant cannot carry a signal.
 * - **Rule 7, the allocation budget.** One rectangle with a repeating shader, and the brush is
 *   remembered against the period rather than rebuilt per frame. The obvious version — a loop of
 *   a hundred and thirty `drawRect`s — costs a hundred and thirty draw calls a frame to say the
 *   same thing.
 * - **It must not eat a tap.** The CSS says `pointer-events:none`; here that is simply a `Box`
 *   with no pointer input, which Compose does not hit-test. `ScreenGraphTest` walks the whole
 *   graph by firing clicks and would notice at once if it did.
 *
 * ### What is deliberately NOT here
 *
 * The same CSS rule carries a second layer — a vignette,
 * `radial-gradient(ellipse 130% 90% at 50% 50%, transparent 55%, rgba(0,0,0,.55) 100%)` — that
 * darkens the corners. It is the design's and it is left out, because it was not what was asked
 * for and because it is a *gradient* on a screen whose art direction says there are none. It is
 * one brush away if somebody rules for it.
 */
@Composable
private fun ScanLines() {
    val periodPx = with(LocalDensity.current) { SCAN_LINE_PERIOD.toPx() }
    val glass = remember(periodPx) {
        Brush.verticalGradient(
            0f to SCAN_LINE_INK,
            SCAN_LINE_DUTY to SCAN_LINE_INK,
            SCAN_LINE_DUTY to Color.Transparent,
            1f to Color.Transparent,
            startY = 0f,
            endY = periodPx,
            tileMode = TileMode.Repeated,
        )
    }
    Box(Modifier.fillMaxSize().background(glass))
}

/** One dark unit in every three — `0 1px, transparent 1px 3px` in the design's own CSS. */
private val SCAN_LINE_PERIOD: Dp = 3.u

/** Where the dark part ends inside one period. One unit of three. */
private const val SCAN_LINE_DUTY: Float = 1f / 3f

/** `rgba(0,0,0,.32)`. Black, because the banding is an absence of light rather than a colour. */
private val SCAN_LINE_INK: Color = Color.Black.copy(alpha = 0.32f)

/**
 * The status row's height, which the overlay and the content inset both measure against.
 *
 * Named rather than repeated: it was written as a bare `17.u` in two places, and the two are
 * required to agree or a banner lands on top of the one row that must never move.
 */
val STATUS_BAR_HEIGHT: Dp = 17.u

/**
 * How far the panel drops when something arrives unasked.
 *
 * Deep enough to read as a light change across a room, shallow enough that the screen underneath
 * is still legible — the player must be able to see *what they were doing* behind the banner, or
 * it is a takeover after all.
 */
const val NOTIFIED_DIM: Float = 0.32f
