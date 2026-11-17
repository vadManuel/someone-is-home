/**
 * The matching logic behind the source guards, kept out of the Gradle task classes so it can be
 * unit-tested directly.
 *
 * That separation is the point. These rules are the only thing standing between a wire type and
 * a leak, and the first version of the redaction rule silently ignored `enum class` — which is
 * the declaration form of `Role`, the most alignment-revealing type in the game. It would have
 * passed green while putting every player's role on the wire. A guard nobody can test is a guard
 * nobody knows is working.
 */

data class Violation(val line: Int, val text: String, val reason: String)

object SourceText {

    /**
     * Blanks comments while respecting string literals.
     *
     * A naive `substringBefore("//")` truncates at a `//` inside a URL, so any violation later on
     * that line becomes invisible — and a naive block-comment regex treats a block-comment
     * opener appearing inside a string as a real one, blanking live code after it. Both are false NEGATIVES, which is the
     * direction that matters: a missed violation ships.
     *
     * Characters are replaced with spaces rather than removed so line numbers survive.
     */
    fun blankComments(source: String): String {
        val out = StringBuilder(source.length)
        var i = 0
        var inString = false
        var inChar = false
        var inLineComment = false
        var inBlockComment = false

        while (i < source.length) {
            val c = source[i]
            val next = if (i + 1 < source.length) source[i + 1] else ' '

            when {
                inLineComment -> {
                    if (c == '\n') {
                        inLineComment = false
                        out.append(c)
                    } else {
                        out.append(' ')
                    }
                }

                inBlockComment -> {
                    if (c == '*' && next == '/') {
                        inBlockComment = false
                        out.append("  ")
                        i++
                    } else {
                        out.append(if (c == '\n') '\n' else ' ')
                    }
                }

                inString -> {
                    out.append(c)
                    if (c == '\\' && i + 1 < source.length) {
                        out.append(next)
                        i++
                    } else if (c == '"') {
                        inString = false
                    }
                }

                inChar -> {
                    out.append(c)
                    if (c == '\\' && i + 1 < source.length) {
                        out.append(next)
                        i++
                    } else if (c == '\'') {
                        inChar = false
                    }
                }

                c == '/' && next == '/' -> {
                    inLineComment = true
                    out.append("  ")
                    i++
                }

                c == '/' && next == '*' -> {
                    inBlockComment = true
                    out.append("  ")
                    i++
                }

                c == '"' -> {
                    inString = true
                    out.append(c)
                }

                c == '\'' -> {
                    inChar = true
                    out.append(c)
                }

                else -> out.append(c)
            }
            i++
        }
        return out.toString()
    }
}

object VocabularyRules {

    /**
     * Stems, matched against whole identifiers case-insensitively by CONTAINMENT.
     *
     * Containment, not prefix. The original rule anchored at the start of an identifier, so it
     * caught `victimSeat` but not `playerVictim`, `isTraitor`, `onSabotage` or `handleEvict` —
     * and the camelCase suffix is the more likely form in real code.
     *
     * **Deliberately does not duplicate `.git/hooks/pre-commit`.** That hook guards protectable
     * expression in a public repo and owns those words; duplicating them here only made this
     * lint's own source and its tests trip it. These are the words that have actually drifted in
     * this codebase — elimination framing among them, which needed a forty-instance sweep (D-061).
     */
    val STEMS: List<Pair<String, String>> = listOf(
        "traitor" to "role word — use Insider",
        "sabotag" to "use Egress",
        "evict" to "use Restrain",
        "task" to "use Subroutine",
        "murder" to "use Revoke",
        "Revoked seat" to "nobody is eliminated — there is no body",
        "victim" to "use target, or revoked player",
        "deactivat" to "use Revoke — the state is REVOKED, on screen as well as in code",
        // **Spoof left the vocabulary and Static entered it** (D-186). The ability was replaced
        // rather than tuned, and the word went with the mechanic: what is in the slot now is a
        // house-wide jam with no target, which nothing about *spoof* describes. Held here for
        // `deactivat`'s reason exactly — the old word reached code, tests and one screen, and a
        // rename that is only a sweep is a rename somebody undoes by writing the familiar word.
        //
        // ⚠️ **Static is NOT a stem here and must not become one.** These are rejected forms; the
        // accepted vocabulary is the list in the advice below and in `project-context.md` rule 9.
        "spoof" to "the ability is Static (D-186) — Spoof left the vocabulary with its mechanic",
    )

