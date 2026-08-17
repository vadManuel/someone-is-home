package home.spike

/**
 * Report generation. Allocates freely — it runs after the last trial, never during one.
 */
object Report {

    class Summary(
        val label: String,
        val note: String,
        val device: String,
        val n: Int,
        val nPrewarm: Int,
        val pressure: PressureLevel,
        val pressureMbPerSec: Double,
        val idle: IdleProfile,
        val triggerName: String,
        val nominalIntervalNanos: Long,
        val measuredIntervalNanos: Long,
        val drawLatency: Percentiles,
        val presentLatency: Percentiles,
        val spanHistogram: IntArray,
        val spanWorst: Int,
        val trialsWithGc: Int,
        val trialsWithStall: Int,
        val gcEvents: Int,
        val gcPauseNanos: Percentiles,
        val gcAllocatedBytes: Long,
        val mainThreadStalls: Int,
        val worstStallNanos: Long,
        val runSeconds: Double,
        val coldFirstTrialNanos: Long,
        val allocBytesPerTrial: Double,
    )

    class Percentiles(val n: Int, sortedNanos: LongArray) {
        val p50 = pick(sortedNanos, 0.50)
        val p90 = pick(sortedNanos, 0.90)
        val p99 = pick(sortedNanos, 0.99)
        val p999 = pick(sortedNanos, 0.999)
        val max = if (sortedNanos.isEmpty()) 0L else sortedNanos[sortedNanos.size - 1]
        val mean = if (sortedNanos.isEmpty()) 0L else sortedNanos.sum() / sortedNanos.size

        private companion object {
            fun pick(sorted: LongArray, q: Double): Long {
                if (sorted.isEmpty()) return 0L
                val idx = ((sorted.size - 1) * q).toInt().coerceIn(0, sorted.size - 1)
                return sorted[idx]
            }
        }
    }

    fun summarise(): Summary {
        val cfg = GateEngine.config
        val t = GateEngine.trials
        val interval = Vsync.measuredIntervalNanos().takeIf { it > 0 } ?: Vsync.nominalIntervalNanos

        val measured = ArrayList<Int>(t.count)
        var nPrewarm = 0
        for (i in 0 until t.count) {
            if (t.isPrewarm(i)) nPrewarm++ else measured.add(i)
        }

        val draw = LongArray(measured.size) { t.drawLatencyNanos(measured[it]) }
        val present = LongArray(measured.size) { t.presentLatencyNanos(measured[it]) }
        draw.sort()
        present.sort()

        // Span 0..7, with the last bucket meaning "8 or more".
        val spans = IntArray(8)
        var worst = 0
        var withGc = 0
        var withStall = 0
        for (i in measured) {
            val s = t.span(i, interval).coerceIn(0, 7)
            spans[s]++
            if (s > worst) worst = s
            if (t.flags[i] and FLAG_GC_DURING != 0) withGc++
            if (t.flags[i] and FLAG_STALL_DURING != 0) withStall++
        }

        val gcCount = GcProbe.eventCount()
        // Stop-the-world time, not wall-clock collection time. The concurrent stretch between
        // the two pauses does not freeze the frame, so counting it would overstate the risk —
        // and overstating it is how you end up rejecting the stack for the wrong reason.
        val gcPauses = LongArray(gcCount) { GcProbe.eventStwNanos(it) }
        gcPauses.sort()

        var worstStall = 0L
        for (i in 0 until Vsync.stallCount()) {
            val g = Vsync.stallGapNanos(i)
            if (g > worstStall) worstStall = g
        }

        val runSeconds = (GateEngine.endedAtNanos - GateEngine.startedAtNanos) / 1e9
        val coldFirst = if (t.count > 0) t.drawLatencyNanos(0) else 0L
        val allocBytes = GcProbe.allocatedBytesAcrossEpochs()
        val perTrial = if (t.count > 0) allocBytes.toDouble() / t.count else 0.0

        return Summary(
            label = cfg.label,
            note = cfg.note,
            device = deviceDescription(),
            n = measured.size,
            nPrewarm = nPrewarm,
            pressure = cfg.pressure,
            pressureMbPerSec = Pressure.estimatedMegabytesPerSecond(),
            idle = cfg.idle,
            triggerName = cfg.trigger.name.lowercase(),
            nominalIntervalNanos = Vsync.nominalIntervalNanos,
            measuredIntervalNanos = interval,
            drawLatency = Percentiles(draw.size, draw),
            presentLatency = Percentiles(present.size, present),
            spanHistogram = spans,
            spanWorst = worst,
            trialsWithGc = withGc,
            trialsWithStall = withStall,
            gcEvents = gcCount,
            gcPauseNanos = Percentiles(gcPauses.size, gcPauses),
            gcAllocatedBytes = allocBytes,
            mainThreadStalls = Vsync.stallCount(),
            worstStallNanos = worstStall,
            runSeconds = runSeconds,
            coldFirstTrialNanos = coldFirst,
            allocBytesPerTrial = perTrial,
        )
    }

