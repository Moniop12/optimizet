package com.monai.optimizer.optimizer

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.io.File

enum class OptProfile { PERFORMANCE, BALANCED, BATTERY }

data class DeviceSpec(
    val brand      : String,
    val model      : String,
    val android    : String,
    val api        : Int,
    val cores      : Int,
    val totalRamMb : Long,
    val availRamMb : Long,
    val chipset    : String,
    val maxFreqMhz : Int,
    val hasRoot    : Boolean,
    val hasShizuku : Boolean,
    val recommended: OptProfile,
)

object DeviceAnalyzer {

    fun analyze(ctx: Context, hasRoot: Boolean, hasShizuku: Boolean): DeviceSpec {
        val am  = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val ram = mem.totalMem / (1024L * 1024L)
        val avl = mem.availMem / (1024L * 1024L)
        val rec = when {
            ram >= 8192L -> OptProfile.PERFORMANCE
            ram >= 4096L -> OptProfile.BALANCED
            else         -> OptProfile.BATTERY
        }
        return DeviceSpec(
            brand       = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            model       = Build.MODEL,
            android     = Build.VERSION.RELEASE,
            api         = Build.VERSION.SDK_INT,
            cores       = Runtime.getRuntime().availableProcessors(),
            totalRamMb  = ram,
            availRamMb  = avl,
            chipset     = readChip(),
            maxFreqMhz  = readFreq(),
            hasRoot     = hasRoot,
            hasShizuku  = hasShizuku,
            recommended = rec,
        )
    }

    private fun readChip(): String = try {
        File("/proc/cpuinfo").readLines()
            .firstOrNull { it.startsWith("Hardware") }
            ?.substringAfter(":")?.trim()
            ?: Build.HARDWARE
    } catch (_: Exception) { Build.HARDWARE }

    private fun readFreq(): Int = try {
        val f = File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")
        if (f.exists()) f.readText().trim().toLong().div(1000L).toInt() else 0
    } catch (_: Exception) { 0 }
}
