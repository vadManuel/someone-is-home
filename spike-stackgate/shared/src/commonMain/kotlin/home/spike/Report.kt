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
        val measuredAllocMbPerSec: Double,
        val idle: IdleProfile,
        val triggerName: String,
        val nominalIntervalNanos: Long,
        val measuredIntervalNanos: Long,
        val drawLatency: Percentiles,
        val presentLatency: Percentiles,
        val spanHistogram: IntArray,
        val spanWorst: Int,
        val spanImpossible: Int,
        val lateDraws: Int,
        val latePresents: Int,
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
        // A negative span is impossible and means the instrument is wrong, not that the frame
        // was early. Counted separately rather than clamped into bucket 0, because bucket 0
        // is a PASS — clamping would turn a broken clock into a green result.
        var impossible = 0
        for (i in measured) {
            val raw = t.span(i, interval)
            if (raw < 0) {
                impossible++
                continue
            }
            val s = if (raw > 7) 7 else raw
            spans[s]++
            if (raw > worst) worst = raw
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

        // A clean trial waits at most one frame interval for the next vsync, then draws a
        // short render offset into that frame. Anything beyond interval x 1.15 has spent
        // longer than one whole frame getting to the surface, which is a missed frame however
        // the vsync bookkeeping labels it.
        val lateThreshold = (interval * 115) / 100
        var lateDraws = 0
        var latePresents = 0
        for (i in measured) {
            if (t.drawLatencyNanos(i) > lateThreshold) lateDraws++
            if (t.presentLatencyNanos(i) > interval + lateThreshold) latePresents++
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
            measuredAllocMbPerSec =
                if (runSeconds > 0) allocBytes / runSeconds / (1024.0 * 1024.0) else 0.0,
            idle = cfg.idle,
            triggerName = cfg.trigger.name.lowercase(),
            nominalIntervalNanos = Vsync.nominalIntervalNanos,
            measuredIntervalNanos = interval,
            drawLatency = Percentiles(draw.size, draw),
            presentLatency = Percentiles(present.size, present),
            spanHistogram = spans,
            spanWorst = worst,
            spanImpossible = impossible,
            lateDraws = lateDraws,
            latePresents = latePresents,
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

    /**
     * The verdict, decided by LATENCY rather than span.
     *
     * Span was the original headline and it is blind. In VOLUME_CRUSH it reported span 0 for
     * every single one of the 39 trials whose draw provably overran a frame interval — because
     * it is dominated by whether our CADisplayLink callback happens to run before or after
     * Compose's within a vsync, not by how long the frame actually took. Latency is measured
     * against the monotonic clock and does not care about callback ordering.
     */
    fun verdict(s: Summary): String {
        val late = s.lateDraws
        return when {
            s.n == 0 -> "NO DATA"
            // Checked before any pass case: a broken instrument must never report a pass.
            s.spanImpossible > 0 ->
                "INVALID — ${s.spanImpossible}/${s.n} trials have an impossible span"
            late == 0 && s.latePresents == 0 ->
                "PASS — no trial overran a frame (n=${s.n})"
            late == 0 ->
                "MARGINAL — draws were on time, but ${s.latePresents}/${s.n} presented late"
            late * 1000 <= s.n ->
                "MARGINAL — $late/${s.n} draws overran a frame (<= 0.1%)"
            else -> {
                val pct = fmt2(late * 100.0 / s.n)
                "FAIL — $late/${s.n} draws overran a frame ($pct%), worst ${ms(s.drawLatency.max)}ms"
            }
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
        appendLine("pressure         ${s.pressure} — MEASURED ${fmt2(s.measuredAllocMbPerSec)} MB/s allocated")
        appendLine("                 (the level name is a guess; this measured rate is the fact.")
        appendLine("                  Compose alone allocates ~0.04 MB/s, so compare against that,")
        appendLine("                  not against zero — pressure that does not clear the app's own")
        appendLine("                  baseline leaves the GC half of the gate untested.)")
        appendLine("frame interval   nominal ${ms(s.nominalIntervalNanos)}ms, measured ${ms(s.measuredIntervalNanos)}ms")
        val ratio = if (s.nominalIntervalNanos > 0) s.measuredIntervalNanos.toDouble() / s.nominalIntervalNanos else 1.0
        if (ratio > 1.25 || ratio < 0.8) {
            appendLine("  *** MEASURED INTERVAL DISAGREES WITH THE PANEL'S RATED RATE ***")
            appendLine("  Span and stall figures below are derived from this timeline. If it is")
            appendLine("  wrong, they are wrong, and the latency figures are the only valid ones.")
        }
        appendLine()
        appendLine("LATE FRAMES — the verdict is decided here")
        appendLine("  draws over one frame      ${s.lateDraws} / ${s.n}")
        appendLine("  presentations over one    ${s.latePresents} / ${s.n}")
        appendLine("  Cross-reference 'trials overlapping a collection' below. If the late")
        appendLine("  trials are the GC-overlapping ones, the collector is the cause.")
        appendLine()
        appendLine("SPAN — UNRELIABLE, kept only for continuity with earlier runs")
        appendLine("  It reads 0 for late frames too: it is dominated by whether our display")
        appendLine("  link ticks before or after Compose's, not by how long the frame took.")
        appendLine("  1 is the passing shape. 2+ means the renderer missed a frame.")
        appendLine("  0 also passes: our display link and Compose's may fire in either order")
        appendLine("  within one vsync, so 0 vs 1 is instrument noise. 2+ is not.")
        for (i in s.spanHistogram.indices) {
            val c = s.spanHistogram[i]
            if (c == 0) continue
            val pct = if (s.n > 0) c * 100.0 / s.n else 0.0
            val tag = if (i >= 7) "7+" else i.toString()
            appendLine("  span $tag".padEnd(12) + "$c".padStart(7) + "   " + fmt2(pct) + "%")
        }
        if (s.spanImpossible > 0) {
            appendLine("  IMPOSSIBLE  ${s.spanImpossible}  <- negative span; the clock is wrong, not the renderer")
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
        if (Vsync.stallsDroppedCount() > 0) {
            appendLine("  BUFFER FULL — ${Vsync.stallsDroppedCount()} more not recorded; count is a floor")
        }
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
