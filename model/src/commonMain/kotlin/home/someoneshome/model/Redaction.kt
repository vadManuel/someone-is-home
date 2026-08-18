package home.someoneshome.model

/**
 * The marker that makes redaction fail closed.
 *
 * Only `@ClientFacing` types may be wire-`@Serializable`; `redactionLint` fails the build on any
 * `@Serializable` type without it. That inverts the dangerous default: a new type does not
 * silently acquire the right to be sent, someone has to say so.
 *
 * **Marking a type is a claim that it is physically incapable of carrying ground truth.** Not
 * that its fields are currently nulled out — a nulled field still exists, and someone makes it
 * non-null later for an unrelated reason. The type must *be* the field list.
 *
 * Say in the KDoc what the type is allowed to reveal, and to whom.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class ClientFacing
