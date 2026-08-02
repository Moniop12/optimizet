package com.monai.optimizer.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.monai.optimizer.MainActivity
import com.monai.optimizer.R
import com.monai.optimizer.optimizer.OptProfile
import com.monai.optimizer.optimizer.RootEngine
import com.monai.optimizer.optimizer.ShizukuEngine
import kotlinx.coroutines.*
import java.io.File

class MonAiService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

    companion object {
        const val CHANNEL_ID = "monai_live_channel"
        const val NOTIF_ID = 9901

        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_SET_PROFILE = "ACTION_SET_PROFILE"
        const val EXTRA_PROFILE = "EXTRA_PROFILE"

        var currentActiveProfile: OptProfile? = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (!isRunning) {
                    isRunning = true
                    startForeground(NOTIF_ID, buildNotification("Memulai Monitoring...", "Inisialisasi..."))
                    startMonitoringLoop()
                }
            }
            ACTION_STOP -> {
                isRunning = false
                scope.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_SET_PROFILE -> {
                val profileName = intent.getStringExtra(EXTRA_PROFILE)
                profileName?.let {
                    val profile = OptProfile.valueOf(it)
                    currentActiveProfile = profile
                    scope.launch {
                        applyProfileFromService(profile)
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun startMonitoringLoop() {
        scope.launch {
            while (isActive && isRunning) {
                val hasRoot = RootEngine.hasRoot()
                val activeApp = getForegroundApp(hasRoot)
                val cpuFreq = if (hasRoot) RootEngine.getCpuFreqInfo() else "--"
                val cpuTemp = if (hasRoot) RootEngine.getCpuTemp() else "--"
                val batteryInfo = getBatteryStats()

                val ramUsedPct = getRamUsagePct()

                val title = "🤖 MonAi Live: $activeApp"
                val profileLabel = currentActiveProfile?.name ?: "AUTO"
                val body = "Mode: $profileLabel | CPU: $cpuFreq ($cpuTemp) | RAM: $ramUsedPct% | Bat: $batteryInfo"

                val notif = buildNotification(title, body)
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIF_ID, notif)

                delay(2000) // Update notifikasi setiap 2 detik
            }
        }
    }

    private fun applyProfileFromService(profile: OptProfile) {
        val hasRoot = RootEngine.hasRoot()
        val hasShz = ShizukuEngine.isRunning() && ShizukuEngine.hasPerm()

        if (hasRoot) {
            when (profile) {
                OptProfile.PERFORMANCE -> RootEngine.applyPerformance()
                OptProfile.BALANCED    -> RootEngine.applyBalanced()
                OptProfile.BATTERY     -> RootEngine.applyBattery()
            }
        } else if (hasShz) {
            when (profile) {
                OptProfile.PERFORMANCE -> ShizukuEngine.applyPerformance()
                OptProfile.BALANCED    -> ShizukuEngine.applyBalanced()
                OptProfile.BATTERY     -> ShizukuEngine.applyBattery()
            }
        }
    }

    private fun getForegroundApp(hasRoot: Boolean): String {
        if (!hasRoot) return "System Active"
        return try {
            val r = RootEngine.su("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'")
            if (r.success && r.output.isNotBlank()) {
                val pkg = r.output.substringAfter("/").substringBefore("}").substringBefore(" ")
                if (pkg.contains(".")) pkg.split(".").last().replaceFirstChar { it.uppercase() } else "Home Screen"
            } else "Home Screen"
        } catch (_: Exception) { "System Active" }
    }

    private fun getBatteryStats(): String = try {
        val level = File("/sys/class/power_supply/battery/capacity").readText().trim()
        val tempRaw = File("/sys/class/power_supply/battery/temp").readText().trim().toDoubleOrNull() ?: 0.0
        val tempC = if (tempRaw > 100) tempRaw / 10.0 else tempRaw
        "$level% (%.1f°C)".format(tempC)
    } catch (_: Exception) { "N/A" }

    private fun getRamUsagePct(): Int {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val avail = mem.availMem / (1024L * 1024L)
        val total = mem.totalMem / (1024L * 1024L)
        return (((total - avail).toDouble() / total.toDouble()) * 100).toInt()
    }

    private fun buildNotification(title: String, content: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Notification Action Buttons
        val perfIntent = PendingIntent.getService(
            this, 1, Intent(this, MonAiService::class.java).apply {
                action = ACTION_SET_PROFILE
                putExtra(EXTRA_PROFILE, OptProfile.PERFORMANCE.name)
            }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val balIntent = PendingIntent.getService(
            this, 2, Intent(this, MonAiService::class.java).apply {
                action = ACTION_SET_PROFILE
                putExtra(EXTRA_PROFILE, OptProfile.BALANCED.name)
            }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val saveIntent = PendingIntent.getService(
            this, 3, Intent(this, MonAiService::class.java).apply {
                action = ACTION_SET_PROFILE
                putExtra(EXTRA_PROFILE, OptProfile.BATTERY.name)
            }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "🚀 Perf", perfIntent)
            .addAction(0, "⚖️ Bal", balIntent)
            .addAction(0, "🔋 Save", saveIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "MonAi Live Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Monitoring CPU, RAM, & Profile Switcher" }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}