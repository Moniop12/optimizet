package com.monai.optimizer.service

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.monai.optimizer.MainActivity
import com.monai.optimizer.R
import com.monai.optimizer.data.UserPreferencesRepository
import com.monai.optimizer.optimizer.ChargingController
import com.monai.optimizer.optimizer.MonitorEngine
import com.monai.optimizer.optimizer.OptProfile
import com.monai.optimizer.optimizer.RootEngine
import com.monai.optimizer.optimizer.ShizukuEngine
import com.monai.optimizer.optimizer.TelemetryCache
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.abs

data class AppFocusInfo(val appLabel: String, val pkgName: String? = null, val appRamMb: Long = 0L)

data class BatteryPowerInfo(
    val isCharging: Boolean,
    val currentMa: Int,
    val percentage: Int,
    val tempC: Double,
)

class MonAiService : Service() {

    // ===== SCOPE LIFECYCLE (CRITICAL-1) =====
    // prefsScope: HIDUP selama service hidup — observePreferences() & prefs writes.
    //             TIDAK PERNAH di-cancel (dulu ikut terbunuh oleh scope.cancel()).
    // monitorScope: dibuat ULANG setiap ACTION_START; di-cancel anaknya saat STOP
    //               via cancelChildren() — scope-nya sendiri tetap hidup.
    private val prefsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitorScope: CoroutineScope? = null

    @Volatile private var isRunning = false

    private lateinit var prefsRepo: UserPreferencesRepository
    private lateinit var chargingController: ChargingController
    private var previousUncaughtHandler: Thread.UncaughtExceptionHandler? = null

    private var lastFocusInfo = AppFocusInfo("System Active", null, 0L)
    private var dumpsysPollTick = 0

    private val pkgRegex = Regex("""([a-zA-Z0-9_]+(\.[a-zA-Z0-9_]+)+)""")

    @Volatile private var tempNotifLogMsg: String? = null
    private var tempLogJob: Job? = null

    @Volatile private var showNotifRam = true
    @Volatile private var showNotifCpu = true
    @Volatile private var showNotifPower = true
    @Volatile private var showNotifProfiles = true

    // Cache PendingIntent — dibangun SEKALI, dipakai ulang tiap tick (MEDIUM-16)
    private var cachedOpenIntent: PendingIntent? = null
    private var cachedPerfIntent: PendingIntent? = null
    private var cachedBalIntent: PendingIntent? = null
    private var cachedSaveIntent: PendingIntent? = null

