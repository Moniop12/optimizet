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
            chipset      = readChip(),
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

    private fun readChip(): String = try {
        File("/proc/cpuinfo").readLines()
            .firstOrNull { it.startsWith("Hardware") }
            ?.substringAfter(":")?.trim()
            ?: Build.HARDWARE
    } catch (_: Exception) { Build.HARDWARE }

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