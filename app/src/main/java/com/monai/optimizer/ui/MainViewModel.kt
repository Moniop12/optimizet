package com.monai.optimizer.ui

import android.content.Context
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

    // Root live data
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

    private val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun init(ctx: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val root = RootEngine.hasRoot()
            val shz  = ShizukuEngine.isRunning() && ShizukuEngine.hasPerm()
            val dev  = DeviceAnalyzer.analyze(ctx, root, shz)
            withContext(Dispatchers.Main) {
                hasRoot = root; hasShizuku = shz; spec = dev
            }
            if (root) {
                val fr  = RootEngine.getCpuFreqInfo()
                val tp  = RootEngine.getCpuTemp()
                val zr  = RootEngine.getZramInfo()
                val gvs = RootEngine.getGovernors()
                val gv  = RootEngine.getCurrentGovernor()
                withContext(Dispatchers.Main) {
                    cpuFreq = fr; cpuTemp = tp; zramInfo = zr
                    governors = gvs; currentGov = gv
                }
            }
        }
    }

    fun refresh(ctx: Context) = init(ctx)
    fun requestShizuku() { ShizukuEngine.requestPerm() }

    // ── Apply profile ─────────────────────────────────────────────────

    fun applyProfile(profile: OptProfile) {
        if (isOptimizing) return
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                isOptimizing = true; progress = 0f; statusMsg = "Starting…"
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
                val short = r.cmd.take(44) + if (r.cmd.length > 44) "…" else ""
                withContext(Dispatchers.Main) {
                    progress = done.toFloat() / total
                    statusMsg = "[root] $short"
                }
                newLog += LogEntry(sdf.format(Date()), r.cmd, r.success)
                delay(80)
            }
            for (r in shzCmds) {
                done++
                val short = r.cmd.take(44) + if (r.cmd.length > 44) "…" else ""
                withContext(Dispatchers.Main) {
                    progress = done.toFloat() / total
                    statusMsg = "[adb] $short"
                }
                newLog += LogEntry(sdf.format(Date()), r.cmd, r.success)
                delay(80)
            }

            withContext(Dispatchers.Main) {
                progress = 1f
                statusMsg = "Done ✓  (${newLog.count { it.success }}/${newLog.size} OK)"
                activeProfile = profile
                log = newLog + log
                isOptimizing = false
            }
        }
    }

    // ── Governor control ──────────────────────────────────────────────

    fun setGovernor(gov: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val r = RootEngine.setGovernor(gov)
            withContext(Dispatchers.Main) {
                if (r.success) { currentGov = gov; statusMsg = "✓ Governor → $gov" }
                else statusMsg = "✗ Could not set $gov"
                log = listOf(LogEntry(sdf.format(Date()), r.cmd, r.success)) + log
            }
        }
    }

    // ── Tool helpers ──────────────────────────────────────────────────

    fun doRoot(label: String, fn: () -> CmdResult) {
        viewModelScope.launch(Dispatchers.IO) {
            val r = fn()
            withContext(Dispatchers.Main) {
                statusMsg = if (r.success) "✓ $label" else "✗ $label failed: ${r.output.take(60)}"
                log = listOf(LogEntry(sdf.format(Date()), r.cmd, r.success)) + log
            }
        }
    }

    fun doRootMulti(label: String, fn: () -> List<CmdResult>) {
        viewModelScope.launch(Dispatchers.IO) {
            val rs = fn()
            val ok = rs.count { it.success }
            withContext(Dispatchers.Main) {
                statusMsg = "✓ $label: $ok/${rs.size} commands OK"
                log = rs.map { LogEntry(sdf.format(Date()), it.cmd, it.success) } + log
            }
        }
    }

    fun doShz(label: String, fn: () -> SCmd) {
        viewModelScope.launch(Dispatchers.IO) {
            val r = fn()
            withContext(Dispatchers.Main) {
                statusMsg = if (r.success) "✓ $label" else "✗ $label failed"
                log = listOf(LogEntry(sdf.format(Date()), r.cmd, r.success)) + log
            }
        }
    }

    fun doShzMulti(label: String, fn: () -> List<SCmd>) {
        viewModelScope.launch(Dispatchers.IO) {
            val rs = fn()
            val ok = rs.count { it.success }
            withContext(Dispatchers.Main) {
                statusMsg = "✓ $label: $ok/${rs.size} OK"
                log = rs.map { LogEntry(sdf.format(Date()), it.cmd, it.success) } + log
            }
        }
    }
}
