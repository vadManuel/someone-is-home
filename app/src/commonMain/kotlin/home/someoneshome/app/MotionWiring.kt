package home.someoneshome.app

import home.someoneshome.platform.MotionSensor
import home.someoneshome.platform.MotionSink
import home.someoneshome.ui.FlowModel

/**
 * **The panel's `onMeasuring`, joined to a motion sensor** (M2, D-321).
 *
 * True starts [sensor] with the panel's own [FlowModel.motionSampled] as its sink; false stops it.
 * [panel] is read when the budget opens rather than when this is built, because the app root hands
 * this to the very `FlowModel` it reads. Hand it a [home.someoneshome.platform.PermittedMotion] and
 * the MOTION & FITNESS grant is asked at every opening.
 *
 * The sink is a `fun interface` over three doubles, made once an opening, so no sample boxes
 * anything (rule 7).
 */
fun measuringWith(sensor: MotionSensor, panel: () -> FlowModel?): (Boolean) -> Unit = { on ->
    val shown = panel()
    if (on && shown != null) sensor.start(MotionSink(shown::motionSampled)) else sensor.stop()
}