    /**
     * Every word on the line, which — because [SourceText.blankComments] deliberately keeps
     * string contents intact — means **player-facing copy is scanned too**, not only identifiers.
     *
     * That is load-bearing rather than incidental. The vocabulary is closed *on screen* as much
     * as in code: `DEACTIVATED` reached two screens as a synonym for REVOKED, and the device
     * ended up contradicting itself about what to call the same state, while the `revoked` screen
     * said ACCESS REVOKED three taps away. Nothing about that is visible in a diff.
     */
    private val IDENTIFIER = Regex("""[A-Za-z_][A-Za-z0-9_]*""")

    fun violations(source: String): List<Violation> {
        val code = SourceText.blankComments(source)
        val found = mutableListOf<Violation>()
        code.lines().forEachIndexed { index, line ->
            for (match in IDENTIFIER.findAll(line)) {
                val lower = match.value.lowercase()
                for ((stem, reason) in STEMS) {
                    if (lower.contains(stem)) found += Violation(index + 1, match.value, reason)
                }
            }
        }
        return found
    }
}

object RedactionRules {

    /**
     * Any annotation run, any modifier run, any declaration keyword.
     *
     * The first version listed modifiers explicitly and matched annotations as `@\w+`. It
     * therefore ignored `enum class`, `private`, `abstract`, `open`, `expect`, `actual`, and any
     * fully-qualified `@kotlinx.serialization.Serializable`, because `\w` cannot cross a dot.
     *
     * **`Role` is an `enum class`.** The guard whose entire job is to fail closed default-
     * permitted the most alignment-revealing type in the game.
     */
    private val DECLARATION = Regex(
        """((?:@[\w.]+(?:\([^)]*\))?\s+)+)""" +
            """(?:(?:public|internal|private|protected|abstract|open|final|expect|actual|""" +
            """inner|data|value|sealed|enum|annotation|companion)\s+)*""" +
            """(?:class|object|interface)\s+(\w+)"""
    )

    /** `\b` so `@ClientFacingDraft` cannot satisfy the requirement, nor `@NotSerializable` trip it. */
    private val SERIALIZABLE = Regex("""@(?:\w+\.)*Serializable\b""")
    private val CLIENT_FACING = Regex("""@(?:\w+\.)*ClientFacing\b""")

    fun violations(source: String): List<Violation> {
        // Comments blanked here too: a KDoc on ClientFacing showing an unmarked @Serializable
        // type as a counter-example is documentation doing its job, and a guard that cries wolf
        // on its own docs gets switched off.
        val code = SourceText.blankComments(source)
        val found = mutableListOf<Violation>()
        for (m in DECLARATION.findAll(code)) {
            val annotations = m.groupValues[1]
            val name = m.groupValues[2]
            if (!SERIALIZABLE.containsMatchIn(annotations)) continue
            if (CLIENT_FACING.containsMatchIn(annotations)) continue
            val line = code.take(m.range.first).count { it == '\n' } + 1
            found += Violation(line, name, "@Serializable but not @ClientFacing")
        }
        return found
    }
}

/**
 * **The arming cordon: production reads no launch environment** (E-PAY-1, E-PAY-7).
 *
 * For three units the only thing in this application that could arm a round was a bench script
 * behind `SOMEONES_HOME_ROUND_HOUSE`. A phone a person launched from the springboard armed
 * nothing, and the failure had no symptom until the host held LIGHTS OUT and the house did not
 * move — which is precisely what happened to the owner on the one evening they tried to play: they
 * force-quit the app to fix their name, relaunched it by hand, lost the switch with the process,
 * and their thumb reached a bare build.
 *
 * The fix is that arming is ordinary production code. **This is what stops it quietly becoming a
 * switch again.** A `launchSwitch` or `launchValue` read anywhere in the scanned source set fails
 * the build, so the only way to make the round depend on a launch environment is to move the code
 * out of production — which is a thing a reviewer can see.
 *
 * ### It is a whole-source-set rule rather than a rule about one function
 *
 * A guard that named `armAtLightsOut` would be satisfied by a switch read one call up. What is
 * cordoned is the module's production source: the authority, the house, the transport and the
 * clock. The benches keep their switches and keep them where a release build cannot compile them.
 *
 * The names are matched as calls, so a `const val` holding a switch's *name* is not a violation —
 * the cheats' constants are theirs to declare and a production file that merely mentioned one
 * would be caught by the read, which is the thing that changes behaviour.
 */
object ArmingCordonRules {

    /**
     * The two readers, as calls.
     *
     * `\b` and the open paren together, so `launchSwitchName` is not a match and neither is a
     * KDoc's `[launchSwitch]` — comments are blanked before this runs in any case, for the reason
     * the other two guards blank them: a rule that trips on its own documentation gets switched
     * off.
     */
    private val READS = Regex("""\b(launchSwitch|launchValue)\s*\(""")

