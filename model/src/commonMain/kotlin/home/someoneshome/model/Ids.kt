package home.someoneshome.model

/** A house marker: a scan point, the Terminal, or an Array Wipe station. */
value class MarkerId(val value: String)

/** An identifier minted by the rules. Seeded and recorded — never random. */
value class EntityId(val value: Long)
