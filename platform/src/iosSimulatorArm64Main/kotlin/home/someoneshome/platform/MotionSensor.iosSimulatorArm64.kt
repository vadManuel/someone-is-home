package home.someoneshome.platform

/**
 * **The simulator: a stand-in that takes no samples, every time** (D-321).
 *
 * The simulator has no device motion (Core Motion reports none), and nothing about the game is
 * signed off there; it stays the layout and wire rehearsal it is (`run-app.sh`, `rehearse.sh`,
 * `e2e.sh`). So its sensor is deterministic in the plainest way: it starts, it stops, and it
 * delivers nothing, so the motion budget holds on every card and at the station and no bench or
 * rehearsal is walked off a Subroutine by a sensor that is not there. A synthetic signal would be a
 * second thing to keep in step with the benches and would prove nothing about a hand. This file is
 * in the simulator's source set only: no build that installs on a phone contains it.
 */
actual fun deviceMotion(note: (String) -> Unit): MotionSensor = QuietMotion

private object QuietMotion : MotionSensor {
    override fun start(sink: MotionSink) {}
    override fun stop() {}
}
