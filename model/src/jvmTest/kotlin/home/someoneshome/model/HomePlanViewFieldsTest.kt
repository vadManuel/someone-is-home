package home.someoneshome.model

import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * **GUARD (rule 3, O2): the plan view's type IS its field list, and a card cannot be expressed in
 * it.** Read off the classes by reflection, on the JVM because common Kotlin has none: every
 * instance field of [HomePlanView], [StoreyView] and [RoomView], by name and by declared type,
 * exactly. A field added for a terminal, a meeting card, a station, an active marker or a seat
 * changes the list, and this fails before anything could put a value in it.
 */
class HomePlanViewFieldsTest {

    private fun fieldsOf(type: Class<*>): Set<String> = type.declaredFields
        .filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic }
        // The lazy adjacency is derived from the cells, not a field the view is built with.
        .filterNot { it.name.endsWith("\$delegate") }
        .map { "${it.name}: ${it.genericType.typeName}" }
        .toSet()

    @Test
    fun `the plan view holds storeys, cells and room names and nothing else`() {
        assertEquals(
            setOf(
                "storeys: java.util.List<home.someoneshome.model.StoreyView>",
                "rooms: java.util.List<home.someoneshome.model.RoomView>",
            ),
            fieldsOf(HomePlanView::class.java),
        )
        assertEquals(
            setOf("name: java.lang.String", "cols: int", "rows: int", "cells: java.util.List<java.lang.Integer>"),
            fieldsOf(StoreyView::class.java),
        )
        assertEquals(
            setOf("name: java.lang.String", "kind: home.someoneshome.model.RoomKind"),
            fieldsOf(RoomView::class.java),
        )
        // Belt and braces: no constructor anywhere in the three takes a card, a destination or a seat.
        val forbidden = setOf(MarkerId::class.java, MarkerCard::class.java, Destination::class.java, Registration::class.java, Seat::class.java)
        for (type in listOf(HomePlanView::class.java, StoreyView::class.java, RoomView::class.java)) {
            for (constructor in type.declaredConstructors) {
                for (parameter in constructor.parameterTypes) {
                    assertFalse(parameter in forbidden, "${type.simpleName} can be built with a ${parameter.simpleName}")
                }
            }
        }
    }

    /**
     * **And it is not wire-serialisable**: marking it so is the owner's Q1, not this unit's. (The
     * `@ClientFacing` marker has binary retention, so the redaction lint, which reads source, is
     * what refuses a `@Serializable` on an unmarked type; this checks the compiled result.)
     */
    @Test
    fun `the plan view has no serializer`() {
        for (type in listOf(HomePlanView::class.java, StoreyView::class.java, RoomView::class.java)) {
            assertFalse(type.declaredClasses.any { it.simpleName == "\$serializer" }, "${type.simpleName} has a generated serializer")
        }
        // The check can see one: the client-facing view beside it has its serializer.
        assertEquals(true, ObservationView::class.java.declaredClasses.any { it.simpleName == "\$serializer" })
    }
}
