package home.someoneshome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration

/**
 * Work — what Residents actually do, and what an Insider imitates.
 *
 * Every Subroutine here is a *real* Subroutine for both roles: same screen, same duration, same
 * completion. The only difference is whether the completion writes, and it never shows on screen.
 * That is what makes rule 8's "every subroutine ships with its fake" cheap — there is no separate
 * fake to build or forget.
 */

/**
 * The Egress banner — **a banner, not a takeover.**
 *
 * Drawn over the springboard rather than instead of it, because you cannot scan a marker through
 * a modal and the player has to be able to see that their phone still works. The panel behind
 * dims; this stays at full intensity.
 *
 * It names both nodes. Containment needs two people at two separate markers and nobody may
 * speak, so the only way to coordinate is for the device to have already said where.
 */
@Composable
fun BoxScope.EgressBanner() {
    val go = navigator()
    BannerBody(
        headline = "EGRESS ATTEMPT IN PROGRESS",
        detail = "CONTAIN AT UTILITY AND LANDING",
        onClick = { go(ScreenId.EgressWidget) },
    )
}

/** The house's text, arriving over page 1. Everyone gets one, at the same moment. */
@Composable
fun BoxScope.HouseBanner() {
    val go = navigator()
    BannerBody(
        headline = "Regarding this evening. Please read.",
        headlineSize = 8.0,
        onClick = { go(ScreenId.Reveal) },
    )
}

@Composable
private fun BoxScope.BannerBody(
    headline: String,
    detail: String? = null,
    headlineSize: Double = 9.0,
    onClick: () -> Unit,
) {
    Column(
        Modifier.align(Alignment.TopCenter)
            .padding(6.u)
            .fillMaxWidth()
            .background(Amber.Bright)
            .tap(onClick)
            .padding(horizontal = 8.u, vertical = 7.u),
        verticalArrangement = Arrangement.spacedBy(3.u),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label("HOUSE", size = 6.0, color = Amber.Black, tracking = 0.14)
            Label("NOW", size = 6.0, color = Amber.Black, tracking = 0.14)
        }
        Label(
            headline,
            size = headlineSize, color = Amber.Black, tracking = 0.02, lineHeight = 1.5,
        )
        if (detail != null) {
            Label(detail, size = 6.5, color = Amber.Black, tracking = 0.08)
        }
    }
}

/**
 * The work order — seven Subroutines, lazily unlocked.
 *
 * **A Subroutine opens only at its marker**, which is what turns the list into travel. The two
 * locked rows say WAITING UPSTREAM and name nothing: you do not learn which Subroutine is blocked
 * or whose completion would free it, because that would tell you what someone else is doing.
 */
@Composable
fun WorkScreen(vals: PanelVals) {
    val go = navigator()
    Column(
        Modifier.fillMaxSize().padding(8.u),
        verticalArrangement = Arrangement.spacedBy(5.u),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BackChevron(ScreenId.Home, Amber.Dim)
            Label("SUBROUTINES", size = 7.0, color = Amber.Dim, tracking = 0.16)
            Label(
                "${vals.current.done} OF ${vals.current.total} DONE",
                modifier = Modifier.weight(1f),
                size = 7.0, color = Amber.Bright, tracking = 0.16, align = TextAlign.End,
            )
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.u)) {
            WorkRow("REPLAY", "HALL", MarkerShapes["diamond"], done = true)
            WorkRow("SHORT", "GARAGE", MarkerShapes["arrow_right"], done = true)
            WorkRow("JAM", "BED 2", MarkerShapes["cross"], done = true)
            WorkRow(
                vals.current.name, vals.current.room, vals.current.marker, current = true,
            ) { go(ScreenId.Scan) }
            // The design's fixture used a hexagon; the vetted roster has none, because the
            // legibility pass cut everything reading as "circle with corners". The first
            // substitution was `trapezoid`, which at 10 units read as the same amber wedge as
            // SNIFF's triangle two rows up -- exactly the confusion the roster exists to avoid.
            // A frame is topologically distinct from every solid shape in this list.
            WorkRow("ARRAY WIPE", "STUDY", MarkerShapes["square_frame"])
            WorkLocked()
            WorkLocked()
        }

        Box(Modifier.fillMaxWidth().border(1.u, Amber.Edge).padding(horizontal = 7.u, vertical = 6.u)) {
            Label(
                "A SUBROUTINE OPENS ONLY AT ITS MARKER.\nSCAN TO BEGIN.",
                size = 6.0, color = Amber.Faint, tracking = 0.1, lineHeight = 1.9,
            )
        }
    }
}

