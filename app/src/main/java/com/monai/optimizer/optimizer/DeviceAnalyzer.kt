package com.monai.optimizer.optimizer

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.io.File
import kotlin.math.roundToInt

enum class OptProfile { PERFORMANCE, BALANCED, BATTERY }

data class DeviceSpec(
    val brand        : String,
    val model        : String,
    val android      : String,
    val api          : Int,
    val cores        : Int,
    val totalRamMb   : Long,
    val physicalRamGb: Int,
    val availRamMb   : Long,
    val chipset      : String,
    val maxFreqMhz   : Int,
    val hasRoot      : Boolean,
    val hasShizuku   : Boolean,
    val recommended  : OptProfile,
)

object DeviceAnalyzer {

    fun analyze(ctx: Context, hasRoot: Boolean, hasShizuku: Boolean): DeviceSpec {
        val am  = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val usableRamMb = mem.totalMem / (1024L * 1024L)
        val availRamMb  = mem.availMem / (1024L * 1024L)
        val physicalGb  = calculatePhysicalRamGb(usableRamMb)

        val rec = when {
            physicalGb >= 8 -> OptProfile.PERFORMANCE
            physicalGb >= 4 -> OptProfile.BALANCED
            else            -> OptProfile.BATTERY
        }

        return DeviceSpec(
            brand        = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            model        = Build.MODEL,
            android      = Build.VERSION.RELEASE,
            api          = Build.VERSION.SDK_INT,
            cores        = Runtime.getRuntime().availableProcessors(),
            totalRamMb   = usableRamMb,
            physicalRamGb= physicalGb,
            availRamMb   = availRamMb,
            chipset      = readChip(hasRoot, hasShizuku),
            maxFreqMhz   = readFreq(),
            hasRoot      = hasRoot,
            hasShizuku   = hasShizuku,
            recommended  = rec,
        )
    }

    private fun calculatePhysicalRamGb(usableMb: Long): Int {
        val gb = usableMb / 1024.0
        return when {
            gb <= 1.2 -> 1
            gb <= 2.3 -> 2
            gb <= 3.4 -> 3
            gb <= 4.5 -> 4
            gb <= 6.5 -> 6
            gb <= 8.5 -> 8
            gb <= 12.5 -> 12
            gb <= 16.5 -> 16
            else -> gb.roundToInt()
        }
    }

    /**
     * MEDIUM-8: Baca chipset dengan fallback berlapis:
     *  1. `getprop ro.soc.manufacturer / ro.soc.model` — hanya jika ada Root/Shizuku
     *     (nilai paling akurat di kernel modern, mis. "Qualcomm SM8550")
     *  2. Build.SOC_MANUFACTURER + SOC_MODEL (API 31+)
     *  3. /proc/cpuinfo Hardware (kernel lama)
     *  4. Build.HARDWARE (paling umum)
     */
    private fun readChip(hasRoot: Boolean, hasShizuku: Boolean): String {
        if (hasRoot || hasShizuku) {
            val provider = if (hasRoot) RootEngine::su else { cmd: String -> ShizukuEngine.sh(cmd).let { CmdResult(it.success, it.output, it.cmd) } }
            val man = provider("getprop ro.soc.manufacturer 2>/dev/null").output.trim()
            val model = provider("getprop ro.soc.model 2>/dev/null").output.trim()
            if (model.isNotBlank() && model.lowercase() != "unknown") {
                return if (man.isNotBlank() && man.lowercase() != "unknown") "$man $model" else model
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val socModel = Build.SOC_MODEL
            if (!socModel.isNullOrBlank() && socModel != "unknown") {
                val socMan = Build.SOC_MANUFACTURER
                return if (!socMan.isNullOrBlank() && socMan != "unknown") "$socMan $socModel" else socModel
            }
        }

        return try {
            File("/proc/cpuinfo").readLines()
                .firstOrNull { it.startsWith("Hardware") }
                ?.substringAfter(":")?.trim()
                ?.takeIf { it.isNotBlank() && it.lowercase() != "unknown" }
                ?: Build.HARDWARE
        } catch (_: Exception) { Build.HARDWARE }
    }

    // Perbaikan: baca semua core, ambil max tertinggi
    private fun readFreq(): Int {
        try {
            val maxFreqs = (0 until Runtime.getRuntime().availableProcessors())
                .map { "/sys/devices/system/cpu/cpu$it/cpufreq/cpuinfo_max_freq" }
                .mapNotNull { path ->
                    File(path).takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull()
                }
            return (maxFreqs.maxOrNull() ?: 0L).div(1000L).toInt()
        } catch (_: Exception) { return 0 }
    }
}