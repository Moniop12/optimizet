package com.monai.optimizer.optimizer

import java.io.File

data class RamSnapshot(val totalMb: Long, val availMb: Long, val usedPct: Int)

/**
 * MonitorEngine — membaca data CPU & RAM langsung dari /proc dan sysfs.
 * Tidak perlu root maupun Shizuku — file-file ini world-readable di hampir
 * semua Android. Cocok untuk user non-root yang pakai Shizuku biasa maupun Plus.
 */
object MonitorEngine {

    // State untuk hitung CPU delta
    @Volatile private var prevIdle  = 0L
    @Volatile private var prevTotal = 0L

    // ── CPU Usage % ─────────────────────────────────────────────────
    /**
     * Membaca /proc/stat dua kali (delta) untuk mendapat CPU usage %.
     * Panggil ini setiap tick (misal 1.5 detik), hasil pertama selalu 0.
     */
    fun getCpuUsagePercent(): Int = try {
        val line = File("/proc/stat").readLines()
            .firstOrNull { it.startsWith("cpu ") } ?: return 0
        val p = line.trim().split(Regex("\\s+"))
        if (p.size < 8) return 0

        val user    = p[1].toLong()
        val nice    = p[2].toLong()
        val system  = p[3].toLong()
        val idle    = p[4].toLong()
        val iowait  = p[5].toLong()
        val irq     = p[6].toLong()
        val softirq = p[7].toLong()

        val totalIdle = idle + iowait
        val total     = user + nice + system + totalIdle + irq + softirq

        val diffIdle  = totalIdle - prevIdle
        val diffTotal = total     - prevTotal
        prevIdle  = totalIdle
        prevTotal = total

        if (diffTotal == 0L) return 0
        ((diffTotal - diffIdle) * 100L / diffTotal).toInt().coerceIn(0, 100)
    } catch (_: Exception) { 0 }

    // ── CPU Frequency ───────────────────────────────────────────────
    /** Current CPU freq di core0, dalam MHz. */
    fun getCpuFreqMhz(): Long = try {
        File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq")
            .readText().trim().toLongOrNull()?.div(1000L) ?: 0L
    } catch (_: Exception) { 0L }

    /** Max CPU freq di core0, dalam MHz. */
    fun getCpuMaxFreqMhz(): Long = try {
        File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")
            .readText().trim().toLongOrNull()?.div(1000L) ?: 0L
    } catch (_: Exception) { 0L }

    /** Format "cur/max MHz" siap display, atau "--" jika tidak tersedia. */
    fun getCpuFreqDisplay(): String {
        val cur = getCpuFreqMhz()
        val max = getCpuMaxFreqMhz()
        return if (cur > 0 && max > 0) "${cur}/${max}MHz"
               else if (cur > 0) "${cur}MHz"
               else "--"
    }

    // ── CPU Temperature ─────────────────────────────────────────────
    /**
     * Coba berbagai thermal zone node; kembalikan nilai pertama yang masuk
     * akal (10–95 °C). Kalau semua gagal, return 0f.
     */
    fun getCpuTempC(): Float {
        val zones = (0..15).map { "/sys/class/thermal/thermal_zone$it/temp" }
        for (path in zones) {
            try {
                val raw = File(path).readText().trim().toFloatOrNull() ?: continue
                val c   = if (raw > 1000f) raw / 1000f else raw
                if (c in 10f..95f) return c
            } catch (_: Exception) {}
        }
        return 0f
    }

    /** Format suhu, misal "42.3°C", atau "--" jika tidak tersedia. */
    fun getCpuTempDisplay(): String {
        val c = getCpuTempC()
        return if (c > 0f) "%.1f°C".format(c) else "--"
    }

    // ── RAM ─────────────────────────────────────────────────────────
    /**
     * Membaca /proc/meminfo (world-readable) untuk mendapat total dan
     * MemAvailable yang lebih akurat dari ActivityManager.getMemoryInfo().
     */
    fun getRamSnapshot(): RamSnapshot = try {
        val lines = File("/proc/meminfo").readLines()
        fun kbLine(prefix: String) = lines
            .find { it.startsWith(prefix) }
            ?.split(Regex("\\s+"))
            ?.getOrNull(1)?.toLongOrNull() ?: 0L

        val totalKb = kbLine("MemTotal:")
        val availKb = kbLine("MemAvailable:")
        val totalMb = totalKb / 1024L
        val availMb = availKb / 1024L
        val usedMb  = totalMb - availMb
        val pct     = if (totalMb > 0) (usedMb * 100L / totalMb).toInt() else 0
        RamSnapshot(totalMb, availMb, pct.coerceIn(0, 100))
    } catch (_: Exception) { RamSnapshot(0L, 0L, 0) }
}