@Composable
private fun WorkRow(
    name: String,
    room: String,
    shape: MarkerShape?,
    done: Boolean = false,
    current: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val ink = when {
        current -> Amber.Bright
        done -> Amber.Faint
        else -> Amber.Mid
    }
    val meta = if (current) Amber.Bright else if (done) Amber.Faint else Amber.Dim

    Row(
        Modifier.fillMaxWidth()
            .border(1.u, if (current) Amber.Bright else Amber.Edge)
            .background(if (current) Amber.Edge else Color.Transparent)
            .then(if (onClick != null) Modifier.tap(onClick) else Modifier)
            .padding(horizontal = 7.u, vertical = if (current) 7.u else 6.u),
        horizontalArrangement = Arrangement.spacedBy(6.u),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Done is a filled box, current is an outlined one at full intensity, pending is outlined
        // and dim. Three states in one glyph rather than three different marks.
        if (done) {
            Box(Modifier.size(9.u).background(Amber.Bright))
        } else {
            Box(Modifier.size(9.u).border(1.u, if (current) Amber.Bright else Amber.Dim))
        }
        Label(
            name,
            modifier = Modifier.weight(1f),
            size = if (current) 8.5 else 7.5,
            color = ink,
            tracking = if (current) 0.06 else 0.0,
            // Struck through as well as dimmed. Dim alone is ambiguous on this screen, where
            // "faint" is also how a locked row reads.
            decoration = if (done) TextDecoration.LineThrough else null,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.u),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label(room, size = 6.0, color = meta)
            shape?.let { MarkerGlyph(it, if (current) 11.u else 10.u, meta) }
        }
    }
}

/** A blocked chain step. Names nothing — not the Subroutine, not who would free it. */
@Composable
private fun WorkLocked() {
    Row(
        Modifier.fillMaxWidth().border(1.u, Amber.Edge).padding(horizontal = 7.u, vertical = 6.u),
        horizontalArrangement = Arrangement.spacedBy(6.u),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.u).border(1.u, Amber.Faint))
        Label("LOCKED", modifier = Modifier.weight(1f), size = 7.5, color = Amber.Faint)
        Label("WAITING UPSTREAM", size = 6.0, color = Amber.Faint)
    }
}

/**
 * Scanning — **the screen is the lamp.**
 *
 * The front camera reads the card, so the lit screen has to face the marker, which means the
 * player cannot see it while it works. Everything about the screen follows from that: the
 * instruction is legible *before* you turn the phone over, confirmation is a haptic rather than a
 * visual, and it gives up on its own so nobody is left standing in a dark room holding a lit
 * screen at a wall.
 *
 * A lit field, and one of only three in the game. Here that is the function, not the style.
 */
