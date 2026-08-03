package com.monai.optimizer.optimizer

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.io.File

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
            return CmdResult(false, "Governor $gov is not supported by the kernel", "setGov $gov")
        }
        val cmd = "chmod 666 /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor 2>/dev/null; " +
                  "for g in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo $gov > \$g; done"
        return su(cmd)
    }

    private fun readSysctl(key: String): String {
        val r = su("sysctl -n $key 2>/dev/null")
        return r.output.trim()
    }

    // ── Original State Backup ────────────────────────────────────────

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
        Log.d(T, "Original kernel state backed up: gov=$gov swap=$swap dirty=$dirty dirtyBg=$dirtyBg vfs=$vfs")
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

    // ── Profiles ──────────────────────────────────────────────────────

    fun applyPerformance(): List<CmdResult> {
        val avail = getGovernors()
        val targetGov = if (avail.contains("performance")) "performance" else avail.firstOrNull() ?: "schedutil"
        return listOf(
            setGovernor(targetGov),
            su("sysctl -w vm.swappiness=10"),
            su("sysctl -w vm.dirty_ratio=30"),
            su("sysctl -w vm.vfs_cache_pressure=50")
        )
    }

    fun applyBalanced(): List<CmdResult> {
        val avail = getGovernors()
        val targetGov = if (avail.contains("schedutil")) "schedutil" else avail.firstOrNull() ?: "schedutil"
        return listOf(
            setGovernor(targetGov),
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
            su("sysctl -w vm.swappiness=60"),
            su("sysctl -w vm.dirty_ratio=10"),
            su("dumpsys deviceidle force-idle 2>/dev/null || true")
        )
    }

    // ── Reset to Defaults ──────────────────────────────────────────────

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
            su("sysctl -w vm.swappiness=$swappiness"),
            su("sysctl -w vm.dirty_ratio=$dirtyRatio"),
            su("sysctl -w vm.dirty_background_ratio=$dirtyBgRatio"),
            su("sysctl -w vm.vfs_cache_pressure=$vfsCachePressure"),
            su("settings delete global window_animation_scale 2>/dev/null || true"),
            su("settings delete global transition_animation_scale 2>/dev/null || true"),
            su("settings delete global animator_duration_scale 2>/dev/null || true"),
            su("settings delete global background_process_limit 2>/dev/null || true"),
            su("dumpsys deviceidle disable 2>/dev/null || true")
        )
    }

    // ── Enhanced Info Helpers ──────────────────────────────────────────

    fun getMaxFreqForCpu(cpu: Int): Int {
        return try {
            val path = "/sys/devices/system/cpu/cpu$cpu/cpufreq/cpuinfo_max_freq"
            File(path).takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull()?.div(1000L)?.toInt() ?: 0
        } catch (_: Exception) { 0 }
    }

    fun getCpuClusters(): List<Int> {
        val clusterMap = mutableMapOf<Int, MutableList<Int>>()
        for (i in 0 until Runtime.getRuntime().availableProcessors()) {
            val maxFreq = getMaxFreqForCpu(i)
            if (maxFreq == 0) continue
            var found = false
            for ((clusterId, list) in clusterMap) {
                val existingMax = list.maxOrNull() ?: 0
                if (kotlin.math.abs(existingMax - maxFreq) < 100) {
                    list.add(maxFreq)
                    found = true
                    break
                }
            }
            if (!found) {
                clusterMap[clusterMap.size] = mutableListOf(maxFreq)
            }
        }
        return clusterMap.keys.sorted()
    }

    private fun getClusterMaxFreqs(): Map<Int, Int> {
        val clusterMap = mutableMapOf<Int, MutableList<Int>>()
        for (i in 0 until Runtime.getRuntime().availableProcessors()) {
            val maxFreq = getMaxFreqForCpu(i)
            if (maxFreq == 0) continue
            var found = false
            for ((clusterId, list) in clusterMap) {
                val existingMax = list.maxOrNull() ?: 0
                if (kotlin.math.abs(existingMax - maxFreq) < 100) {
                    list.add(maxFreq)
                    found = true
                    break
                }
            }
            if (!found) {
                clusterMap[clusterMap.size] = mutableListOf(maxFreq)
            }
        }
        return clusterMap.mapValues { it.value.maxOrNull() ?: 0 }
    }

    fun getCurrentMaxFreqInfo(): String {
        val curFreqs = (0 until Runtime.getRuntime().availableProcessors())
            .map { "/sys/devices/system/cpu/cpu$it/cpufreq/scaling_cur_freq" }
            .mapNotNull { path ->
                File(path).takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull()
            }
            .filter { it > 0 }
        val curMax = curFreqs.maxOrNull()?.div(1000L) ?: 0L
        val maxFreq = getClusterMaxFreqs().values.maxOrNull() ?: 0
        return if (maxFreq > 0) "${curMax}/${maxFreq}MHz" else "N/A"
    }

    // Override existing getCpuFreqInfo with new implementation
    fun getCpuFreqInfo(): String = getCurrentMaxFreqInfo()

    // ── Dynamic thermal throttling ────────────────────────────────────

    fun applyDynamicThermalProfile(tempC: Double): List<CmdResult> {
        val clusterMax = getClusterMaxFreqs()
        if (clusterMax.isEmpty()) return emptyList()

        val cmds = mutableListOf<String>()
        var littleRatio = 1.0
        var bigRatio = 1.0

        when {
            tempC > 45.0 -> { littleRatio = 0.4; bigRatio = 0.3 }
            tempC > 42.0 -> { littleRatio = 0.7; bigRatio = 0.5 }
            tempC > 39.0 -> { littleRatio = 0.9; bigRatio = 0.8 }
            else -> { littleRatio = 1.0; bigRatio = 1.0 }
        }

        val sortedClusters = clusterMax.entries.sortedBy { it.value }
        for ((idx, entry) in sortedClusters.withIndex()) {
            val baseFreq = entry.value
            val ratio = if (idx == 0) littleRatio else bigRatio
            val targetFreq = (baseFreq * ratio).toInt()
            for (cpu in 0 until Runtime.getRuntime().availableProcessors()) {
                val cpuMax = getMaxFreqForCpu(cpu)
                if (cpuMax == 0) continue
                if (kotlin.math.abs(cpuMax - baseFreq) < 100) {
                    val path = "/sys/devices/system/cpu/cpu$cpu/cpufreq/scaling_max_freq"
                    cmds.add("[ -f $path ] && echo ${targetFreq * 1000} > $path")
                }
            }
        }

        return if (cmds.isNotEmpty()) listOf(su(cmds.joinToString("; "))) else emptyList()
    }

    fun adaptiveMemoryTune(availRamMb: Long): CmdResult {
        val swappiness = when {
            availRamMb < 500 -> 80
            availRamMb < 1000 -> 50
            else -> 20
        }
        return su("sysctl -w vm.swappiness=$swappiness")
    }

    // ── Auto-detect thermal zone ─────────────────────────────────────

    fun findThermalZone(): String {
        val base = "/sys/class/thermal"
        val dir = File(base)
        if (dir.exists()) {
            dir.listFiles()?.forEach { zoneDir ->
                val typeFile = File(zoneDir, "type")
                if (typeFile.exists()) {
                    val type = typeFile.readText().trim().lowercase()
                    if (type.contains("cpu-thermal") || type.contains("tsens") || type.contains("bcl") || type == "cpu-0-usr") {
                        return File(zoneDir, "temp").absolutePath
                    }
                }
            }
            val fallback = File(base, "thermal_zone0/temp")
            if (fallback.exists()) return fallback.absolutePath
        }
        return "/sys/class/thermal/thermal_zone0/temp"
    }

    fun getCpuTempDynamic(): String {
        val zone = findThermalZone()
        return try {
            val raw = File(zone).readText().trim().toDoubleOrNull() ?: 0.0
            val celsius = if (raw > 1000) raw / 1000.0 else raw
            if (celsius in 10.0..95.0) String.format("%.1f°C", celsius) else "N/A"
        } catch (_: Exception) { "N/A" }
    }

    // Override existing getCpuTemp with dynamic version
    fun getCpuTemp(): String = getCpuTempDynamic()

    // ── Other info helpers ────────────────────────────────────────────

    fun getZramInfo(): String {
        val used = su("cat /sys/block/zram0/mem_used_total 2>/dev/null").output.trim().toLongOrNull()?.div(1048576L) ?: 0L
        val total = su("cat /sys/block/zram0/disksize 2>/dev/null").output.trim().toLongOrNull()?.div(1048576L) ?: 0L
        return if (total > 0L) "${used}/${total}MB" else "N/A"
    }

    fun killBgApps(): CmdResult = su("am kill-all 2>/dev/null; cmd activity kill-all 2>/dev/null; sync; echo 3 > /proc/sys/vm/drop_caches; echo done")
    fun dropCaches(): CmdResult = su("sync; echo 3 > /proc/sys/vm/drop_caches; echo done")
    fun clearCaches(): CmdResult = su("cmd package trim-caches 999G 2>/dev/null; echo done")
    fun getEstimatedCacheSizeMb(): Long = try {
        su("du -sm /data/user/0/*/cache 2>/dev/null | awk '{s+=\$1} END {print s}'").output.trim().toLongOrNull() ?: 0L
    } catch (_: Exception) { 0L }
}