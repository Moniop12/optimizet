package com.monai.optimizer.ui

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monai.optimizer.optimizer.CmdResult
import com.monai.optimizer.optimizer.DeviceAnalyzer
import com.monai.optimizer.optimizer.DeviceSpec
import com.monai.optimizer.optimizer.OptProfile
import com.monai.optimizer.optimizer.RootEngine
import com.monai.optimizer.optimizer.SCmd
import com.monai.optimizer.optimizer.ShizukuEngine
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
    var progress by mutableStateOf(0f)
        private set
    var statusMsg by mutableStateOf("")
        private set
    var activeProfile by mutableStateOf<OptProfile?>(null)
        private set
    var log by mutableStateOf<List<LogEntry>>(emptyList())
        private set

    // Real-Time Stats
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

    fun init(ctx: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val root = RootEngine.hasRoot()
            val shz  = ShizukuEngine.isRunning() && ShizukuEngine.hasPerm()
            val dev  = DeviceAnalyzer.analyze(ctx, root, shz)

            withContext(Dispatchers.Main) {
                hasRoot = root
                hasShizuku = shz
                spec = dev
            }

            if (root) {
                val gvs = RootEngine.getGovernors()
                withContext(Dispatchers.Main) { governors = gvs }
            }

            startRealTimeTicker(ctx)
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
                delay(1500) // Poll real-time setiap 1.5s
            }
        }
    }

    fun refresh(ctx: Context) = init(ctx)
    fun requestShizuku() { ShizukuEngine.requestPerm() }

    fun applyProfile(profile: OptProfile) {
        if (isOptimizing) return
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                isOptimizing = true; progress = 0f; statusMsg = "Menjalankan optimasi..."
            }

            val rootCmds: List<CmdResult> = if (hasRoot) when (profile) {
                OptProfile.PERFORMANCE -> RootEngine.applyPerformance()
                OptProfile.BALANCED    -> RootEngine.applyBalanced()
                OptProfile.BATTERY     -> RootEngine.applyBattery()
            } else emptyList()

            val shzCmds: List<SCmd> = if (hasShizuku) when (profile) {
                OptProfile.PERFORMANCE -> ShizukuEngine.applyPerformance()
                OptProfile.BALANCED    -> ShizukuEngine.applyBalanced()
                OptProfile.BATTERY     -> ShizukuEngine.applyBattery()
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
                delay(60)
            }

            for (r in shzCmds) {
                done++
                withContext(Dispatchers.Main) {
                    progress = done.toFloat() / total
                    statusMsg = "[ADB] ${r.cmd.take(35)}..."
                }
                newLog += LogEntry(sdf.format(Date()), r.cmd, r.success)
                delay(60)
            }

            withContext(Dispatchers.Main) {
                progress = 1f
                statusMsg = "Selesai ✓ (${newLog.count { it.success }}/${newLog.size} Berhasil)"
                activeProfile = profile
                log = newLog + log
                isOptimizing = false
            }
        }
    }

    fun setGovernor(gov: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val r = RootEngine.setGovernor(gov)
            withContext(Dispatchers.Main) {
                if (r.success) { currentGov = gov; statusMsg = "✓ CPU Governor → $gov" }
                else statusMsg = "✗ Gagal set $gov: ${r.output}"
                log = listOf(LogEntry(sdf.format(Date()), r.cmd, r.success)) + log
            }
        }
    }

    fun doRoot(label: String, fn: () -> CmdResult) {
        viewModelScope.launch(Dispatchers.IO) {
            val r = fn()
            withContext(Dispatchers.Main) {
                statusMsg = if (r.success) "✓ $label Berhasil" else "✗ $label Gagal: ${r.output.take(50)}"
                log = listOf(LogEntry(sdf.format(Date()), r.cmd, r.success)) + log
            }
        }
    }

    fun doShz(label: String, fn: () -> SCmd) {
        viewModelScope.launch(Dispatchers.IO) {
            val r = fn()
            withContext(Dispatchers.Main) {
                statusMsg = if (r.success) "✓ $label Berhasil" else "✗ $label Gagal"
                log = listOf(LogEntry(sdf.format(Date()), r.cmd, r.success)) + log
            }
        }
    }

    fun copyLogsToClipboard(ctx: Context) {
        if (log.isEmpty()) return
        val text = log.joinToString("\n") { "[${it.time}] [${if (it.success) "OK" else "FAIL"}] ${it.cmd}" }
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("MonAi Logs", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(ctx, "Log berhasil disalin ke Clipboard!", Toast.LENGTH_SHORT).show()
    }

    fun clearLogHistory() {
        log = emptyList()
    }
}