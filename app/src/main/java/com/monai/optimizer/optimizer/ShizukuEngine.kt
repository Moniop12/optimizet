package com.monai.optimizer.optimizer

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import af.shizuku.plus.api.ShizukuPlusAPI
import com.monai.optimizer.IShellUserService
import rikka.shizuku.Shizuku

data class SCmd(val success: Boolean, val output: String, val cmd: String)

/**
 * Unified Dual-Support Engine:
 * Mendukung ShizukuPlus (thejaustin) & Shizuku Original (Rikka) secara otomatis.
 */
object ShizukuEngine {
    private const val T = "ShizukuEngine"
    const val PERM_CODE = 1001

    fun isRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Exception) { false }

    fun hasPerm(): Boolean = try {
        if (!isRunning()) false
        else Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) { false }

    fun requestPerm() {
        try {
            if (isRunning()) {
                Shizuku.requestPermission(PERM_CODE)
            }
        } catch (_: Exception) {}
    }

    @Volatile private var service: IShellUserService? = null
    private val bindLock = Object()

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName("com.monai.optimizer", ShellUserService::class.java.name))
            .daemon(false)
            .processNameSuffix("shell")
            .debuggable(false)
            .version(1)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            synchronized(bindLock) {
                service = if (binder != null && binder.pingBinder()) IShellUserService.Stub.asInterface(binder) else null
                bindLock.notifyAll()
            }
            Log.d(T, "UserService connected: ${service != null}")
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            synchronized(bindLock) { service = null }
        }
    }

    private fun ensureBound(): IShellUserService? {
        service?.let { return it }
        if (!isRunning() || !hasPerm()) return null
        synchronized(bindLock) {
            service?.let { return it }
            try {
                Shizuku.bindUserService(userServiceArgs, connection)
            } catch (e: Throwable) {
                Log.e(T, "bindUserService failed", e)
                return null
            }
            val deadline = System.currentTimeMillis() + 2500
            while (service == null && System.currentTimeMillis() < deadline) {
                try { bindLock.wait(150) } catch (_: InterruptedException) {}
            }
            return service
        }
    }

    /**
     * Eksekusi Shell Cerdas:
     * 1. Coba eksekusi via ShizukuPlusAPI (Jalur Utama ShizukuPlus).
     * 2. Jika bukan ShizukuPlus, otomatis fallback ke AIDL (Jalur Standar Shizuku Rikka).
     */
    fun sh(cmd: String): SCmd {
        if (!isRunning() || !hasPerm()) {
            return SCmd(false, "Shizuku / ShizukuPlus service not available", cmd)
        }

        // 1. Jalur ShizukuPlus API
        try {
            val plusResult = ShizukuPlusAPI.executeShell(cmd)
            if (plusResult != null && (plusResult.isSuccess || plusResult.exitCode == 0)) {
                Log.d(T, "[SHZ+ ${plusResult.exitCode}] $cmd")
                return SCmd(true, plusResult.output ?: "ok", cmd)
            }
        } catch (_: Throwable) {
            // Jika bukan server ShizukuPlus, lanjut ke jalur standar Rikka AIDL
        }

        // 2. Jalur Fallback Rikka Shizuku AIDL
        val svc = ensureBound()
            ?: return SCmd(false, "Failed to bind UserService on Shizuku", cmd)
        return try {
            val raw = svc.exec(cmd)
            val sep = raw.indexOf('\u0001')
            if (sep == -1) return SCmd(false, raw, cmd)
            val code = raw.substring(0, sep).toIntOrNull() ?: -1
            val out = raw.substring(sep + 1)
            Log.d(T, "[SHZ $code] $cmd")
            SCmd(code == 0, out, cmd)
        } catch (e: Throwable) {
            synchronized(bindLock) { service = null }
            SCmd(false, e.message ?: "error", cmd)
        }
    }

    fun setStandbyBucketsRestricted(): SCmd = sh(
        "for pkg in \$(pm list packages -3 | cut -d: -f2); do " +
        "am set-standby-bucket \$pkg restricted 2>/dev/null; " +
        "done; echo done"
    )

    fun setResolutionPreset(size: String?, density: Int?): SCmd {
        val cmd = if (size == null || density == null) {
            "wm size reset && wm density reset"
        } else {
            "wm size $size && wm density $density"
        }
        return sh(cmd)
    }

    fun applyPerformance(maxRefreshRate: Float = 90f): List<SCmd> = listOf(
        sh("settings put global window_animation_scale 0.5"),
        sh("settings put global transition_animation_scale 0.5"),
        sh("settings put global animator_duration_scale 0.5"),
        sh("settings put system peak_refresh_rate $maxRefreshRate"),
        sh("settings put system min_refresh_rate $maxRefreshRate"),
        sh("settings put global disable_window_blurs 1"),
        sh("settings put global accessibility_reduce_transparency 1"),
        sh("cmd power set-fixed-performance-mode-enabled true"),
        sh("settings put global background_process_limit 6"),
        sh("settings put global wifi_scan_throttle_enabled 1")
    )

    fun applyBalanced(maxRefreshRate: Float = 90f): List<SCmd> = listOf(
        sh("settings put global window_animation_scale 1.0"),
        sh("settings put global transition_animation_scale 1.0"),
        sh("settings put global animator_duration_scale 1.0"),
        sh("settings put system peak_refresh_rate $maxRefreshRate"),
        sh("settings put system min_refresh_rate $maxRefreshRate"),
        sh("settings put global disable_window_blurs 1"),
        sh("settings put global accessibility_reduce_transparency 1"),
        sh("cmd power set-fixed-performance-mode-enabled false"),
        sh("settings put global background_process_limit 5")
    )

    fun applyBattery(): List<SCmd> = listOf(
        sh("settings put global window_animation_scale 0"),
        sh("settings put global transition_animation_scale 0"),
        sh("settings put global animator_duration_scale 0"),
        sh("settings delete system peak_refresh_rate"),
        sh("settings delete system min_refresh_rate"),
        sh("settings put global disable_window_blurs 0"),
        sh("settings put global accessibility_reduce_transparency 0"),
        sh("cmd power set-fixed-performance-mode-enabled false"),
        sh("settings put global background_process_limit 3"),
        sh("dumpsys deviceidle force-idle")
    )

    fun restrictBackground(): SCmd = sh(
        "for pkg in \$(pm list packages -3 | cut -d: -f2); do " +
        "appops set \$pkg RUN_IN_BACKGROUND ignore; " +
        "appops set \$pkg RUN_ANY_IN_BACKGROUND ignore; " +
        "done; echo done"
    )

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

    fun applySmoothRenderingTweaks(enable: Boolean, maxRefreshRate: Float = 90f): SCmd {
        return if (enable) sh(
            "settings put global window_animation_scale 0.5 && " +
            "settings put global transition_animation_scale 0.5 && " +
            "settings put global animator_duration_scale 0.5 && " +
            "settings put system peak_refresh_rate $maxRefreshRate && " +
            "settings put system min_refresh_rate $maxRefreshRate && " +
            "settings put global disable_window_blurs 1 && " +
            "settings put global accessibility_reduce_transparency 1"
        ) else sh(
            "settings put global window_animation_scale 1.0 && " +
            "settings put global transition_animation_scale 1.0 && " +
            "settings put global animator_duration_scale 1.0 && " +
            "settings delete system peak_refresh_rate && " +
            "settings delete system min_refresh_rate && " +
            "settings put global disable_window_blurs 0 && " +
            "settings put global accessibility_reduce_transparency 0"
        )
    }

    fun resetToDefaults(): List<SCmd> = listOf(
        applySmoothRenderingTweaks(false),
        sh("cmd power set-fixed-performance-mode-enabled false"),
        sh("settings delete global background_process_limit"),
        sh("dumpsys deviceidle disable")
    )

    fun freezeApp(pkg: String): SCmd =
        sh("pm disable-user --user 0 $pkg 2>/dev/null || pm suspend --user 0 $pkg 2>/dev/null && echo frozen")

    fun unfreezeApp(pkg: String): SCmd =
        sh("pm enable --user 0 $pkg 2>/dev/null || pm unsuspend --user 0 $pkg 2>/dev/null && echo active")

    fun listDisabledPkgs(): Set<String> {
        val raw = sh("pm list packages -d 2>/dev/null").output
        return raw.lines()
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim() }
            .toSet()
    }
}