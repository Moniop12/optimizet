package com.monai.optimizer.optimizer

import java.io.File

data class RamSnapshot(val totalMb: Long, val availMb: Long, val usedPct: Int)

/**
 * MonitorEngine — Membaca data CPU Usage % & Memory.
 * Dilengkapi fallback ke Shell (Shizuku/Root) jika SELinux memblokir pembacaan /proc/stat.
 */
object MonitorEngine {

    @Volatile private var prevIdle  = 0L
    @Volatile private var prevTotal = 0L

    fun getCpuUsagePercent(rawProcStatOverride: String? = null): Int {
        return try {
            val line = if (!rawProcStatOverride.isNullOrBlank()) {
                rawProcStatOverride.lines().firstOrNull { it.startsWith("cpu ") }
            } else {
                runCatching {
                    File("/proc/stat").readLines().firstOrNull { it.startsWith("cpu ") }
                }.getOrNull()
            } ?: return 0

            val p = line.trim().split(Regex("\\s+"))
            if (p.size < 8) return 0

            val user     = p[1].toLong()
            val nice     = p[2].toLong()
            val system   = p[3].toLong()
            val idle     = p[4].toLong()
            val iowait   = p[5].toLong()
            val irq      = p[6].toLong()
            val softirq  = p[7].toLong()

            val totalIdle = idle + iowait
            val total     = user + nice + system + totalIdle + irq + softirq

            val diffIdle  = totalIdle - prevIdle
            val diffTotal = total     - prevTotal
            prevIdle  = totalIdle
            prevTotal = total

            if (diffTotal == 0L) return 0
            ((diffTotal - diffIdle) * 100L / diffTotal).toInt().coerceIn(0, 100)
        } catch (_: Exception) { 0 }
    }

    fun getCpuFreqMhz(): Long {
        return try {
            File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq")
                .readText().trim().toLongOrNull()?.div(1000L) ?: 0L
        } catch (_: Exception) { 0L }
    }

    fun getCpuMaxFreqMhz(): Long {
        return try {
            File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")
                .readText().trim().toLongOrNull()?.div(1000L) ?: 0L
        } catch (_: Exception) { 0L }
    }

    fun getCpuFreqDisplay(): String {
        val cur = getCpuFreqMhz()
        val max = getCpuMaxFreqMhz()
        return when {
            cur > 0 && max > 0 -> "${cur}/${max}MHz"
            cur > 0            -> "${cur}MHz"
            else               -> "--"
        }
    }

    fun getCpuTempC(): Float {
        for (i in 0..15) {
            try {
                val raw = File("/sys/class/thermal/thermal_zone$i/temp")
                    .readText().trim().toFloatOrNull() ?: continue
                val c = if (raw > 1000f) raw / 1000f else raw
                if (c in 10f..95f) return c
            } catch (_: Exception) {}
        }
        return 0f
    }

    fun getCpuTempDisplay(): String {
        val c = getCpuTempC()
        return if (c > 0f) "%.1f°C".format(c) else "--"
    }

    fun getRamSnapshot(): RamSnapshot {
        return try {
            val lines = File("/proc/meminfo").readLines()
            fun kbOf(prefix: String): Long = lines
                .find { it.startsWith(prefix) }
                ?.split(Regex("\\s+"))?.getOrNull(1)?.toLongOrNull() ?: 0L

            val totalMb = kbOf("MemTotal:")     / 1024L
            val availMb = kbOf("MemAvailable:") / 1024L
            val usedMb  = totalMb - availMb
            val pct     = if (totalMb > 0) (usedMb * 100L / totalMb).toInt() else 0
            RamSnapshot(totalMb, availMb, pct.coerceIn(0, 100))
        } catch (_: Exception) { RamSnapshot(0L, 0L, 0) }
    }
}