    companion object {
        const val CHANNEL_ID = "monai_live_channel"
        const val NOTIF_ID = 9901

        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_SET_PROFILE = "ACTION_SET_PROFILE"
        const val ACTION_POST_STATUS_LOG = "ACTION_POST_STATUS_LOG"
        const val EXTRA_PROFILE = "EXTRA_PROFILE"
        const val EXTRA_LOG_MSG = "EXTRA_LOG_MSG"

        private const val MONITOR_INTERVAL_MS = 3000L
        private const val DUMPSYS_POLL_EVERY = 3
        private const val USAGE_EVENTS_INTERVAL_MS = 12_000L
        private const val APP_NAME = "MonProject"

        @Volatile var currentActiveProfile: OptProfile? = null

        // State charging dipindah ke ChargingController — field berikut dipertahankan
        // sebagai delegasi baca untuk kompatibilitas pemanggil lama (UI).
        // CATATAN: tanpa @Volatile (custom getter/setter tidak punya backing field —
        // keamanan thread dijamin oleh @Volatile field di dalam ChargingController).
        var isChargeLimitEnabled: Boolean
            get() = activeChargingController()?.isChargeLimitEnabled ?: false
            set(value) { activeChargingController()?.syncFromPrefs(value, activeChargingController()?.chargeLimitPct ?: 80, activeChargingController()?.chargeSpeedMa ?: UserPreferencesRepository.DEFAULT_CHARGE_SPEED_MA, activeChargingController()?.isThermalProtectEnabled ?: false, activeChargingController()?.isBypassChargingEnabled ?: false) }

        var chargeLimitPct: Int
            get() = activeChargingController()?.chargeLimitPct ?: UserPreferencesRepository.DEFAULT_CHARGE_LIMIT_PCT
            set(value) { activeChargingController()?.syncFromPrefs(activeChargingController()?.isChargeLimitEnabled ?: false, value, activeChargingController()?.chargeSpeedMa ?: UserPreferencesRepository.DEFAULT_CHARGE_SPEED_MA, activeChargingController()?.isThermalProtectEnabled ?: false, activeChargingController()?.isBypassChargingEnabled ?: false) }

        var chargeSpeedMa: Int
            get() = activeChargingController()?.chargeSpeedMa ?: UserPreferencesRepository.DEFAULT_CHARGE_SPEED_MA
            set(value) { activeChargingController()?.syncFromPrefs(activeChargingController()?.isChargeLimitEnabled ?: false, activeChargingController()?.chargeLimitPct ?: 80, value, activeChargingController()?.isThermalProtectEnabled ?: false, activeChargingController()?.isBypassChargingEnabled ?: false) }

        var isThermalProtectEnabled: Boolean
            get() = activeChargingController()?.isThermalProtectEnabled ?: false
            set(value) { activeChargingController()?.syncFromPrefs(activeChargingController()?.isChargeLimitEnabled ?: false, activeChargingController()?.chargeLimitPct ?: 80, activeChargingController()?.chargeSpeedMa ?: UserPreferencesRepository.DEFAULT_CHARGE_SPEED_MA, value, activeChargingController()?.isBypassChargingEnabled ?: false) }

        var isChargePausedByLimit: Boolean
            get() = activeChargingController()?.isChargePausedByLimit ?: false
            set(value) { activeChargingController()?.syncRuntimeFlags(pausedByLimit = value) }

        var isThermalThrottled: Boolean
            get() = activeChargingController()?.isThermalThrottled ?: false
            set(value) { activeChargingController()?.syncRuntimeFlags(thermalThrottled = value) }

        var aiOptimizerEnabled = false

        @Volatile private var chargingControllerRef: ChargingController? = null

        fun attachChargingController(controller: ChargingController) {
            chargingControllerRef = controller
        }

        private fun activeChargingController(): ChargingController? = chargingControllerRef

        // Fail-safe lama disederhanakan — sekarang lewat controller (HIGH-5)
        fun restoreChargingFailSafe() {
            val ctrl = chargingControllerRef ?: return
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { ctrl.restoreChargingFailSafe() }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        RootEngine.init(applicationContext)
        prefsRepo = UserPreferencesRepository(applicationContext)
        // SINGLETON — instance yang sama dipakai MainViewModel (satu penulis charging)
        chargingController = ChargingController.getInstance(prefsRepo)
        attachChargingController(chargingController)
        createNotificationChannel()
        installFailSafeUncaughtHandler()
        observePreferences()
    }

    private fun installFailSafeUncaughtHandler() {
        previousUncaughtHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            restoreChargingFailSafe()
            val prev = previousUncaughtHandler
            if (prev != null) {
                prev.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
            }
        }
    }