@Composable
fun ScanScreen() {
    val go = navigator()
    val ink = Amber.Black
    Column(
        Modifier.fillMaxSize().background(Amber.Bright).padding(8.u),
        verticalArrangement = Arrangement.spacedBy(7.u),
    ) {
        InvertedStatusRow("SCANNING")

        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(9.u, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(62.u), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.fillMaxSize()
                        .border(2.u, ink.copy(alpha = ringPulse()), CircleShape)
                )
                Box(Modifier.size(38.u).border(1.u, ink.copy(alpha = 0.35f), CircleShape))
                Box(Modifier.size(9.u).background(ink, CircleShape))
            }
            Label(
                "HOLD THE SCREEN\nUP TO THE MARKER",
                size = 12.0, color = ink, tracking = 0.1, lineHeight = 1.5,
                align = TextAlign.Center,
            )
            Label(
                "The lit screen is your light. Your front camera reads the card, so you will " +
                    "not see this while it works.",
                size = 7.0, color = ink.copy(alpha = 0.68f), tracking = 0.04, lineHeight = 1.9,
                align = TextAlign.Center,
            )
        }

        Column(
            Modifier.fillMaxWidth().border(1.u, ink).padding(horizontal = 8.u, vertical = 7.u),
            verticalArrangement = Arrangement.spacedBy(5.u),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Label("YOU WILL FEEL IT CATCH", size = 6.0, color = ink, tracking = 0.14)
                Label("6S LEFT", size = 6.0, color = ink.copy(alpha = 0.55f), tracking = 0.14)
            }
            SegmentBar(
                total = PanelVals.SCAN_SEGMENTS,
                lit = PanelVals.SCAN_LIT,
                litColor = ink,
                unlitColor = ink.copy(alpha = 0.2f),
                height = 4.u,
            )
            Label(
                "One buzz, then turn the phone over. Gives up on its own and puts you back.",
                size = 7.0, color = ink.copy(alpha = 0.72f), tracking = 0.02, lineHeight = 1.8,
            )
        }

        PanelButton(
            "STOP NOW",
            border = ink, ink = ink, size = 7.0, verticalPadding = 9.u,
            onClick = { go(ScreenId.Home) },
        )
    }
}

/**
 * The status row the lit-field screens draw for themselves, in black on amber.
 *
 * They suppress the real status bar because they are inverted; drawing it here keeps the chrome
 * present rather than letting a lit screen become a different device.
 */
