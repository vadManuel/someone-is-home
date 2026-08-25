package home.someoneshome.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import home.someoneshome.platform.CameraCardScanner
import home.someoneshome.platform.CardScanner
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.CoreGraphics.CGRectMake
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.UIView

/**
 * **The registration camera's picture, inside a `UIView`, inside the Compose tree.**
 *
 * ### Only a camera has one, and asking a deck for a picture answers nothing
 *
 * The type this is handed is the common [CardScanner], which the playtest deck also implements. A
 * deck has no lens and therefore no picture, so what comes back for one is a composable that draws
 * nothing — the same honest hole a Mac gets. **No placeholder**: `LocalViewfinder`'s KDoc argues
 * that one at length, and the bug it argues from is this screen shipping with a camera label over
 * an empty rectangle.
 *
 * ### The layer arrives after the view does, and that is why `update` exists
 *
 * A capture session is configured off the main thread and takes tens to hundreds of milliseconds,
 * so the composable is on screen before `previewLayer` is anything but null. The factory therefore
 * makes an empty view and every recomposition offers it the layer again; the first offer that is
 * not null sticks. A view built once and never revisited would be a black rectangle for the whole
 * of host setup on a phone whose camera came up perfectly.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun viewfinderFor(scanner: CardScanner): @Composable (Modifier) -> Unit {
    val camera = scanner as? CameraCardScanner ?: return { }
    return { modifier ->
        UIKitView(
            factory = { PreviewHost() },
            modifier = modifier,
            update = { host -> host.show(camera.previewLayer) },
        )
    }
}

/**
 * A view whose only job is to hold one `AVCaptureVideoPreviewLayer` at its own size.
 *
 * ### The frame is set in `layoutSubviews` because a `CALayer` does not autoresize
 *
 * A sublayer keeps whatever frame it was given; the standard fix is a view subclass whose
 * `layerClass` *is* the preview layer, which Kotlin/Native cannot express — a class method cannot
 * be overridden through the Objective-C interop. So the layer is a sublayer and this follows the
 * bounds by hand, which is the same result by the other road.
 *
 * **The frame change is wrapped in a `CATransaction` with actions disabled.** Core Animation
 * implicitly animates a layer's frame over a quarter of a second, and an implicit animation is
 * exactly the fade nobody authored that rule 5 forbids — here it would be the viewfinder sliding
 * into place behind brackets that are already still.
 */
@OptIn(ExperimentalForeignApi::class)
private class PreviewHost : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {

    private var shown: AVCaptureVideoPreviewLayer? = null

    /** Take the layer if there is one and it is not already here. Idempotent by design. */
    fun show(preview: AVCaptureVideoPreviewLayer?) {
        if (preview == null || preview == shown) return
        shown = preview
        layer.addSublayer(preview)
        setNeedsLayout()
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        val preview = shown ?: return
        CATransaction.begin()
        CATransaction.setValue(true, forKey = kCATransactionDisableActions)
        bounds.useContents {
            preview.setFrame(CGRectMake(origin.x, origin.y, size.width, size.height))
        }
        CATransaction.commit()
    }
}
