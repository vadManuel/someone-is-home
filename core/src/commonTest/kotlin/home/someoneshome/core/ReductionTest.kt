package home.someoneshome.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ReductionTest {
    @Test
    fun carriesStateAndEffects() {
        val r = Reduction(state = 1, effects = listOf("a"))
        assertEquals(1, r.state)
        assertEquals(listOf("a"), r.effects)
    }
}
