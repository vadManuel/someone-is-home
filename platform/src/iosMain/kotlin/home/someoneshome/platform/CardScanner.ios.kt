package home.someoneshome.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetHigh
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.defaultDeviceWithDeviceType
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreMedia.CMSampleBufferRef
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create

/**
 * The camera, on a target that has one — and the same class on a target that does not.
 *
 * There is no build flag choosing between two implementations: [CameraCardScanner] discovers that
 * it has no lens the way it discovers that permission was refused, and both go quiet.
 */
actual fun deviceCardScanner(): CardScanner = CameraCardScanner()

/**
 * **The front camera, no preview, nine characters out.**
 *
 * ### There is no preview layer, and its absence is the design rather than an omission
 *
 * The lit screen *is* the light the card is read by (gdd M1, and `ScanScreen` in `ui`): the player
 * holds the panel up to the marker, which puts the front camera on the paper and the screen out of
 * their own sight. A preview would be a picture nobody can look at, drawn at the cost of the frame
 * budget, on the one screen whose brightness is a game value. So the session carries a metadata
 * output and nothing else — no `AVCaptureVideoPreviewLayer` is created here, and none may be.
 *
 * ### It hands over what the camera saw, and asks nothing about it
 *
 * `stringValue` goes to the listener exactly as it arrived — not trimmed, not upper-cased, not
 * length-checked. Deciding what a card *is* belongs to `CardPayload` in `model`, and a camera that
 * pre-filtered would be a second opinion about the payload format; the two would one day disagree
 * about a piece of paper that cannot be edited. **A foreign code — a URL, a boarding pass, a
 * neighbour's wifi sticker — is therefore delivered rather than dropped**, and refused by the
 * decoder with a reason the scan surface already knows how to say (D-071).
 *
 * A symbol whose `stringValue` is null is delivered as the empty string for that same reason: it
 * is a fact about a piece of paper, and the one thing this class must never do is swallow a
 * detection so that the screen shows nothing at all.
 *
 * ### A phone that cannot see is silent, and says so only to the log
 *
 * No lens, permission refused, an input the session would not take: every one of them ends the
 * same way — nothing starts, nothing is delivered, and [log] carries the reason. Rule 6, errors
 * silent to the player and loud to the authority; rule 5 behind it, because the alternative to
 * silence on this screen is a screen that blanks, and a screen that blanks in a dark house is a
 * revocation to everyone watching it.
 *
 * ### The session is started off the main thread and read on it
 *
 * `startRunning` blocks for as long as the hardware takes to configure, which is tens to hundreds
 * of milliseconds and is not spendable on the thread that draws the lamp. Metadata comes back on
 * the main queue, because what it feeds is the screen.
 */
