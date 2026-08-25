package home.someoneshome.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePosition
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetHigh
import platform.AVFoundation.AVCaptureTorchModeOff
import platform.AVFoundation.AVCaptureTorchModeOn
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.defaultDeviceWithDeviceType
import platform.AVFoundation.hasTorch
import platform.AVFoundation.isTorchModeSupported
import platform.AVFoundation.requestAccessForMediaType
import platform.AVFoundation.torchMode
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
actual fun deviceCardScanner(use: ScanUse): CardScanner = CameraCardScanner(use)

/**
 * **One capture session, configured by the scan it is for** — see [ScanUse].
 *
 * ### In a round there is no preview layer, and its absence is the design rather than an omission
 *
 * The lit screen *is* the light the card is read by (gdd M1, and `ScanScreen` in `ui`): the player
 * holds the panel up to the marker, which puts the front camera on the paper and the screen out of
 * their own sight. A preview would be a picture nobody can look at, drawn at the cost of the frame
 * budget, on the one screen whose brightness is a game value. So an in-round session carries a
 * metadata output and nothing else — [ScanUse.InRound] sets `preview = false` and no
 * `AVCaptureVideoPreviewLayer` is built, and none may be.
 *
 * ### At registration there is one, and its absence was a bug the owner found on a phone
 *
 * Host setup is the opposite screen in every way: the lights are on, the card is on the far side of
 * the phone, and the host is aiming. The screen said BACK CAMERA . POINT AT THE CODE over a session
 * that was hard-coded to the front lens with no picture in it — so the instruction was wrong, the
 * aiming was blind, and the torch button beside it reached no hardware at all. The configuration is
 * now read from [use] on every line that could get it wrong, which is what makes `ScanUseTest`'s
 * injection meaningful rather than decorative.
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
    /**
     * Which scan this session is. **Every hardware decision below reads it** — the lens, whether a
     * preview layer is built, whether the torch answers — so there is one place a configuration can
     * be wrong and one test that has to be satisfied to change it.
     */
    override val use: ScanUse = ScanUse.InRound,
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

    /**
     * The capture device the session opened, kept because **the torch belongs to the lens**.
     *
     * `AVCaptureDevice.torchMode` is a property of the camera rather than of the session, and it is
     * only settable between `lockForConfiguration` and `unlockForConfiguration`. Holding the device
     * is the whole of what makes [torch] able to reach hardware — the toggle it sits behind spent
     * this project's whole life so far reaching a colour value in `Amber` and nothing else.
     */
    private var device: AVCaptureDevice? = null

    /**
     * **What the host is aiming with**, on a registration scanner, and null on an in-round one.
     *
     * Built inside [configure] rather than lazily on first read, because a layer attached to a
     * session that was never configured shows a black rectangle indistinguishable from a camera
     * that is refusing — and this is the one screen in the game where the player is meant to be
     * looking at the picture.
     */
    private var preview: AVCaptureVideoPreviewLayer? = null

    /**
     * The live picture, for whoever draws it, or null where there is none.
     *
     * `app` puts it inside a `UIView`; nothing in `ui` can see this type and nothing should — see
     * `Viewfinder.ios.kt`. **Reading it does not create it**: a scanner whose [ScanUse.preview] is
     * false answers null forever, which is the in-round session saying it has no picture rather
     * than being asked not to draw one.
     */
    val previewLayer: AVCaptureVideoPreviewLayer? get() = preview

    /** Whether the torch is currently lit, as last set. Read by a bench and by the log. */
    var torchLit: Boolean = false
        private set

    /**
     * **The LED, reached at last** — and refused by name on the scan that may not have one.
     *
     * The refusal is first and is not a guard clause standing in front of working code: the
     * in-round session is a phone in a dark house, and rule 5 makes an unauthored light a signal
     * nobody authored. `ScanUseTest` fails if this branch is removed.
     */
    override fun torch(lit: Boolean) {
        if (!use.torch) {
            log("$TAG a $use scan may not light the torch — nothing was switched")
            return
        }
        val camera = device
        if (camera == null) {
            log("$TAG no session yet, so there is no torch to switch")
            return
        }
        if (!camera.hasTorch || !camera.isTorchModeSupported(if (lit) AVCaptureTorchModeOn else AVCaptureTorchModeOff)) {
            log("$TAG '${camera.localizedName}' has no torch — nothing was switched")
            return
        }
        // The lock is not optional and its failure is not fatal. Another process holding the
        // configuration means the LED does not move; the host taps again, and the screen has
        // already told them which state it believes it is in.
        if (!camera.lockForConfiguration(null)) {
            log("$TAG the camera would not lock for configuration — torch unchanged")
            return
        }
        camera.torchMode = if (lit) AVCaptureTorchModeOn else AVCaptureTorchModeOff
        camera.unlockForConfiguration()
        torchLit = lit
        log("$TAG torch ${if (lit) "ON" else "OFF"}")
    }

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
        // **The LED goes out with the screen.** A session torn down with the torch still lit leaves
        // a phone burning a light in the host's pocket, on the battery an evening runs on — and on
        // the walk from host setup to the lobby, which is the walk to the dark.
        if (torchLit) torch(false)
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
        // **The lens comes off the configuration and is decided nowhere else.** A literal here is
        // the bug the owner found: BACK CAMERA on the screen, front camera in the session.
        val facing = positionOf(use.facing)
        val camera = AVCaptureDevice.defaultDeviceWithDeviceType(
            deviceType = AVCaptureDeviceTypeBuiltInWideAngleCamera,
            mediaType = AVMediaTypeVideo,
            position = facing,
        )
        if (camera == null) {
            log("$TAG this target has no ${use.facing} camera — every scan will be a silent no-op")
            return false
        }
        device = camera
        val input = AVCaptureDeviceInput.deviceInputWithDevice(camera, null)
        if (input == null) {
            log("$TAG the ${use.facing} camera would not open as an input")
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

        // **The picture, built only where the configuration says there is one.** `ResizeAspectFill`
        // because the viewfinder brackets on the screen mark the area the host is aiming with, and
        // a letterboxed picture inside them would put bars where the design draws the frame.
        if (use.preview) {
            preview = AVCaptureVideoPreviewLayer(session = session).apply {
                videoGravity = AVLayerVideoGravityResizeAspectFill
            }
        }

        session.commitConfiguration()
        configured = true
        log(
            "$TAG configured — ${use.facing} camera '${camera.localizedName}', use=$use, " +
                "preview=${preview != null}, torch=${camera.hasTorch}, " +
                "types=${output.metadataObjectTypes.size}, frameCounter=${frames != null}"
        )
        return true
    }

    /**
     * The configuration's lens, as AVFoundation spells it. **The only place the two vocabularies
     * meet** — a second translation somewhere else would be a second chance to get it backwards.
     */
    private fun positionOf(facing: CameraFacing): AVCaptureDevicePosition = when (facing) {
        CameraFacing.Front -> AVCaptureDevicePositionFront
        CameraFacing.Back -> AVCaptureDevicePositionBack
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
