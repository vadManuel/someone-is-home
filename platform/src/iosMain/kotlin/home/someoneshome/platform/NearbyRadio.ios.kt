@file:OptIn(kotlinx.cinterop.BetaInteropApi::class)

package home.someoneshome.platform

import home.someoneshome.model.protocol.RangingPool
import home.someoneshome.model.protocol.RangingSessions
import home.someoneshome.model.protocol.RangingToken
import home.someoneshome.model.protocol.RangingWire
import home.someoneshome.model.protocol.SessionEnd
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSKeyedArchiver
import platform.Foundation.NSKeyedUnarchiver
import platform.Foundation.dataWithBytes
import platform.NearbyInteraction.NIDiscoveryToken
import platform.NearbyInteraction.NINearbyObject
import platform.NearbyInteraction.NINearbyObjectDistanceNotAvailable
import platform.NearbyInteraction.NINearbyObjectRemovalReason
import platform.NearbyInteraction.NINearbyPeerConfiguration
import platform.NearbyInteraction.NISession
import platform.NearbyInteraction.NISessionDelegateProtocol
import platform.darwin.NSObject
import platform.posix.memcpy

actual fun nearbyRangingRadio(log: (String) -> Unit): RangingRadio {
    val sessions = NearbySessions(log)
    val pool = RangingPool(sessions, nextKey = ::randomKey, log = log)
    sessions.pool = pool
    return PooledRanging(pool)
}

private fun randomKey(): Long {
    val bytes = secureRandomBytes(RangingWire.KEY_BYTES)
    var bits = 0L
    for (b in bytes) bits = (bits shl 8) or (b.toLong() and 0xff)
    return bits
}

/**
 * **One `NISession` per slot of the pool** (D-303), started from the spike's `UwbPeerSession`
 * (`app/src/cheats/.../UwbSpike.kt`), which two phones measured on 2026-11-18.
 *
 * - **Opened idle**: `NISession()` hands its discovery token over at once, before it runs; the token
 *   is archived with secure coding, the bytes the spike relayed and D-300's wire carries.
 * - **Run** against one peer's token with an `NINearbyPeerConfiguration`, and **run again** with the
 *   same configuration when a suspension ends (the header: *a suspended session may be run again only
 *   after -sessionSuspensionEnded:*) or the peer times out. The spike: back within 0.5 s, no new token.
 * - **Closed** with `invalidate()`. A closed slot's delegate is dropped, and anything it still says
 *   carries an old generation, which the pool ignores.
 *
 * Each session gets its own delegate, which knows its slot and generation: the spike's `==` check
 * (2c323e9) answered *is this callback from the session I hold?*, and the generation answers it
 * without comparing Kotlin wrappers at all. No `delegateQueue` is set, so every callback arrives on
 * the main queue, where the uploader beats and applies the plan: nothing here is shared across
 * threads.
 *
 * **Rule 7.** A reading is a `Float` handed to [RangingPool.read], which writes two ints. What iOS
 * itself allocates to hand over an update (the array of nearby objects) is not this file's to save.
 */
private class NearbySessions(private val log: (String) -> Unit) : RangingSessions {
    lateinit var pool: RangingPool

    private val sessions = arrayOfNulls<NISession>(RangingWire.MAX_PEERS)
    private val listeners = arrayOfNulls<Listener>(RangingWire.MAX_PEERS)
    private val configurations = arrayOfNulls<NINearbyPeerConfiguration>(RangingWire.MAX_PEERS)

    override fun open(slot: Int, generation: Int): ByteArray? {
        val session = NISession()
        val listener = Listener(pool, slot, generation)
        // The delegate is held weakly by the session: [listeners] keeps it alive.
        session.delegate = listener
        sessions[slot] = session
        listeners[slot] = listener
        configurations[slot] = null
        val token = session.discoveryToken ?: return null.also { log("SESSION ${slot + 1} HAS NO DISCOVERY TOKEN") }
        val data = NSKeyedArchiver.archivedDataWithRootObject(token, requiringSecureCoding = true, error = null)
            ?: return null.also { log("SESSION ${slot + 1}'S TOKEN DID NOT ARCHIVE") }
        return bytesOf(data)
    }

    override fun run(slot: Int, peer: RangingToken): Boolean {
        val session = sessions[slot] ?: return false
        val token = tokenOf(peer.bytes()) ?: return false
        val configuration = NINearbyPeerConfiguration(peerToken = token)
        configurations[slot] = configuration
        session.runWithConfiguration(configuration)
        return true
    }

    override fun rerun(slot: Int) {
        val session = sessions[slot] ?: return
        val configuration = configurations[slot] ?: return
        session.runWithConfiguration(configuration)
    }

    override fun close(slot: Int) {
        sessions[slot]?.invalidate()
        sessions[slot] = null
        listeners[slot] = null
        configurations[slot] = null
    }

    private fun tokenOf(bytes: ByteArray): NIDiscoveryToken? {
        val data = bytes.usePinned { NSData.dataWithBytes(it.addressOf(0), bytes.size.toULong()) }
        return NSKeyedUnarchiver.unarchivedObjectOfClass(NIDiscoveryToken, fromData = data, error = null) as? NIDiscoveryToken
    }

    private fun bytesOf(data: NSData): ByteArray? {
        val length = data.length.toInt()
        // Past the wire's pad it is the pool's to refuse as a device fault (D-300): hand it over whole.
        if (length <= 0 || length > RangingWire.TOKEN_BYTES * 4) return null
        val source = data.bytes ?: return null
        val out = ByteArray(length)
        out.usePinned { memcpy(it.addressOf(0), source, length.toULong()) }
        return out
    }

    /** One session's delegate: its slot and generation, and the pool it reports to. */
    private class Listener(private val pool: RangingPool, private val slot: Int, private val generation: Int) :
        NSObject(), NISessionDelegateProtocol {

        @ObjCSignatureOverride
        override fun session(session: NISession, didUpdateNearbyObjects: List<*>) {
            // Indexed, not iterated: no iterator per update.
            for (i in 0 until didUpdateNearbyObjects.size) {
                val distance = (didUpdateNearbyObjects[i] as NINearbyObject).distance
                if (distance.toRawBits() == NINearbyObjectDistanceNotAvailable.toRawBits()) continue
                pool.read(slot, generation, distance)
            }
        }

        override fun session(
            session: NISession,
            didRemoveNearbyObjects: List<*>,
            withReason: NINearbyObjectRemovalReason,
        ) = pool.lost(slot, generation, timedOut = withReason == NINearbyObjectRemovalReason.NINearbyObjectRemovalReasonTimeout)

        override fun sessionWasSuspended(session: NISession) = pool.suspended(slot, generation)

        override fun sessionSuspensionEnded(session: NISession) = pool.resumed(slot, generation)

        override fun session(session: NISession, didInvalidateWithError: NSError) =
            pool.ended(slot, generation, endOf(didInvalidateWithError.code))
    }
}

/** `NIError.h`'s codes, as much as the pool needs (the spike's `errorName` has every one). */
internal fun endOf(code: Long): SessionEnd = when (code) {
    -5885L, -5880L -> SessionEnd.LimitReached // activeSessionsLimitExceeded, …ExtendedDistance…
    -5884L -> SessionEnd.NotAllowed // userDidNotAllow
    -5889L -> SessionEnd.Unsupported // unsupportedPlatform
    else -> SessionEnd.Failed // resourceUsageTimeout, sessionFailed, invalidConfiguration, …
}
