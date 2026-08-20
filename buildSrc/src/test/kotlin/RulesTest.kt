import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * These exist because `./gradlew check` passing proves only that the guards did not fire.
 *
 * Every case below is a violation that a previous version of these rules let through, or that a
 * code review found it would let through. They run as part of `check`, so a guard cannot be
 * quietly neutered — swapping a `throw` for a `warn` in the task class still leaves these
 * asserting the matching logic itself.
 */
class RedactionRulesTest {

    private fun caught(source: String) = RedactionRules.violations(source).isNotEmpty()

    @Test
    fun `catches every declaration form that can go on the wire`() {
        // enum class is FIRST because Role is one, and the original regex missed it entirely.
        assertTrue(caught("@Serializable\nenum class Role { Resident, Insider }"), "enum class")
        assertTrue(caught("@Serializable\ndata class A(val x: Int)"), "data class")
        assertTrue(caught("@Serializable\nclass B"), "plain class")
        assertTrue(caught("@Serializable\nprivate class C"), "private")
        assertTrue(caught("@Serializable\nabstract class D"), "abstract")
        assertTrue(caught("@Serializable\nopen class E"), "open")
        assertTrue(caught("@Serializable\nexpect class F"), "expect")
        assertTrue(caught("@Serializable\nactual class G"), "actual")
        assertTrue(caught("@Serializable\nsealed interface H"), "sealed interface")
        assertTrue(caught("@Serializable\ndata object I"), "data object")
        assertTrue(caught("@Serializable\nvalue class J(val x: Int)"), "value class")
    }

    @Test
    fun `catches a fully qualified annotation`() {
        assertTrue(caught("@kotlinx.serialization.Serializable\ndata class K(val x: Int)"))
    }

    @Test
    fun `a marked type is allowed`() {
        assertTrue(!caught("@ClientFacing\n@Serializable\ndata class L(val x: Int)"))
        assertTrue(!caught("@Serializable\n@ClientFacing\ndata class M(val x: Int)"))
    }

    @Test
    fun `a lookalike marker does not satisfy the requirement`() {
        // @ClientFacingDraft is not @ClientFacing. Substring matching would have accepted it,
        // which is a wire type shipping on the strength of a name that looks close enough.
        assertTrue(caught("@ClientFacingDraft\n@Serializable\ndata class N(val x: Int)"))
    }

    @Test
    fun `a lookalike serializable does not trigger the rule`() {
        assertTrue(!caught("@NotSerializable\ndata class O(val x: Int)"))
    }

    @Test
    fun `an unannotated type is not a wire type`() {
        assertTrue(!caught("data class P(val trueCount: Int)"))
    }

    @Test
    fun `a counter-example inside a doc comment does not fail the build`() {
        assertTrue(!caught("/** Bad: @Serializable data class Q(val x: Int) */\nclass R"))
    }
}

class VocabularyRulesTest {

    private fun caught(source: String) = VocabularyRules.violations(source).isNotEmpty()

    @Test
    fun `catches a stem at the start of an identifier`() {
        assertTrue(caught("val victimSeat = 3"))
    }

    @Test
    fun `catches a stem in the middle or end of a camelCase identifier`() {
        // The original rule anchored at the identifier start and missed all of these — and the
        // camelCase suffix is the likelier form in real code.
        assertTrue(caught("val playerVictim = 3"), "playerVictim")
        assertTrue(caught("val isTraitor = false"), "isTraitor")
        assertTrue(caught("fun onSabotage() {}"), "onSabotage")
        assertTrue(caught("fun handleEvict() {}"), "handleEvict")
        assertTrue(caught("val subtaskCount = 1"), "subtaskCount")
    }

    @Test
    fun `a violation after a url is still seen`() {
        // substringBefore("//") truncated the line at the URL and hid everything after it.
        assertTrue(caught("""val docs = "https://example/rules"; val victimSeat = 3"""))
    }

    @Test
    fun `a slash-star inside a string literal does not blank real code`() {
        assertTrue(caught("""val re = "a/*b"; val victimSeat = 3; val re2 = "c*/d""""))
    }

    @Test
    fun `prose in comments is exempt`() {
        assertTrue(!caught("// never say victim or murder here"))
        assertTrue(!caught("/**\n * Not a victim. Not a corpse.\n */\nclass Ok"))
    }

    @Test
    fun `approved vocabulary passes`() {
        assertTrue(!caught("enum class Role { Resident, Insider }"))
        assertTrue(!caught("fun revoke(seat: Seat) {}"))
        assertTrue(!caught("fun restrain(seat: Seat) {}"))
        assertTrue(!caught("class SubroutineProgress"))
        assertTrue(!caught("val systemIntegrity = 0"))
    }

    @Test
    fun `reports the correct line number after a multi-line comment`() {
        val v = VocabularyRules.violations("/*\n * filler\n */\nval victimSeat = 3")
        assertEquals(4, v.single().line)
    }

    @Test
    fun `a vocabulary synonym in player-facing copy is caught, not just in identifiers`() {
        val src = """
            fun screen() {
                Label("DEACTIVATED RESIDENT FOUND")
            }
        """.trimIndent()
        val found = VocabularyRules.violations(src)
        assertTrue(found.any { it.text == "DEACTIVATED" }, "expected the copy to trip: ${'$'}found")
    }

    @Test
    fun `the word it should have been is not itself a violation`() {
        val src = """Label("REVOKED RESIDENT FOUND")"""
        assertEquals(emptyList(), VocabularyRules.violations(src))
    }
}
