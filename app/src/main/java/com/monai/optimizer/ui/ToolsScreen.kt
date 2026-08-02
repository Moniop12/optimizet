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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monai.optimizer.optimizer.RootEngine
import com.monai.optimizer.optimizer.SCmd
import com.monai.optimizer.optimizer.ShizukuEngine
import com.monai.optimizer.ui.theme.*

@Composable
fun ToolsScreen(vm: MainViewModel) {
    val hasControl = vm.hasRoot || vm.hasShizuku

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("System Tools", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
        Text("Fine-Grained Hardware & OS Control", color = TextSecondary, style = MaterialTheme.typography.bodySmall)

        if (vm.statusMsg.isNotEmpty()) {
            Surface(shape = RoundedCornerShape(8.dp), color = GlassCard, border = BorderStroke(1.dp, GlassBorder)) {
                Text(vm.statusMsg, Modifier.padding(8.dp), color = CyanGlow, style = MaterialTheme.typography.bodySmall)
            }
        }

        // KARTU SMART CHARGING CONTROL
        Card(
            Modifier.fillMaxWidth(), RoundedCornerShape(10.dp),
            CardDefaults.cardColors(containerColor = GlassCard),
            border = BorderStroke(1.dp, EmeraldGlow.copy(alpha = 0.4f))
        ) {
            Column(Modifier.padding(12.dp), Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.BatteryChargingFull, null, tint = EmeraldGlow, modifier = Modifier.size(20.dp))
                        Text("Smart Charging Control", color = TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Switch(
                        checked = vm.isChargeLimitEnabled,
                        onCheckedChange = { vm.setChargeLimit(it, vm.chargeLimitPct) },
                        enabled = vm.hasRoot
                    )
                }

                if (!vm.hasRoot) {
                    Text("Kontrol pengisian hardware membutuhkan Akses Root!", color = RedErr, fontSize = 10.sp)
                } else {
                    Text("Batas Stop Pengisian: ${vm.chargeLimitPct.toInt()}%", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = vm.chargeLimitPct,
                        onValueChange = { vm.setChargeLimit(vm.isChargeLimitEnabled, it) },
                        valueRange = 70f..95f,
                        steps = 4,
                        enabled = vm.isChargeLimitEnabled
                    )

                    HorizontalDivider(color = GlassBorder, thickness = 0.8.dp)

                    Text("Kecepatan Cas (Milliampere Limit):", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(4.dp)) {
                        listOf(500 to "500mA (Slow)", 1000 to "1000mA", 1500 to "1500mA", 2000 to "2000mA (Fast)", 3000 to "Max Speed").forEach { (mA, label) ->
                            FilterChip(
                                selected = vm.chargeSpeedMa == mA,
                                onClick = { vm.setChargeSpeed(mA) },
                                label = { Text(label, fontSize = 10.sp) }
                            )
                        }
                    }
                }
            }
        }

        // KARTU RESET TO DEFAULTS
        Card(
            Modifier.fillMaxWidth(), RoundedCornerShape(10.dp),
            CardDefaults.cardColors(containerColor = RedErr.copy(alpha = 0.08f)),
            border = BorderStroke(1.dp, RedErr.copy(alpha = 0.3f))
        ) {
            Row(Modifier.padding(10.dp), Arrangement.spacedBy(10.dp), Alignment.CenterVertically) {
                Icon(Icons.Filled.RestartAlt, null, Modifier.size(20.dp), tint = RedErr)
                Column(Modifier.weight(1f)) {
                    Text("Reset to Stock Defaults", color = RedErr, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Kembalikan Governor, Sysctl, & Doze ke Setelan Awal", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = { vm.resetToDefaults() },
                    enabled = !vm.isOptimizing && hasControl,
                    colors = ButtonDefaults.buttonColors(containerColor = RedErr),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) { Text("Reset", fontSize = 11.sp, color = Color.White) }
            }
        }

        SecLabel("CPU & MEMORY TUNER")
        GovernorCard(vm)

        InteractiveToolItem(
            toolId = "ram_clean",
            icon = Icons.Filled.Memory,
            title = "Deep RAM Cleaner",
            desc = "Hentikan background task & Flush Memory",
            accent = OrangeGlow,
            enabled = hasControl,
            vm = vm
        ) {
            vm.runTool("ram_clean", "Clean RAM", "am kill-all; cmd activity kill-all") { ShizukuEngine.killBgApps() }
        }

        InteractiveToolItem(
            toolId = "drop_cache",
            icon = Icons.Filled.DeleteSweep,
            title = "Drop Page Cache",
            desc = "sync; echo 3 > /proc/sys/vm/drop_caches",
            accent = OrangeGlow,
            enabled = vm.hasRoot,
            vm = vm
        ) {
            vm.runTool("drop_cache", "Drop Page Cache", "sync; echo 3 > /proc/sys/vm/drop_caches") { SCmd(false, "", "") }
        }

        InteractiveToolItem(
            toolId = "clear_cache",
            icon = Icons.Filled.Delete,
            title = "Clear System Caches",
            desc = "Trim & Flush Cache Aplikasi",
            accent = OrangeGlow,
            enabled = hasControl,
            vm = vm
        ) {
            vm.runTool("clear_cache", "Clear System Caches", "cmd package trim-caches 999G") { ShizukuEngine.trimMemory() }
        }

        SecLabel("SYSTEM UI & ADB TOOLS")

        InteractiveToolItem(
            toolId = "trim_mem",
            icon = Icons.Filled.Stop,
            title = "Trim App Memory (ADB)",
            desc = "Kirim sinyal Trim ke seluruh App Pihak-3",
            accent = CyanGlow,
            enabled = hasControl,
            vm = vm
        ) {
            vm.runTool("trim_mem", "Trim Memory", "cmd package trim-caches 999G") { ShizukuEngine.trimMemory() }
        }

        InteractiveToolItem(
            toolId = "doze_mode",
            icon = Icons.Filled.ModeNight,
            title = "Aggressive Doze Mode",
            desc = "Paksa HP masuk ke Deep Idle Battery",
            accent = AmberWarn,
            enabled = hasControl,
            vm = vm
        ) {
            vm.runTool("doze_mode", "Aggressive Doze", "dumpsys deviceidle force-idle") { ShizukuEngine.aggressiveDoze().first() }
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable fun SecLabel(t: String) {
    Text(t, color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
}

@Composable fun InteractiveToolItem(
    toolId: String,
    icon: ImageVector,
    title: String,
    desc: String,
    accent: Color,
    enabled: Boolean,
    vm: MainViewModel,
    onClick: () -> Unit
) {
    val isRunning = vm.runningTools[toolId] == true

    Card(
        Modifier.fillMaxWidth(), RoundedCornerShape(10.dp),
        CardDefaults.cardColors(containerColor = GlassCard),
        border = BorderStroke(1.dp, if (enabled) GlassBorder else RedErr.copy(alpha = 0.2f))
    ) {
        Row(Modifier.padding(10.dp), Arrangement.spacedBy(10.dp), Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp), tint = if (enabled) accent else TextDisabled)
            Column(Modifier.weight(1f)) {
                Text(title, color = if (enabled) TextPrimary else TextDisabled, style = MaterialTheme.typography.titleSmall)
                Text(desc, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }

            OutlinedButton(
                onClick = onClick,
                enabled = enabled && !isRunning,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, if (enabled) accent else TextDisabled),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(30.dp)
            ) {
                if (isRunning) {
                    CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = accent)
                } else {
                    Text(if (enabled) "Run" else "Locked", fontSize = 10.sp, color = if (enabled) accent else TextDisabled)
                }
            }
        }
    }
}

@Composable fun GovernorCard(vm: MainViewModel) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(10.dp), CardDefaults.cardColors(containerColor = GlassCard)) {
        Column(Modifier.padding(10.dp), Arrangement.spacedBy(6.dp)) {
            Text("CPU Governor Control", color = TextPrimary, style = MaterialTheme.typography.titleSmall)
            Text("Active: ${vm.currentGov}", color = CyanGlow, style = MaterialTheme.typography.bodySmall)

            if (!vm.hasRoot) {
                Text("Membutuhkan Akses Root!", color = RedErr, fontSize = 10.sp)
            } else if (vm.governors.isEmpty()) {
                Text("Membaca Governor Kernel...", color = TextSecondary, fontSize = 10.sp)
            } else {
                Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(4.dp)) {
                    vm.governors.forEach { gov ->
                        FilterChip(
                            selected = gov == vm.currentGov,
                            onClick = { vm.setGovernor(gov) },
                            label = { Text(gov, fontSize = 10.sp) }
                        )
                    }
                }
            }
        }
    }
}