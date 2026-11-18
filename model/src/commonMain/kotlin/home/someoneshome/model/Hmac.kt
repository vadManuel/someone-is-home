package home.someoneshome.model

/**
 * **HMAC-SHA-256, written out in common Kotlin** (RFC 2104, FIPS 180-4).
 *
 * The contact tokens (D-212) are an HMAC of a seat's seed and the window index, and the same
 * function has to run on the phone that advertises the token and on the host that inverts it. So it
 * lives in `model`, which every module sees, and it is written out rather than borrowed: `model` has
 * no platform, and a digest that differed between the iOS build and the host's would be a token the
 * house could never resolve, failing silently in a dark room.
 *
 * It runs once per window per seat on the host, once per window on a phone, and twenty-four times a
 * beat on every phone for an upload's filler, so what it allocates is rule 7's business: [Hmac.Keyed]
 * holds one key's pads and one engine for many messages (D-276). It never runs on the blackout path.
 *
 * `HmacTest` pins it to RFC 4231's vectors, so a slip in the rounds is a red build rather than a
 * token nobody can resolve.
 */
object Hmac {

    /** HMAC-SHA-256 of [message] under [key]. 32 bytes. */
    fun sha256(key: ByteArray, message: ByteArray): ByteArray = Keyed(key).mac(message)

    /**
     * **One key's HMAC, for as many messages as the caller has** (rule 7, D-276).
     *
     * The pads are derived once, at construction, and one digest engine is reused for every
     * [mac], so a message costs its 32-byte answer and nothing else, or nothing at all into the
     * caller's own array. An upload's filler is twenty-four MACs under one seed every beat, on every
     * phone; computed through [sha256] each one built the pads, two concatenations and two
     * schedules afresh, about two kilobytes apiece.
     *
     * **Not thread-safe, and never shared.** It holds the engine's working state between the two
     * halves of a MAC. Make one where it is used and let it go there.
     */
    class Keyed(key: ByteArray) {
        private val inner = ByteArray(BLOCK)
        private val outer = ByteArray(BLOCK)
        private val innerDigest = ByteArray(Sha256.DIGEST_BYTES)
        private val engine = Sha256.Engine()

        init {
            val block = if (key.size > BLOCK) Sha256.digest(key) else key
            for (i in 0 until BLOCK) {
                val k = if (i < block.size) block[i].toInt() else 0
                inner[i] = (k xor 0x36).toByte()
                outer[i] = (k xor 0x5c).toByte()
            }
        }

        /** HMAC-SHA-256 of [message]'s first [length] bytes, written into [into] and returned. */
        fun mac(
            message: ByteArray,
            length: Int = message.size,
            into: ByteArray = ByteArray(Sha256.DIGEST_BYTES),
        ): ByteArray {
            engine.reset()
            engine.update(inner, 0, BLOCK)
            engine.update(message, 0, length)
            engine.finish(innerDigest)
            engine.reset()
            engine.update(outer, 0, BLOCK)
            engine.update(innerDigest, 0, Sha256.DIGEST_BYTES)
            engine.finish(into)
            return into
        }
    }

    private const val BLOCK = 64
}

/** SHA-256 (FIPS 180-4). Only [Hmac] reads it. */
internal object Sha256 {

    const val DIGEST_BYTES: Int = 32

    fun digest(message: ByteArray): ByteArray {
        val engine = Engine()
        engine.reset()
        engine.update(message, 0, message.size)
        return engine.finish(ByteArray(DIGEST_BYTES))
    }

    /**
     * **One running digest, reusable**: [reset], [update] any number of times, [finish]. Its three
     * arrays are allocated once, with it, and nothing after (rule 7, D-276). Not thread-safe.
     */
    class Engine {
        private val h = IntArray(8)
        private val w = IntArray(64)
        private val block = ByteArray(64)
        private var buffered = 0
        private var total = 0L

        fun reset() {
            INITIAL.copyInto(h)
            buffered = 0
            total = 0L
        }

        fun update(bytes: ByteArray, offset: Int, length: Int) {
            for (i in offset until offset + length) push(bytes[i])
            total += length
        }

        /** The padding and the length, then the digest into [out], which is returned. */
        fun finish(out: ByteArray): ByteArray {
            val bitLength = total * 8
            push(0x80.toByte())
            while (buffered != 56) push(0)
            for (i in 7 downTo 0) push((bitLength ushr (8 * i)).toByte())
            for (i in 0 until DIGEST_BYTES) out[i] = (h[i / 4] ushr (24 - 8 * (i % 4))).toByte()
            return out
        }