@Composable
fun InvertedStatusRow(carrier: String) {
    val ink = Amber.Black
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.u),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.height(7.u),
            horizontalArrangement = Arrangement.spacedBy(1.u),
            verticalAlignment = Alignment.Bottom,
        ) {
            val spent = ink.copy(alpha = 0.25f)
            Block(2.u, 3.u, spent); Block(2.u, 5.u, spent); Block(2.u, 7.u, spent)
        }
        Column(
            Modifier.width(7.u),
            verticalArrangement = Arrangement.spacedBy(1.u),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Block(7.u, 1.u, ink); Block(5.u, 1.u, ink); Block(1.u, 1.u, ink)
        }
        Label(carrier, modifier = Modifier.weight(1f), size = 6.5, color = ink, tracking = 0.1)
        Box(Modifier.size(7.u).border(1.u, ink, CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(3.u).background(ink))
        }
        Row(Modifier.width(11.u).height(6.u).border(1.u, ink.copy(alpha = 0.55f)).padding(1.u)) {
            Box(Modifier.weight(1f).fillMaxHeight().background(ink))
            Box(Modifier.weight(1f).fillMaxHeight().background(ink))
            Box(Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/**
 * The scan caught. **The light dies the instant it does.**
 *
 * The shape is drawn large and outlined so the player can check it against the card still in
 * their hand — the app is not asserting which marker this is, it is offering a comparison. NOT
 * THIS ONE exists because a scan can catch the wrong card in the dark.
 */
@Composable
fun ScanCaughtScreen(vals: PanelVals) {
    val go = navigator()
    Column(
        Modifier.fillMaxSize().padding(8.u),
        verticalArrangement = Arrangement.spacedBy(7.u),
    ) {
        Label("CAUGHT", size = 7.0, color = Amber.Dim, tracking = 0.16)

        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.u, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The marker you just scanned -- the same shape the work order sent you to, not the
            // host-setup fixture this used to read by mistake.
            vals.current.marker?.let { MarkerGlyph(it, 52.u, Amber.Bright) }
            Label(
                vals.current.instruction,
                size = 13.0, color = Amber.Bright, tracking = 0.06, lineHeight = 1.4,
                align = TextAlign.Center,
            )
            Label(
                "${vals.current.room} . SUBROUTINE ${vals.current.index} OF ${vals.current.total}",
                size = 7.0, color = Amber.Dim, tracking = 0.12,
            )
            Label("THE CARD IN YOUR HAND MATCHES", size = 6.0, color = Amber.Faint, tracking = 0.1)
        }

        PanelButton(
            "BEGIN",
            border = Amber.Bright, ink = Amber.Bright,
            size = 9.0, tracking = 0.18, verticalPadding = 13.u,
            onClick = { go(ScreenId.Sub) },
        )
        PanelButton(
            "NOT THIS ONE",
            border = Amber.Faint, ink = Amber.Dim,
            size = 7.0, verticalPadding = 9.u,
            onClick = { go(ScreenId.Work) },
        )
    }
}

/**
 * The scan caught something that is not yours.
 *
 * **Every game-side reason lands here.** Not assigned to you and blocked upstream produce the
 * same four words, so the refusal cannot be read backwards into a fact about the marker or about
 * anybody else.
 *
 * *"Nothing of yours opens here"* is phrased about **you**, deliberately. The alternatives —
 * "already completed", "belongs to another resident" — would each be a small statement about a
 * player who is not standing there.
 */
@Composable
fun ScanBadScreen() {
    ScanRefusal(
        headline = "NOT YOURS",
        detail = "Nothing of yours opens here.",
    )
}

/**
 * The card is not in this home at all.
 *
 * The **one** refusal that can afford to be specific, because it is a fact about a piece of paper
 * rather than about any player: the host printed a card and never registered it. Saying so costs
 * nothing and buys the only diagnosis available from inside a dark house — otherwise a missed
 * card reads as another player's marker and the setup fault stays invisible all evening.
 *
 * ### Nothing reports this anywhere. That is the decision, not an omission.
 *
 * It would be easy to surface a count at the end of the round, or a notice at the next meeting.
 * **Do not.** Either one turns the app into an arbiter of a player's claim: someone says *"I
 * scanned a card in the garage and it wasn't a marker"*, and a system report lets the room check
 * them. Fact-checking testimony is the thing this app refuses to do everywhere else.
 *
 * Left unreported it becomes material instead. A Resident who mentions it gives up their own
 * position to be useful, which is a real cost freely paid. An Insider can claim it to explain
 * standing at a marker doing nothing. Neither can be verified, which is the point.
 *
 * **The general rule, which this is a case of: the house announces only what no player could
 * have observed.** It reports a dead radio at the next meeting because a radio failure is
 * invisible to everyone including the player it happened to, so without the announcement a
 * phantom appears that nobody designed. An unregistered card was seen by whoever scanned it.
 * Everything a player saw is theirs to report, or to lie about.
 */
@Composable
fun ScanUnknownScreen() {
    ScanRefusal(
        headline = "NOT A MARKER",
        detail = "This card was never registered in this home. It is not part of the game.",
    )
}

/**
 * Both refusals, drawn by one function.
 *
 * **The two screens are identical apart from their two lines of text, and that must stay true.**
 * If they had different icons or different shapes, an onlooker could tell which refusal you got
 * from across a room — and one of them (NOT YOURS) is reachable only by a Resident. Sharing the
 * body makes that identity structural instead of a coincidence someone later "improves" away.
 */
@Composable
private fun ScanRefusal(headline: String, detail: String) {
    val go = navigator()
    Column(
        Modifier.fillMaxSize().padding(8.u),
        verticalArrangement = Arrangement.spacedBy(7.u),
    ) {
        Label("SCAN", size = 7.0, color = Amber.Dim, tracking = 0.16)

        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(9.u, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.padding(vertical = 3.u).size(46.u), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxSize().border(1.3.u, Amber.Bright, CircleShape))
                Box(Modifier.fillMaxHeight().width(1.3.u).rotate(45f).background(Amber.Bright))
            }
            Label(headline, size = 13.0, color = Amber.Bright, tracking = 0.1)
            Label(
                detail,
                size = 7.0, color = Amber.Dim, tracking = 0.02, lineHeight = 1.9,
                align = TextAlign.Center,
            )
        }

        PanelButton(
            "SCAN ANOTHER",
            border = Amber.Bright, ink = Amber.Bright,
            size = 9.0, tracking = 0.18, verticalPadding = 13.u,
            onClick = { go(ScreenId.Scan) },
        )
        PanelButton(
            "SEE MY SUBROUTINES",
            border = Amber.Faint, ink = Amber.Dim,
            size = 7.0, verticalPadding = 9.u,
            onClick = { go(ScreenId.Work) },
        )
    }
}

