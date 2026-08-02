package com.monai.optimizer.optimizer

import android.util.Log

data class CmdResult(val success: Boolean, val output: String, val cmd: String)

object RootEngine {
    private const val T = "RootEngine"

    // ── Root check ────────────────────────────────────────────────────
    fun hasRoot(): Boolean = try {
        val p   = Runtime.getRuntime().exec(arrayOf("su", "-c", "echo ok"))
        val out = p.inputStream.bufferedReader().readLine() ?: ""
        p.waitFor()
        out.trim() == "ok"
    } catch (_: Exception) { false }

    private fun su(cmd: String): CmdResult = try {
        val p   = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
        val out = p.inputStream.bufferedReader().readText().trim()
        val err = p.errorStream.bufferedReader().readText().trim()
        val rc  = p.waitFor()
        Log.d(T, "[$rc] $cmd")
        CmdResult(rc == 0, out.ifEmpty { err }, cmd)
    } catch (e: Exception) { CmdResult(false, e.message ?: "error", cmd) }

    // ── Profiles ──────────────────────────────────────────────────────

    fun applyPerformance(): List<CmdResult> = listOf(
        // CPU: schedutil governor (smart frequency scaling)
        su("for g in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo schedutil > \$g 2>/dev/null; done"),
        // VM: aggressive caching, low swap
        su("sysctl -w vm.swappiness=5"),
        su("sysctl -w vm.dirty_ratio=30"),
        su("sysctl -w vm.dirty_background_ratio=5"),
        su("sysctl -w vm.vfs_cache_pressure=50"),
        su("sysctl -w vm.overcommit_memory=1"),
        // I/O: mq-deadline for lower latency
        su("for s in /sys/block/*/queue/scheduler; do echo mq-deadline > \$s 2>/dev/null || echo deadline > \$s 2>/dev/null; done"),
        // I/O: bigger read-ahead for throughput
        su("for r in /sys/block/*/queue/read_ahead_kb; do echo 2048 > \$r 2>/dev/null; done"),
        // Kernel: reduce migration cost → less context switching overhead
        su("sysctl -w kernel.sched_migration_cost_ns=5000000"),
        su("sysctl -w kernel.sched_autogroup_enabled=1"),
    )

    fun applyBalanced(): List<CmdResult> = listOf(
        su("for g in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo schedutil > \$g 2>/dev/null; done"),
        su("sysctl -w vm.swappiness=20"),
        su("sysctl -w vm.dirty_ratio=20"),
        su("sysctl -w vm.dirty_background_ratio=5"),
        su("sysctl -w vm.vfs_cache_pressure=70"),
        su("for r in /sys/block/*/queue/read_ahead_kb; do echo 512 > \$r 2>/dev/null; done"),
    )

    fun applyBattery(): List<CmdResult> = listOf(
        // Conservative/powersave governor
        su("for g in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo conservative > \$g 2>/dev/null || echo powersave > \$g 2>/dev/null; done"),
        su("sysctl -w vm.swappiness=60"),
        su("sysctl -w vm.dirty_ratio=10"),
        su("sysctl -w vm.dirty_background_ratio=3"),
        // Enable deep doze
        su("dumpsys deviceidle enable deep 2>/dev/null || true"),
    )

    // ── CPU info ──────────────────────────────────────────────────────

    fun getGovernors(): List<String> {
        val r = su("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors 2>/dev/null")
        return r.output.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    }

    fun getCurrentGovernor(): String =
        su("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null").output.trim()

    fun setGovernor(gov: String): CmdResult =
        su("for g in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo $gov > \$g; done")

    fun getCpuFreqInfo(): String {
        val cur = su("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq 2>/dev/null")
            .output.trim().toLongOrNull()?.div(1000L) ?: 0L
        val max = su("cat /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq 2>/dev/null")
            .output.trim().toLongOrNull()?.div(1000L) ?: 0L
        return "${cur}/${max}MHz"
    }

    fun getCpuTemp(): String {
        val paths = listOf(
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/class/thermal/thermal_zone1/temp",
            "/sys/class/thermal/thermal_zone4/temp",
        )
        for (p in paths) {
            val r = su("cat $p 2>/dev/null")
            if (r.success && r.output.isNotBlank()) {
                val raw = r.output.trim().toLongOrNull() ?: continue
                return "%.1f°C".format(if (raw > 1000L) raw / 1000.0 else raw.toDouble())
            }
        }
        return "N/A"
    }

    // ── RAM & Cache ───────────────────────────────────────────────────

    fun killBgApps(): CmdResult  = su("am kill-all 2>/dev/null; echo done")
    fun dropCaches(): CmdResult  = su("sync; echo 3 > /proc/sys/vm/drop_caches")
    fun clearCaches(): CmdResult = su(
        "find /data/data -maxdepth 2 -type d -name cache -exec rm -rf {} + 2>/dev/null; echo done"
    )

    fun getZramInfo(): String {
        val used  = su("cat /sys/block/zram0/mem_used_total 2>/dev/null")
            .output.trim().toLongOrNull()?.div(1048576L) ?: 0L
        val total = su("cat /sys/block/zram0/disksize 2>/dev/null")
            .output.trim().toLongOrNull()?.div(1048576L) ?: 0L
        return if (total > 0L) "${used}/${total}MB" else "N/A"
    }

    // ── Network ───────────────────────────────────────────────────────

    fun enableBBR(): List<CmdResult> = listOf(
        su("sysctl -w net.ipv4.tcp_congestion_control=bbr 2>/dev/null || echo 'bbr not supported'"),
        su("sysctl -w net.core.default_qdisc=fq 2>/dev/null || true"),
        su("sysctl -w net.core.rmem_max=16777216"),
        su("sysctl -w net.core.wmem_max=16777216"),
    )
}
