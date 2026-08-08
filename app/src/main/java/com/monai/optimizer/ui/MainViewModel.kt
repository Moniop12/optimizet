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
import androidx.compose.runtime.mutableStateListOf
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
import com.monai.optimizer.optimizer.FrozenAppItem
import com.monai.optimizer.optimizer.MonitorEngine
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
    var privateDnsPreset by mutableStateOf("OFF")
    private set
    var tcpCongestionPreset by mutableStateOf("DEFAULT")
    private set
    var ioReadAheadPreset by mutableStateOf("DEFAULT")
    private set

    var batteryHealthPct by mutableStateOf(0.0)
    private set
    var batteryCycleCount by mutableStateOf(0)
    private set
    var batteryChargeFull by mutableStateOf(0L)
    private set
    var batteryChargeDesign by mutableStateOf(0L)
    private set

    var artCompiledAppsMap by mutableStateOf<Map<String, String>>(emptyMap())
    private set

    val dexoptTerminalLogs = mutableStateListOf<String>()
    val selectedDexoptPkgs = mutableStateListOf<String>()
    var dexoptMode by mutableStateOf("speed-profile")
    var isDexoptRunning by mutableStateOf(false)
    private set
    var dexoptProgress by mutableFloatStateOf(0f)
    private set
    @Volatile private var isDexoptCancelled = false

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
        "com.android.wallpapercropper"
    )

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

    @Suppress("DEPRECATION")
    private fun readNativeDisplayMetrics(ctx: Context) {
        try {
            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(metrics)
            if (metrics.widthPixels > 0 && metrics.heightPixels > 0) {
                nativeWidth = metrics.widthPixels
                nativeHeight = metrics.heightPixels
                nativeDensity = metrics.densityDpi
            }
        } catch (_: Exception) {}
    }

    private fun addLogEntry(entry: LogEntry) {
        val updated = listOf(entry) + log
        log = if (updated.size > 200) updated.take(200) else updated
    }

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        RootEngine.init(ctx)
        readNativeDisplayMetrics(ctx)

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

            readHardwareBatteryHealth()
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
                privateDnsPreset = state.privateDnsPreset
                tcpCongestionPreset = state.tcpCongestionPreset
                ioReadAheadPreset = state.ioReadAheadPreset
                artCompiledAppsMap = parseArtMap(state.artCompiledAppsRaw)

                showNotifRam = state.showNotifRam
                showNotifCpu = state.showNotifCpu
                showNotifPower = state.showNotifPower
                showNotifProfiles = state.showNotifProfiles

                MonAiService.currentActiveProfile = state.activeProfile
                MonAiService.aiOptimizerEnabled = state.aiOptimizerEnabled
            }
        }
    }

    private fun parseArtMap(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return raw.split(",")
            .mapNotNull { entry ->
                val parts = entry.split(":")
                if (parts.size == 2) parts[0] to parts[1] else null
            }.toMap()
    }

    private fun readHardwareBatteryHealth() {
        try {
            val cycleNode = listOf(
                "/sys/class/power_supply/battery/cycle_count",
                "/sys/class/power_supply/battery/charge_cycle",
                "/sys/class/power_supply/bms/cycle_count"
            ).firstOrNull { java.io.File(it).exists() }
            
            val cycle = cycleNode?.let { RootEngine.su("cat $it 2>/dev/null").output.trim().toIntOrNull() } ?: 0
            val full = RootEngine.su("cat /sys/class/power_supply/battery/charge_full 2>/dev/null").output.trim().toLongOrNull() ?: 0L
            val design = RootEngine.su("cat /sys/class/power_supply/battery/charge_full_design 2>/dev/null").output.trim().toLongOrNull() ?: 0L

            val health = if (full > 0 && design > 0) {
                ((full.toDouble() / design.toDouble()) * 100.0).coerceIn(0.0, 100.0)
            } else 0.0

            viewModelScope.launch(Dispatchers.Main) {
                batteryCycleCount = cycle
                batteryChargeFull = full / 1000L
                batteryChargeDesign = design / 1000L
                batteryHealthPct = health
            }
        } catch (_: Exception) {}
    }

    private fun startRealTimeTicker() {
        if (isTickerRunning) return
        isTickerRunning = true
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                var rawStat: String? = null
                if (hasRoot) {
                    rawStat = RootEngine.su("cat /proc/stat 2>/dev/null").output
                } else if (hasShizuku) {
                    rawStat = ShizukuEngine.sh("cat /proc/stat 2>/dev/null").output
                } else {
                    rawStat = runCatching { java.io.File("/proc/stat").readLines().firstOrNull { it.startsWith("cpu ") } }.getOrNull()
                }

                val cpuPct = MonitorEngine.getCpuUsagePercent(rawStat)
                val ramSnap = MonitorEngine.getRamSnapshot()

                withContext(Dispatchers.Main) {
                    liveAvailRamMb = ramSnap.availMb
                    ramUsedPercent = ramSnap.usedPct
                    cpuUsagePct = cpuPct
                    cpuFreq = MonitorEngine.getCpuFreqDisplay()
                    cpuTemp = MonitorEngine.getCpuTempDisplay()
                }
                delay(2000)
            }
        }
    }

    fun toggleSelectDexoptPkg(pkg: String) {
        if (pkg in selectedDexoptPkgs) selectedDexoptPkgs.remove(pkg)
        else selectedDexoptPkgs.add(pkg)
    }

    fun selectAllUserDexoptPkgs() {
        selectedDexoptPkgs.clear()
        selectedDexoptPkgs.addAll(freezerApps.filter { !it.isSystem && !it.isCritical }.map { it.pkg })
    }

    fun clearDexoptPkgSelection() {
        selectedDexoptPkgs.clear()
    }

    fun stopDexoptCompilation() {
        isDexoptCancelled = true
    }

    fun compileAppsArtDexopt(ctx: Context, mode: String = dexoptMode) {
        if (isDexoptRunning || selectedDexoptPkgs.isEmpty()) return
        isDexoptCancelled = false

        viewModelScope.launch(Dispatchers.IO) {
            val targets = selectedDexoptPkgs.toList()
            val timeStr = sdf.format(Date())
            withContext(Dispatchers.Main) {
                isDexoptRunning = true
                dexoptTerminalLogs.clear()
                dexoptProgress = 0f
                dexoptTerminalLogs.add("[$timeStr] [SYS] Initializing ART Dexopt Compilation Engine...")
                dexoptTerminalLogs.add("[$timeStr] [SYS] Mode: $mode | Target Apps: ${targets.size}")
            }

            val total = targets.size
            var completed = 0

            for (pkg in targets) {
                if (isDexoptCancelled) {
                    withContext(Dispatchers.Main) {
                        dexoptTerminalLogs.add("[ABORT] Compilation stopped by user.")
                        statusMsg = "ART Compilation Aborted"
                        statusSuccess = false
                    }
                    break
                }

                val startTime = System.currentTimeMillis()
                val cmd = "cmd package compile -m $mode -f $pkg"
                val pkgNameDisplay = freezerApps.firstOrNull { it.pkg == pkg }?.name ?: pkg
                
                withContext(Dispatchers.Main) {
                    dexoptTerminalLogs.add("[${sdf.format(Date())}] Target: $pkgNameDisplay ($pkg)")
                    dexoptTerminalLogs.add("[${sdf.format(Date())}] Executing: $cmd")
                }

                val r: SCmd = if (hasRoot) {
                    val rootRes = RootEngine.su(cmd)
                    SCmd(rootRes.success, rootRes.output, rootRes.cmd)
                } else {
                    ShizukuEngine.sh(cmd)
                }

                val duration = System.currentTimeMillis() - startTime
                completed++
                val success = r.output.contains("Success") || r.success

                if (success) {
                    prefsRepo.saveArtCompiledApp(pkg, mode)
                }

                withContext(Dispatchers.Main) {
                    dexoptProgress = completed.toFloat() / total
                    dexoptTerminalLogs.add("[${sdf.format(Date())}] Exit Code: ${if (success) 0 else -1} | Duration: ${duration}ms")
                    if (success) {
                        dexoptTerminalLogs.add("[OK] $pkgNameDisplay successfully compiled & state saved!")
                    } else {
                        dexoptTerminalLogs.add("[FAIL] $pkgNameDisplay failed: ${r.output.ifBlank { "Unknown Error" }}")
                    }
                    addLogEntry(LogEntry(sdf.format(Date()), cmd, success))
                }
            }

            withContext(Dispatchers.Main) {
                if (!isDexoptCancelled) {
                    dexoptTerminalLogs.add("[${sdf.format(Date())}] [SYS] ART Dexopt Compilation Finished.")
                    statusMsg = "ART Dexopt Speed Compilation Finished"
                    statusSuccess = true
                }
                isDexoptRunning = false
            }
        }
    }

    fun restoreOriginalArtDexopt(pkgs: List<String>) {
        if (isDexoptRunning || pkgs.isEmpty()) return
        isDexoptCancelled = false

        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                isDexoptRunning = true
                dexoptTerminalLogs.clear()
                dexoptProgress = 0f
                dexoptTerminalLogs.add("[SYS] Restoring Apps to Original Dexopt State...")
            }

            val total = pkgs.size
            var completed = 0

            for (pkg in pkgs) {
                if (isDexoptCancelled) break
                val startTime = System.currentTimeMillis()
                val cmd = "cmd package compile --reset $pkg"
                val pkgNameDisplay = freezerApps.firstOrNull { it.pkg == pkg }?.name ?: pkg

                withContext(Dispatchers.Main) {
                    dexoptTerminalLogs.add("[${sdf.format(Date())}] Resetting: $pkgNameDisplay ($pkg)")
                    dexoptTerminalLogs.add("[${sdf.format(Date())}] Executing: $cmd")
                }

                val r: SCmd = if (hasRoot) {
                    val rootRes = RootEngine.su(cmd)
                    SCmd(rootRes.success, rootRes.output, rootRes.cmd)
                } else {
                    ShizukuEngine.sh(cmd)
                }

                val duration = System.currentTimeMillis() - startTime
                completed++
                val success = r.success || r.output.contains("Success")
                prefsRepo.removeArtCompiledApp(pkg)

                withContext(Dispatchers.Main) {
                    dexoptProgress = completed.toFloat() / total
                    dexoptTerminalLogs.add("[${sdf.format(Date())}] Duration: ${duration}ms | State: Original Restored")
                    addLogEntry(LogEntry(sdf.format(Date()), cmd, success))
                }
            }

            withContext(Dispatchers.Main) {
                dexoptTerminalLogs.add("[SYS] Original Dexopt State Restored.")
                isDexoptRunning = false
                statusMsg = "Apps Restored to Original Dexopt State"
                statusSuccess = true
            }
        }
    }

    fun applyTcpCongestion(algo: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val cmd = "sysctl -w net.ipv4.tcp_congestion_control=$algo"
            val r = RootEngine.su(cmd)
            
            val readBack = RootEngine.su("sysctl -n net.ipv4.tcp_congestion_control").output.trim()
            val finalSuccess = r.success && readBack == algo
            
            prefsRepo.setTcpCongestionPreset(if (finalSuccess) algo else tcpCongestionPreset)
            
            withContext(Dispatchers.Main) {
                if (finalSuccess) {
                    tcpCongestionPreset = algo
                    statusMsg = "TCP Congestion set to $algo"
                } else {
                    statusMsg = "Kernel does not support TCP $algo"
                }
                statusSuccess = finalSuccess
                addLogEntry(LogEntry(sdf.format(Date()), cmd, finalSuccess))
            }
        }
    }

    fun applyIoReadAhead(readAheadKb: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val cmd = "for q in /sys/block/sd*/queue/read_ahead_kb /sys/block/mmcblk*/queue/read_ahead_kb; do " +
                    "[ -f \$q ] && echo $readAheadKb > \$q; done; echo done"
            val r = RootEngine.su(cmd)
            prefsRepo.setIoReadAheadPreset(if (r.success) readAheadKb else ioReadAheadPreset)
            withContext(Dispatchers.Main) {
                if (r.success) ioReadAheadPreset = readAheadKb
                statusMsg = if (r.success) "I/O Read-Ahead set to ${readAheadKb}KB" else "Failed to set I/O Read-Ahead"
                statusSuccess = r.success
                addLogEntry(LogEntry(sdf.format(Date()), cmd, r.success))
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

    fun applyPrivateDns(preset: String, cmdText: String, shzFn: () -> SCmd) {
        viewModelScope.launch(Dispatchers.IO) {
            val r: SCmd = if (hasRoot) {
                val rootRes = RootEngine.su(cmdText)
                SCmd(rootRes.success, rootRes.output, rootRes.cmd)
            } else {
                shzFn()
            }

            prefsRepo.setPrivateDnsPreset(if (r.success) preset else privateDnsPreset)
            withContext(Dispatchers.Main) {
                if (r.success) privateDnsPreset = preset
                statusMsg = if (r.success) "Private DNS set to $preset" else "Failed to set Private DNS"
                statusSuccess = r.success
                addLogEntry(LogEntry(sdf.format(Date()), r.cmd, r.success))
            }
        }
    }

    fun setBypassCharging(enabled: Boolean) {
        isBypassChargingEnabled = enabled
        viewModelScope.launch(Dispatchers.IO) {
            prefsRepo.setBypassChargingEnabled(enabled)
            val r = ChargingEngine.setBypassCharging(enabled)
            withContext(Dispatchers.Main) {
                val msg = if (enabled) "Bypass Charging Enabled (Direct Power)" else "Bypass Charging Disabled"
                statusMsg = if (r.success) msg else "Kernel does not support Bypass Charging"
                statusSuccess = r.success
                addLogEntry(LogEntry(sdf.format(Date()), r.cmd, r.success))
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
                addLogEntry(LogEntry(sdf.format(Date()), r.cmd, r.success))
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
                val status = if (aiOptimizerEnabled) "Window Animation Scaler Enabled" else "Window Animation Scaler Disabled"
                statusMsg = status
                statusSuccess = r.success
                postNotifLogMsg(ctx, "⚡ $status")
                addLogEntry(LogEntry(sdf.format(Date()), r.cmd, r.success))
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
                    addLogEntry(LogEntry(sdf.format(Date()), r.cmd, r.success))
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
                    addLogEntry(LogEntry(sdf.format(Date()), r.cmd, r.success))
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
                    addLogEntry(LogEntry(sdf.format(Date()), r.cmd, r.success))
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
                statusMsg = "Applying system configurations..."
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
                newLog.forEach { addLogEntry(it) }
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
                statusMsg = "Restoring features to stock defaults..."
                statusSuccess = null
            }

            val resetLogs = mutableListOf<LogEntry>()

            fun runShellReset(cmd: String): Boolean {
                return if (hasRoot) RootEngine.su(cmd).success else ShizukuEngine.sh(cmd).success
            }

            val compiledPkgs = artCompiledAppsMap.keys.toList()
            if (compiledPkgs.isNotEmpty()) {
                for (pkg in compiledPkgs) {
                    val cmd = "cmd package compile --reset $pkg"
                    val ok = runShellReset(cmd)
                    resetLogs.add(LogEntry(sdf.format(Date()), cmd, ok))
                }
            }

            val dnsResetCmd = "settings put global private_dns_mode opportunistic; settings delete global private_dns_specifier"
            val dnsOk = runShellReset(dnsResetCmd)
            resetLogs.add(LogEntry(sdf.format(Date()), dnsResetCmd, dnsOk))

            if (hasRoot) {
                val tcpCmd = "sysctl -w net.ipv4.tcp_congestion_control=cubic"
                val tcpRes = RootEngine.su(tcpCmd)
                resetLogs.add(LogEntry(sdf.format(Date()), tcpCmd, tcpRes.success))
            }

            if (hasRoot) {
                val ioCmd = "for q in /sys/block/sd*/queue/read_ahead_kb /sys/block/mmcblk*/queue/read_ahead_kb; do [ -f \$q ] && echo 128 > \$q; done"
                val ioRes = RootEngine.su(ioCmd)
                resetLogs.add(LogEntry(sdf.format(Date()), ioCmd, ioRes.success))
            }

            val wmResetCmd = "wm size reset && wm density reset"
            val wmOk = runShellReset(wmResetCmd)
            resetLogs.add(LogEntry(sdf.format(Date()), wmResetCmd, wmOk))

            if (hasRoot) {
                val rootResList = RootEngine.resetToDefaults()
                rootResList.forEach { resetLogs.add(LogEntry(sdf.format(Date()), it.cmd, it.success)) }
            } else if (hasShizuku) {
                val shzResList = ShizukuEngine.resetToDefaults()
                shzResList.forEach { resetLogs.add(LogEntry(sdf.format(Date()), it.cmd, it.success)) }
            }

            prefsRepo.clearAllPreferences()

            aiOptimizerEnabled = false
            MonAiService.aiOptimizerEnabled = false
            resolutionPreset = "NATIVE"
            privateDnsPreset = "OFF"
            tcpCongestionPreset = "DEFAULT"
            ioReadAheadPreset = "DEFAULT"
            artCompiledAppsMap = emptyMap()

            withContext(Dispatchers.Main) {
                progress = 1f
                statusMsg = "Factory Stock Reset Complete (${resetLogs.count { it.success }}/${resetLogs.size} OK)"
                statusSuccess = resetLogs.isNotEmpty() && resetLogs.all { it.success }
                activeProfile = null
                MonAiService.currentActiveProfile = null
                resetLogs.forEach { addLogEntry(it) }
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
                addLogEntry(LogEntry(sdf.format(Date()), r.cmd, r.success))
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
                addLogEntry(LogEntry(sdf.format(Date()), cmdText, success))
            }
        }
    }

    fun loadFreezerApps(ctx: Context) {
        if (freezerLoading) return
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { freezerLoading = true }

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
                        isCritical = isCritical
                    )
                }
                .sortedWith(
                    compareBy<FrozenAppItem> { !it.isLocked }
                    .thenBy { it.isCritical }
                    .thenBy { it.isSystem }
                    .thenBy { it.name.lowercase() }
                )

            withContext(Dispatchers.Main) {
                freezerApps = items
                freezerLoading = false
            }
        }
    }

    fun toggleFreezeApp(ctx: Context, item: FrozenAppItem) {
        if (item.isCritical) {
            Toast.makeText(ctx, "System App ${item.name} is protected to prevent bootloops!", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { freezerActionPkg = item.pkg }

            val r: SCmd = if (hasRoot) {
                val cmd = if (item.isLocked) "pm enable --user 0 ${item.pkg}" else "pm disable-user --user 0 ${item.pkg}"
                val rootRes = RootEngine.su(cmd)
                SCmd(rootRes.success, rootRes.output, rootRes.cmd)
            } else {
                if (item.isLocked) ShizukuEngine.unfreezeApp(item.pkg)
                else ShizukuEngine.freezeApp(item.pkg)
            }

            withContext(Dispatchers.Main) {
                freezerActionPkg = null
                if (r.success) {
                    val nowFrozen = !item.isLocked
                    freezerApps = freezerApps.map { app ->
                        if (app.pkg == item.pkg) app.copy(isFrozen = nowFrozen, isDisabled = nowFrozen)
                        else app
                    }
                    statusMsg = if (nowFrozen) "${item.name} frozen" else "${item.name} unfrozen"
                    statusSuccess = true
                } else {
                    statusMsg = "Failed to change state for ${item.name}"
                    statusSuccess = false
                }
                addLogEntry(LogEntry(sdf.format(Date()), r.cmd, r.success))
            }
        }
    }

    fun debloatSystemApp(ctx: Context, item: FrozenAppItem) {
        if (item.isCritical) {
            Toast.makeText(ctx, "System App ${item.name} is protected to prevent bootloops!", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { freezerActionPkg = item.pkg }

            val r: SCmd = if (hasRoot) {
                val rootRes = RootEngine.debloatApp(item.pkg)
                SCmd(rootRes.success, rootRes.output, rootRes.cmd)
            } else {
                ShizukuEngine.debloatApp(item.pkg)
            }

            withContext(Dispatchers.Main) {
                freezerActionPkg = null
                if (r.output.contains("Success") || r.success) {
                    freezerApps = freezerApps.filter { it.pkg != item.pkg }
                    statusMsg = "${item.name} successfully debloated"
                    statusSuccess = true
                } else {
                    statusMsg = "Failed to debloat ${item.name}"
                    statusSuccess = false
                }
                addLogEntry(LogEntry(sdf.format(Date()), r.cmd, statusSuccess ?: false))
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