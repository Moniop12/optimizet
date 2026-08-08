package com.monai.optimizer.optimizer

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.monai.optimizer.IShellUserService
import rikka.shizuku.Shizuku

data class SCmd(val success: Boolean, val output: String, val cmd: String)

object ShizukuEngine {
    private const val T = "ShizukuEngine"
    const val PERM_CODE = 1001

    fun isRunning(): Boolean = try { Shizuku.pingBinder() } catch (_: Exception) { false }

    fun hasPerm(): Boolean = try {
        if (!isRunning()) false
        else Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) { false }

    fun requestPerm() {
        try { if (isRunning()) Shizuku.requestPermission(PERM_CODE) } catch (_: Exception) {}
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

    fun sh(cmd: String): SCmd {
        val svc = ensureBound() ?: return SCmd(false, "Shizuku service not available", cmd)
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

    // BATCH READ: Menggabungkan beberapa bacaan jadi 1 eksekusi sh (Hemat Baterai)
    fun getSystemStatsBatch(): String {
        val cmd = "cat /proc/stat 2>/dev/null | head -n 1; echo '|||'; " +
                "cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq 2>/dev/null; echo '|||'; " +
                "cat /sys/class/thermal/thermal_zone*/temp 2>/dev/null | head -n 1; echo '|||'; " +
                "cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null"
        return sh(cmd).output
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
        sh("settings put global background_process_limit 3"),
        sh("dumpsys deviceidle force-idle")
    )

    fun restrictBackground(): SCmd = sh(
        "for pkg in \$(pm list packages -3 | cut -d: -f2); do " +
        "appops set \$pkg RUN_IN_BACKGROUND ignore 2>/dev/null; " +
        "appops set \$pkg RUN_ANY_IN_BACKGROUND ignore 2>/dev/null; " +
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
        sh("settings delete global background_process_limit"),
        sh("dumpsys deviceidle disable")
    )

    // FIX (M2): sebelumnya blok if/elif/else selalu berakhir dengan `echo ...` sebagai
    // perintah TERAKHIR, jadi exit code shell SELALU 0 walau cabang "else echo failed"
    // yang dieksekusi — sh() menilai code==0 sebagai sukses, UI pun menampilkan
    // "X frozen" padahal app TIDAK dibekukan. Sekarang tiap cabang diakhiri `exit 0`
    // / `exit 1` eksplisit sehingga exit code selalu mencerminkan hasil asli.
    fun freezeApp(pkg: String): SCmd =
        sh("if pm disable-user --user 0 $pkg 2>/dev/null; then echo frozen; exit 0; elif pm suspend --user 0 $pkg 2>/dev/null; then echo frozen; exit 0; else echo failed; exit 1; fi")

    fun unfreezeApp(pkg: String): SCmd =
        sh("if pm enable --user 0 $pkg 2>/dev/null; then echo active; exit 0; elif pm unsuspend --user 0 $pkg 2>/dev/null; then echo active; exit 0; else echo failed; exit 1; fi")

    fun listDisabledPkgs(): Set<String> {
        val raw = sh("pm list packages -d 2>/dev/null").output
        return raw.lines()
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim() }
            .toSet()
    }
}