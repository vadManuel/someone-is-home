package home.spike

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** The only symbol the Swift shell needs. */
fun spikeViewController(): UIViewController = ComposeUIViewController { SpikeApp() }
