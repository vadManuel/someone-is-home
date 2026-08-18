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
     * this codebase — death framing among them, which needed a forty-instance sweep (D-061).
     */
    val STEMS: List<Pair<String, String>> = listOf(
        "traitor" to "role word — use Insider",
        "sabotag" to "use Egress",
        "evict" to "use Restrain",
        "task" to "use Subroutine",
        "murder" to "use Revoke",
        "corpse" to "nobody dies — there is no body",
        "victim" to "use target, or revoked player",
    )

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