        private fun push(b: Byte) {
            block[buffered++] = b
            if (buffered == 64) {
                compress()
                buffered = 0
            }
        }

        private fun compress() {
            for (t in 0 until 16) {
                val o = t * 4
                w[t] = ((block[o].toInt() and 0xff) shl 24) or ((block[o + 1].toInt() and 0xff) shl 16) or
                    ((block[o + 2].toInt() and 0xff) shl 8) or (block[o + 3].toInt() and 0xff)
            }
            for (t in 16 until 64) {
                val s0 = w[t - 15].rotateRight(7) xor w[t - 15].rotateRight(18) xor (w[t - 15] ushr 3)
                val s1 = w[t - 2].rotateRight(17) xor w[t - 2].rotateRight(19) xor (w[t - 2] ushr 10)
                w[t] = w[t - 16] + s0 + w[t - 7] + s1
            }
            var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]
            var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]
            for (t in 0 until 64) {
                val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
                val ch = (e and f) xor (e.inv() and g)
                val t1 = hh + s1 + ch + K[t] + w[t]
                val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val t2 = s0 + maj
                hh = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2
            }
            h[0] += a; h[1] += b; h[2] += c; h[3] += d
            h[4] += e; h[5] += f; h[6] += g; h[7] += hh
        }
    }

    private val INITIAL = intArrayOf(
        0x6a09e667L.toInt(), 0xbb67ae85L.toInt(), 0x3c6ef372L.toInt(), 0xa54ff53aL.toInt(),
        0x510e527fL.toInt(), 0x9b05688cL.toInt(), 0x1f83d9abL.toInt(), 0x5be0cd19L.toInt(),
    )

    private val K = intArrayOf(
        0x428a2f98L.toInt(), 0x71374491L.toInt(), 0xb5c0fbcfL.toInt(), 0xe9b5dba5L.toInt(),
        0x3956c25bL.toInt(), 0x59f111f1L.toInt(), 0x923f82a4L.toInt(), 0xab1c5ed5L.toInt(),
        0xd807aa98L.toInt(), 0x12835b01L.toInt(), 0x243185beL.toInt(), 0x550c7dc3L.toInt(),
        0x72be5d74L.toInt(), 0x80deb1feL.toInt(), 0x9bdc06a7L.toInt(), 0xc19bf174L.toInt(),
        0xe49b69c1L.toInt(), 0xefbe4786L.toInt(), 0x0fc19dc6L.toInt(), 0x240ca1ccL.toInt(),
        0x2de92c6fL.toInt(), 0x4a7484aaL.toInt(), 0x5cb0a9dcL.toInt(), 0x76f988daL.toInt(),
        0x983e5152L.toInt(), 0xa831c66dL.toInt(), 0xb00327c8L.toInt(), 0xbf597fc7L.toInt(),
        0xc6e00bf3L.toInt(), 0xd5a79147L.toInt(), 0x06ca6351L.toInt(), 0x14292967L.toInt(),
        0x27b70a85L.toInt(), 0x2e1b2138L.toInt(), 0x4d2c6dfcL.toInt(), 0x53380d13L.toInt(),
        0x650a7354L.toInt(), 0x766a0abbL.toInt(), 0x81c2c92eL.toInt(), 0x92722c85L.toInt(),
        0xa2bfe8a1L.toInt(), 0xa81a664bL.toInt(), 0xc24b8b70L.toInt(), 0xc76c51a3L.toInt(),
        0xd192e819L.toInt(), 0xd6990624L.toInt(), 0xf40e3585L.toInt(), 0x106aa070L.toInt(),
        0x19a4c116L.toInt(), 0x1e376c08L.toInt(), 0x2748774cL.toInt(), 0x34b0bcb5L.toInt(),
        0x391c0cb3L.toInt(), 0x4ed8aa4aL.toInt(), 0x5b9cca4fL.toInt(), 0x682e6ff3L.toInt(),
        0x748f82eeL.toInt(), 0x78a5636fL.toInt(), 0x84c87814L.toInt(), 0x8cc70208L.toInt(),
        0x90befffaL.toInt(), 0xa4506cebL.toInt(), 0xbef9a3f7L.toInt(), 0xc67178f2L.toInt(),
    )
}