/**
 * The handshake — **near-black, haptic, maximum concealment.**
 *
 * You return a rhythm by feel. Nothing on this screen needs to be seen, so almost nothing is lit,
 * which makes it the safest Subroutine to be caught doing in a corridor.
 */
@Composable
fun SubScreen() {
    val go = navigator()
    Column(
        Modifier.fillMaxSize().padding(8.u),
        verticalArrangement = Arrangement.spacedBy(7.u),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label("HANDSHAKE", size = 7.0, color = Amber.Dim, tracking = 0.14)
            Label("SCREEN STAYS DARK", size = 7.0, color = Amber.Faint, tracking = 0.14)
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().border(1.u, Amber.Edge).padding(10.u),
            verticalArrangement = Arrangement.spacedBy(12.u, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Label(
                "RETURN THE RHYTHM\nBY FEEL",
                size = 6.5, color = Amber.Faint, tracking = 0.14, lineHeight = 2.0,
                align = TextAlign.Center,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.u),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Block(8.u, 8.u, Amber.Dim)
                Block(16.u, 8.u, Amber.Dim)
                Block(8.u, 8.u, Amber.Faint)
                Block(8.u, 8.u, Amber.Faint)
                Block(16.u, 8.u, Amber.Edge)
            }
            Label("3 OF 5 RETURNED", size = 6.0, color = Amber.Faint, tracking = 0.12)
        }

        Row(
            Modifier.fillMaxWidth().border(1.u, Amber.Edge).padding(horizontal = 7.u, vertical = 6.u),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Label("MOTION BUDGET", size = 6.0, color = Amber.Faint, tracking = 0.1)
            Label("HOLDING", size = 6.0, color = Amber.Dim, tracking = 0.1)
        }

        PanelButton(
            "TAP",
            border = Amber.Faint, ink = Amber.Dim,
            size = 16.0, tracking = 0.2, verticalPadding = 26.u,
            onClick = { go(ScreenId.SubBright) },
        )
    }
}

/**
 * The parity check — **lit for its whole duration**, and that is the point.
 *
 * Some Subroutines have to be bright; if every one were concealable, choosing a dark one would
 * itself be a choice worth reading. The pair exists so that being lit at a marker is ordinary.
 *
 * **THE MINIGAME ITSELF IS A PLACEHOLDER.** This screen carries the design's *style* — segment
 * grid, four luminance steps, inverted emphasis — but not its rules. The Subroutines were
 * designed separately on the diagnostics bench, and that set is the authoritative one; this
 * fixture is not. Two things visible here are artifacts rather than intent:
 *
 * - corrupt-but-unfound cells render lighter than clean ones, which shows the answer if the task
 *   is genuinely to *find* them
 * - "PASS 2 OF 2" implies a structure this screen does not otherwise express
 *
 * When the real Subroutines land, take their rules from the bench and their look from here.
 */
