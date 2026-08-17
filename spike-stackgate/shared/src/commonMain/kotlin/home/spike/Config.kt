package home.spike

/**
 * Trigger source kind. The gate measures *trigger -> pixels dark*, and the rendering pipeline
 * does not care what produced the trigger, so this is deliberately pluggable. A real BLE
 * contact handshake needs two devices and drags radio risk into a rendering test — that is
 * story 0.6c, kept apart on purpose.
 */
enum class TriggerKind { TAP, SCRIPTED }

/**
 * How much garbage other threads generate while the blackout fires.
 *
 * This exists because a GC pause only happens when there is garbage to collect. A minimal
 * spike — one amber screen, a tap, a timestamp — never triggers a collection inside the test
 * window and returns a clean result you would wrongly read as "Kotlin/Native GC is a
 * non-issue". OFF is the control, not the default.
 *
 * **Scales were raised 40x after run 2 measured them useless.** The original numbers assumed
 * the app's own allocation was near zero. It is not: Compose alone allocates ~46 KB/s, and the
 * first REPRESENTATIVE run added only 14% on top of that, so pressure ON and pressure OFF were
 * statistically identical and the GC half of the gate went untested.
 *
 * **The label is a guess; the measured MB/s in the report is the fact.** Nobody knows the real
 * app's allocation rate yet, so the useful experiment is not "is REPRESENTATIVE right" but
 * "does the pause stay inside a frame as allocation scales by orders of magnitude".
 */
enum class PressureLevel(val scale: Double) {
    OFF(0.0),
    LIGHT(1.0),
    REPRESENTATIVE(10.0),
    HEAVY(50.0),
    CRUSH(250.0),
}

/**
 * Spacing between trials.
 *
 * TIGHT keeps Compose's redrawer permanently warm, which is not the condition the gate is
 * about — in the real game the lamp sits static in amber for minutes and then must blank.
 * LONG exists to catch the cost of the renderer having gone idle.
 */
enum class IdleProfile(val minMillis: Int, val maxMillis: Int) {
    TIGHT(80, 120),
    REALISTIC(500, 2_000),
    LONG(3_000, 10_000),
}

data class RunConfig(
    val label: String,
    val trigger: TriggerKind,
    val trials: Int,
    val pressure: PressureLevel,
    val idle: IdleProfile,
    /** Trials fired before measurement starts. Logged, but excluded from the headline stats. */
    val prewarmTrials: Int,
    /** Frames the lamp is held black before restoring amber. */
    val holdFrames: Int,
    /** Corner patch that toggles every drawn frame, so app frames are countable on video. */
    val framePatch: Boolean,
    val note: String,
) {
    companion object {
        /** THE GATE. The tail, under representative load. */
        val VOLUME = RunConfig(
            label = "VOLUME",
            trigger = TriggerKind.SCRIPTED,
            trials = 10_000,
            pressure = PressureLevel.REPRESENTATIVE,
            idle = IdleProfile.TIGHT,
            prewarmTrials = 50,
            holdFrames = 3,
            framePatch = false,
            note = "The gate. Read the tail, never the mean.",
        )

        /**
         * The control. If this is indistinguishable from VOLUME, the pressure generator is not
         * doing its job and the GC half of the gate has not actually been tested.
         */
        val VOLUME_CONTROL = VOLUME.copy(
            label = "VOLUME_CONTROL",
            pressure = PressureLevel.OFF,
            note = "Control. Must differ from VOLUME, or the pressure generator is a no-op.",
        )

        val VOLUME_HEAVY = VOLUME.copy(
            label = "VOLUME_HEAVY",
            pressure = PressureLevel.HEAVY,
            note = "Headroom probe. 5x REPRESENTATIVE.",
        )

        /**
         * Bounds the GC question instead of estimating it. If the pause still fits inside a
         * frame at an allocation rate far above anything the real app could plausibly reach,
         * the answer holds whatever that rate turns out to be. Fewer trials because this run
         * is about collections, not about the latency tail.
         */
        val VOLUME_CRUSH = VOLUME.copy(
            label = "VOLUME_CRUSH",
            trials = 5_000,
            pressure = PressureLevel.CRUSH,
            note = "Bounds the GC half: absurd allocation, does the pause still fit a frame?",
        )

        /** 1.7a: the first-run shader stall, with the pre-warm mitigation switched off. */
        val COLD = RunConfig(
            label = "COLD",
            trigger = TriggerKind.SCRIPTED,
            trials = 200,
            pressure = PressureLevel.REPRESENTATIVE,
            idle = IdleProfile.TIGHT,
            prewarmTrials = 0,
            holdFrames = 3,
            framePatch = false,
            note = "No pre-warm. Trial 1 is the shader stall; quantifies what pre-warming buys.",
        )

        val LONG_IDLE = RunConfig(
            label = "LONG_IDLE",
            trigger = TriggerKind.SCRIPTED,
            trials = 200,
            pressure = PressureLevel.REPRESENTATIVE,
            idle = IdleProfile.LONG,
            prewarmTrials = 0,
            holdFrames = 3,
            framePatch = false,
            note = "Renderer allowed to go idle between trials — the real game's condition.",
        )

        /** Calibration. ~30 samples proving the in-app number corresponds to photons. */
        val CAMERA = RunConfig(
            label = "CAMERA",
            trigger = TriggerKind.TAP,
            trials = 30,
            pressure = PressureLevel.REPRESENTATIVE,
            idle = IdleProfile.REALISTIC,
            prewarmTrials = 0,
            holdFrames = 60,
            framePatch = true,
            note = "Tap trigger, 240 fps video. Validates the instrument; cannot find the tail.",
        )

        /** 1.7b: bytes allocated per blackout. Sets the permanent assertion's threshold. */
        val ALLOC_PROBE = RunConfig(
            label = "ALLOC_PROBE",
            trigger = TriggerKind.SCRIPTED,
            trials = 20_000,
            pressure = PressureLevel.OFF,
            idle = IdleProfile.TIGHT,
            prewarmTrials = 200,
            holdFrames = 1,
            framePatch = false,
            note = "Allocation accounting only. Pressure OFF so the heap delta is attributable.",
        )

        val all = listOf(VOLUME, VOLUME_CONTROL, VOLUME_CRUSH, VOLUME_HEAVY, COLD, LONG_IDLE, CAMERA, ALLOC_PROBE)
    }
}
