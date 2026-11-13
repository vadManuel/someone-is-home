package home.someoneshome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
 * **The status strip: the one piece of chrome on screen for nearly the whole round** (D-204).
 *
 * The mockup's strip, drawn by [StatusStrip] in mockup pixels under the top safe area. It says four
 * things and only four: the signal the house took, what happened to you once something has (the
 * carrier, D-078), whether the perimeter is armed, and the time. Files and carrying add the padlock.
 *
 * **Nothing here is role-dependent.** If a value in this function ever needs to consult the role,
 * that is the bug, not the feature.
 */
@Composable
fun StatusBar(vals: PanelVals) = Mockup {
    StatusStrip(
        ink = vals.stripInk, dim = vals.stripDim, edge = vals.stripEdge,
        signal = vals.signalOn, signalOff = vals.signalOff,
        carrier = vals.carrier,
        perimeter = when {
            vals.armedGlyph -> Perimeter.Armed
            vals.disarmedGlyph -> Perimeter.Disarmed
            else -> null
        },
        lock = vals.lockChip,
        clock = vals.clock,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}

/** The perimeter's two states as the strip words them. */
enum class Perimeter { Armed, Disarmed }

/**
 * **The mockup's status strip, in its own pixels** (D-204). Draw it inside [Mockup].
 *
 * 34 tall with a hairline beneath: three signal bars, the carrier taking what room is left, then
 * the perimeter chip, the padlock and the clock. Every colour is passed in, because the same row is
 * drawn amber on black, ink on bone, dimmed for concealment and black on the lantern's amber.
 */
@Composable
fun StatusStrip(
    ink: Color,
    dim: Color,
    edge: Color,
    signal: Color,
    signalOff: Color,
    carrier: String,
    perimeter: Perimeter?,
    lock: Boolean,
    clock: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().height(STRIP_HEIGHT.dp)
            .edgeLine(PanelSide.Bottom, edge, 1.dp)
            .padding(bottom = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Three bars, and after arming all three are spent. The house is the network now.
        Row(
            Modifier.height(12.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Block(3.dp, 5.dp, signal)
            Block(3.dp, 8.dp, signal)
            Block(3.dp, 12.dp, signalOff)
        }
        Tag(
            carrier, dim, Modifier.weight(1f), tracking = 0.04,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        when (perimeter) {
            Perimeter.Armed -> PerimeterChip("ARMED", ink = ink, core = ink)
            Perimeter.Disarmed -> PerimeterChip("DISARMED", ink = dim, core = edge)
            null -> Unit
        }
        if (lock) Padlock(ink)
        if (clock != null) Words(clock, size = 22, color = ink, lineHeight = 1.0)
    }
}

/**
 * The perimeter as a worded chip: the iris and the word, in a 1-unit box (D-204). One symbol in two
 * intensities, so "the perimeter came down" reads as a change on something already looked at.
 */
@Composable
private fun PerimeterChip(word: String, ink: Color, core: Color) {
    Row(
        Modifier.border(1.dp, ink).padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.dp).border(1.dp, ink, CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(3.dp).background(core))
        }
        Tag(word, ink, tracking = 0.04)
    }
}

/** The mockup's padlock, `M1 5.5h8v5.5H1z M3 5.5V2h4v3.5` on a 10 by 12 grid. */
@Composable
private fun Padlock(ink: Color) {
    Canvas(Modifier.size(10.dp, 12.dp).semantics { contentDescription = "Locked" }) {
        val k = size.width / 10f
        val stroke = Stroke(width = 1.5f * k, cap = StrokeCap.Square, join = StrokeJoin.Miter)
        drawRect(ink, topLeft = Offset(1f * k, 5.5f * k), size = Size(8f * k, 5.5f * k), style = stroke)
        val shackle = Path().apply {
            moveTo(3f * k, 5.5f * k); lineTo(3f * k, 2f * k); lineTo(7f * k, 2f * k); lineTo(7f * k, 5.5f * k)
        }
        drawPath(shackle, ink, style = stroke)
    }
}

/**
 * **The strip the lantern and the scan draw for themselves**, black on their amber (D-204).
 *
 * They suppress the shared strip because their ground is the lamp; this keeps the chrome present
 * rather than letting a lit screen become a different device. No clock: the lantern draws its own,
 * large.
 */
@Composable
fun InvertedStatusRow(carrier: String) = Mockup {
    StatusStrip(
        ink = Amber.Black, dim = Amber.Black, edge = Amber.Black,
        signal = Ramp.Faint, signalOff = Ramp.Faint,
        carrier = carrier, perimeter = Perimeter.Armed, lock = false, clock = null,
    )
}

/** The strip's height in mockup pixels. */
private const val STRIP_HEIGHT = 34

/**
 * The device screen: ground, status bar, and the content area every screen fills.
 *
 * The bone ground is painted *behind* everything rather than as the column's background, because
 * it is a property of the device being unarmed rather than of any one screen's layout — the same
 * reason the design paints it as an absolutely-positioned fill.
 *
 * ### The dim, and why it is not styling
 *
 * When [dimmed] the whole panel — **status bar included** — drops to [NOTIFIED_DIM], and the
 * overlay draws at full intensity on top. Two jobs at once:
 *
 * 1. It makes the banner the only bright thing, so it is noticed.
 * 2. **It is a notification channel that works when the screen cannot be seen at all.** A phone
 *    held as a lamp faces away from its owner. They cannot read a banner, but they can see the
 *    light in the room drop. The buzz says something arrived; the dim confirms it.
 *
 * Which makes this a **lamp change**, and lamp changes are game state (project rule 5). Three
 * consequences that outlive this file:
 *
 * - The dim has to arrive as an authored effect from the rules, not as a UI transition, and it
 *   has to be **a step rather than a fade, in both directions**. A ramp nobody authored is a
 *   signal nobody authored, and that is as true coming back up as going down (D-119). There is no
 *   `animate*AsState` anywhere near this and `NotificationInputTest` measures the first frame
 *   after the change to keep it that way.
 * - **Anything that dims must go to everyone at once.** A lamp dimming is world-observable, so a
 *   notification addressed to fewer than all players is a beacon pointing at whoever got it. If
 *   a per-player notification is ever wanted, it cannot use this.
 * - **[dimmed] is not [overlay] != null, and the difference is the ruling.** D-118 keys the dim to
 *   exactly two notification kinds — the Egress and the house's opening text — rather than to a
 *   banner being up. Every other notification arrives quiet: a dark banner over a panel that never
 *   changed brightness. The two arguments are separate here because the two questions are
 *   separate; [Notifications.dims] is the one place the second one is answered.
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
    dimmed: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    // THE BACKGROUNDS FILL THE SCREEN; ONLY THE CONTENT INSETS. Padding the whole panel would
    // put a black bar above a lit screen, and on a phone held as a lamp that is a reduction in
    // emitted light the core never authored -- rule 5. So the black base and the light-field
    // below still run edge to edge, and the Dynamic Island sits on bare light.
    val insets = LocalPanelInsets.current
    val safeTop = insets.top
    val safeBottom = insets.bottom

    // THE STRIP SITS UNDER THE ISLAND, NOT BESIDE IT (D-204). The band the Island takes is left
    // as ground, and the strip starts at the safe area's edge, so nothing the strip draws can go
    // under the pill whatever width the next phone's pill turns out to be. The inverted screens
    // draw their own strip, and their ground is the lamp, so it runs to the top edge with the
    // Island on bare light: the frame does not inset them, and each pads its own content by the
    // safe area inside its ground.
    val statusBand = if (vals.statusVisible) safeTop + STATUS_BAR_HEIGHT else 0.u
    val overlayTop = if (vals.statusVisible) statusBand else safeTop

    // How tall the banner turned out to be, measured at layout. Read back as room for the content
    // only while there IS an overlay, so a banner that leaves does not leave its hole behind.
    var bannerHeight by remember { mutableStateOf(0.dp) }
    val bannerRoom = if (overlay != null) bannerHeight else 0.dp
    val density = LocalDensity.current

    Box(modifier.fillMaxSize().background(Amber.Black)) {
        Box(
            Modifier.fillMaxSize()
                // A STEP, NOT A FADE, IN BOTH DIRECTIONS (D-119). `alpha` reads a value; nothing
                // here animates it, and nothing here may. A fade on the way down is a light the
                // core never authored, and a fade on the way back up is the same light again.
                .then(if (dimmed) Modifier.alpha(NOTIFIED_DIM) else Modifier)
        ) {
            if (vals.isPre) Box(Modifier.fillMaxSize().background(Amber.Bone))

            Column(Modifier.fillMaxSize()) {
                if (vals.statusVisible) {
                    Spacer(Modifier.height(safeTop))
                    StatusBar(vals)
                }
                // BOTTOM AS WELL AS TOP. The home indicator takes 34pt and the boot screen's
                // UPTIME line, progress bar and STARTING label were sitting under it — fixing
                // only the Island moved the collision to the other end of the panel.
                Column(
                    Modifier.fillMaxWidth().weight(1f)
                        .padding(top = bannerRoom, bottom = safeBottom),
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
                    .padding(top = overlayTop, bottom = safeBottom)
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
        //
        // THE GROUND, AND NOTHING ELSE, CHOOSES THE STRENGTH -- `litGround` is a function of
        // `state.screen` alone, so two phones on the same screen carry identical glass whatever
        // their roles and whatever the round is doing. See [ScanLines] for why a ground that
        // emits cannot afford the band a black one can.
        ScanLines(lit = vals.litGround)
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
 * ### The one thing the transcription got wrong: the band is a multiply, and it was drawn over
 * one ground and shipped over four
 *
 * The mockup's screen is amber on black. Over black the band takes 32% of *nothing* and is
 * invisible; what you see of it there is the texture it lays over lit content, which is the effect
 * it was chosen for. **The port drew the same brush over every ground the app has**, and over a
 * ground that already emits, a multiply takes the ink and the ground together. Contrast is not
 * scale-invariant — WCAG's ratio carries a `+0.05` flare term — so the pair comes out closer than
 * it went in. Measured, on the pairs that carry actual words:
 *
 * | ink on ground | clear | under the .32 band | kept |
 * |---|---|---|---|
 * | `BoneInk` on `Bone` — every setup screen | 6.73 | **3.57** | 53% |
 * | `BoneDim` on `Bone` — their secondary ink | 4.82 | **2.98** | 62% |
 * | `Black` on `Amber.Bright` — the lantern, the scan | 13.57 | **6.34** | **47%** |
 * | `Black` on `Amber.Mid` — the inverted blocks | 8.91 | **4.40** | 49% |
 *
 * The owner reported the bone screens first — *"the scan lines make the text barely legible on
 * light mode"* — and then, exactly right, that it *"also applies to dark mode, but only whenever
 * there is text on the amber color background"*. **The amber is the worse of the two.** It was
 * cleared once on the grounds that 6.34:1 still passes the 4.5:1 small-text floor outright, which
 * is the wrong instrument: the floor asks whether a *palette* is legible, and the question here is
 * what the *glass* costs a palette that was already chosen. On that question amber loses more than
 * bone does, and the 65 sRGB levels the band takes out of `Amber.Bright` — against bone's 49 — is
 * the same fact stated the way an eye sees it.
 *
 * So the strength is a function of the **ground**: [SCAN_LINE_INK] where the ground is black,
 * unrounded and untouched, and [SCAN_LINE_INK_LIT] everywhere a ground emits. Same brush, same
 * period, same phase, same black — one constant differs, and it is the one the grounds disagree
 * about. [PanelVals.litGround] is the question, and it is asked of the screen.
 *
 * ### The three rules it has to not break
 *
 * - **Rule 5, the lamp is a pure function of state.** The strength varies with the *ground* and
 *   with nothing else: [PanelVals.litGround] is derived from [PanelState.screen] alone, never from
 *   the role, the round or the moment. Two phones showing the same screen therefore carry identical
 *   glass, which is the whole of what the rule protects — a difference a player could read off
 *   somebody else's phone. Two phones showing *different* screens already look different, and a lit
 *   ground announces itself long before its banding does.
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
private fun ScanLines(lit: Boolean) {
    val periodPx = with(LocalDensity.current) { SCAN_LINE_PERIOD.toPx() }
    val ink = scanLineInk(lit)
    val glass = remember(periodPx, ink) {
        Brush.verticalGradient(
            0f to ink,
            SCAN_LINE_DUTY to ink,
            SCAN_LINE_DUTY to Color.Transparent,
            1f to Color.Transparent,
            startY = 0f,
            endY = periodPx,
            tileMode = TileMode.Repeated,
        )
    }
    Box(Modifier.fillMaxSize().background(glass))
}

/**
 * The band's colour for a ground. **The only thing the two grounds disagree about** — same black,
 * same period, same phase, same duty; one alpha.
 *
 * Internal rather than private because `ScanLinesTest` asserts on both numbers, and a guard that
 * re-declared them would be a second copy that agrees on the day it is written.
 */
internal fun scanLineInk(lit: Boolean): Color =
    if (lit) SCAN_LINE_INK_LIT else SCAN_LINE_INK

/** One dark unit in every three — `0 1px, transparent 1px 3px` in the design's own CSS. */
private val SCAN_LINE_PERIOD: Dp = 3.u

/** Where the dark part ends inside one period. One unit of three. */
private const val SCAN_LINE_DUTY: Float = 1f / 3f

/**
 * `rgba(0,0,0,.32)`. Black, because the banding is an absence of light rather than a colour.
 *
 * The mockup's own number, and it stays the mockup's own number: black is the ground it was drawn
 * over, and over black the band takes nothing at all. Every in-round screen the game is actually
 * played on is one of these, which is where the design's texture lives.
 */
private val SCAN_LINE_INK: Color = Color.Black.copy(alpha = 0.32f)

/**
 * The same black over **any ground that emits** — bone or amber — at the strength those grounds can
 * afford.
 *
 * **One number for both, not one per ground**, and that is a ruling rather than a shortcut: two lit
 * constants would be two numbers to keep in step and a third case for every future surface to
 * choose between, to buy a difference no eye is going to attribute. So it is solved for the
 * *worse* of the two, which is amber, and bone gets the margin.
 *
 * Not tuned by eye and not a round number chosen for looking like one. The band multiplies ink and
 * ground alike, so what a pair of colours has left after it is a function of this alpha:
 *
 * | ink on ground | clear | at .08, off a render | kept |
 * |---|---|---|---|
 * | `Black` on `Amber.Bright` — the binding case | 13.57 | 11.27 | **83%** |
 * | `BoneInk` on `Bone` | 6.73 | 5.76 | 86% |
 * | `BoneDim` on `Bone` — already marginal clear | 4.82 | 4.31 | 89% |
 *
 * Every lit pair keeps at least 83% of the contrast it has on a clear row, against the 47% the full
 * band left the lantern. `ScanLinesTest` holds that as the floor, for every lit ground at once —
 * one rule, so that no ground can be cleared by a kinder instrument than its neighbour was.
 *
 * The candidates either side were rendered at 600×1300 and looked at rather than reasoned about:
 * .16 still chews the two-line footers on `Perms` and `Join`, and .06 is not visibly cleaner than
 * .08 while having less band left to see.
 *
 * It is still a band and still visible: **12 levels of step on bone and 16 on amber**, against the
 * 49 and 65 the mockup's number was putting there — the row structure of a real panel at arm's
 * length rather than a dimmer.
 */
private val SCAN_LINE_INK_LIT: Color = Color.Black.copy(alpha = 0.08f)

/**
 * The strip's height in the panel's units, which the overlay and the content inset both measure
 * against: [STRIP_HEIGHT] mockup pixels, carried across the [Mockup] bridge.
 *
 * Named rather than repeated: the two are required to agree or a banner lands on top of the one row
 * that must never move.
 */
val STATUS_BAR_HEIGHT: Dp = (STRIP_HEIGHT * DESIGN_WIDTH / MOCKUP_WIDTH).dp

/**
 * How far the panel drops for the two notifications that dim the house (D-118).
 *
 * Deep enough to read as a light change across a room, shallow enough that the screen underneath
 * is still legible — the player must be able to see *what they were doing* behind the banner, or
 * it is a takeover after all.
 *
 * **Two events reach this value and nothing else does.** The Egress and the house's opening text;
 * see [NotificationKind.heavy] for why the light vocabulary is kept as closed as the word
 * vocabulary. The same value darkens the lantern's amber field on the lock screen, so a player who
 * looks up from a lit phone and a player who looks up from a locked one saw the same drop.
 */
const val NOTIFIED_DIM: Float = 0.32f