@OptIn(ExperimentalForeignApi::class)
class CameraCardScanner(
    private val log: (String) -> Unit = { println(it) },
    /**
     * A frame counter, off by default and switched on only by a bench.
     *
     * The metadata output speaks when it sees a symbol and is silent otherwise, so *the camera is
     * running* and *the camera is delivering pixels* look identical in a log from a room with no
     * card in it. This attaches a second output whose only job is to count, so a device run with
     * nobody holding paper can still say the pipeline moved. **It costs a buffer per frame** and
     * is never on in a build a round is played on — nothing in `commonMain` passes it.
     */
    private val countFrames: Boolean = false,
) : CardScanner {

    private val session = AVCaptureSession()
    private val queue = dispatch_queue_create("home.someoneshome.scan", null)

    /**
     * **Whether anything still wants the camera**, asked again after the permission prompt.
     *
     * iOS asks for the camera once ever, and the answer arrives whenever the person answers it —
     * which on a first run is after they have read a sentence, thought about it, and possibly
     * walked into another room. `stop` can easily land first. Without this the callback would then
     * open a capture session behind a screen the host has already left: a lens and an ISP running
     * for a scan nobody can make, on a phone that has to last an evening.
     *
     * It is the same fact `listener` carries and is kept separately on purpose — a listener is what
     * a reader is told about, and this is whether there is anything to read for.
     */
    private var wanted = false

    private var listener: ((String) -> Unit)? = null
    private var reader: MetadataReader? = null
    private var frames: FrameCounter? = null
    private var configured = false

    override fun start(onPayload: (String) -> Unit) {
        // Replaces rather than adds, exactly as the seam says: a scan screen opened, left and
        // opened again must not register every card twice.
        listener = onPayload
        wanted = true
        when (val status = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusAuthorized -> begin()

            // The prompt. It is asked here rather than warmed at setup, and that is flagged in the
            // worklog: iOS asks once ever, and asking for the first time in a dark room is the
            // same mistake the local-network prompt already carries a warning about.
            AVAuthorizationStatusNotDetermined -> {
                log("$TAG asking for the camera")
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                    if (granted) {
                        log("$TAG permission granted")
                        begin()
                    } else {
                        log("$TAG permission refused — this phone will read no cards")
                    }
                }
            }

            AVAuthorizationStatusDenied, AVAuthorizationStatusRestricted ->
                log("$TAG permission is refused in Settings (status $status) — no session started")

            else -> log("$TAG unknown authorization status $status — no session started")
        }
    }

    override fun stop() {
        listener = null
        wanted = false
        dispatch_async(queue) {
            if (session.isRunning()) {
                session.stopRunning()
                log("$TAG session stopped")
            }
        }
    }

    /** Configure once, then run. Called only with permission in hand. */
    private fun begin() {
        dispatch_async(queue) {
            if (!wanted) {
                log("$TAG nothing wants the camera any more — not starting")
                return@dispatch_async
            }
            if (!configured && !configure()) return@dispatch_async
            if (session.isRunning()) {
                log("$TAG session already running")
                return@dispatch_async
            }
            session.startRunning()
            log("$TAG session running=${session.isRunning()}")
        }
    }

    /** Build the session. False, with a reason in the log, if this phone cannot. */
    private fun configure(): Boolean {
        val camera = AVCaptureDevice.defaultDeviceWithDeviceType(
            deviceType = AVCaptureDeviceTypeBuiltInWideAngleCamera,
            mediaType = AVMediaTypeVideo,
            // FRONT. The screen faces the card, so the camera that faces the card is this one.
            position = AVCaptureDevicePositionFront,
        )
        if (camera == null) {
            log("$TAG this target has no front camera — every scan will be a silent no-op")
            return false
        }
        val input = AVCaptureDeviceInput.deviceInputWithDevice(camera, null)
        if (input == null) {
            log("$TAG the front camera would not open as an input")
            return false
        }

        session.beginConfiguration()
        session.sessionPreset = AVCaptureSessionPresetHigh
        if (!session.canAddInput(input)) {
            session.commitConfiguration()
            log("$TAG the session refused the camera input")
            return false
        }
        session.addInput(input)

        val output = AVCaptureMetadataOutput()
        if (!session.canAddOutput(output)) {
            session.commitConfiguration()
            log("$TAG the session refused the metadata output")
            return false
        }
        session.addOutput(output)
        // AFTER addOutput, and that ordering is not style: availableMetadataObjectTypes is empty
        // until the output belongs to a session, so setting the types first sets them to nothing
        // and the delegate is never called — a scanner that runs, sees, and says nothing.
        val reader = MetadataReader(::deliver)
        this.reader = reader
        output.setMetadataObjectsDelegate(reader, dispatch_get_main_queue())
        output.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)

        if (countFrames) {
            val counter = FrameCounter(log)
            if (counter.attachTo(session)) frames = counter
        }

        session.commitConfiguration()
        configured = true
        log(
            "$TAG configured — front camera '${camera.localizedName}', " +
                "types=${output.metadataObjectTypes.size}, frameCounter=${frames != null}"
        )
        return true
    }

    /**
     * A symbol resolved. **Verbatim**, and past a listener that may have gone away.
     *
     * Verbatim as in *no trimming, no case folding, no length check*. Nine characters that came off
     * a symbol as `1abcdef gh` are nine characters that came off a symbol; tidying them here would
     * make this class the second opinion about the payload format, and the card is a piece of paper
     * that cannot be edited when the two opinions diverge. `CameraCardScannerTest` fails on any
     * transformation, and the injection that added a `trim()` is what it was written against.
     *
     * The listener is read once into a local: `stop` can land between the null check and the call
     * on the main queue, and a scan delivered into a screen that is no longer there registers a
     * card in a room the host walked out of.
     *
     * **Internal rather than private on purpose**, and for [SeededCardScanner.present]'s reason:
     * this is the camera's own event, and no target `./gradlew check` can run has a camera to raise
     * it. A test that could not raise it could only certify the class's silence.
     */
    /** Whether a screen still has this scanner open. Read by a test; see [wanted]. */
    internal val wantsToRun: Boolean get() = wanted

    internal fun deliver(payload: String) {
        val onPayload = listener
        log("$TAG read '$payload' (${payload.length} chars)")
        if (onPayload == null) {
            log("$TAG …after stop, so nobody was told")
            return
        }
        onPayload(payload)
    }

    private companion object {

        /** What the device log is grepped for. The evidence a unit with no hand on a card has. */
        const val TAG = "[scan]"
    }
}

