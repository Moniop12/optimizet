package com.monai.optimizer.ui

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monai.optimizer.data.UserPreferencesRepository
import com.monai.optimizer.optimizer.ChargingController
import com.monai.optimizer.optimizer.ChargingEngine
import com.monai.optimizer.optimizer.CmdResult
import com.monai.optimizer.optimizer.DeviceAnalyzer
import com.monai.optimizer.optimizer.DeviceSpec
import com.monai.optimizer.optimizer.FrozenAppItem
import com.monai.optimizer.optimizer.MonitorEngine
import com.monai.optimizer.optimizer.OptProfile
import com.monai.optimizer.optimizer.RootEngine
import com.monai.optimizer.optimizer.SCmd
import com.monai.optimizer.optimizer.ShizukuEngine
import com.monai.optimizer.optimizer.TelemetryCache
import com.monai.optimizer.service.MonAiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class LogEntry(val time: String, val cmd: String, val success: Boolean)

class MainViewModel : ViewModel() {

    var spec by mutableStateOf<DeviceSpec?>(null)
        private set
    var hasRoot by mutableStateOf(false)
        private set
    var hasShizuku by mutableStateOf(false)
        private set
    var isOptimizing by mutableStateOf(false)
        private set
    var isRefreshing by mutableStateOf(false)
        private set
    var isLiveServiceRunning by mutableStateOf(false)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var statusMsg by mutableStateOf("")
    var statusSuccess by mutableStateOf<Boolean?>(null)
        private set
    var activeProfile by mutableStateOf<OptProfile?>(null)
        private set
    var log by mutableStateOf<List<LogEntry>>(emptyList())
        private set

    var isChargeLimitEnabled by mutableStateOf(false)
        private set
    var chargeLimitPct by mutableStateOf(80f)
        private set
    var chargeSpeedMa by mutableStateOf(UserPreferencesRepository.DEFAULT_CHARGE_SPEED_MA)
        private set
    var isThermalProtectEnabled by mutableStateOf(false)
        private set
    var isBypassChargingEnabled by mutableStateOf(false)
        private set

    var aiOptimizerEnabled by mutableStateOf(false)
        private set

    var resolutionPreset by mutableStateOf("NATIVE")
        private set

    var nativeWidth by mutableStateOf(1080)
        private set
    var nativeHeight by mutableStateOf(2400)
        private set
    var nativeDensity by mutableStateOf(400)
        private set

    var showNotifRam by mutableStateOf(true)
        private set
    var showNotifCpu by mutableStateOf(true)
        private set
    var showNotifPower by mutableStateOf(true)
        private set
    var showNotifProfiles by mutableStateOf(true)
        private set

    val runningTools = mutableStateMapOf<String, Boolean>()

    var cpuFreq by mutableStateOf("--")
        private set
    var cpuTemp by mutableStateOf("--")
        private set
    var zramInfo by mutableStateOf("--")
        private set
    var governors by mutableStateOf<List<String>>(emptyList())
        private set
    var currentGov by mutableStateOf("--")
        private set
    var liveAvailRamMb by mutableStateOf(0L)
        private set
    var ramUsedPercent by mutableStateOf(0)
        private set

    var cpuUsagePct by mutableStateOf(0)
        private set

    var freezerApps by mutableStateOf<List<FrozenAppItem>>(emptyList())
        private set
    var freezerLoading by mutableStateOf(false)
        private set
    var freezerActionPkg by mutableStateOf<String?>(null)
        private set

