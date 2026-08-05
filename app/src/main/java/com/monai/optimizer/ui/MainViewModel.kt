package com.monai.optimizer.ui

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
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
import com.monai.optimizer.optimizer.ChargingEngine
import com.monai.optimizer.optimizer.CmdResult
import com.monai.optimizer.optimizer.DeviceAnalyzer
import com.monai.optimizer.optimizer.DeviceSpec
import com.monai.optimizer.optimizer.OptProfile
import com.monai.optimizer.optimizer.RootEngine
import com.monai.optimizer.optimizer.SCmd
import com.monai.optimizer.optimizer.ShizukuEngine
import com.monai.optimizer.service.MonAiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    var cacheSizeMb by mutableStateOf(0L)
        private set

    private val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private var isTickerRunning = false
    private var isPrefsSyncRunning = false
    private lateinit var prefsRepo: UserPreferencesRepository

    private var appCtx: Context? = null

    @Suppress("DEPRECATION")
    private fun maxRefreshRate(): Float = try {
        val wm = appCtx?.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        wm?.defaultDisplay?.supportedModes?.maxOfOrNull { it.refreshRate } ?: 90f
    } catch (_: Exception) { 90f }

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        RootEngine.init(ctx)
        if (!::prefsRepo.isInitialized) {
            prefsRepo = UserPreferencesRepository(ctx)
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

            startRealTimeTicker(ctx)
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
            }
        }
    }

    private fun startRealTimeTicker(ctx: Context) {
        if (isTickerRunning) return
        isTickerRunning = true
        viewModelScope.launch(Dispatchers.IO) {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mem = ActivityManager.MemoryInfo()

            while (isActive) {
                am.getMemoryInfo(mem)
                val avail = mem.availMem / (1024L * 1024L)
                val total = mem.totalMem / (1024L * 1024L)
                val usedPct = (((total - avail).toDouble() / total.toDouble()) * 100).toInt()

                var fr = "--"
                var tp = "--"
                var zr = "--"
                var gv = "--"
                var cSize = 0L

                if (hasRoot) {
                    fr = RootEngine.getCpuFreqInfo()
                    tp = RootEngine.getCpuTemp()
                    zr = RootEngine.getZramInfo()
                    gv = RootEngine.getCurrentGovernor()
                    cSize = RootEngine.getEstimatedCacheSizeMb()
                }

                withContext(Dispatchers.Main) {
                    liveAvailRamMb = avail
                    ramUsedPercent = usedPct
                    cpuFreq = fr
                    cpuTemp = tp
                    zramInfo = zr
                    currentGov = gv
                    cacheSizeMb = cSize
                }
                delay(1500)
            }
        }
    }

    private fun postNotifLogMsg(ctx: Context, msg: String) {
        if (!isLiveServiceRunning) return
        val intent = Intent(ctx, MonAiService::class.java).apply {
            action = MonAiService.ACTION_POST_STATUS_LOG
            putExtra(MonAiService.EXTRA_LOG_MSG, msg)
        }
        ctx.startService(intent)
    }

    // ── FITUR BARU LOGGING: Bypass Charging Toggle ───────────────────
    fun setBypassCharging(enabled: Boolean) {
        isBypassChargingEnabled = enabled
        viewModelScope.launch(Dispatchers.IO) {
            prefsRepo.setBypassChargingEnabled(enabled)
            val r = ChargingEngine.setBypassCharging(enabled)
            withContext(Dispatchers.Main) {
                val msg = if (enabled) "Bypass Charging Enabled (Direct Power)" else "Bypass Charging Disabled"
                statusMsg = if (r.success) msg else "Kernel does not support Bypass Charging"
                statusSuccess = r.success
                log = listOf(LogEntry(sdf.format(Date()), r.cmd, r.success)) + log
            }
        }
    }

    // ── FITUR BARU LOGGING: Resolution Preset Scaler ────────────────
    fun applyResolutionPreset(preset: String, size: String?, density: Int?) {
        viewModelScope.launch(Dispatchers.IO) {
            val r: SCmd = if (hasRoot) {
                val rootRes = RootEngine.su(if (size == null) "wm size reset && wm density reset" else "wm size $size && wm density $density")
                SCmd(rootRes.success, rootRes.output, rootRes.cmd)
            } else {
                ShizukuEngine.setResolutionPreset(size, density)
            }
            prefsRepo.setResolutionPreset(preset)
            withContext(Dispatchers.Main) {
                resolutionPreset = preset
                statusMsg = if (r.success) "Resolution preset set to $preset" else "Failed to set resolution"
                statusSuccess = r.success
                log = listOf(LogEntry(sdf.format(Date()), r.cmd, r.success)) + log
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
                postNotifLogMsg(ctx, "✨ $status")
                log = listOf(LogEntry(sdf.format(Date()), r.cmd, r.success)) + log
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
        MonAiService.isChargeLimitEnabled = enabled
        MonAiService.chargeLimitPct = pct.toInt()
        viewModelScope.launch(Dispatchers.IO) {
            prefsRepo.setChargeLimit(enabled, pct.toInt())
            if (!enabled && hasRoot) {
                val r = ChargingEngine.setChargingEnabled(true)
                MonAiService.isChargePausedByLimit = false
                withContext(Dispatchers.Main) {
                    log = listOf(LogEntry(sdf.format(Date()), r.cmd, r.success)) + log
                }
            }
        }
    }

    fun setThermalProtect(enabled: Boolean) {
        isThermalProtectEnabled = enabled
        MonAiService.isThermalProtectEnabled = enabled
        viewModelScope.launch(Dispatchers.IO) {
            prefsRepo.setThermalProtectEnabled(enabled)
            if (!enabled && hasRoot && MonAiService.isThermalThrottled) {
                val r = ChargingEngine.setChargeCurrentMaxMa(chargeSpeedMa)
                MonAiService.isThermalThrottled = false
                withContext(Dispatchers.Main) {
                    log = listOf(LogEntry(sdf.format(Date()), r.cmd, r.success)) + log
                }
            }
        }
    }

    fun setChargeSpeed(mA: Int) {
        val clamped = mA.coerceIn(UserPreferencesRepository.MIN_CHARGE_SPEED_MA, UserPreferencesRepository.MAX_CHARGE_SPEED_MA)
        chargeSpeedMa = clamped
        MonAiService.chargeSpeedMa = clamped
        viewModelScope.launch(Dispatchers.IO) {
            prefsRepo.setChargeSpeedMa(clamped)
            if (hasRoot) {
                val r = ChargingEngine.setChargeCurrentMaxMa(clamped)
                withContext(Dispatchers.Main) {
                    statusMsg = if (r.success) "Charging speed set to $clamped mA" else "Kernel does not support current limiting"
                    statusSuccess = r.success
                    log = listOf(LogEntry(sdf.format(Date()), r.cmd, r.success)) + log
                }
            }
        }
    }

    fun toggleLiveService(ctx: Context) {
        val intent = Intent(ctx, MonAiService::class.java)
        if (isLiveServiceRunning) {
            intent.action = MonAiService.ACTION_STOP
            ctx.startService(intent)
            isLiveServiceRunning = false
            Toast.makeText(ctx, "Live Service Stopped", Toast.LENGTH_SHORT).show()
        } else {
            intent.action = MonAiService.ACTION_START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
            isLiveServiceRunning = true
            Toast.makeText(ctx, "Live Service Enabled!", Toast.LENGTH_SHORT).show()
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

            for (r in rootCmds) {
                done++
                withContext(Dispatchers.Main) {
                    progress = done.toFloat() / total
                    statusMsg = "[Root] ${r.cmd.take(35)}..."
                }
                newLog += LogEntry(sdf.format(Date()), r.cmd, r.success)
                delay(40)
            }

            for (r in shzCmds) {
                done++
                withContext(Dispatchers.Main) {
                    progress = done.toFloat() / total
                    statusMsg = "[ADB] ${r.cmd.take(35)}..."
                }
                newLog += LogEntry(sdf.format(Date()), r.cmd, r.success)
                delay(40)
            }

            prefsRepo.setActiveProfile(profile)

            withContext(Dispatchers.Main) {
                progress = 1f
                statusMsg = "Done (${newLog.count { it.success }}/${newLog.size} OK)"
                statusSuccess = newLog.isNotEmpty() && newLog.all { it.success }
                activeProfile = profile
                MonAiService.currentActiveProfile = profile
                log = newLog + log
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
                    val res = RootEngine.resetToDefaults()
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
                log = newLog + log
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
                log = listOf(LogEntry(sdf.format(Date()), r.cmd, r.success)) + log
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

            delay(200)
            withContext(Dispatchers.Main) {
                runningTools[toolId] = false
                val msg = if (success) "$label succeeded" else "$label failed"
                statusMsg = msg
                statusSuccess = success
                postNotifLogMsg(ctx, "⚡ $msg")
                log = listOf(LogEntry(sdf.format(Date()), cmdText, success)) + log
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
}