/**
 * The delegate object, kept separate so the scanner itself is not an `NSObject` subclass.
 *
 * Every resolved symbol is passed on, including the ones that are obviously not ours. What a code
 * means is `CardPayload`'s question and is asked one layer up.
 */
@OptIn(ExperimentalForeignApi::class)
private class MetadataReader(
    private val onCode: (String) -> Unit,
) : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: AVCaptureConnection,
    ) {
        for (item in didOutputMetadataObjects) {
            val code = item as? AVMetadataMachineReadableCodeObject ?: continue
            onCode(code.stringValue.orEmpty())
        }
    }
}

/**
 * **Proof that pixels moved, for a room with no card in it.**
 *
 * A metadata output is silent until it sees a symbol, so *the session started* and *the session is
 * delivering frames* produce identical logs from a phone on a desk at three in the morning — and
 * the first of those is exactly the claim a device unit is most tempted to overstate. This counts
 * buffers and says so every [EVERY] frames, which is a sentence the log can carry that nothing
 * else in the pipeline can fake.
 *
 * It is a bench instrument. Nothing in `commonMain` builds one, `deviceCardScanner` does not pass
 * the flag, and a round played on this build has one output on its session rather than two.
 */
@OptIn(ExperimentalForeignApi::class)
private class FrameCounter(private val log: (String) -> Unit) :
    NSObject(), AVCaptureVideoDataOutputSampleBufferDelegateProtocol {

    private var seen = 0

    /** Attached, or false with the reason logged. A counter is never worth failing a scan for. */
    fun attachTo(session: AVCaptureSession): Boolean {
        val output = AVCaptureVideoDataOutput()
        // Drop rather than queue: this exists to say frames arrived, not to see all of them, and
        // a backlog of buffers on the scan screen is the allocation budget spent on diagnostics.
        output.alwaysDiscardsLateVideoFrames = true
        output.setSampleBufferDelegate(this, dispatch_queue_create("home.someoneshome.frames", null))
        if (!session.canAddOutput(output)) {
            log("[frames] the session refused a video output — no frame count in this run")
            return false
        }
        session.addOutput(output)
        return true
    }

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputSampleBuffer: CMSampleBufferRef?,
        fromConnection: AVCaptureConnection,
    ) {
        seen++
        if (seen % FRAME_LOG_EVERY == 0) log("[frames] $seen frame(s) delivered")
    }
}

/**
 * Roughly a second at 30 fps — often enough to be evidence, rare enough to read.
 *
 * Top-level rather than a companion because a subclass of an Objective-C type cannot have one
 * carrying fields, which is a Kotlin/Native rule and not a preference.
 */
private const val FRAME_LOG_EVERY = 30
