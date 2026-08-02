package com.monai.optimizer.optimizer

import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku

data class SCmd(val success: Boolean, val output: String, val cmd: String)

object ShizukuEngine {
    private const val T = "ShizukuEngine"
    const val PERM_CODE = 1001

    fun isRunning(): Boolean = try { Shizuku.pingBinder() } catch (_: Exception) { false }
    fun hasPerm(): Boolean   = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) { false }

    fun requestPerm() { try { Shizuku.requestPermission(PERM_CODE) } catch (_: Exception) {} }

    @Suppress("DiscouragedPrivateApi", "UNCHECKED_CAST")
    fun sh(cmd: String): SCmd = try {
        val method = Class.forName("rikka.shizuku.Shizuku").getMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        val p   = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        val out = p.inputStream.bufferedReader().readText().trim()
        val err = p.errorStream.bufferedReader().readText().trim()
        val rc  = p.waitFor()
        Log.d(T, "[SHZ $rc] $cmd")
        SCmd(rc == 0, out.ifEmpty { err }, cmd)
    } catch (e: Throwable) { SCmd(false, e.message ?: "error", cmd) }

    // ── Profiles ──────────────────────────────────────────────────────

    fun applyPerformance(): List<SCmd> = listOf(
        sh("settings put global window_animation_scale 0.5"),
        sh("settings put global transition_animation_scale 0.5"),
        sh("settings put global animator_duration_scale 0.5"),
        sh("settings put global background_process_limit 6"),
        sh("settings put global wifi_scan_throttle_enabled 1")
    )

    fun applyBalanced(): List<SCmd> = listOf(
        sh("settings put global window_animation_scale 1.0"),
        sh("settings put global transition_animation_scale 1.0"),
        sh("settings put global animator_duration_scale 1.0"),
        sh("settings put global background_process_limit 5")
    )

    fun applyBattery(): List<SCmd> = listOf(
        sh("settings put global window_animation_scale 0"),
        sh("settings put global transition_animation_scale 0"),
        sh("settings put global animator_duration_scale 0"),
        sh("settings put global background_process_limit 3"),
        sh("dumpsys deviceidle force-idle")
    )

    // ── Fixed Trim Memory Engine ──────────────────────────────────────

    fun trimMemory(): SCmd = sh(
        "cmd package trim-caches 999G 2>/dev/null; " +
        "for pkg in \$(pm list packages -3 | cut -d: -f2); do am send-trim-memory \$pkg COMPLETE 2>/dev/null; done; " +
        "echo done"
    )

    fun killBgApps(): SCmd = sh("am kill-all; cmd activity kill-all")
    fun aggressiveDoze(): List<SCmd> = listOf(
        sh("dumpsys deviceidle enable"),
        sh("dumpsys deviceidle force-idle")
    )

    fun setAnimScale(s: Float): SCmd = sh(
        "settings put global window_animation_scale $s && " +
        "settings put global transition_animation_scale $s && " +
        "settings put global animator_duration_scale $s"
    )
}