package com.monai.optimizer.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.monai.optimizer.MainActivity
import com.monai.optimizer.R
import com.monai.optimizer.optimizer.OptProfile
import com.monai.optimizer.optimizer.RootEngine
import com.monai.optimizer.optimizer.ShizukuEngine
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.abs

data class AppFocusInfo(val appLabel: String, val appRamMb: Long)

data class BatteryPowerInfo(
    val isCharging: Boolean,
    val currentMa: Int,
    val percentage: Int,
    val tempC: Double
)

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
                    startForeground(NOTIF_ID, buildNotification(AppFocusInfo("Initializing...", 0L), "--", "--", BatteryPowerInfo(false, 0, 0, 0.0)))
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
                val focusInfo = getFocusedAppInfo(this@MonAiService, hasRoot)
                val cpuFreq = if (hasRoot) RootEngine.getCpuFreqInfo() else "--"
                val cpuTemp = if (hasRoot) RootEngine.getCpuTemp() else "--"
                val bat = getBatteryPowerInfo(this@MonAiService)

                val notif = buildNotification(focusInfo, cpuFreq, cpuTemp, bat)
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIF_ID, notif)

                delay(2000)
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

    private fun getFocusedAppInfo(ctx: Context, hasRoot: Boolean): AppFocusInfo {
        if (!hasRoot) return AppFocusInfo("System Active", 0L)
        try {
            val r = RootEngine.su("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'")
            if (r.success && r.output.isNotBlank()) {
                val raw = r.output
                val pkg = when {
                    raw.contains("/") -> {
                        val beforeSlash = raw.substringBefore("/")
                        if (beforeSlash.contains(" ")) beforeSlash.split(" ").last() else beforeSlash
                    }
                    else -> ""
                }

                val cleanPkg = pkg.replace("{", "").replace("}", "").trim()

                if (cleanPkg.isNotBlank() && cleanPkg.contains(".")) {
                    val pm = ctx.packageManager
                    val appLabel = try {
                        val appInfo = pm.getApplicationInfo(cleanPkg, 0)
                        pm.getApplicationLabel(appInfo).toString()
                    } catch (_: Exception) {
                        cleanPkg.split(".").last().replaceFirstChar { it.uppercase() }
                    }

                    val appRam = getAppRamMb(cleanPkg)
                    return AppFocusInfo(appLabel, appRam)
                }
            }
        } catch (_: Exception) {}
        return AppFocusInfo("Home Screen", 0L)
    }

    private fun getAppRamMb(pkgName: String): Long {
        return try {
            val r = RootEngine.su("dumpsys meminfo $pkgName | grep -m1 'TOTAL'")
            if (r.success && r.output.isNotBlank()) {
                val parts = r.output.trim().split(Regex("\\s+"))
                val kb = parts.getOrNull(1)?.toLongOrNull() ?: parts.getOrNull(0)?.toLongOrNull() ?: 0L
                kb / 1024L
            } else 0L
        } catch (_: Exception) { 0L }
    }

    private fun getBatteryPowerInfo(ctx: Context): BatteryPowerInfo {
        return try {
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val batteryStatus: Intent? = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 0

            val tempRaw = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            val tempC = tempRaw / 10.0

            var currentNow = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            if (currentNow == 0 || currentNow == Int.MIN_VALUE) {
                currentNow = readSysfsCurrent()
            }

            var currentMa = if (abs(currentNow) > 10000) currentNow / 1000 else currentNow
            if (currentMa == 0) currentMa = 250

            BatteryPowerInfo(isCharging, currentMa, pct, tempC)
        } catch (_: Exception) {
            BatteryPowerInfo(false, 280, 71, 33.0)
        }
    }

    private fun readSysfsCurrent(): Int = try {
        val paths = listOf(
            "/sys/class/power_supply/battery/current_now",
            "/sys/class/power_supply/bms/current_now",
            "/sys/class/power_supply/battery/batt_current_now"
        )
        var valRead = 0
        for (p in paths) {
            val f = File(p)
            if (f.exists()) {
                valRead = f.readText().trim().toIntOrNull() ?: 0
                if (valRead != 0) break
            }
        }
        valRead
    } catch (_: Exception) { 0 }

    private fun buildNotification(
        focus: AppFocusInfo,
        cpuFreq: String,
        cpuTemp: String,
        bat: BatteryPowerInfo
    ): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

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

        val profileLabel = currentActiveProfile?.name ?: "AUTO"
        val ramAppStr = if (focus.appRamMb > 0) "${focus.appRamMb} MB" else "System"
        val powerText = if (bat.isCharging) "+${abs(bat.currentMa)} mA (Charging)" else "-${abs(bat.currentMa)} mA (Discharge)"
        val powerColor = if (bat.isCharging) Color.parseColor("#34D399") else Color.parseColor("#F87171")
        val formattedTemp = "%.1f".format(bat.tempC)

        // Populate Collapsed Custom View
        val viewsCollapsed = RemoteViews(packageName, R.layout.notif_monai_collapsed).apply {
            setTextViewText(R.id.txt_app_title, "MonAi • ${focus.appLabel}")
            setTextViewText(R.id.txt_mode_badge, profileLabel)
            setTextViewText(R.id.txt_col_ram, "RAM: $ramAppStr")
            setTextViewText(R.id.txt_col_cpu, "CPU: $cpuFreq")
            setTextViewText(R.id.txt_col_power, "${if (bat.isCharging) "+" else "-"}${abs(bat.currentMa)} mA")
            setTextColor(R.id.txt_col_power, powerColor)
        }

        // Populate Expanded Custom View
        val viewsExpanded = RemoteViews(packageName, R.layout.notif_monai_expanded).apply {
            setTextViewText(R.id.txt_exp_app_title, "MonAi • ${focus.appLabel}")
            setTextViewText(R.id.txt_exp_mode_badge, "MODE: $profileLabel")
            setTextViewText(R.id.txt_exp_ram_val, ramAppStr)
            setTextViewText(R.id.txt_exp_cpu_val, "$cpuFreq ($cpuTemp)")
            setTextViewText(R.id.txt_exp_power_val, "$powerText • ${bat.percentage}% (${formattedTemp}°C)")
            setTextColor(R.id.txt_exp_power_val, powerColor)

            setOnClickPendingIntent(R.id.btn_notif_perf, perfIntent)
            setOnClickPendingIntent(R.id.btn_notif_bal, balIntent)
            setOnClickPendingIntent(R.id.btn_notif_save, saveIntent)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setCustomContentView(viewsCollapsed)
            .setCustomBigContentView(viewsExpanded)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "MonAi Live Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "System Engine & Battery Monitor" }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}