    private val CRITICAL_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.phone",
        "com.android.providers.telephony",
        "com.android.providers.media",
        "com.android.providers.settings",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.android.inputmethod",
        "com.android.wallpaperbackup",
        "com.android.wallpapercropper",
    )

    private val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private var isTickerRunning = false
    private var isPrefsSyncRunning = false
    private lateinit var prefsRepo: UserPreferencesRepository

    // HIGH-12/NEW-HIGH-B: SATU penulis state charging — semua aksi lewat controller
    private var chargingController: ChargingController? = null

    private var appCtx: Context? = null

    /** MEDIUM-11: WindowMetrics (API 30+) — fallback DisplayMetrics (API < 30). */
    private fun readNativeDisplayMetrics(ctx: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val bounds = wm.currentWindowMetrics.bounds
                val metrics = ctx.resources.displayMetrics
                if (bounds.width() > 0 && bounds.height() > 0) {
                    nativeWidth = bounds.width()
                    nativeHeight = bounds.height()
                    nativeDensity = metrics.densityDpi
                    return
                }
            }
            @Suppress("DEPRECATION")
            runCatching {
                val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val metrics = DisplayMetrics()
                wm.defaultDisplay.getRealMetrics(metrics)
                if (metrics.widthPixels > 0 && metrics.heightPixels > 0) {
                    nativeWidth = metrics.widthPixels
                    nativeHeight = metrics.heightPixels
                    nativeDensity = metrics.densityDpi
                }
            }
        } catch (_: Exception) {}
    }

    private fun maxRefreshRate(): Float = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm = appCtx?.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            wm?.currentWindowMetrics?.bounds?.let {
                // refresh rate dari display default via WindowManager
                val display = wm.defaultDisplay
                @Suppress("DEPRECATION")
                display?.supportedModes?.maxOfOrNull { m -> m.refreshRate } ?: 90f
            } ?: 90f
        } else {
            @Suppress("DEPRECATION")
            val wm = appCtx?.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            wm?.defaultDisplay?.supportedModes?.maxOfOrNull { it.refreshRate } ?: 90f
        }
    } catch (_: Exception) { 90f }

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        RootEngine.init(ctx)
        readNativeDisplayMetrics(ctx)

        if (!::prefsRepo.isInitialized) {
            prefsRepo = UserPreferencesRepository(ctx)
            chargingController = ChargingController.getInstance(prefsRepo)
            MonAiService.attachChargingController(chargingController!!)
            observePreferences()
        }

        viewModelScope.launch(Dispatchers.IO) {
            val root = RootEngine.hasRoot()
            val shz = ShizukuEngine.isRunning() && ShizukuEngine.hasPerm()
            val dev = DeviceAnalyzer.analyze(ctx, root, shz)

            withContext(Dispatchers.Main) {
                hasRoot = root
                hasShizuku = shz
                spec = dev
            }

            if (root) {
                RootEngine.backupOriginalStateIfNeeded()
                val gvs = RootEngine.getGovernors()
                withContext(Dispatchers.Main) { governors = gvs }
            }

            startRealTimeTicker()
        }
    }

    private fun observePreferences() {
        if (isPrefsSyncRunning) return
        isPrefsSyncRunning = true
        viewModelScope.launch {
            prefsRepo.preferencesFlow.collect { state ->
                activeProfile = state.activeProfile
                isChargeLimitEnabled = state.isChargeLimitEnabled
                chargeLimitPct = state.chargeLimitPct.toFloat()
                chargeSpeedMa = state.chargeSpeedMa
                isLiveServiceRunning = state.isLiveServiceRunning
                isThermalProtectEnabled = state.isThermalProtectEnabled
                isBypassChargingEnabled = state.isBypassChargingEnabled
                aiOptimizerEnabled = state.aiOptimizerEnabled
                resolutionPreset = state.resolutionPreset

                showNotifRam = state.showNotifRam
                showNotifCpu = state.showNotifCpu
                showNotifPower = state.showNotifPower
                showNotifProfiles = state.showNotifProfiles

                MonAiService.currentActiveProfile = state.activeProfile
                MonAiService.aiOptimizerEnabled = state.aiOptimizerEnabled

                chargingController?.syncFromPrefs(
                    chargeLimitEnabled = state.isChargeLimitEnabled,
                    chargeLimitPct = state.chargeLimitPct,
                    chargeSpeedMa = state.chargeSpeedMa,
                    thermalProtect = state.isThermalProtectEnabled,
                    bypassCharging = state.isBypassChargingEnabled,
                )
            }
        }
    }

    // HIGH-16: UI ticker MEMBACA TelemetryCache — TIDAK menjalankan shell sendiri
    // saat service hidup. Fallback ke pembacaan langsung hanya jika service mati
    // (batch basi / null).
    private fun startRealTimeTicker() {
        if (isTickerRunning) return
        isTickerRunning = true
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                var rawStat: String? = null

                val cached = TelemetryCache.readBatch()
                if (cached != null) {
                    val parts = cached.split("|||")
                    if (parts.size >= 2) {
                        rawStat = parts[0]
                        val freqRaw = parts[1].trim().toLongOrNull()?.div(1000L)
                        val tempRaw = parts.getOrNull(2)?.trim()?.toFloatOrNull()
                        val govRaw = parts.getOrNull(3)?.trim()
                        val freqStr = freqRaw?.let { "${it}MHz" } ?: "--"
                        val tempStr = if (tempRaw != null && tempRaw > 0f) {
                            "%.1f°C".format(if (tempRaw > 1000f) tempRaw / 1000f else tempRaw)
                        } else "--"
                        withContext(Dispatchers.Main) {
                            cpuFreq = freqStr
                            cpuTemp = tempStr
                            if (!govRaw.isNullOrBlank() && govRaw != "unknown") currentGov = govRaw
                        }
                    }
                }

                if (rawStat == null) {
                    // Service mati → fallback (satu shell per tick maksimal)
                    if (hasRoot) {
                        rawStat = RootEngine.su("cat /proc/stat 2>/dev/null").output
                    } else if (hasShizuku) {
                        rawStat = ShizukuEngine.sh("cat /proc/stat 2>/dev/null").output
                    } else {
                        rawStat = runCatching { java.io.File("/proc/stat").readLines().firstOrNull { it.startsWith("cpu ") } }.getOrNull()
                    }
                    val freqDisp = MonitorEngine.getCpuFreqDisplay()
                    val tempDisp = MonitorEngine.getCpuTempDisplay()
                    val zram = if (hasRoot) RootEngine.getZramInfo() else "--"
                    withContext(Dispatchers.Main) {
                        cpuFreq = freqDisp
                        cpuTemp = tempDisp
                        zramInfo = zram
                    }
                }

                val cpuPct = MonitorEngine.getCpuUsagePercent(rawStat)
                val ramSnap = MonitorEngine.getRamSnapshot()

                withContext(Dispatchers.Main) {
                    liveAvailRamMb = ramSnap.availMb
                    ramUsedPercent = ramSnap.usedPct
                    cpuUsagePct = cpuPct
                }
                delay(2000)
            }
        }
    }

    private fun postNotifLogMsg(ctx: Context, msg: String) {
        if (!isLiveServiceRunning) return
        val intent = Intent(ctx, MonAiService::class.java).apply {
            action = MonAiService.ACTION_POST_STATUS_LOG
            putExtra(MonAiService.EXTRA_LOG_MSG, msg)
        }
        runCatching { ctx.startService(intent) }
    }

    // ===== Charging (SEMUA lewat ChargingController — NEW-HIGH-B) =====

    fun setBypassCharging(enabled: Boolean) {
        isBypassChargingEnabled = enabled
        viewModelScope.launch(Dispatchers.IO) {
            val r = chargingController?.setBypassCharging(enabled)
                ?: CmdResult(false, "Controller belum siap", "set-bypass")
            withContext(Dispatchers.Main) {
                val msg = if (enabled) "Bypass Charging Enabled (Direct Power)" else "Bypass Charging Disabled"
                statusMsg = if (r.success) msg else r.output
                statusSuccess = r.success
                addLog(r.cmd, r.success)
            }
        }
    }

    fun applyDynamicResolutionScale(presetName: String, scaleFactor: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            val cmdText: String = if (scaleFactor >= 1.0f || presetName == "NATIVE") {
                "wm size reset && wm density reset"
            } else {
                var targetW = (nativeWidth * scaleFactor).roundToInt()
                var targetH = (nativeHeight * scaleFactor).roundToInt()
                var targetD = (nativeDensity * scaleFactor).roundToInt()

                if (targetW % 2 != 0) targetW += 1
                if (targetH % 2 != 0) targetH += 1

                "wm size ${targetW}x${targetH} && wm density $targetD"
            }

            val r: SCmd = if (hasRoot) {
                val rootRes = RootEngine.su(cmdText)
                SCmd(rootRes.success, rootRes.output, rootRes.cmd)
            } else {
                ShizukuEngine.sh(cmdText)
            }

            prefsRepo.setResolutionPreset(presetName)
            withContext(Dispatchers.Main) {
                resolutionPreset = presetName
                statusMsg = if (r.success) "Resolution scaled to $presetName (${(scaleFactor * 100).toInt()}%)" else "Failed to scale resolution"
                statusSuccess = r.success
                addLog(r.cmd, r.success)
            }
        }
    }

    fun toggleAiOptimizer(ctx: Context) {
        aiOptimizerEnabled = !aiOptimizerEnabled
        MonAiService.aiOptimizerEnabled = aiOptimizerEnabled
        viewModelScope.launch(Dispatchers.IO) {
            prefsRepo.setAiOptimizerEnabled(aiOptimizerEnabled)
            val hz = maxRefreshRate()
            val r: SCmd = if (hasRoot) {
                val rootRes = RootEngine.applySmoothRenderingTweaks(aiOptimizerEnabled, hz)
                SCmd(rootRes.success, rootRes.output, rootRes.cmd)
            } else {
                ShizukuEngine.applySmoothRenderingTweaks(aiOptimizerEnabled, hz)
            }
            withContext(Dispatchers.Main) {
                val status = if (aiOptimizerEnabled) "Smooth UI Engine Enabled" else "Smooth UI Engine Disabled"
                statusMsg = status
                statusSuccess = r.success
                postNotifLogMsg(ctx, "⚡ $status")
                addLog(r.cmd, r.success)
            }
        }
    }

    fun toggleNotifRam() { viewModelScope.launch(Dispatchers.IO) { prefsRepo.setNotifRamVisible(!showNotifRam) } }
    fun toggleNotifCpu() { viewModelScope.launch(Dispatchers.IO) { prefsRepo.setNotifCpuVisible(!showNotifCpu) } }
    fun toggleNotifPower() { viewModelScope.launch(Dispatchers.IO) { prefsRepo.setNotifPowerVisible(!showNotifPower) } }
    fun toggleNotifProfiles() { viewModelScope.launch(Dispatchers.IO) { prefsRepo.setNotifProfilesVisible(!showNotifProfiles) } }

    fun setChargeLimit(enabled: Boolean, pct: Float) {
        isChargeLimitEnabled = enabled
        chargeLimitPct = pct
        viewModelScope.launch(Dispatchers.IO) {
            val r = chargingController?.setChargeLimit(enabled, pct.toInt())
                ?: CmdResult(false, "Controller belum siap", "set-limit")
            withContext(Dispatchers.Main) {
                statusMsg = if (r.success) "Charge limit ${if (enabled) "${pct.toInt()}%" else "disabled"}" else r.output
                statusSuccess = r.success
                addLog(r.cmd, r.success)
            }
        }
    }

    fun setThermalProtect(enabled: Boolean) {
        isThermalProtectEnabled = enabled
        viewModelScope.launch(Dispatchers.IO) {
            val r = chargingController?.setThermalProtect(enabled)
                ?: CmdResult(false, "Controller belum siap", "set-thermal")
            withContext(Dispatchers.Main) {
                statusMsg = if (r.success) "Thermal protection ${if (enabled) "on" else "off"}" else r.output
                statusSuccess = r.success
                addLog(r.cmd, r.success)
            }
        }
    }

    fun setChargeSpeed(mA: Int) {
        val clamped = mA.coerceIn(UserPreferencesRepository.MIN_CHARGE_SPEED_MA, UserPreferencesRepository.MAX_CHARGE_SPEED_MA)
        chargeSpeedMa = clamped
        viewModelScope.launch(Dispatchers.IO) {
            val r = chargingController?.setChargeSpeed(clamped)
                ?: CmdResult(false, "Controller belum siap", "set-speed")
            withContext(Dispatchers.Main) {
                statusMsg = if (r.success) "Charging speed set to $clamped mA" else r.output
                statusSuccess = r.success
                addLog(r.cmd, r.success)
            }
        }
    }

    // HIGH-7: toggleLiveService — startForegroundService dengan try-catch.
    // State UI TIDAK optimis murni: disinkronkan via prefs flow (isLiveServiceRunning
    // ditulis service saat start/stop berhasil).
    fun toggleLiveService(ctx: Context) {
        val intent = Intent(ctx, MonAiService::class.java)
        if (isLiveServiceRunning) {
            intent.action = MonAiService.ACTION_STOP
            runCatching { ctx.startService(intent) }
                .onSuccess {
                    statusMsg = "Live Service Stopped"
                    statusSuccess = true
                }
                .onFailure { e ->
                    statusMsg = "Gagal stop service: ${e.message}"
                    statusSuccess = false
                }
        } else {
            intent.action = MonAiService.ACTION_START
            val started = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ctx.startForegroundService(intent)
                } else {
                    ctx.startService(intent)
                }
            }
            started.onSuccess {
                // JANGAN set true di sini — biarkan prefs flow dari service yang konfirmasi.
                // Fallback optimis kecil: tampilkan toast "starting" saja.
                Toast.makeText(ctx, "Live Service starting…", Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                isLiveServiceRunning = false
                statusMsg = "Gagal start service: ${e.message}"
                statusSuccess = false
                Toast.makeText(ctx, "Failed to start: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun refresh(ctx: Context) {
        viewModelScope.launch {
            isRefreshing = true
            init(ctx)
            delay(500)
            isRefreshing = false
        }
    }

    fun requestShizuku() { ShizukuEngine.requestPerm() }

    fun applyProfile(profile: OptProfile) {
        if (isOptimizing) return
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                isOptimizing = true
                progress = 0f
                statusMsg = "Running optimization..."
                statusSuccess = null
            }

            val hz = maxRefreshRate()
            val rootCmds: List<CmdResult> = if (hasRoot) when (profile) {
                OptProfile.PERFORMANCE -> RootEngine.applyPerformance(hz)
                OptProfile.BALANCED -> RootEngine.applyBalanced(hz)
                OptProfile.BATTERY -> RootEngine.applyBattery()
            } else emptyList()

            val shzCmds: List<SCmd> = if (hasShizuku && !hasRoot) when (profile) {
                OptProfile.PERFORMANCE -> ShizukuEngine.applyPerformance(hz)
                OptProfile.BALANCED -> ShizukuEngine.applyBalanced(hz)
                OptProfile.BATTERY -> ShizukuEngine.applyBattery()
            } else emptyList()

            val total = (rootCmds.size + shzCmds.size).coerceAtLeast(1)
            val newLog = mutableListOf<LogEntry>()
            var done = 0

            // LOW-6: delay(40) palsu DIHAPUS — progress murni dari hasil riil, tanpa penundaan buatan
            for (r in rootCmds) {
                done++
                withContext(Dispatchers.Main) {
                    progress = done.toFloat() / total
                    statusMsg = "[Root] ${r.cmd.take(35)}..."
                }
                newLog += LogEntry(sdf.format(Date()), r.cmd, r.success)
            }

            for (r in shzCmds) {
                done++
                withContext(Dispatchers.Main) {
                    progress = done.toFloat() / total
                    statusMsg = "[ADB] ${r.cmd.take(35)}..."
                }
                newLog += LogEntry(sdf.format(Date()), r.cmd, r.success)
            }

            prefsRepo.setActiveProfile(profile)

            withContext(Dispatchers.Main) {
                progress = 1f
                statusMsg = "Done (${newLog.count { it.success }}/${newLog.size} OK)"
                statusSuccess = newLog.isNotEmpty() && newLog.all { it.success }
                activeProfile = profile
                MonAiService.currentActiveProfile = profile
                log = (newLog + log).take(MAX_LOG_ENTRIES)
                isOptimizing = false
            }
        }
    }

    fun resetToDefaults() {
        if (isOptimizing) return
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                isOptimizing = true
                progress = 0f
                statusMsg = "Restoring defaults..."
                statusSuccess = null
            }

            val newLog: List<LogEntry>
            val resultMsg: String
            when {
                hasRoot -> {
                    val res = RootEngine.resetToDefaults() + listOf(RootEngine.restoreBackgroundAppOps())
                    newLog = res.map { LogEntry(sdf.format(Date()), it.cmd, it.success) }
                    resultMsg = if (res.all { it.success }) "Reset to factory defaults (Root — full)"
                    else "Reset partially failed — check Log"
                }
                hasShizuku -> {
                    val res = ShizukuEngine.resetToDefaults()
                    newLog = res.map { LogEntry(sdf.format(Date()), it.cmd, it.success) }
                    resultMsg = if (res.all { it.success })
                        "Reset applied (Shizuku — anim & process limit only)"
                    else "Reset partially failed — check Log"
                }
                else -> {
                    newLog = emptyList()
                    resultMsg = "No root or Shizuku — nothing to reset"
                }
            }

            aiOptimizerEnabled = false
            MonAiService.aiOptimizerEnabled = false
            prefsRepo.setAiOptimizerEnabled(false)
            prefsRepo.setActiveProfile(null)

            withContext(Dispatchers.Main) {
                progress = 1f
                statusMsg = resultMsg
                statusSuccess = newLog.isNotEmpty() && newLog.all { it.success }
                activeProfile = null
                MonAiService.currentActiveProfile = null
                log = (newLog + log).take(MAX_LOG_ENTRIES)
                isOptimizing = false
            }
        }
    }

    fun setGovernor(gov: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val r = RootEngine.setGovernor(gov)
            withContext(Dispatchers.Main) {
                if (r.success) {
                    currentGov = gov
                    statusMsg = "CPU Governor -> $gov"
                    statusSuccess = true
                } else {
                    statusMsg = "Failed to set $gov"
                    statusSuccess = false
                }
                addLog(r.cmd, r.success)
            }
        }
    }

    fun runTool(ctx: Context, toolId: String, label: String, rootCmd: String, shzFn: () -> SCmd) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { runningTools[toolId] = true }

            var success = false
            var cmdText = ""

            if (hasRoot) {
                val r = RootEngine.su(rootCmd)
                success = r.success
                cmdText = r.cmd
            } else if (hasShizuku) {
                val r = shzFn()
                success = r.success
                cmdText = r.cmd
            }

            withContext(Dispatchers.Main) {
                runningTools[toolId] = false
                val msg = if (success) "$label succeeded" else "$label failed"
                statusMsg = msg
                statusSuccess = success
                postNotifLogMsg(ctx, "⚡ $msg")
                addLog(cmdText, success)
            }
        }
    }

    /** MEDIUM-5: Restrict background — snapshot dulu, lalu aplikasikan. */
    fun restrictBackground(ctx: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { runningTools["restrict_bg"] = true }

            // Snapshot status appops SEBELUM diubah — untuk restore nanti
            RootEngine.snapshotBackgroundAppOps()

            val r: SCmd = if (hasRoot) {
                val rootRes = RootEngine.su(
                    "for pkg in \\$(pm list packages -3 | cut -d: -f2); do appops set \\$pkg RUN_IN_BACKGROUND ignore; appops set \\$pkg RUN_ANY_IN_BACKGROUND ignore; done; echo done",
                )
                SCmd(rootRes.success, rootRes.output, rootRes.cmd)
            } else {
                ShizukuEngine.restrictBackground()
            }

            withContext(Dispatchers.Main) {
                runningTools["restrict_bg"] = false
                statusMsg = if (r.success) "Background restrictions applied (snapshot saved)" else "Restrict failed"
                statusSuccess = r.success
                addLog(r.cmd, r.success)
            }
        }
    }

    /** MEDIUM-5: Restore appops RUN_IN_BACKGROUND dari snapshot (tombol di Tools). */
    fun restoreBackgroundRestrictions(ctx: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { runningTools["restore_bg"] = true }
            val r = RootEngine.restoreBackgroundAppOps()
            withContext(Dispatchers.Main) {
                runningTools["restore_bg"] = false
                statusMsg = if (r.success) "Background restrictions restored" else r.output
                statusSuccess = r.success
                addLog(r.cmd, r.success)
            }
        }
    }

    /** HIGH-9: Deep RAM Cleaner → trim memory aman (bukan am kill-all). */
    fun trimMemorySafe(ctx: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { runningTools["ram_clean"] = true }
            val r: SCmd = if (hasRoot) {
                val rootRes = RootEngine.trimMemorySafe()
                SCmd(rootRes.success, rootRes.output, rootRes.cmd)
            } else {
                ShizukuEngine.trimMemory()
            }
            withContext(Dispatchers.Main) {
                runningTools["ram_clean"] = false
                statusMsg = if (r.success) "Memory trim completed" else "Memory trim failed"
                statusSuccess = r.success
                postNotifLogMsg(ctx, "⚡ ${statusMsg}")
                addLog(r.cmd, r.success)
            }
        }
    }

    fun loadFreezerApps(ctx: Context) {
        if (freezerLoading) return
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { freezerLoading = true }

            // HIGH-13: QUERY_ALL_PACKAGES dihapus dari manifest → daftar app
            // diambil dari PackageManager (app yang terlihat) + daftar disabled
            // via shell jika ada kontrol (pm list packages -d).
            val disabledPkgs = if (hasShizuku || hasRoot) ShizukuEngine.listDisabledPkgs() else emptySet()

            val pm = ctx.packageManager
            val pkgs = runCatching { pm.getInstalledPackages(0) }.getOrElse { emptyList() }

            val defaultLauncherPkg = runCatching {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            }.getOrNull()

            val activeIme = runCatching {
                Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            }.getOrNull()?.split("/")?.firstOrNull()

            val items = pkgs
                .filter { it.packageName != ctx.packageName }
                .mapNotNull { pi ->
                    val appInfo = pi.applicationInfo ?: return@mapNotNull null
                    val label = runCatching { pm.getApplicationLabel(appInfo).toString() }.getOrElse { pi.packageName }
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                    val isCritical = CRITICAL_PACKAGES.contains(pi.packageName) ||
                        pi.packageName == defaultLauncherPkg ||
                        pi.packageName == activeIme ||
                        pi.packageName.startsWith("com.android.providers")

                    val isDisabled = !appInfo.enabled || pi.packageName in disabledPkgs

                    FrozenAppItem(
                        name = label,
                        pkg = pi.packageName,
                        isSystem = isSystem,
                        isFrozen = isDisabled,
                        isDisabled = isDisabled,
                        isCritical = isCritical,
                    )
                }
                .sortedWith(
                    compareBy<FrozenAppItem> { !it.isLocked }
                        .thenBy { it.isCritical }
                        .thenBy { it.isSystem }
                        .thenBy { it.name.lowercase() },
                )

            withContext(Dispatchers.Main) {
                freezerApps = items
                freezerLoading = false
            }
        }
    }

    // HIGH-8: toggleFreezeApp berdasarkan HASIL RIIL (r.success + output frozen/active),
    // bukan asumsi status. Package name di-escape (MEDIUM-14).
    fun toggleFreezeApp(ctx: Context, item: FrozenAppItem) {
        if (item.isCritical) {
            Toast.makeText(ctx, "System App ${item.name} is protected to prevent bootloops!", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { freezerActionPkg = item.pkg }

            val r: SCmd = if (hasRoot) {
                val cmd = if (item.isLocked) "pm enable --user 0 ${RootEngine.shellEscape(item.pkg)}" else "pm disable-user --user 0 ${RootEngine.shellEscape(item.pkg)}"
                val rootRes = RootEngine.su(cmd)
                SCmd(rootRes.success, rootRes.output, rootRes.cmd)
            } else {
                if (item.isLocked) ShizukuEngine.unfreezeApp(item.pkg)
                else ShizukuEngine.freezeApp(item.pkg)
            }

            withContext(Dispatchers.Main) {
                freezerActionPkg = null

                // Verifikasi HASIL RIIL — bukan asumsi:
                // - root: r.success == true
                // - shizuku: output mengandung "frozen"/"active"
                val targetFrozen = !item.isLocked
                val succeeded = if (hasRoot) {
                    r.success
                } else {
                    r.success && (r.output.contains("frozen") || r.output.contains("active"))
                }

                if (succeeded) {
                    freezerApps = freezerApps.map { app ->
                        if (app.pkg == item.pkg) app.copy(isFrozen = targetFrozen, isDisabled = targetFrozen)
                        else app
                    }
                    statusMsg = if (targetFrozen) "${item.name} frozen" else "${item.name} unfrozen"
                    statusSuccess = true
                } else {
                    statusMsg = "Gagal ${if (targetFrozen) "membekukan" else "membuka"} ${item.name} (${r.output.take(60)})"
                    statusSuccess = false
                }
                addLog(r.cmd, succeeded)
            }
        }
    }

    fun copyLogsToClipboard(ctx: Context) {
        if (log.isEmpty()) return
        val text = log.joinToString("\n") { "[${it.time}] [${if (it.success) "OK" else "FAIL"}] ${it.cmd}" }
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("MonProject Logs", text))
        Toast.makeText(ctx, "Logs copied to clipboard!", Toast.LENGTH_SHORT).show()
    }

    fun clearLogHistory() { log = emptyList() }

    // MEDIUM-15: batasi log — helper tunggal
    private fun addLog(cmd: String, success: Boolean) {
        log = (listOf(LogEntry(sdf.format(Date()), cmd, success)) + log).take(MAX_LOG_ENTRIES)
    }

    companion object {
        private const val MAX_LOG_ENTRIES = 200
    }
}