    fun violations(source: String): List<Violation> {
        val code = SourceText.blankComments(source)
        return READS.findAll(code).map { m ->
            Violation(
                line = code.take(m.range.first).count { it == '\n' } + 1,
                text = m.groupValues[1],
                reason = "production must not read the launch environment — a round that depends " +
                    "on one is a round a person cannot start",
            )
        }.toList()
    }
}

/**
 * **The bench-seed cordon: in the cheats, the round seed's launch switch is read in one place, behind
 * the bench's own switch** (R4, D-240).
 *
 * [ArmingCordonRules] keeps every launch read out of production, and a real LIGHTS OUT still read one:
 * the press is answered in `src/cheats/` (the app root lives there), and it asked for
 * `SOMEONES_HOME_ROUND_SEED` on every launch. `round.sh` sets that switch by default and its README
 * says which seat the seed makes the Insider. So in the cheats the seed switch may be named only by
 * its own declaration and inside the one gate, `fun benchSeed`, and that gate must name
 * `ROUND_SWITCH` before it names the seed. Any other mention of the switch, by constant or by its
 * literal, is a violation: a second read is a read nobody gated.
 */
object BenchSeedRules {

    private val NAMES = Regex("""\bSEED_SWITCH\b|"SOMEONES_HOME_ROUND_SEED"""")
    private val DECLARATION = Regex("""\bconst\s+val\s+SEED_SWITCH\s*=\s*"SOMEONES_HOME_ROUND_SEED"""")
    private val GATE = Regex("""\bfun\s+benchSeed\s*\(""")
    private val BENCH = Regex("""\bROUND_SWITCH\b""")

    fun violations(source: String): List<Violation> {
        val code = SourceText.blankComments(source)
        val allowed = DECLARATION.findAll(code).map { it.range }.toMutableList()
        val out = mutableListOf<Violation>()
        for (gate in GATE.findAll(code)) {
            val body = bodyAfter(code, gate.range.last) ?: continue
            val bench = BENCH.find(code, body.first)?.takeIf { it.range.first in body }
            val seed = NAMES.find(code, body.first)?.takeIf { it.range.first in body }
            if (seed != null && (bench == null || bench.range.first > seed.range.first)) {
                out += Violation(lineOf(code, seed.range.first), seed.value, "the gate reads the seed before it asks for ROUND_SWITCH")
            }
            allowed += body
        }
        NAMES.findAll(code).filter { m -> allowed.none { m.range.first in it } }.forEach { m ->
            out += Violation(
                lineOf(code, m.range.first), m.value,
                "the round seed's switch is read outside `benchSeed` — a press on a phone a person launched " +
                    "must never read it",
            )
        }
        return out.sortedBy { it.line }
    }

    /** The braces of the body that opens after [from], matched; null when there is none. */
    private fun bodyAfter(code: String, from: Int): IntRange? {
        val open = code.indexOf('{', from)
        if (open < 0) return null
        var depth = 0
        for (i in open until code.length) {
            when (code[i]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return open..i
            }
        }
        return null
    }

    private fun lineOf(code: String, at: Int): Int = code.take(at).count { it == '\n' } + 1
}

/**
 * **The seed-source cordon: no seed and no seat token is drawn from `kotlin.random`** (D-215).
 *
 * Every secret in a round is an HMAC keyed by the round's seed (D-212, D-214): the Insider draw,
 * every seat's contact seed and so every radio token, the jam's noise, the work orders. An HMAC
 * hides its key only as well as the key is unpredictable, and `kotlin.random.Random` is a seeded
 * generator with a published algorithm. `RoundCheat.seed()` fell back to `Random.nextLong()` for a
 * whole unit after the derivations were made one-way, and nothing failed.
 *
 * So the modules that draw seeds and tokens may not name `kotlin.random` at all, in any source set
 * that compiles into a phone, cheats included: the secure draw is `drawRoundSeed` and
 * `mintSeatToken`, over `platform`'s `secureRandomBytes`. A whole-module rule rather than one about
 * a function, for the arming cordon's reason: a guard that named one call site is satisfied by the
 * call one line up.
 *
 * Matched: the package, and `Random` used as a receiver or a constructor. A word that merely
 * contains it (`secureRandomLong`, `RandomAccess`) is not a use.
 */
object SeedSourceRules {

    private val USES = Regex("""\bkotlin\.random\b|\bRandom\s*[.(]""")

    fun violations(source: String): List<Violation> {
        val code = SourceText.blankComments(source)
        return USES.findAll(code).map { m ->
            Violation(
                line = code.take(m.range.first).count { it == '\n' } + 1,
                text = m.value.trim(),
                reason = "a seed or a seat token drawn from kotlin.random can be guessed — draw it " +
                    "with drawRoundSeed() / mintSeatToken() (platform secureRandomBytes)",
            )
        }.toList()
    }
}