@Composable
fun SubBrightScreen() {
    val go = navigator()
    Column(
        Modifier.fillMaxSize().padding(8.u),
        verticalArrangement = Arrangement.spacedBy(7.u),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label("PARITY CHECK", size = 7.0, color = Amber.Dim, tracking = 0.14)
            Label("SCREEN STAYS LIT", size = 7.0, color = Amber.Bright, tracking = 0.14)
        }
        Label(
            "FIND EVERY CORRUPTED BLOCK . PASS 2 OF 2",
            modifier = Modifier.fillMaxWidth(),
            size = 6.5, color = Amber.Dim, tracking = 0.12, align = TextAlign.Center,
        )

        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(3.u),
        ) {
            Parity.cells.chunked(6).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(3.u),
                ) {
                    row.forEach { cell ->
                        Box(
                            Modifier.weight(1f).fillMaxHeight()
                                .background(cell.fill).border(1.u, cell.border)
                        )
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label("FOUND 4 OF 7", size = 6.5, color = Amber.Dim, tracking = 0.1)
            Label("LIT", size = 6.5, color = Amber.Bright, tracking = 0.1)
        }

        PanelButton(
            "SUBMIT PASS",
            border = Amber.Dim, ink = Amber.Bright,
            tracking = 0.18, verticalPadding = 9.u,
            onClick = { go(ScreenId.Work) },
        )
    }
}

/**
 * Files: **one item, and every other app refuses.**
 *
 * Carrying is a device state rather than a rule the player has to remember. The refusals are
 * listed as standing conditions — UNAVAILABLE, on screen, before you try — which is the safe way
 * to say no.
 */
@Composable
fun FilesScreen() {
    Column(
        Modifier.fillMaxSize().padding(8.u),
        verticalArrangement = Arrangement.spacedBy(7.u),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BackChevron(ScreenId.Home, Amber.Dim)
            Label("FILES", size = 7.0, color = Amber.Dim, tracking = 0.16)
            Label(
                "1 ITEM",
                modifier = Modifier.weight(1f),
                size = 7.0, color = Amber.Bright, tracking = 0.16, align = TextAlign.End,
            )
        }

        InfoBox(border = Amber.Bright, gap = 4.u) {
            Label("DISK 01", size = 9.0, color = Amber.Bright, tracking = 0.06)
            Label(
                "DEGRADED . IN HAND\nDESTINATION . RACK",
                size = 6.5, color = Amber.Dim, tracking = 0.1, lineHeight = 1.8,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(3.u)) {
            listOf("SUBROUTINES", "NOTES", "ARCHIVE").forEach { name ->
                Row(
                    Modifier.fillMaxWidth().border(1.u, Amber.Edge)
                        .padding(horizontal = 7.u, vertical = 6.u),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Label(name, size = 7.0, color = Amber.Faint)
                    Label("UNAVAILABLE", size = 7.0, color = Amber.Faint)
                }
            }
        }

        Box(Modifier.weight(1f))

        Box(
            Modifier.fillMaxWidth().border(1.u, Amber.Dim).padding(7.u),
            contentAlignment = Alignment.Center,
        ) {
            Label(
                "STORAGE IN USE\nHANDS FULL UNTIL DELIVERED",
                size = 6.5, color = Amber.Mid, tracking = 0.1, lineHeight = 1.9,
                align = TextAlign.Center,
            )
        }
    }
}

/**
 * Notes: **read only. It writes to you, not the other way round.**
 *
 * The line is plausible, unverifiable and deliberately not context aware. *These are not your
 * notes* is the whole joke and the whole mechanic: the device hands you an accusation you did not
 * make and cannot check.
 */
