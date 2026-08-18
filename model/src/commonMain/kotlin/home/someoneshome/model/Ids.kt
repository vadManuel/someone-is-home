package home.someoneshome.model

import kotlin.jvm.JvmInline

/** A house marker: a scan point, the Terminal, or an Array Wipe station. */
@JvmInline
value class MarkerId(val value: String)

/** An identifier minted by the rules. Seeded and recorded — never random. */
@JvmInline
value class EntityId(val value: Long)
