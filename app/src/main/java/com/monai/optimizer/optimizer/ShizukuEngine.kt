package com.monai.optimizer.optimizer

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.monai.optimizer.IShellUserService
import rikka.shizuku.Shizuku

data class SCmd(val success: Boolean, val output: String, val cmd: String)

/**
 * Eksekusi shell via Shizuku UserService — proses terpisah yang dijalankan
 * Shizuku dengan UID shell/root beneran (bukan reflection ke
 * Shizuku.newProcess yang sudah deprecated/hidden di versi Shizuku modern
 * dan sering gagal dgn "method not visible" di banyak device).
 * Lihat: ShellUserService.kt (proses yang benar-benar mengeksekusi command).
 */
object ShizukuEngine {
    private const val T = "ShizukuEngine"
    const val PERM_CODE = 1001

    fun isRunning(): Boolean = try { Shizuku.pingBinder() } catch (_: Exception) { false }
    fun hasPerm(): Boolean   = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) { false }

    fun requestPerm() { try { Shizuku.requestPermission(PERM_CODE) } catch (_: Exception) {} }

    // ── UserService binding ──────────────────────────────────────────
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

    /** Blocking bind (dipanggil dari Dispatchers.IO, aman nunggu). Timeout 4 detik. */
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
            val deadline = System.currentTimeMillis() + 4000
            while (service == null && System.currentTimeMillis() < deadline) {
                try { bindLock.wait(200) } catch (_: InterruptedException) {}
            }
            return service
        }
    }

    fun sh(cmd: String): SCmd {
        val svc = ensureBound()
            ?: return SCmd(false, "Shizuku service not available — pastikan app Shizuku berjalan & izin sudah diberikan", cmd)
        return try {
            val raw = svc.exec(cmd)
            val sep = raw.indexOf('\u0001')
            if (sep == -1) return SCmd(false, raw, cmd)
            val code = raw.substring(0, sep).toIntOrNull() ?: -1
            val out = raw.substring(sep + 1)
            Log.d(T, "[SHZ $code] $cmd")
            SCmd(code == 0, out, cmd)
        } catch (e: Throwable) {
            synchronized(bindLock) { service = null }  // proses mungkin mati, paksa rebind next call
            SCmd(false, e.message ?: "error", cmd)
        }
    }

    // ── Profiles ──────────────────────────────────────────────────────
    // Refresh rate, disable blur, dan Fixed Performance Mode SEMUA cuma
    // command shell biasa (settings put / cmd power) — genuinely jalan
    // tanpa root, jadi ditaruh di sini juga (bukan cuma root-only).

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

    // ── AppOps: batasi aktivitas background app pihak-3 ─────────────────
    fun restrictBackground(): SCmd = sh(
        "for pkg in \$(pm list packages -3 | cut -d: -f2); do " +
        "appops set \$pkg RUN_IN_BACKGROUND ignore; " +
        "appops set \$pkg RUN_ANY_IN_BACKGROUND ignore; " +
        "done; echo done"
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

    /** Dipakai toggle "Smooth UI" di Home — scope Shizuku sekarang lebih
     *  luas dari sekadar anim scale: refresh rate & disable blur juga
     *  genuinely jalan tanpa root. Yang TETAP root-only cuma setprop
     *  SurfaceFlinger/Skia internal (RootEngine.applySmoothRenderingTweaks). */
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

    // ── Reset (scope terbatas — cuma yg genuinely bisa Shizuku sentuh) ──
    // TIDAK bisa reset governor/sysctl kernel (butuh root beneran, sysfs
    // /proc read-only buat shell UID). Jangan diklaim "full reset".
    fun resetToDefaults(): List<SCmd> = listOf(
        applySmoothRenderingTweaks(false),
        sh("cmd power set-fixed-performance-mode-enabled false"),
        sh("settings delete global background_process_limit"),
        sh("dumpsys deviceidle disable")
    )
}