@Composable
fun NotesScreen(vals: PanelVals) {
    Column(
        Modifier.fillMaxSize().padding(8.u),
        verticalArrangement = Arrangement.spacedBy(7.u),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BackChevron(ScreenId.Home, Amber.Dim)
            Label("NOTES", size = 7.0, color = Amber.Dim, tracking = 0.16)
            Label(
                "UNTITLED",
                modifier = Modifier.weight(1f),
                size = 7.0, color = Amber.Faint, tracking = 0.16, align = TextAlign.End,
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth().border(1.u, Amber.Edge).padding(9.u)) {
            Readout(vals.noteLine, size = 18.0, color = Amber.Bright, lineHeight = 1.45)
        }

        Box(
            Modifier.fillMaxWidth().border(1.u, Amber.Edge).padding(horizontal = 7.u, vertical = 6.u),
            contentAlignment = Alignment.Center,
        ) {
            Label(
                "READ ONLY . NO INPUT PERMITTED\nTHESE ARE NOT YOUR NOTES",
                size = 6.0, color = Amber.Faint, tracking = 0.1, lineHeight = 1.9,
                align = TextAlign.Center,
            )
        }
    }
}

/**
 * Terminal, away from the station: **NO SIGNAL.**
 *
 * Self-enforcing. Nobody has to remember that the map only works at one place, because the app is
 * simply in that state everywhere else — and the state is identical for both roles, so trying it
 * from the wrong room reveals nothing about who tried.
 */
@Composable
fun TermNoScreen() {
    val go = navigator()
    Column(
        Modifier.fillMaxSize().padding(8.u),
        verticalArrangement = Arrangement.spacedBy(7.u),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BackChevron(ScreenId.Home, Amber.Dim)
            Label("TERMINAL", size = 7.0, color = Amber.Dim, tracking = 0.16)
            Label(
                "NO SIGNAL",
                modifier = Modifier.weight(1f),
                size = 7.0, color = Amber.Faint, tracking = 0.16, align = TextAlign.End,
            )
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().border(1.u, Amber.Edge),
            verticalArrangement = Arrangement.spacedBy(10.u, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Label("NO SIGNAL", size = 15.0, color = Amber.Dim, tracking = 0.16)
            Label(
                "TERMINAL UNREACHABLE\nFROM THIS LOCATION",
                size = 6.5, color = Amber.Faint, tracking = 0.12, lineHeight = 2.0,
                align = TextAlign.Center,
            )
        }

        PanelButton(
            "RETRY",
            border = Amber.Edge, ink = Amber.Faint,
            size = 7.0, tracking = 0.14, verticalPadding = 8.u,
            onClick = { go(ScreenId.TermLive) },
        )
    }
}

/** Counts per room, three staleness bands, your own room outlined. Never dots. */
@Composable
fun TermLiveScreen() {
    val go = navigator()
    Column(
        Modifier.fillMaxSize().padding(8.u),
        verticalArrangement = Arrangement.spacedBy(6.u),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BackChevron(ScreenId.Home, Amber.Dim)
            Label("TERMINAL . LIVE", size = 7.0, color = Amber.Dim, tracking = 0.14)
        }
        FloorStrip()
        Box(Modifier.weight(1f).fillMaxWidth().border(1.u, Amber.Edge)) {
            LivePlan(Plan.mapCells(), Modifier.fillMaxSize())
            PlanRoomLabels(Modifier.fillMaxSize())
            PlanCounts(Plan.terminalCounts, Modifier.fillMaxSize())
        }
        Label("YOUR ROOM OUTLINED", size = 5.5, color = Amber.Faint, tracking = 0.08)
        PanelButton(
            "TIMELAPSE . 2:00 AT 6X",
            border = Amber.Dim, ink = Amber.Bright,
            size = 7.5, tracking = 0.14, verticalPadding = 8.u,
            onClick = { go(ScreenId.Timelapse) },
        )
    }
}

/**
 * Timelapse: two minutes at 6×, **plays once**.
 *
 * The gated resource. The live view is free and limited only by the motion budget; this one you
 * spend, which is what makes standing at the terminal a decision rather than a habit.
 */