    fun ms(nanos: Long): String {
        val v = nanos / 1_000_000.0
        val scaled = ((v * 1000).toLong()).toString()
        val padded = scaled.padStart(4, '0')
        return padded.dropLast(3) + "." + padded.takeLast(3)
    }

    /** The on-screen verdict. Span is the metric; latency is the sanity check. */
    fun verdict(s: Summary): String {
        val onTime = s.spanHistogram.getOrElse(1) { 0 } + s.spanHistogram.getOrElse(0) { 0 }
        val late = s.n - onTime
        return when {
            s.n == 0 -> "NO DATA"
            late == 0 -> "PASS — every trial drew black on the next frame (n=${s.n})"
            late * 1000 <= s.n -> "MARGINAL — $late/${s.n} missed a frame (<= 0.1%)"
            else -> "FAIL — $late/${s.n} missed a frame, worst span ${s.spanWorst}"
        }
    }

    fun text(s: Summary): String = buildString {
        appendLine("SOMEONE'S HOME — stack gate spike (story 1.7)")
        appendLine(verdict(s))
        appendLine()
        appendLine("run              ${s.label}   ${s.note}")
        appendLine("device           ${s.device}")
        appendLine("trigger          ${s.triggerName}, idle ${s.idle} (${s.idle.minMillis}-${s.idle.maxMillis}ms)")
        appendLine("trials           ${s.n} measured, ${s.nPrewarm} pre-warm excluded, ${fmt1(s.runSeconds)}s")
        appendLine("pressure         ${s.pressure} (~${fmt1(s.pressureMbPerSec)} MB/s est.)")
        appendLine("frame interval   nominal ${ms(s.nominalIntervalNanos)}ms, measured ${ms(s.measuredIntervalNanos)}ms")
        appendLine()
        appendLine("SPAN — vsync boundaries between trigger and the frame that drew black")
        appendLine("  1 is the passing shape. 2+ means the renderer missed a frame.")
        for (i in s.spanHistogram.indices) {
            val c = s.spanHistogram[i]
            if (c == 0) continue
            val pct = if (s.n > 0) c * 100.0 / s.n else 0.0
            val tag = if (i >= 7) "7+" else i.toString()
            appendLine("  span $tag".padEnd(12) + "$c".padStart(7) + "   " + fmt2(pct) + "%")
        }
        appendLine()
        appendLine("LATENCY trigger -> frame that drew black (ms)")
        appendLine(percLine(s.drawLatency))
        appendLine("LATENCY trigger -> first vsync after that frame (ms, inferred)")
        appendLine(percLine(s.presentLatency))
        appendLine()
        appendLine("GC")
        appendLine("  collections    ${s.gcEvents}")
        appendLine("  stop-the-world p50/p99/max  ${ms(s.gcPauseNanos.p50)} / ${ms(s.gcPauseNanos.p99)} / ${ms(s.gcPauseNanos.max)} ms")
        appendLine("  trials overlapping a collection  ${s.trialsWithGc}")
        appendLine("  allocated across epochs          ${s.gcAllocatedBytes / 1024} KB")
        appendLine("  per trial                        ${fmt1(s.allocBytesPerTrial)} bytes")
        if (s.pressure != PressureLevel.OFF) {
            // Attributing this to the blackout path would be wrong by orders of magnitude —
            // most of it is the pressure generator, by design.
            appendLine("  (per-trial figure includes pressure allocation — use ALLOC_PROBE)")
        } else if (s.gcEvents < 2) {
            appendLine("  (fewer than 2 epochs — allocation figure is NOT measured, ignore it)")
        }
        appendLine()
        appendLine("MAIN-THREAD STALLS (display-link gaps > 1.5x nominal)")
        appendLine("  count ${s.mainThreadStalls}, worst ${ms(s.worstStallNanos)} ms")
        appendLine("  trials overlapping a stall  ${s.trialsWithStall}")
        appendLine()
        appendLine("first trial (cold) ${ms(s.coldFirstTrialNanos)} ms")
    }

