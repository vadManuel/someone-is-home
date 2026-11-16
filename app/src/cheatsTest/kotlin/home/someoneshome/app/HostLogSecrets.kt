package home.someoneshome.app

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * **Every way a secret could be written into a log line, and the search for them** (S3, D-219).
 *
 * A seed printed as a number, as hex or as base64 is the same seed, so a guard that looked for one
 * spelling would pass the day somebody logs another. Each secret is named so a failure says which
 * one leaked and in what form. Matched without regard to case: hex has two.
 */
@OptIn(ExperimentalEncodingApi::class)
object HostLogSecrets {

    /** The round seed: decimal signed and unsigned, hex, and its eight bytes in both orders. */
    fun ofSeed(name: String, seed: Long): Map<String, String> {
        val bigEndian = ByteArray(8) { i -> (seed ushr (56 - 8 * i)).toByte() }
        return buildMap {
            put("$name, decimal", seed.toString())
            put("$name, unsigned decimal", seed.toULong().toString())
            put("$name, signed hex", seed.toString(16))
            put("$name, hex", seed.toULong().toString(16))
            putAll(ofBytes("$name, big-endian", bigEndian))
            putAll(ofBytes("$name, little-endian", bigEndian.reversedArray()))
        }
    }

    /** A secret held as lowercase hex, such as a seat token. */
    fun ofHex(name: String, hex: String): Map<String, String> =
        ofBytes(name, ByteArray(hex.length / 2) { i -> hex.substring(2 * i, 2 * i + 2).toInt(16).toByte() })

    /** Raw key bytes: hex, and base64 in both alphabets, padded and not. */
    fun ofBytes(name: String, bytes: ByteArray): Map<String, String> {
        val standard = Base64.encode(bytes)
        val urlSafe = Base64.UrlSafe.encode(bytes)
        return mapOf(
            "$name, hex" to bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') },
            "$name, base64" to standard,
            "$name, base64 unpadded" to standard.trimEnd('='),
            "$name, url-safe base64" to urlSafe.trimEnd('='),
        )
    }

    /** Every (line, secret) pair where the secret's spelling appears in the line. */
    fun found(lines: List<String>, secrets: Map<String, String>): List<String> =
        lines.flatMap { line ->
            secrets.filter { (_, spelling) -> line.contains(spelling, ignoreCase = true) }
                .map { (name, spelling) -> "$name ('$spelling') in: $line" }
        }
}