@Composable
fun TimelapseScreen() {
    val go = navigator()
    Column(
        Modifier.fillMaxSize().padding(8.u),
        verticalArrangement = Arrangement.spacedBy(6.u),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BackChevron(ScreenId.TermLive, Amber.Dim)
            Label("TERMINAL . -1:12", size = 7.0, color = Amber.Dim, tracking = 0.14)
        }
        FloorStrip()
        Box(Modifier.weight(1f).fillMaxWidth().border(1.u, Amber.Edge)) {
            LivePlan(Plan.mapCells(), Modifier.fillMaxSize())
            PlanRoomLabels(Modifier.fillMaxSize())
            PlanCounts(Plan.timelapseCounts, Modifier.fillMaxSize())
        }
        Row(Modifier.fillMaxWidth().height(6.u), horizontalArrangement = Arrangement.spacedBy(1.u)) {
            Box(Modifier.weight(44f).fillMaxHeight().background(Amber.Bright))
            Box(Modifier.weight(56f).fillMaxHeight().background(Amber.Edge))
        }
        // Playback can be abandoned. It plays once either way, so stopping early is a real
        // decision rather than a convenience.
        PanelButton(
            "STOP THE PLAYBACK",
            border = Amber.Edge, ink = Amber.Dim,
            size = 7.0, tracking = 0.14, verticalPadding = 8.u,
            onClick = { go(ScreenId.TermLive) },
        )
    }
}

@Composable
private fun FloorStrip() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.u)) {
        Box(
            Modifier.weight(1f).border(1.u, Amber.Dim).padding(vertical = 4.u),
            contentAlignment = Alignment.Center,
        ) { Label("GROUND", size = 6.5, color = Amber.Bright, tracking = 0.1) }
        Box(
            Modifier.weight(1f).border(1.u, Amber.Edge).padding(vertical = 4.u),
            contentAlignment = Alignment.Center,
        ) { Label("UPPER", size = 6.5, color = Amber.Dim, tracking = 0.1) }
    }
}

/**
 * Containment: **the house takes the only number back.**
 *
 * This is page 1 with **one widget swapped** — not a screen of its own. The Egress countdown
 * occupies the System Integrity widget's slot, at the same size, in the same place, and
 * everything below it carries on working: the next Subroutine, the app grid, the dock. That is
 * the whole point. The Residents lose their only measure of progress at exactly the moment they
 * most want it, and nothing else about the phone changes to soften it.
 *
 * The design's fixture draws placeholder rectangles below the widget because it only needed to
 * show the widget. Drawing the real springboard is what the swap actually means.
 */
@Composable
fun EgressWidgetScreen(vals: PanelVals) {
    HomeScreen(vals) { EgressWidget(it) }
}

/**
 * The countdown, built to the same measurements as the meter it replaces.
 *
 * It names both nodes because containment needs two people at two separate markers and nobody may
 * speak — the device saying where is the only coordination available.
 */
@Composable
private fun EgressWidget(vals: PanelVals) {
    Column(
        Modifier.fillMaxWidth().border(1.u, Amber.Bright).padding(horizontal = 7.u, vertical = 6.u),
        verticalArrangement = Arrangement.spacedBy(5.u),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Label("EGRESS . BEACON", size = 6.5, color = Amber.Bright, tracking = 0.14)
            Readout("1:42", size = 19.0, color = Amber.Bright, lineHeight = 1.0)
        }
        SegmentBar(
            total = PanelVals.METER_SEGMENTS,
            lit = vals.egressLit,
            litColor = Amber.Bright,
            unlitColor = Amber.Edge,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label("CONTAIN AT", size = 6.0, color = Amber.Dim, tracking = 0.1)
            Label("UTILITY . LANDING", size = 6.0, color = Amber.Bright, tracking = 0.1)
        }
    }
}
