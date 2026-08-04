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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monai.optimizer.optimizer.ShizukuEngine
import com.monai.optimizer.ui.components.*
import com.monai.optimizer.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(vm: MainViewModel, onOpenCharging: () -> Unit) {
    val ctx = LocalContext.current
    val hasControl = vm.hasRoot || vm.hasShizuku
    var showNotifSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("System Tools", style = MaterialTheme.typography.headlineSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
        Text("Fine-grained hardware & OS control", color = TextSecondary, style = MaterialTheme.typography.bodySmall)

        if (vm.statusMsg.isNotEmpty()) {
            InfoBanner(vm.statusMsg)
        }

        SectionLabel("NOTIFICATION BAR")
        NavSummaryCard(
            icon = Icons.Filled.Notifications,
            accent = CyanGlow,
            title = "Customize Notification Bar",
            subtitle = "Manage visible modules & custom layout",
            onClick = { showNotifSheet = true }
        )

        SectionLabel("POWER")
        NavSummaryCard(
            icon = Icons.Filled.BatteryChargingFull,
            accent = EmeraldGlow,
            title = "Smart Charging Control",
            subtitle = if (!vm.hasRoot) "Requires root access"
                       else if (vm.isChargeLimitEnabled) "Limit ${vm.chargeLimitPct.toInt()}% · ${vm.chargeSpeedMa} mA"
                       else "Charge limit disabled",
            onClick = onOpenCharging
        )

        SectionLabel("CPU & MEMORY TUNER")
        GovernorCard(vm)

        ToolActionRow(
            icon = Icons.Filled.Memory,
            title = "Deep RAM Cleaner",
            desc = "Stop background tasks & flush memory",
            accent = CyanGlow,
            enabled = hasControl,
            isRunning = vm.runningTools["ram_clean"] == true
        ) {
            vm.runTool(ctx, "ram_clean", "Clean RAM", "am kill-all; cmd activity kill-all") { ShizukuEngine.killBgApps() }
        }

        ToolActionRow(
            icon = Icons.Filled.DeleteSweep,
            title = "Drop Page Cache",
            desc = "sync; echo 3 > /proc/sys/vm/drop_caches",
            accent = PurpleGlow,
            enabled = vm.hasRoot,
            isRunning = vm.runningTools["drop_cache"] == true
        ) {
            vm.runTool(ctx, "drop_cache", "Drop Page Cache", "sync; echo 3 > /proc/sys/vm/drop_caches") { com.monai.optimizer.optimizer.SCmd(false, "", "") }
        }

        ToolActionRow(
            icon = Icons.Filled.Delete,
            title = "Clear System Caches",
            desc = "Trim & flush app cache",
            accent = PurpleGlow,
            enabled = hasControl,
            isRunning = vm.runningTools["clear_cache"] == true
        ) {
            vm.runTool(ctx, "clear_cache", "Clear System Caches", "cmd package trim-caches 999G") { ShizukuEngine.trimMemory() }
        }

        SectionLabel("SYSTEM UI & ADB TOOLS")

        ToolActionRow(
            icon = Icons.Filled.Stop,
            title = "Trim App Memory (ADB)",
            desc = "Send trim signal to all third-party apps",
            accent = UtilityTeal,
            enabled = hasControl,
            isRunning = vm.runningTools["trim_mem"] == true
        ) {
            vm.runTool(ctx, "trim_mem", "Trim Memory", "cmd package trim-caches 999G") { ShizukuEngine.trimMemory() }
        }

        ToolActionRow(
            icon = Icons.Filled.ModeNight,
            title = "Aggressive Doze Mode",
            desc = "Force device into deep idle battery mode",
            accent = AmberWarn,
            enabled = hasControl,
            isRunning = vm.runningTools["doze_mode"] == true
        ) {
            vm.runTool(ctx, "doze_mode", "Aggressive Doze", "dumpsys deviceidle force-idle") { ShizukuEngine.aggressiveDoze().first() }
        }

        SectionLabel("DANGER ZONE")
        DangerResetCard(vm, hasControl)

        Spacer(Modifier.height(12.dp))
    }

    // Modal Bottom Sheet Ramping untuk Setting Notifikasi
    if (showNotifSheet) {
        ModalBottomSheet(
            onDismissRequest = { showNotifSheet = false },
            containerColor = DarkSurface,
            contentColor = TextPrimary
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Notification Dashboard Bar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("Toggle modules to show or hide completely in notification", color = TextSecondary, style = MaterialTheme.typography.bodySmall)

                HorizontalDivider(color = GlassBorder, thickness = 0.8.dp)

                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("Show App RAM Usage", color = TextPrimary, fontSize = 13.sp)
                    Switch(checked = vm.showNotifRam, onCheckedChange = { vm.toggleNotifRam() })
                }

                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("Show CPU Freq & Temp", color = TextPrimary, fontSize = 13.sp)
                    Switch(checked = vm.showNotifCpu, onCheckedChange = { vm.toggleNotifCpu() })
                }

                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("Show Power Stream (Battery)", color = TextPrimary, fontSize = 13.sp)
                    Switch(checked = vm.showNotifPower, onCheckedChange = { vm.toggleNotifPower() })
                }

                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("Show Profile Switcher Buttons", color = TextPrimary, fontSize = 13.sp)
                    Switch(checked = vm.showNotifProfiles, onCheckedChange = { vm.toggleNotifProfiles() })
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun DangerResetCard(vm: MainViewModel, hasControl: Boolean) {
    AppCard(emphasize = true, accent = RedErr) {
        Row(Modifier.padding(12.dp), Arrangement.spacedBy(12.dp), Alignment.CenterVertically) {
            IconBadge(Icons.Filled.RestartAlt, RedErr, size = 34.dp, iconSize = 16.dp, active = true)
            Column(Modifier.weight(1f)) {
                Text("Reset to Stock Defaults", color = RedErr, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("Restore governor, sysctl & doze to factory settings", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(
                onClick = { vm.resetToDefaults() },
                enabled = !vm.isOptimizing && hasControl,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = RedErr),
                border = BorderStroke(1.dp, RedErr.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(9.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp)
            ) { Text("Reset", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
fun GovernorCard(vm: MainViewModel) {
    AppCard {
        Column(Modifier.padding(12.dp), Arrangement.spacedBy(6.dp)) {
            Text("CPU Governor Control", color = TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("Active: ${vm.currentGov}", color = OrangeGlow, style = MaterialTheme.typography.bodySmall)

            if (!vm.hasRoot) {
                Text("Requires root access", color = RedErr, fontSize = 10.sp)
            } else if (vm.governors.isEmpty()) {
                Text("Reading kernel governors…", color = TextSecondary, fontSize = 10.sp)
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