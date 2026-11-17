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
        assertTrue(!caught("/**\n * Not a victim. Not a Revoked seat.\n */\nclass Ok"))
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

/**
 * The arming cordon, checked the way its two siblings are: every case is something the rule has to
 * catch or has to leave alone, and a guard nobody can test is a guard nobody knows is working.
 */
class ArmingCordonRulesTest {

    private fun caught(source: String) = ArmingCordonRules.violations(source).isNotEmpty()

    @Test
    fun `a launch switch read in production is caught`() {
        assertTrue(caught("""val armed = launchSwitch("SOMEONES_HOME_ROUND_HOUSE")"""))
        assertTrue(caught("""val seed = launchValue("SOMEONES_HOME_ROUND_SEED")?.toLongOrNull()"""))
    }

    /** Whitespace before the paren is still a call, and is the spelling a formatter can produce. */
    @Test
    fun `a read with space before the paren is still a read`() {
        assertTrue(caught("val armed = launchSwitch (NAME)"))
    }

    /**
     * ⚠️ **Comments and KDoc are blanked first**, which is the lesson both other guards learned
     * the same way: a rule that trips on the paragraph explaining it is a rule somebody deletes.
     */
    @Test
    fun `the rule does not trip on documentation about itself`() {
        assertTrue(!caught("// nothing here may call launchSwitch(NAME)\nval x = 1"))
        assertTrue(!caught("/**\n * See [launchSwitch] — the benches own it.\n */\nval x = 1"))
    }

    /** A name that merely starts the same way is not a read. */
    @Test
    fun `a longer identifier is not a read`() {
        assertTrue(!caught("val launchSwitchName = SEED_SWITCH"))
        assertTrue(!caught("""const val ARM_SWITCH = "SOMEONES_HOME_ROUND_HOUSE""""))
    }

    @Test
    fun `it reports the line the read is on`() {
        val v = ArmingCordonRules.violations("val a = 1\nval b = 2\nval c = launchSwitch(NAME)")
        assertEquals(3, v.single().line)
        assertEquals("launchSwitch", v.single().text)
    }
}

/** The bench-seed cordon (R4, D-240), checked the way the arming cordon is. */
class BenchSeedRulesTest {

    private val gate = """
        const val SEED_SWITCH = "SOMEONES_HOME_ROUND_SEED"
        fun benchSeed(read: (String) -> String? = ::launchValue): Long? {
            val bench = read(ROUND_SWITCH) != null || read(BY_HAND_SWITCH) != null
            if (!bench) return null
            return read(SEED_SWITCH)?.toLongOrNull()
        }
    """.trimIndent()

    private fun caught(source: String) = BenchSeedRules.violations(source).isNotEmpty()

    @Test
    fun `the gate and the declaration are allowed`() {
        assertTrue(!caught(gate))
    }

    /** The two reads that were here before D-240: the press, and the log's seat. */
    @Test
    fun `a read outside the gate is caught`() {
        assertTrue(caught(gate + "\nfun seed(): Long = launchValue(SEED_SWITCH)?.toLongOrNull() ?: drawRoundSeed()"))
        assertTrue(caught(gate + "\nval names = launchValue(RoundCheat.SEED_SWITCH) != null"))
        assertTrue(caught(gate + "\nval s = getenv(\"SOMEONES_HOME_ROUND_SEED\")"))
    }

    @Test
    fun `a gate that reads the seed before the bench switch is caught`() {
        assertTrue(caught("fun benchSeed(read: (String) -> String?): Long? { return read(SEED_SWITCH)?.toLongOrNull() }"))
        assertTrue(
            caught("fun benchSeed(read: (String) -> String?): Long? { val s = read(SEED_SWITCH); return if (read(ROUND_SWITCH) != null) s?.toLongOrNull() else null }"),
        )
    }

    @Test
    fun `documentation about the switch does not trip it`() {
        assertTrue(!caught(gate + "\n// until D-240 the press read launchValue(SEED_SWITCH) itself\n/** [SEED_SWITCH] */ val x = 1"))
    }

    @Test
    fun `it reports the line the read is on`() {
        val v = BenchSeedRules.violations("val a = 1\nval b = launchValue(SEED_SWITCH)")
        assertEquals(2, v.single().line)
    }
}

/** The seed-source cordon (D-215), checked the way the arming cordon is. */
class SeedSourceRulesTest {

    private fun caught(source: String) = SeedSourceRules.violations(source).isNotEmpty()

    @Test
    fun `the insecure fallback that was here is caught`() {
        assertTrue(caught("fun seed(): Long = launchValue(SEED_SWITCH)?.toLongOrNull() ?: Random.nextLong()"))
        assertTrue(caught("import kotlin.random.Random"))
        assertTrue(caught("val r = Random(42)"))
        assertTrue(caught("val r = kotlin.random.Random.Default"))
        assertTrue(caught("val t = Random . nextBytes(16)"))
    }

    @Test
    fun `the secure draw and names that merely contain the word are not uses`() {
        assertTrue(!caught("fun seed(): Long = launchValue(SEED_SWITCH)?.toLongOrNull() ?: drawRoundSeed()"))
        assertTrue(!caught("val s = secureRandomLong()"))
        assertTrue(!caught("val b = secureRandomBytes(16)"))
        assertTrue(!caught("class RandomAccessThing"))
    }

    @Test
    fun `documentation about the rule does not trip it`() {
        assertTrue(!caught("// it used to be Random.nextLong()\nval x = 1"))
        assertTrue(!caught("/**\n * Never `kotlin.random.Random`.\n */\nval x = 1"))
    }

    @Test
    fun `it reports the line the use is on`() {
        val v = SeedSourceRules.violations("val a = 1\nval b = Random.nextLong()")
        assertEquals(2, v.single().line)
    }
}
