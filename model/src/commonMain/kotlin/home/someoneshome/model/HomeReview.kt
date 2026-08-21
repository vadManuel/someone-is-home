package home.someoneshome.model

/**
 * **What a home is still missing before it can be hosted — the one gate, D-127.**
 *
 * A home passes REVIEW with **one terminal, one meeting card and at least eight ordinary markers**.
 * The eight is arithmetic rather than taste: the minimum party is five (D-128) and the Array Wipe
 * circuit takes three stations drawn from the ordinary markers (D-122), so eight is the smallest
 * home in which the smallest lawful round can be armed.
 *
 * ### It is the only gate, and it is deliberately not an opinion about houses
 *
 * D-125: **the app never has an opinion about what a home *is*.** A one-room home is lawful, so is
 * a strange footprint or a tiny one. Every check here is about what a round mechanically requires
 * and nothing else — there is no minimum room count, no floor count, no shape.
 *
 * ### It fails early, in the light, and it names every missing thing at once
 *
 * D-126: every check happens while the host is alone with time and light, never at hosting time
 * with the party standing in the hall. And [missing] is a list rather than the first failure,
 * because a host sent back for a terminal, then for a meeting card, then for three more markers is
 * a host walking their house three times for a fact the app knew on the first pass.
 *
 * ### Capacity is guidance and never a gate
 *
 * [hosts] is `markers − 3` — the same three stations — and it is a number the host is *told*, on
 * REVIEW and on the home's own screen and in the lobby when more people turn up than it. It never
 * refuses anything. D-123 is what makes that affordable: a card is a place rather than a container,
 * so a work order deeper than the marker count simply visits some markers twice. **The honest thing
 * to tell a host with nine markers and ten players is that it will be crowded, not that it is
 * forbidden.**
 */
data class HomeReview(
    val markers: Int,
    val hasTerminal: Boolean,
    val hasMeeting: Boolean,
) {

    /**
     * Every requirement this home does not meet, in the order the host would go and fix them.
     *
     * Empty means it passes. Ordered rather than a set so the gate screen reads the same way twice
     * running — a list of failures that reshuffled itself would look like a different set of
     * failures to a host reading it a second time.
     */
    val missing: List<Missing> = buildList {
        if (!hasTerminal) add(Missing.Terminal)
        if (!hasMeeting) add(Missing.MeetingCard)
        if (markers < MARKERS) add(Missing.Markers(have = markers, need = MARKERS))
    }

    val passes: Boolean get() = missing.isEmpty()

    /**
     * **HOSTS UP TO N**, where N is the markers minus the circuit's three stations.
     *
     * Never negative: a home with two markers hosts up to nobody, and saying `-1` would be
     * arithmetic leaking onto a screen. It is computed for any home, passing or not, because a
     * host mid-walk is owed the number they are working towards.
     */
    val hosts: Int get() = (markers - STATIONS).coerceAtLeast(0)

    /** One thing a home has not got. Each carries what the host needs to know to go and fix it. */
    sealed interface Missing {

        /** No room holds the card marked T. */
        data object Terminal : Missing

        /** No room holds the meeting card, so there is nowhere a meeting could be called. */
        data object MeetingCard : Missing

        /** Fewer than [need] ordinary markers are registered anywhere in the home. */
        data class Markers(val have: Int, val need: Int) : Missing {
            val short: Int get() = need - have
        }
    }

    companion object {

        /**
         * The three Array Wipe stations, drawn fresh from the ordinary markers every round (D-122).
         *
         * They are why the floor is eight rather than five and why capacity is markers minus three
         * rather than markers. Named once, used twice, so the two numbers cannot drift apart.
         */
        const val STATIONS: Int = 3

        /** Minimum party (D-128) plus the circuit. The smallest home a lawful round fits in. */
        const val MIN_PARTY: Int = 5
        const val MARKERS: Int = MIN_PARTY + STATIONS

        fun of(markers: Int, hasTerminal: Boolean, hasMeeting: Boolean): HomeReview =
            HomeReview(markers = markers, hasTerminal = hasTerminal, hasMeeting = hasMeeting)
    }
}
