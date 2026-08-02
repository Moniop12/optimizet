package com.monai.optimizer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monai.optimizer.optimizer.RootEngine
import com.monai.optimizer.optimizer.ShizukuEngine
import com.monai.optimizer.ui.theme.*

@Composable
fun ToolsScreen(vm: MainViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Tools", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
        Text("Manual Tweaks & System Control", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)

        if (vm.statusMsg.isNotEmpty()) {
            Surface(shape = RoundedCornerShape(8.dp), color = DarkCard, border = BorderStroke(1.dp, Cyan900)) {
                Text(vm.statusMsg, Modifier.padding(10.dp), color = Cyan400, style = MaterialTheme.typography.bodySmall)
            }
        }

        // ROOT TOOLS SECTION
        SecLabel("CPU & MEMORY CONTROL • ROOT")
        GovernorCard(vm)

        ToolItem(Icons.Filled.Memory, "Deep RAM Cleaner", "Paksa hentikan proses background & Flush RAM", OrangeAcc, enabled = vm.hasRoot) {
            vm.doRoot("Clean RAM") { RootEngine.killBgApps() }
        }

        ToolItem(Icons.Filled.DeleteSweep, "Drop Page Cache", "sync; echo 3 > /proc/sys/vm/drop_caches", OrangeAcc, enabled = vm.hasRoot) {
            vm.doRoot("Drop Cache") { RootEngine.dropCaches() }
        }

        ToolItem(Icons.Filled.Delete, "Clear System Caches", "Estimasi cache: ${vm.cacheSizeMb} MB", OrangeAcc, enabled = vm.hasRoot) {
            vm.doRoot("Clear App Cache") { RootEngine.clearCaches() }
        }

        SecLabel("NETWORK CONTROL • ROOT")
        ToolItem(Icons.Filled.Speed, "Enable TCP BBR", "Optimasi Buffer Jaringan TCP", Cyan500, enabled = vm.hasRoot) {
            vm.doRoot("TCP BBR") { RootEngine.enableBBR().first() }
        }

        // SHIZUKU SECTION
        SecLabel("SYSTEM UI & ADB • SHIZUKU")
        ToolItem(Icons.Filled.Stop, "Trim App Memory", "am send-trim-memory all 80", Cyan500, enabled = vm.hasShizuku) {
            vm.doShz("Trim Memory") { ShizukuEngine.trimMemory() }
        }

        ToolItem(Icons.Filled.ModeNight, "Force Aggressive Doze", "Paksa HP masuk ke Deep Idle Battery", AmberWarn, enabled = vm.hasShizuku) {
            vm.doShz("Aggressive Doze") { ShizukuEngine.aggressiveDoze().first() }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable fun SecLabel(t: String) {
    Text(t, color = TextSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
}

@Composable fun ToolItem(icon: ImageVector, title: String, desc: String, accent: Color, enabled: Boolean, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(), RoundedCornerShape(10.dp),
        CardDefaults.cardColors(containerColor = DarkCard),
        border = BorderStroke(1.dp, if (enabled) DarkSurfaceVar else RedErr.copy(alpha = 0.2f))
    ) {
        Row(Modifier.padding(12.dp), Arrangement.spacedBy(10.dp), Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = if (enabled) accent else TextDisabled)
            Column(Modifier.weight(1f)) {
                Text(title, color = if (enabled) TextPrimary else TextDisabled, style = MaterialTheme.typography.titleMedium)
                Text(desc, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(
                onClick = onClick, enabled = enabled,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, if (enabled) accent else TextDisabled)
            ) {
                Text(if (enabled) "Run" else "Locked", fontSize = 11.sp, color = if (enabled) accent else TextDisabled)
            }
        }
    }
}

@Composable fun GovernorCard(vm: MainViewModel) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(10.dp), CardDefaults.cardColors(containerColor = DarkCard)) {
        Column(Modifier.padding(12.dp), Arrangement.spacedBy(8.dp)) {
            Text("CPU Governor Selector", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
            Text("Active: ${vm.currentGov}", color = Cyan400, style = MaterialTheme.typography.bodySmall)

            if (!vm.hasRoot) {
                Text("Membutuhkan Akses Root!", color = RedErr, style = MaterialTheme.typography.labelSmall)
            } else if (vm.governors.isEmpty()) {
                Text("Membaca Governor Kernel...", color = TextSecondary)
            } else {
                Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(6.dp)) {
                    vm.governors.forEach { gov ->
                        FilterChip(
                            selected = gov == vm.currentGov,
                            onClick = { vm.setGovernor(gov) },
                            label = { Text(gov, fontSize = 11.sp) }
                        )
                    }
                }
            }
        }
    }
}