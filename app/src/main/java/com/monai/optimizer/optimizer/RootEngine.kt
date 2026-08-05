package com.monai.optimizer.optimizer

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

data class CmdResult(val success: Boolean, val output: String, val cmd: String)

private val Context.kernelBackupStore by preferencesDataStore(name = "monai_kernel_backup")

data class KernelBackupSnapshot(
    val governor: String,
    val swappiness: String,
    val dirtyRatio: String,
    val dirtyBackgroundRatio: String,
    val vfsCachePressure: String,
)

object RootEngine {
    private const val T = "RootEngine"

    private object BackupKeys {
        val DONE = booleanPreferencesKey("backup_done")
        val GOVERNOR = stringPreferencesKey("backup_governor")
        val SWAPPINESS = stringPreferencesKey("backup_swappiness")
        val DIRTY_RATIO = stringPreferencesKey("backup_dirty_ratio")
        val DIRTY_BG_RATIO = stringPreferencesKey("backup_dirty_bg_ratio")
        val VFS_CACHE_PRESSURE = stringPreferencesKey("backup_vfs_cache_pressure")
    }

    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    fun hasRoot(): Boolean = try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "echo ok"))
        val out = p.inputStream.bufferedReader().readLine() ?: ""
        p.waitFor()
        out.trim() == "ok"
    } catch (_: Exception) { false }

    fun su(cmd: String): CmdResult = try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
        val out = p.inputStream.bufferedReader().readText().trim()
        val err = p.errorStream.bufferedReader().readText().trim()
        val rc = p.waitFor()
        Log.d(T, "[$rc] $cmd")
        CmdResult(rc == 0, out.ifEmpty { err }, cmd)
    } catch (e: Exception) { CmdResult(false, e.message ?: "error", cmd) }

    fun getGovernors(): List<String> {
        val r = su("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors 2>/dev/null")
        return r.output.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    }

    fun getCurrentGovernor(): String =
        su("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null").output.trim()

    fun setGovernor(gov: String): CmdResult {
        val available = getGovernors()
        if (available.isNotEmpty() && !available.contains(gov)) {
            return CmdResult(false, "Governor $gov is not supported by kernel", "setGov $gov")
        }
        val cmd = "chmod 666 /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor 2>/dev/null; " +
                  "for g in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo $gov > \$g; done"
        return su(cmd)
    }

    private fun readSysctl(key: String): String {
        val r = su("sysctl -n $key 2>/dev/null")
        return r.output.trim()
    }

    suspend fun backupOriginalStateIfNeeded() {
        val ctx = appContext ?: return
        if (!hasRoot()) return

        val already = ctx.kernelBackupStore.data.first()[BackupKeys.DONE] ?: false
        if (already) return

        val gov = getCurrentGovernor().ifBlank { "schedutil" }
        val swap = readSysctl("vm.swappiness").ifBlank { "60" }
        val dirty = readSysctl("vm.dirty_ratio").ifBlank { "20" }
        val dirtyBg = readSysctl("vm.dirty_background_ratio").ifBlank { "10" }
        val vfs = readSysctl("vm.vfs_cache_pressure").ifBlank { "100" }

        ctx.kernelBackupStore.edit { prefs ->
            prefs[BackupKeys.GOVERNOR] = gov
            prefs[BackupKeys.SWAPPINESS] = swap
            prefs[BackupKeys.DIRTY_RATIO] = dirty
            prefs[BackupKeys.DIRTY_BG_RATIO] = dirtyBg
            prefs[BackupKeys.VFS_CACHE_PRESSURE] = vfs
            prefs[BackupKeys.DONE] = true
        }
        Log.d(T, "Backed up original state: gov=$gov swap=$swap")
    }

    suspend fun getBackupSnapshot(): KernelBackupSnapshot? {
        val ctx = appContext ?: return null
        val prefs = ctx.kernelBackupStore.data.first()
        if (prefs[BackupKeys.DONE] != true) return null
        return KernelBackupSnapshot(
            governor = prefs[BackupKeys.GOVERNOR] ?: "schedutil",
            swappiness = prefs[BackupKeys.SWAPPINESS] ?: "60",
            dirtyRatio = prefs[BackupKeys.DIRTY_RATIO] ?: "20",
            dirtyBackgroundRatio = prefs[BackupKeys.DIRTY_BG_RATIO] ?: "10",
            vfsCachePressure = prefs[BackupKeys.VFS_CACHE_PRESSURE] ?: "100"
        )
    }

    // ── Smooth UI & Dynamic System Tweaks (Hanya Command yang Real & Efektif Secara Runtime) ──

    fun applySmoothRenderingTweaks(enable: Boolean, maxRefreshRate: Float = 90f): CmdResult {
        return if (enable) {
            su(
                "settings put global window_animation_scale 0.5; " +
                "settings put global transition_animation_scale 0.5; " +
                "settings put global animator_duration_scale 0.5; " +
                "settings put system peak_refresh_rate $maxRefreshRate; " +
                "settings put system min_refresh_rate $maxRefreshRate; " +
                "settings put global disable_window_blurs 1; " +
                "settings put global accessibility_reduce_transparency 1"
            )
        } else {
            su(
                "settings put global window_animation_scale 1.0; " +
                "settings put global transition_animation_scale 1.0; " +
                "settings put global animator_duration_scale 1.0; " +
                "settings delete system peak_refresh_rate; " +
                "settings delete system min_refresh_rate; " +
                "settings put global disable_window_blurs 0; " +
                "settings put global accessibility_reduce_transparency 0"
            )
        }
    }

    fun restrictBackground(): CmdResult = su(
        "for pkg in \$(pm list packages -3 | cut -d: -f2); do " +
        "appops set \$pkg RUN_IN_BACKGROUND ignore; " +
        "appops set \$pkg RUN_ANY_IN_BACKGROUND ignore; " +
        "done; echo done"
    )

    // ── Profiles ──────────────────────────────────────────────────────

    fun applyPerformance(maxRefreshRate: Float = 90f): List<CmdResult> {
        val avail = getGovernors()
        val targetGov = if (avail.contains("performance")) "performance" else avail.firstOrNull() ?: "schedutil"
        return listOf(
            setGovernor(targetGov),
            applySmoothRenderingTweaks(true, maxRefreshRate),
            su("cmd power set-fixed-performance-mode-enabled true"),
            su("sysctl -w vm.swappiness=10"),
            su("sysctl -w vm.dirty_ratio=30"),
            su("sysctl -w vm.vfs_cache_pressure=50")
        )
    }

    fun applyBalanced(maxRefreshRate: Float = 90f): List<CmdResult> {
        val avail = getGovernors()
        val targetGov = if (avail.contains("schedutil")) "schedutil" else avail.firstOrNull() ?: "schedutil"
        return listOf(
            setGovernor(targetGov),
            applySmoothRenderingTweaks(true, maxRefreshRate),
            su("cmd power set-fixed-performance-mode-enabled false"),
            su("sysctl -w vm.swappiness=30"),
            su("sysctl -w vm.dirty_ratio=20"),
            su("sysctl -w vm.vfs_cache_pressure=80")
        )
    }

    fun applyBattery(): List<CmdResult> {
        val avail = getGovernors()
        val targetGov = if (avail.contains("powersave")) "powersave" else avail.firstOrNull() ?: "schedutil"
        return listOf(
            setGovernor(targetGov),
            applySmoothRenderingTweaks(false),
            su("cmd power set-fixed-performance-mode-enabled false"),
            su("sysctl -w vm.swappiness=60"),
            su("sysctl -w vm.dirty_ratio=10"),
            su("dumpsys deviceidle force-idle 2>/dev/null || true")
        )
    }

    suspend fun resetToDefaults(): List<CmdResult> {
        val snap = getBackupSnapshot()
        val avail = getGovernors()

        val defaultGov = when {
            snap != null && avail.contains(snap.governor) -> snap.governor
            avail.contains("schedutil") -> "schedutil"
            else -> avail.firstOrNull() ?: "interactive"
        }
        val swappiness = snap?.swappiness ?: "60"
        val dirtyRatio = snap?.dirtyRatio ?: "20"
        val dirtyBgRatio = snap?.dirtyBackgroundRatio ?: "10"
        val vfsCachePressure = snap?.vfsCachePressure ?: "100"

        return listOf(
            setGovernor(defaultGov),
            applySmoothRenderingTweaks(false),
            su("cmd power set-fixed-performance-mode-enabled false"),
            su("sysctl -w vm.swappiness=$swappiness"),
            su("sysctl -w vm.dirty_ratio=$dirtyRatio"),
            su("sysctl -w vm.dirty_background_ratio=$dirtyBgRatio"),
            su("sysctl -w vm.vfs_cache_pressure=$vfsCachePressure"),
            su("settings delete global background_process_limit 2>/dev/null || true"),
            su("dumpsys deviceidle disable 2>/dev/null || true")
        )
    }

    fun getCpuFreqInfo(): String {
        val cur = su("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq 2>/dev/null").output.trim().toLongOrNull()?.div(1000L) ?: 0L
        val max = su("cat /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq 2>/dev/null").output.trim().toLongOrNull()?.div(1000L) ?: 0L
        return if (max > 0) "${cur}/${max}MHz" else "N/A"
    }

    fun getCpuTemp(): String {
        val cmd = "for z in /sys/class/thermal/thermal_zone*/temp; do [ -f \$z ] && cat \$z 2>/dev/null && break; done"
        val r = su(cmd)
        if (r.success && r.output.isNotBlank()) {
            val raw = r.output.lines().firstOrNull()?.trim()?.toDoubleOrNull() ?: return "N/A"
            val c = if (raw > 1000.0) raw / 1000.0 else raw
            if (c in 10.0..95.0) return "%.1f°C".format(c)
        }
        return "N/A"
    }

    fun getZramInfo(): String {
        val used = su("cat /sys/block/zram0/mem_used_total 2>/dev/null").output.trim().toLongOrNull()?.div(1048576L) ?: 0L
        val total = su("cat /sys/block/zram0/disksize 2>/dev/null").output.trim().toLongOrNull()?.div(1048576L) ?: 0L
        return if (total > 0L) "${used}/${total}MB" else "N/A"
    }

    fun adaptiveMemoryTune(availRamMb: Long): CmdResult {
        val swappiness = when {
            availRamMb < 600 -> 70
            availRamMb < 1200 -> 40
            else -> 20
        }
        return su("sysctl -w vm.swappiness=$swappiness")
    }

    fun killBgApps(): CmdResult = su("am kill-all 2>/dev/null; cmd activity kill-all 2>/dev/null; sync; echo 3 > /proc/sys/vm/drop_caches; echo done")
    fun dropCaches(): CmdResult = su("sync; echo 3 > /proc/sys/vm/drop_caches; echo done")
    fun clearCaches(): CmdResult = su("cmd package trim-caches 999G 2>/dev/null; echo done")
    fun getEstimatedCacheSizeMb(): Long = try {
        su("du -sm /data/user/0/*/cache 2>/dev/null | awk '{s+=\$1} END {print s}'").output.trim().toLongOrNull() ?: 0L
    } catch (_: Exception) { 0L }
}