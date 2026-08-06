package com.monai.optimizer.optimizer

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.monai.optimizer.BuildConfig
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
    private const val SU_TIMEOUT_MS = 5000L
    private const val HAS_ROOT_TIMEOUT_MS = 3000L

    private object BackupKeys {
        val DONE = booleanPreferencesKey("backup_done")
        val GOVERNOR = stringPreferencesKey("backup_governor")
        val SWAPPINESS = stringPreferencesKey("backup_swappiness")
        val DIRTY_RATIO = stringPreferencesKey("backup_dirty_ratio")
        val DIRTY_BG_RATIO = stringPreferencesKey("backup_dirty_bg_ratio")
        val VFS_CACHE_PRESSURE = stringPreferencesKey("backup_vfs_cache_pressure")
        val ANIM_SCALE = stringPreferencesKey("backup_anim_scale")
        val TRANSITION_SCALE = stringPreferencesKey("backup_transition_scale")
        val DURATION_SCALE = stringPreferencesKey("backup_duration_scale")
    }

    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    // ===== EKSEKUSI SU DENGAN TIMEOUT (Anti Hang Magisk/KernelSU) =====

    /**
     * Eksekusi perintah via `su -c` dengan timeout [timeoutMs].
     * Proses di-destroyForcibly oleh daemon thread jika melewati batas waktu,
     * sehingga TIDAK PERNAH hang selamanya.
     */
    private fun suInternal(cmd: String, timeoutMs: Long): CmdResult = try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))

        val worker = Thread {
            try {
                Thread.sleep(timeoutMs)
                if (p.isAlive) p.destroyForcibly()
            } catch (_: Exception) {}
        }
        worker.isDaemon = true
        worker.start()

        val out = p.inputStream.bufferedReader().readText().trim()
        val err = p.errorStream.bufferedReader().readText().trim()
        val rc = p.waitFor()
        if (BuildConfig.DEBUG) Log.d(T, "[$rc] $cmd")
        CmdResult(rc == 0, out.ifEmpty { err }, cmd)
    } catch (e: Exception) { CmdResult(false, e.message ?: "error", cmd) }

    fun su(cmd: String): CmdResult = suInternal(cmd, SU_TIMEOUT_MS)

    fun hasRoot(): Boolean = try {
        val r = suInternal("echo ok", HAS_ROOT_TIMEOUT_MS)
        r.success && r.output.contains("ok")
    } catch (_: Exception) { false }

    // ===== UTIL SHELL =====

    /** Escape package name agar aman di interpolasi shell (MEDIUM-14 / HIGH-8). */
    fun shellEscape(value: String): String = value.replace(Regex("[^a-zA-Z0-9._-]"), "_")

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
        val safeGov = shellEscape(gov)
        val cmd = "chmod 666 /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor 2>/dev/null; " +
                "for g in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo $safeGov > \$g; done"
        return su(cmd)
    }

    private fun readSysctl(key: String): String {
        val r = su("sysctl -n $key 2>/dev/null")
        return r.output.trim()
    }

    private fun readSettings(namespace: String, key: String): String {
        val r = su("settings get $namespace $key 2>/dev/null")
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
        val anim = readSettings("global", "window_animation_scale").ifBlank { "1.0" }
        val trans = readSettings("global", "transition_animation_scale").ifBlank { "1.0" }
        val dur = readSettings("global", "animator_duration_scale").ifBlank { "1.0" }

        ctx.kernelBackupStore.edit { prefs ->
            prefs[BackupKeys.GOVERNOR] = gov
            prefs[BackupKeys.SWAPPINESS] = swap
            prefs[BackupKeys.DIRTY_RATIO] = dirty
            prefs[BackupKeys.DIRTY_BG_RATIO] = dirtyBg
            prefs[BackupKeys.VFS_CACHE_PRESSURE] = vfs
            prefs[BackupKeys.ANIM_SCALE] = anim
            prefs[BackupKeys.TRANSITION_SCALE] = trans
            prefs[BackupKeys.DURATION_SCALE] = dur
            prefs[BackupKeys.DONE] = true
        }
        if (BuildConfig.DEBUG) Log.d(T, "Backed up original state successfully.")
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
            vfsCachePressure = prefs[BackupKeys.VFS_CACHE_PRESSURE] ?: "100",
        )
    }

    fun applySmoothRenderingTweaks(enable: Boolean, maxRefreshRate: Float = 90f): CmdResult {
        return if (enable) {
            su(
                "settings put global window_animation_scale 0.5; " +
                "settings put global transition_animation_scale 0.5; " +
                "settings put global animator_duration_scale 0.5; " +
                "settings put system peak_refresh_rate $maxRefreshRate; " +
                "settings put system min_refresh_rate $maxRefreshRate; " +
                "settings put global disable_window_blurs 1; " +
                "settings put global accessibility_reduce_transparency 1",
            )
        } else {
            su(
                "settings put global window_animation_scale 1.0; " +
                "settings put global transition_animation_scale 1.0; " +
                "settings put global animator_duration_scale 1.0; " +
                "settings delete system peak_refresh_rate; " +
                "settings delete system min_refresh_rate; " +
                "settings put global disable_window_blurs 0; " +
                "settings put global accessibility_reduce_transparency 0",
            )
        }
    }

    fun applyPerformance(maxRefreshRate: Float = 90f): List<CmdResult> {
        val avail = getGovernors()
        val targetGov = if (avail.contains("performance")) "performance" else avail.firstOrNull() ?: "schedutil"
        return listOf(
            setGovernor(targetGov),
            applySmoothRenderingTweaks(true, maxRefreshRate),
            su("cmd power set-fixed-performance-mode-enabled true 2>/dev/null || true"),
            su("sysctl -w vm.swappiness=10"),
            su("sysctl -w vm.dirty_ratio=30"),
            su("sysctl -w vm.vfs_cache_pressure=50"),
        )
    }

    fun applyBalanced(maxRefreshRate: Float = 90f): List<CmdResult> {
        val avail = getGovernors()
        val targetGov = if (avail.contains("schedutil")) "schedutil" else avail.firstOrNull() ?: "schedutil"
        return listOf(
            setGovernor(targetGov),
            applySmoothRenderingTweaks(true, maxRefreshRate),
            su("cmd power set-fixed-performance-mode-enabled false 2>/dev/null || true"),
            su("sysctl -w vm.swappiness=30"),
            su("sysctl -w vm.dirty_ratio=20"),
            su("sysctl -w vm.vfs_cache_pressure=80"),
        )
    }

    fun applyBattery(): List<CmdResult> {
        val avail = getGovernors()
        val targetGov = if (avail.contains("powersave")) "powersave" else avail.firstOrNull() ?: "schedutil"
        return listOf(
            setGovernor(targetGov),
            applySmoothRenderingTweaks(false),
            su("cmd power set-fixed-performance-mode-enabled false 2>/dev/null || true"),
            su("sysctl -w vm.swappiness=60"),
            su("sysctl -w vm.dirty_ratio=10"),
            su("dumpsys deviceidle force-idle 2>/dev/null || true"),
        )
    }

    suspend fun resetToDefaults(): List<CmdResult> {
        val ctx = appContext ?: return emptyList()
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

        val prefs = ctx.kernelBackupStore.data.first()
        val animScale = prefs[BackupKeys.ANIM_SCALE] ?: "1.0"
        val transScale = prefs[BackupKeys.TRANSITION_SCALE] ?: "1.0"
        val durScale = prefs[BackupKeys.DURATION_SCALE] ?: "1.0"

        return listOf(
            setGovernor(defaultGov),
            su("settings put global window_animation_scale $animScale"),
            su("settings put global transition_animation_scale $transScale"),
            su("settings put global animator_duration_scale $durScale"),
            su("settings delete system peak_refresh_rate; settings delete system min_refresh_rate"),
            su("cmd power set-fixed-performance-mode-enabled false 2>/dev/null || true"),
            su("sysctl -w vm.swappiness=$swappiness"),
            su("sysctl -w vm.dirty_ratio=$dirtyRatio"),
            su("sysctl -w vm.dirty_background_ratio=$dirtyBgRatio"),
            su("sysctl -w vm.vfs_cache_pressure=$vfsCachePressure"),
            su("settings delete global background_process_limit 2>/dev/null || true"),
            su("dumpsys deviceidle disable 2>/dev/null || true"),
        )
    }

    // BATCH READ: Menggabungkan beberapa bacaan jadi 1 eksekusi su (Hemat Baterai)
    fun getSystemStatsBatch(): String {
        // Format: stat|||freq|||temp|||gov
        val cmd = "cat /proc/stat 2>/dev/null | head -n 1; echo '|||'; " +
                "cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq 2>/dev/null; echo '|||'; " +
                "cat /sys/class/thermal/thermal_zone*/temp 2>/dev/null | head -n 1; echo '|||'; " +
                "cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null"
        return su(cmd).output
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

    // ===== FITUR PEMBERSIHAN (redesign anti-gimik) =====

    /**
     * HIGH-9/MEDIUM-4: `am kill-all` & `drop_caches` DIHAPUS (kontraproduktif:
     * Android me-restart app yang dibunuh + page cache justru mempercepat I/O).
     * Diganti dengan trim memory aman — sinyal ke Android untuk mengosongkan
     * memori yang TIDAK dipakai app (tanpa membunuh proses paksa).
     */
    fun trimMemorySafe(): CmdResult = su(
        "cmd package trim-caches 999G 2>/dev/null; " +
        "for pkg in \$(pm list packages -3 2>/dev/null | cut -d: -f2 | head -n 30); do " +
        "am send-trim-memory \$pkg COMPLETE 2>/dev/null; done; echo done",
    )

    /** Clear System Caches — mekanisme resmi Android (fitur NYATA, dipertahankan). */
    fun clearCaches(): CmdResult = su("cmd package trim-caches 999G 2>/dev/null; echo done")

    /** Estimasi ukuran cache (dipakai sekali saat Clear System Caches untuk menampilkan delta). */
    fun getEstimatedCacheSizeMb(): Long = try {
        su("du -sm /data/user/0/*/cache 2>/dev/null | awk '{s+=\$1} END {print s}'").output.trim().toLongOrNull() ?: 0L
    } catch (_: Exception) { 0L }

    // ===== APP MANAGEMENT (MEDIUM-5: backup & restore appops) =====

    /** Simpan status RUN_IN_BACKGROUND semua app pihak-3 — untuk restore nanti. */
    suspend fun snapshotBackgroundAppOps(): Map<String, String> = runCatching {
        val ctx = appContext ?: return emptyMap()
        val key = stringPreferencesKey("appops_snapshot")
        val r = su(
            "for pkg in \$(pm list packages -3 2>/dev/null | cut -d: -f2); do " +
            "echo \"\$pkg|RUN_IN_BACKGROUND|\$(appops get \$pkg RUN_IN_BACKGROUND 2>/dev/null | grep -oE '(allow|ignore|deny|default)' | head -n 1)\"; " +
            "echo \"\$pkg|RUN_ANY_IN_BACKGROUND|\$(appops get \$pkg RUN_ANY_IN_BACKGROUND 2>/dev/null | grep -oE '(allow|ignore|deny|default)' | head -n 1)\"; " +
            "done",
        )
        val map = r.output.lines()
            .mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size == 3) "${parts[0]}::${parts[1]}" to parts[2] else null
            }
            .toMap()
        ctx.kernelBackupStore.edit { prefs -> prefs[key] = map.entries.joinToString("\n") { "${it.key}=${it.value}" } }
        map
    }.getOrDefault(emptyMap())

    /** Restore status appops RUN_IN_BACKGROUND dari snapshot (MEDIUM-5). */
    suspend fun restoreBackgroundAppOps(): CmdResult {
        val ctx = appContext ?: return CmdResult(false, "no context", "restore-appops")
        val key = stringPreferencesKey("appops_snapshot")
        val saved = ctx.kernelBackupStore.data.first()[key].orEmpty()
        if (saved.isBlank()) return CmdResult(false, "Tidak ada snapshot appops untuk di-restore", "restore-appops")

        val cmds = saved.lines().mapNotNull { line ->
            val eq = line.indexOf('=')
            if (eq <= 0) return@mapNotNull null
            val k = line.substring(0, eq)
            val v = line.substring(eq + 1)
            val parts = k.split("::")
            if (parts.size != 2) return@mapNotNull null
            val pkg = shellEscape(parts[0])
            val op = parts[1]
            "appops set $pkg $op $v 2>/dev/null"
        }
        if (cmds.isEmpty()) return CmdResult(false, "Snapshot kosong", "restore-appops")
        return su(cmds.joinToString("; ") + "; echo done")
    }
}