    private fun percLine(p: Percentiles): String =
        "  p50 ${ms(p.p50)}  p90 ${ms(p.p90)}  p99 ${ms(p.p99)}  p99.9 ${ms(p.p999)}  max ${ms(p.max)}  mean ${ms(p.mean)}"

    private fun fmt1(v: Double): String {
        val x = (v * 10).toLong()
        return "${x / 10}.${(if (x < 0) -x else x) % 10}"
    }

    private fun fmt2(v: Double): String {
        val x = (v * 100).toLong()
        return "${x / 100}.${((if (x < 0) -x else x) % 100).toString().padStart(2, '0')}"
    }

    /** Every trial, so the tail can be re-derived off-device without trusting this file. */
    fun csv(): String {
        val t = GateEngine.trials
        val interval = Vsync.measuredIntervalNanos().takeIf { it > 0 } ?: Vsync.nominalIntervalNanos
        val sb = StringBuilder(t.count * 96 + 256)
        sb.appendLine(
            "i,prewarm,trigger_ns,draw_ns,present_ns,vsync_at_trigger_ns,vsync_at_draw_ns," +
                "tick_at_trigger,tick_at_draw,gc_epoch,flags,draw_latency_ns,present_latency_ns,span"
        )
        for (i in 0 until t.count) {
            sb.append(i).append(',')
                .append(if (t.isPrewarm(i)) 1 else 0).append(',')
                .append(t.triggerNanos[i]).append(',')
                .append(t.drawNanos[i]).append(',')
                .append(t.presentNanos[i]).append(',')
                .append(t.vsyncAtTriggerNanos[i]).append(',')
                .append(t.vsyncAtDrawNanos[i]).append(',')
                .append(t.tickAtTrigger[i]).append(',')
                .append(t.tickAtDraw[i]).append(',')
                .append(t.gcEpoch[i]).append(',')
                .append(t.flags[i]).append(',')
                .append(t.drawLatencyNanos(i)).append(',')
                .append(t.presentLatencyNanos(i)).append(',')
                .append(t.span(i, interval))
                .appendLine()
        }
        return sb.toString()
    }

    fun gcCsv(): String {
        val sb = StringBuilder(GcProbe.eventCount() * 64 + 128)
        sb.appendLine("epoch,window_start_ns,window_end_ns,stw_ns,heap_before_bytes,heap_after_bytes")
        for (i in 0 until GcProbe.eventCount()) {
            sb.append(GcProbe.eventEpoch(i)).append(',')
                .append(GcProbe.eventPauseStartNanos(i)).append(',')
                .append(GcProbe.eventPauseEndNanos(i)).append(',')
                .append(GcProbe.eventStwNanos(i)).append(',')
                .append(GcProbe.eventHeapBeforeBytes(i)).append(',')
                .append(GcProbe.eventHeapAfterBytes(i))
                .appendLine()
        }
        return sb.toString()
    }

    fun stallCsv(): String {
        val sb = StringBuilder(Vsync.stallCount() * 48 + 128)
        sb.appendLine("tick_index,at_ns,gap_ns")
        for (i in 0 until Vsync.stallCount()) {
            sb.append(Vsync.stallTickIndex(i)).append(',')
                .append(Vsync.stallAtNanos(i)).append(',')
                .append(Vsync.stallGapNanos(i))
                .appendLine()
        }
        return sb.toString()
    }
}