    private fun observePreferences() {
        // prefsScope TIDAK pernah di-cancel — preferensi selalu sinkron (CRITICAL-1/NEW-CRITICAL-A)
        prefsScope.launch {
            prefsRepo.preferencesFlow.collect { state ->
                currentActiveProfile = state.activeProfile
                aiOptimizerEnabled = state.aiOptimizerEnabled
                chargingController.syncFromPrefs(
                    chargeLimitEnabled = state.isChargeLimitEnabled,
                    chargeLimitPct = state.chargeLimitPct,
                    chargeSpeedMa = state.chargeSpeedMa,
                    thermalProtect = state.isThermalProtectEnabled,
                    bypassCharging = state.isBypassChargingEnabled,
                )

                showNotifRam = state.showNotifRam
                showNotifCpu = state.showNotifCpu
                showNotifPower = state.showNotifPower
                showNotifProfiles = state.showNotifProfiles
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // ===== CRITICAL-2: startForeground() di SETIAP onStartCommand =====
        // Android 12+ mewajibkan startForeground() ≤ 5 dtk setelah startForegroundService().
        // Jalur restart sistem (START_STICKY → intent null) TANPA panggilan ini = crash
        // ForegroundServiceDidNotStartInTimeException. Panggil SEBELUM cabang action apa pun.
        startForeground(
            NOTIF_ID,
            buildNotification(AppFocusInfo("Initializing...", null, 0L), "--", "--", 0, BatteryPowerInfo(false, 0, 0, 0.0)),
        )

        when (intent?.action) {
            ACTION_START -> {
                if (!isRunning) {
                    isRunning = true
                    // CRITICAL-1: scope baru dibuat ULANG per START (scope lama yang di-cancel tidak dipakai lagi)
                    val newScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
                    monitorScope = newScope
                    newScope.launch { prefsRepo.setLiveServiceRunning(true) }
                    newScope.launch { RootEngine.backupOriginalStateIfNeeded() }
                    startMonitoringLoop(newScope)
                }
            }
            ACTION_STOP -> {
                isRunning = false
                // CRITICAL-1: cancel HANYA anak monitorScope — prefsScope & prefsRepo tetap hidup
                monitorScope?.cancel()
                monitorScope = null
                // HIGH-16: UI harus segera fallback ke pembacaan sendiri — invalidate cache
                TelemetryCache.invalidate()
                // HIGH-1: jangan runBlocking di main thread — DataStore I/O via coroutine
                prefsScope.launch { prefsRepo.setLiveServiceRunning(false) }
                // Fail-safe dipanggil async (bukan memblokir main thread)
                prefsScope.launch { runCatching { chargingController.restoreChargingFailSafe() } }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_SET_PROFILE -> {
                val profileName = intent.getStringExtra(EXTRA_PROFILE)
                profileName?.let {
                    // CRITICAL-3: runCatching — nama enum invalid dari PendingIntent/versi lama
                    // TIDAK BOLEH crash service (dulu IllegalArgumentException di main thread)
                    val profile = runCatching { OptProfile.valueOf(it) }.getOrNull()
                    if (profile != null) {
                        currentActiveProfile = profile
                        prefsScope.launch {
                            prefsRepo.setActiveProfile(profile)
                            applyProfileFromService(profile)
                            triggerNotificationRefresh()
                        }
                    }
                }
            }
            ACTION_POST_STATUS_LOG -> {
                val msg = intent.getStringExtra(EXTRA_LOG_MSG)
                msg?.let { showTemporaryNotifLog(it) }
            }
        }
        return START_STICKY
    }

    private fun showTemporaryNotifLog(msg: String) {
        tempLogJob?.cancel()
        tempNotifLogMsg = msg
        triggerNotificationRefresh()
        tempLogJob = prefsScope.launch {
            delay(4000L)
            tempNotifLogMsg = null
            triggerNotificationRefresh()
        }
    }

    private fun triggerNotificationRefresh() {
        prefsScope.launch {
            val hasRoot = RootEngine.hasRoot()
            val hasShz = ShizukuEngine.isRunning() && ShizukuEngine.hasPerm()
            val focusInfo = getFocusedAppInfo(this@MonAiService, hasRoot, hasShz)
            val bat = getBatteryPowerInfo(this@MonAiService)

            val batch = TelemetryCache.readBatch()
            var rawStat: String? = null
            var freq = "--"
            var temp = "--"
            if (batch != null) {
                val parts = batch.split("|||")
                if (parts.size == 4) {
                    rawStat = parts[0]
                    freq = parts[1].trim().toLongOrNull()?.div(1000L)?.let { "${it}MHz" } ?: "--"
                    val rawTemp = parts[2].trim().toFloatOrNull() ?: 0f
                    temp = if (rawTemp > 0f) "%.1f°C".format(if (rawTemp > 1000f) rawTemp / 1000f else rawTemp) else "--"
                }
            }

            val cpuPct = MonitorEngine.getCpuUsagePercent(rawStat)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIF_ID, buildNotification(focusInfo, freq, temp, cpuPct, bat))
        }
    }

    override fun onDestroy() {
        // HIGH-5: restore handler lama (idempoten — aman dipanggil berulang)
        restoreFailSafeUncaughtHandler()
        isRunning = false
        monitorScope?.cancel()
        monitorScope = null
        // HIGH-5: fail-safe restore saat service dimusnahkan
        prefsScope.launch { runCatching { chargingController.restoreChargingFailSafe() } }
        super.onDestroy()
    }

    private fun restoreFailSafeUncaughtHandler() {
        val prev = previousUncaughtHandler
        if (prev != null) {
            Thread.setDefaultUncaughtExceptionHandler(prev)
            previousUncaughtHandler = null
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        prefsScope.launch { runCatching { chargingController.restoreChargingFailSafe() } }
        super.onTaskRemoved(rootIntent)
    }

    private fun startMonitoringLoop(scope: CoroutineScope) {
        scope.launch {
            val cachedHasRoot = RootEngine.hasRoot()
            val cachedHasShizuku = ShizukuEngine.isRunning() && ShizukuEngine.hasPerm()

            while (isActive && isRunning) {
                val focusInfo = getFocusedAppInfo(this@MonAiService, cachedHasRoot, cachedHasShizuku)

                var freq = "--"
                var temp = "--"
                var rawStat: String? = null

                if (cachedHasRoot) {
                    val batch = RootEngine.getSystemStatsBatch()
                    TelemetryCache.publishBatch(batch)
                    val parts = batch.split("|||")
                    if (parts.size == 4) {
                        rawStat = parts[0]
                        freq = parts[1].trim().toLongOrNull()?.div(1000L)?.let { "${it}MHz" } ?: "--"
                        val rawTemp = parts[2].trim().toFloatOrNull() ?: 0f
                        temp = if (rawTemp > 0f) "%.1f°C".format(if (rawTemp > 1000f) rawTemp / 1000f else rawTemp) else "--"
                    }
                } else if (cachedHasShizuku) {
                    val batch = ShizukuEngine.getSystemStatsBatch()
                    TelemetryCache.publishBatch(batch)
                    val parts = batch.split("|||")
                    if (parts.size == 4) {
                        rawStat = parts[0]
                        freq = parts[1].trim().toLongOrNull()?.div(1000L)?.let { "${it}MHz" } ?: "--"
                        val rawTemp = parts[2].trim().toFloatOrNull() ?: 0f
                        temp = if (rawTemp > 0f) "%.1f°C".format(if (rawTemp > 1000f) rawTemp / 1000f else rawTemp) else "--"
                    }
                }

                val cpuPct = MonitorEngine.getCpuUsagePercent(rawStat)
                val bat = getBatteryPowerInfo(this@MonAiService)

                // HIGH-6/NEW-HIGH-B: logika charging TIDAK lagi ditulis inline di sini —
                // SEMUA lewat ChargingController (satu penulis)
                chargingController.applyMonitoringLogic(bat.percentage, bat.tempC, bat.isCharging)

                val notif = buildNotification(focusInfo, freq, temp, cpuPct, bat)
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIF_ID, notif)

                delay(MONITOR_INTERVAL_MS)
            }
        }
    }

    private fun applyProfileFromService(profile: OptProfile) {
        val hasRoot = RootEngine.hasRoot()
        val hasShz = ShizukuEngine.isRunning() && ShizukuEngine.hasPerm()

        if (hasRoot) {
            when (profile) {
                OptProfile.PERFORMANCE -> RootEngine.applyPerformance()
                OptProfile.BALANCED -> RootEngine.applyBalanced()
                OptProfile.BATTERY -> RootEngine.applyBattery()
            }
        } else if (hasShz) {
            when (profile) {
                OptProfile.PERFORMANCE -> ShizukuEngine.applyPerformance()
                OptProfile.BALANCED -> ShizukuEngine.applyBalanced()
                OptProfile.BATTERY -> ShizukuEngine.applyBattery()
            }
        }
    }

    private fun getFocusedAppInfo(ctx: Context, hasRoot: Boolean, hasShizuku: Boolean): AppFocusInfo {
        getFocusedAppViaUsageStats(ctx)?.let {
            lastFocusInfo = it
            return it
        }

        if (!hasRoot && !hasShizuku) return AppFocusInfo("System Active", null, 0L)

        dumpsysPollTick++
        if (dumpsysPollTick < DUMPSYS_POLL_EVERY) {
            return lastFocusInfo
        }
        dumpsysPollTick = 0

        val fresh = if (hasRoot) getFocusedAppViaDumpsys(ctx) else getFocusedAppViaShizuku(ctx)
        lastFocusInfo = fresh
        return fresh
    }

    private fun hasUsageStatsPermission(ctx: Context): Boolean = try {
        val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        mode == AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) { false }

    private fun getFocusedAppViaUsageStats(ctx: Context): AppFocusInfo? {
        if (!hasUsageStatsPermission(ctx)) return null
        return try {
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val begin = end - USAGE_EVENTS_INTERVAL_MS
            val events = usm.queryEvents(begin, end)
            val event = UsageEvents.Event()
            var lastPkg: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)
                ) {
                    lastPkg = event.packageName
                }
            }
            val pkg = lastPkg ?: return lastFocusInfo.takeIf { it.appLabel != "System Active" }
            AppFocusInfo(labelFor(ctx, pkg), pkg, getAppRamMb(pkg, useRoot = false))
        } catch (_: Exception) { null }
    }

    private fun parsePackageFromDumpsys(output: String): String? {
        if (output.isBlank()) return null
        if (output.contains("/")) {
            val beforeSlash = output.substringBefore("/")
            val clean = beforeSlash.split(" ", "{").lastOrNull()?.trim()
            if (!clean.isNullOrEmpty() && clean.contains(".")) return clean
        }
        val matches = pkgRegex.findAll(output).map { it.value }.toList()
        return matches.firstOrNull { it.contains(".") && !it.startsWith("android.view") && !it.contains("StatusBar") }
    }

    private fun getFocusedAppViaDumpsys(ctx: Context): AppFocusInfo {
        try {
            val r = RootEngine.su("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'")
            if (r.success && r.output.isNotBlank()) {
                val cleanPkg = parsePackageFromDumpsys(r.output)
                if (!cleanPkg.isNullOrBlank()) {
                    val appRam = getAppRamMb(cleanPkg, useRoot = true)
                    return AppFocusInfo(labelFor(ctx, cleanPkg), cleanPkg, appRam)
                }
            }
        } catch (_: Exception) {}
        return AppFocusInfo("Home Screen", null, 0L)
    }

    private fun getFocusedAppViaShizuku(ctx: Context): AppFocusInfo {
        try {
            val r = ShizukuEngine.sh("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'")
            if (r.success && r.output.isNotBlank()) {
                val cleanPkg = parsePackageFromDumpsys(r.output)
                if (!cleanPkg.isNullOrBlank()) {
                    return AppFocusInfo(labelFor(ctx, cleanPkg), cleanPkg, 0L)
                }
            }
        } catch (_: Exception) {}
        return AppFocusInfo("Home Screen", null, 0L)
    }

    private fun labelFor(ctx: Context, pkgName: String): String = try {
        val pm = ctx.packageManager
        val appInfo = pm.getApplicationInfo(pkgName, 0)
        pm.getApplicationLabel(appInfo).toString()
    } catch (_: Exception) {
        pkgName.substringAfterLast(".").replaceFirstChar { it.uppercase() }
    }

    private fun getAppRamMb(pkgName: String, useRoot: Boolean): Long {
        if (!useRoot) return 0L
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
            // HIGH-21: registerReceiver dengan RECEIVER_NOT_EXPORTED (aman untuk API 33+/34)
            val batteryStatus: Intent? = ContextCompat.registerReceiver(
                ctx,
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )

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
            // MEDIUM-1 (lanjutan): 0 mA ditampilkan sebagai 0 (bukan asumsi 250) —
            // UI menampilkan "--" untuk nilai tidak diketahui
            if (currentMa == 0) currentMa = 0

            BatteryPowerInfo(isCharging, currentMa, pct, tempC)
        } catch (_: Exception) {
            BatteryPowerInfo(false, 0, 0, 0.0)
        }
    }

    private fun readSysfsCurrent(): Int = try {
        val paths = listOf(
            "/sys/class/power_supply/battery/current_now",
            "/sys/class/power_supply/bms/current_now",
            "/sys/class/power_supply/battery/batt_current_now",
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
        cpuUsagePct: Int,
        bat: BatteryPowerInfo,
    ): Notification {
        val openAppIntent = cachedOpenIntent ?: PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        ).also { cachedOpenIntent = it }

        val perfIntent = cachedPerfIntent ?: PendingIntent.getService(
            this, 1, Intent(this, MonAiService::class.java).apply {
                action = ACTION_SET_PROFILE
                putExtra(EXTRA_PROFILE, OptProfile.PERFORMANCE.name)
            }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        ).also { cachedPerfIntent = it }

        val balIntent = cachedBalIntent ?: PendingIntent.getService(
            this, 2, Intent(this, MonAiService::class.java).apply {
                action = ACTION_SET_PROFILE
                putExtra(EXTRA_PROFILE, OptProfile.BALANCED.name)
            }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        ).also { cachedBalIntent = it }

        val saveIntent = cachedSaveIntent ?: PendingIntent.getService(
            this, 3, Intent(this, MonAiService::class.java).apply {
                action = ACTION_SET_PROFILE
                putExtra(EXTRA_PROFILE, OptProfile.BATTERY.name)
            }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        ).also { cachedSaveIntent = it }

        val ctrl = chargingController
        val appName = getString(R.string.app_name)
        val displayTitle = tempNotifLogMsg ?: if (focus.appLabel == appName) "$appName • Dashboard Active" else "$appName • ${focus.appLabel}"
        val profileLabel = currentActiveProfile?.name ?: "AUTO"

        // FIX: Ambil Free RAM lokal, tidak butuh Root
        val ramSnap = MonitorEngine.getRamSnapshot()
        val ramAppStr = "${ramSnap.availMb} MB"

        // MEDIUM-1 lanjutan: 0 mA tidak diketahui → tampilkan "--"
        val currentDisplay = if (bat.currentMa != 0) "${abs(bat.currentMa)} mA" else "--"
        val powerText = if (ctrl.isChargePausedByLimit) {
            "Paused (Limit ${ctrl.chargeLimitPct}%)"
        } else if (ctrl.isThermalThrottled) {
            "Throttled (Thermal)"
        } else if (bat.isCharging) {
            "+$currentDisplay (Charging)"
        } else {
            "-$currentDisplay (Discharge)"
        }

        val powerColor = ContextCompat.getColor(
            this,
            if (bat.isCharging) R.color.notif_power_charge else R.color.notif_power_discharge,
        )
        val formattedTemp = "%.1f".format(bat.tempC)

        val isPerf = currentActiveProfile == OptProfile.PERFORMANCE
        val isBal = currentActiveProfile == OptProfile.BALANCED
        val isSave = currentActiveProfile == OptProfile.BATTERY

        val labelPerf = if (isPerf) "✓ PERF" else "PERF"
        val labelBal = if (isBal) "✓ BALANCED" else "BALANCED"
        val labelSave = if (isSave) "✓ SAVER" else "SAVER"

        val perfColor = ContextCompat.getColor(this, R.color.notif_perf_color)
        val balColor = ContextCompat.getColor(this, R.color.notif_bal_color)
        val saveColor = ContextCompat.getColor(this, R.color.notif_save_color)
        val whiteColor = ContextCompat.getColor(this, R.color.notif_btn_active_text)

        val viewsCollapsed = RemoteViews(packageName, R.layout.notif_monai_collapsed).apply {
            setTextViewText(R.id.txt_app_title, displayTitle)
            setTextViewText(R.id.txt_mode_badge, profileLabel)

            setViewVisibility(R.id.txt_col_ram, if (showNotifRam) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.txt_col_cpu, if (showNotifCpu) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.txt_col_power, if (showNotifPower) View.VISIBLE else View.GONE)

            if (showNotifRam) setTextViewText(R.id.txt_col_ram, "RAM: $ramAppStr")
            if (showNotifCpu) setTextViewText(R.id.txt_col_cpu, "CPU: $cpuUsagePct% • $cpuFreq")
            if (showNotifPower) {
                setTextViewText(R.id.txt_col_power, "${if (bat.isCharging) "+" else "-"}${if (bat.currentMa != 0) abs(bat.currentMa) else "--"} mA")
                setTextColor(R.id.txt_col_power, powerColor)
            }
        }

        val viewsExpanded = RemoteViews(packageName, R.layout.notif_monai_expanded).apply {
            setTextViewText(R.id.txt_exp_app_title, displayTitle)
            setTextViewText(R.id.txt_exp_mode_badge, "MODE: $profileLabel")

            setViewVisibility(R.id.chip_ram, if (showNotifRam) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.chip_cpu, if (showNotifCpu) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.chip_power, if (showNotifPower) View.VISIBLE else View.GONE)

            if (showNotifRam) setTextViewText(R.id.txt_exp_ram_val, ramAppStr)
            if (showNotifCpu) setTextViewText(R.id.txt_exp_cpu_val, "$cpuUsagePct% • $cpuFreq ($cpuTemp)")
            if (showNotifPower) {
                setTextViewText(R.id.txt_exp_power_val, "$powerText • ${bat.percentage}% (${formattedTemp}°C)")
                setTextColor(R.id.txt_exp_power_val, powerColor)
            }

            if (showNotifProfiles) {
                setViewVisibility(R.id.btn_notif_perf, View.VISIBLE)
                setViewVisibility(R.id.btn_notif_bal, View.VISIBLE)
                setViewVisibility(R.id.btn_notif_save, View.VISIBLE)

                setTextViewText(R.id.btn_notif_perf, labelPerf)
                setTextViewText(R.id.btn_notif_bal, labelBal)
                setTextViewText(R.id.btn_notif_save, labelSave)

                setInt(R.id.btn_notif_perf, "setBackgroundResource", if (isPerf) R.drawable.notif_btn_bg_perf_active else R.drawable.notif_btn_bg_perf)
                setInt(R.id.btn_notif_bal, "setBackgroundResource", if (isBal) R.drawable.notif_btn_bg_bal_active else R.drawable.notif_btn_bg_bal)
                setInt(R.id.btn_notif_save, "setBackgroundResource", if (isSave) R.drawable.notif_btn_bg_save_active else R.drawable.notif_btn_bg_save)

                setTextColor(R.id.btn_notif_perf, if (isPerf) whiteColor else perfColor)
                setTextColor(R.id.btn_notif_bal, if (isBal) whiteColor else balColor)
                setTextColor(R.id.btn_notif_save, if (isSave) whiteColor else saveColor)

                setOnClickPendingIntent(R.id.btn_notif_perf, perfIntent)
                setOnClickPendingIntent(R.id.btn_notif_bal, balIntent)
                setOnClickPendingIntent(R.id.btn_notif_save, saveIntent)
            } else {
                setViewVisibility(R.id.btn_notif_perf, View.GONE)
                setViewVisibility(R.id.btn_notif_bal, View.GONE)
                setViewVisibility(R.id.btn_notif_save, View.GONE)
            }
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
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
                CHANNEL_ID, "MonProject Live Service",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "System Engine & Battery Monitor" }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}