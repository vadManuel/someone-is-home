package home.someoneshome.core

import home.someoneshome.model.Destination
import home.someoneshome.model.MarkerId
import home.someoneshome.model.MarkerShapes

/**
 * **A home for a test to arm a round in: cards with a glyph and a room apiece.**
 *
 * `Event.RoundArmed` takes [Destination]s rather than bare cards, because a work order's whole
 * destination column is computed from them. Every test in this module that arms a round holds a
 * list of card ids and cares about nothing else, so this is the one place that turns one into the
 * other — written once so that a test which *does* care about rooms can be read as caring.
 *
 * **The rooms are deliberately not one per card.** Real homes put two cards in a room, and a
 * fixture with eight rooms for eight cards would never once produce two lines of one order
 * pointing at the same room name — which is the case a screen most easily gets wrong.
 *
 * Glyphs come off the real roster in order, so no two cards in a fixture share a shape (D-086
 * refuses that at registration and a fixture should not be able to produce it).
 */
internal fun placed(cards: List<MarkerId>): List<Destination> =
    cards.mapIndexed { i, card ->
        Destination(
            marker = card,
            shape = MarkerShapes.all[i % MarkerShapes.all.size].id,
            room = "ROOM ${i / 2}",
        )
    }

/** The same, for a test that holds card ids as strings. */
internal fun placed(vararg cards: String): List<Destination> = placed(cards.map { MarkerId(it) })
