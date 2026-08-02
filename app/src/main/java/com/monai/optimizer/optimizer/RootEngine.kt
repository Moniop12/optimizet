package com.monai.optimizer.optimizer

import android.util.Log

data class CmdResult(val success: Boolean, val output: String, val cmd: String)

object RootEngine {
    private const val T = "RootEngine"

    fun hasRoot(): Boolean = try {
        val p   = Runtime.getRuntime().exec(arrayOf("su", "-c", "echo ok"))
        val out = p.inputStream.bufferedReader().readLine() ?: ""
        p.waitFor()
        out.trim() == "ok"
    } catch (_: Exception) { false }

    fun su(cmd: String): CmdResult = try {
        val p   = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
        val out = p.inputStream.bufferedReader().readText().trim()
        val err = p.errorStream.bufferedReader().readText().trim()
        val rc  = p.waitFor()
        Log.d(T, "[$rc] $cmd")
        CmdResult(rc == 0, out.ifEmpty { err }, cmd)
    } catch (e: Exception) { CmdResult(false, e.message ?: "error", cmd) }

    // ── CPU Governor Controls ─────────────────────────────────────────

    fun getGovernors(): List<String> {
        val r = su("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors 2>/dev/null")
        return r.output.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    }

    fun getCurrentGovernor(): String =
        su("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null").output.trim()

    fun setGovernor(gov: String): CmdResult {
        val available = getGovernors()
        if (available.isNotEmpty() && !available.contains(gov)) {
            return CmdResult(false, "Governor $gov tidak didukung kernel device ini ($available)", "setGov $gov")
        }
        val cmd = "chmod 666 /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor 2>/dev/null; " +
                  "for g in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo $gov > \$g; done"
        return su(cmd)
    }

    // ── Smart Profiles (Disesuaikan dengan Governor yang ada) ─────────

    fun applyPerformance(): List<CmdResult> {
        val avail = getGovernors()
        val targetGov = when {
            avail.contains("performance") -> "performance"
            avail.contains("schedutil")   -> "schedutil"
            else                          -> avail.firstOrNull() ?: "schedutil"
        }

        return listOf(
            setGovernor(targetGov),
            su("sysctl -w vm.swappiness=10"),
            su("sysctl -w vm.dirty_ratio=30"),
            su("sysctl -w vm.dirty_background_ratio=5"),
            su("sysctl -w vm.vfs_cache_pressure=50"),
            su("for s in /sys/block/*/queue/scheduler; do echo mq-deadline > \$s 2>/dev/null || echo deadline > \$s 2>/dev/null; done"),
            su("for r in /sys/block/*/queue/read_ahead_kb; do echo 2048 > \$r 2>/dev/null; done")
        )
    }

    fun applyBalanced(): List<CmdResult> {
        val avail = getGovernors()
        val targetGov = when {
            avail.contains("schedutil") -> "schedutil"
            avail.contains("interactive") -> "interactive"
            else -> avail.firstOrNull() ?: "schedutil"
        }

        return listOf(
            setGovernor(targetGov),
            su("sysctl -w vm.swappiness=30"),
            su("sysctl -w vm.dirty_ratio=20"),
            su("sysctl -w vm.dirty_background_ratio=5"),
            su("sysctl -w vm.vfs_cache_pressure=80"),
            su("for r in /sys/block/*/queue/read_ahead_kb; do echo 512 > \$r 2>/dev/null; done")
        )
    }

    fun applyBattery(): List<CmdResult> {
        val avail = getGovernors()
        val targetGov = when {
            avail.contains("powersave")    -> "powersave"
            avail.contains("conservative") -> "conservative"
            avail.contains("schedutil")    -> "schedutil"
            else                           -> avail.firstOrNull() ?: "powersave"
        }

        return listOf(
            setGovernor(targetGov),
            su("sysctl -w vm.swappiness=60"),
            su("sysctl -w vm.dirty_ratio=10"),
            su("sysctl -w vm.dirty_background_ratio=3"),
            su("dumpsys deviceidle enable deep 2>/dev/null || true")
        )
    }

    // ── Live Info ─────────────────────────────────────────────────────

    fun getCpuFreqInfo(): String {
        val cur = su("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq 2>/dev/null")
            .output.trim().toLongOrNull()?.div(1000L) ?: 0L
        val max = su("cat /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq 2>/dev/null")
            .output.trim().toLongOrNull()?.div(1000L) ?: 0L
        return if (max > 0) "${cur}/${max}MHz" else "N/A"
    }

    fun getCpuTemp(): String {
        val paths = listOf(
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/class/thermal/thermal_zone1/temp",
            "/sys/class/thermal/thermal_zone4/temp",
            "/sys/class/thermal/thermal_zone5/temp"
        )
        for (p in paths) {
            val r = su("cat $p 2>/dev/null")
            if (r.success && r.output.isNotBlank()) {
                val raw = r.output.trim().toLongOrNull() ?: continue
                val c = if (raw > 1000L) raw / 1000.0 else raw.toDouble()
                if (c in 10.0..95.0) return "%.1f°C".format(c)
            }
        }
        return "N/A"
    }

    fun getZramInfo(): String {
        val used  = su("cat /sys/block/zram0/mem_used_total 2>/dev/null")
            .output.trim().toLongOrNull()?.div(1048576L) ?: 0L
        val total = su("cat /sys/block/zram0/disksize 2>/dev/null")
            .output.trim().toLongOrNull()?.div(1048576L) ?: 0L
        return if (total > 0L) "${used}/${total}MB" else "N/A"
    }

    // ── Real Cache & RAM Cleaner ──────────────────────────────────────

    fun killBgApps(): CmdResult = su(
        "am kill-all 2>/dev/null; " +
        "cmd activity kill-all 2>/dev/null; " +
        "sync; echo 3 > /proc/sys/vm/drop_caches; echo done"
    )

    fun dropCaches(): CmdResult = su("sync; echo 3 > /proc/sys/vm/drop_caches; echo done")

    fun clearCaches(): CmdResult = su(
        "pm trim-caches 999G 2>/dev/null; " +
        "cmd package trim-caches 999G 2>/dev/null; " +
        "rm -rf /data/local/tmp/* /sdcard/Android/data/*/cache /data/data/*/cache /data/user/0/*/cache 2>/dev/null; " +
        "echo done"
    )

    fun getEstimatedCacheSizeMb(): Long = try {
        val r = su("du -sm /data/user/0/*/cache /sdcard/Android/data/*/cache 2>/dev/null | awk '{s+=\$1} END {print s}'")
        r.output.trim().toLongOrNull() ?: 0L
    } catch (_: Exception) { 0L }

    fun enableBBR(): List<CmdResult> = listOf(
        su("sysctl -w net.ipv4.tcp_congestion_control=bbr 2>/dev/null || echo 'bbr unsupported'"),
        su("sysctl -w net.core.default_qdisc=fq 2>/dev/null || true"),
        su("sysctl -w net.core.rmem_max=16777216"),
        su("sysctl -w net.core.wmem_max=16777216")
    )
}