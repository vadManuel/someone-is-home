package home.someoneshome.model

import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
     * **And since D-316 it is wire-serialisable, with exactly the fields above** (the plan goes to
     * every seat at arming). The serializer's own descriptor is the wire's field list, so a field
     * added to the view that could hold a card would show here as well as above.
     */
    @Test
    fun `the plan view's serializer carries the field list and nothing more`() {
        for (type in listOf(HomePlanView::class.java, StoreyView::class.java, RoomView::class.java)) {
            assertTrue(type.declaredClasses.any { it.simpleName == "\$serializer" }, "${type.simpleName} has no generated serializer")
        }
        val names = { d: kotlinx.serialization.descriptors.SerialDescriptor -> (0 until d.elementsCount).map { d.getElementName(it) } }
        assertEquals(listOf("storeys", "rooms"), names(HomePlanView.serializer().descriptor))
        assertEquals(listOf("name", "cols", "rows", "cells"), names(StoreyView.serializer().descriptor))
        assertEquals(listOf("name", "kind"), names(RoomView.serializer().descriptor))
    }
}
