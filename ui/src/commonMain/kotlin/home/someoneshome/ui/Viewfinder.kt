package home.someoneshome.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * **The live picture from the registration camera, as far as `ui` is allowed to know.**
 *
 * ### It is a slot rather than a call, because a preview layer is `platform`'s
 *
 * `AVCaptureVideoPreviewLayer` is an AVFoundation type and `ui` may not see `platform` at all. So
 * the screen leaves a hole the shape of a picture and the app root fills it: the app can see both
 * modules, holds the registration scanner, and knows how to put a `CALayer` inside a `UIView`
 * inside a Compose tree. Every seam in this file's neighbourhood works this way — the settings
 * page, the motor, the radio — and this is the same shape with a rectangle instead of a verb.
 *
 * ### The default is nothing, and nothing is the right thing on a Mac
 *
 * A desktop render, a screenshot and every test draw the viewfinder brackets over the slate field
 * and no picture, which is what those surfaces genuinely have. **A placeholder image would be
 * worse than a hole**: the whole reason this slot exists is that the registration screen shipped
 * saying BACK CAMERA . POINT AT THE CODE with nothing behind it, and a drawn stand-in is how that
 * happens twice.
 *
 * ### One screen fills it, and it is the lit one
 *
 * Registration only. The in-round scan has **no preview and none may be added** (D-159): the lit
 * screen is the light the card is read by, and it is facing the paper. Nothing on that screen
 * reads this local.
 */
val LocalViewfinder: ProvidableCompositionLocal<@Composable (Modifier) -> Unit> =
    staticCompositionLocalOf { { } }
