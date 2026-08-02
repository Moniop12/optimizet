package com.monai.optimizer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.ModeNight
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monai.optimizer.optimizer.SCmd
import com.monai.optimizer.optimizer.ShizukuEngine
import com.monai.optimizer.ui.theme.AmberWarn
import com.monai.optimizer.ui.theme.BrandPrimary
import com.monai.optimizer.ui.theme.CardSurface
import com.monai.optimizer.ui.theme.CyanGlow
import com.monai.optimizer.ui.theme.DarkBg
import com.monai.optimizer.ui.theme.DividerSoft
import com.monai.optimizer.ui.theme.EmeraldGlow
import com.monai.optimizer.ui.theme.GlassBorder
import com.monai.optimizer.ui.theme.GlassCard
import com.monai.optimizer.ui.theme.OrangeGlow
import com.monai.optimizer.ui.theme.RedErr
import com.monai.optimizer.ui.theme.TextDisabled
import com.monai.optimizer.ui.theme.TextPrimary
import com.monai.optimizer.ui.theme.TextSecondary

@Composable
fun ToolsScreen(vm: MainViewModel) {
    val hasControl = vm.hasRoot || vm.hasShizuku
    var chargingExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Control center", style = MaterialTheme.typography.headlineMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
        Text(
            "A cleaner layout with compact controls, smaller charging settings, and modular sections for upcoming features.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )

        if (vm.statusMsg.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = BrandPrimary.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, BrandPrimary.copy(alpha = 0.20f))
            ) {
                Text(
                    vm.statusMsg,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    color = BrandPrimary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        QuickStatusRow(vm)

        CompactSectionLabel("Charging")
        CompactChargingCard(
            vm = vm,
            expanded = chargingExpanded,
            onToggleExpanded = { chargingExpanded = !chargingExpanded }
        )

        CompactSectionLabel("Performance")
        GovernorCard(vm)

        CompactSectionLabel("Maintenance")
        InteractiveToolItem(
            toolId = "ram_clean",
            icon = Icons.Filled.Memory,
            title = "Deep RAM Cleaner",
            desc = "Stops background work and frees memory more aggressively.",
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
            desc = "Flushes the Linux page cache for testing and benchmarking.",
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
            desc = "Requests cache trimming across installed apps.",
            accent = OrangeGlow,
            enabled = hasControl,
            vm = vm
        ) {
            vm.runTool("clear_cache", "Clear System Caches", "cmd package trim-caches 999G") { ShizukuEngine.trimMemory() }
        }

        CompactSectionLabel("System actions")
        InteractiveToolItem(
            toolId = "trim_mem",
            icon = Icons.Filled.Stop,
            title = "Trim App Memory",
            desc = "Sends trim requests to third-party apps through ADB or root.",
            accent = BrandPrimary,
            enabled = hasControl,
            vm = vm
        ) {
            vm.runTool("trim_mem", "Trim Memory", "cmd package trim-caches 999G") { ShizukuEngine.trimMemory() }
        }

        InteractiveToolItem(
            toolId = "doze_mode",
            icon = Icons.Filled.ModeNight,
            title = "Aggressive Doze",
            desc = "Forces the device into a deeper idle battery state.",
            accent = AmberWarn,
            enabled = hasControl,
            vm = vm
        ) {
            vm.runTool("doze_mode", "Aggressive Doze", "dumpsys deviceidle force-idle") { ShizukuEngine.aggressiveDoze().first() }
        }

        CompactSectionLabel("Recovery")
        RecoveryCard(vm, hasControl)

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun QuickStatusRow(vm: MainViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SmallInfoCard("Access", if (vm.hasRoot) "Root" else if (vm.hasShizuku) "ADB" else "Limited", EmeraldGlow, Modifier.weight(1f))
        SmallInfoCard("Governor", vm.currentGov, BrandPrimary, Modifier.weight(1f))
        SmallInfoCard("Charge", "${vm.chargeLimitPct.toInt()}%", CyanGlow, Modifier.weight(1f))
    }
}

@Composable
private fun SmallInfoCard(title: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = TextSecondary, fontSize = 11.sp)
            Text(value, color = TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun CompactChargingCard(vm: MainViewModel, expanded: Boolean, onToggleExpanded: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = GlassCard),
        border = BorderStroke(1.dp, EmeraldGlow.copy(alpha = 0.24f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(EmeraldGlow.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.BatteryChargingFull, contentDescription = null, tint = EmeraldGlow)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Charging controls", color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (vm.hasRoot) "Compact by default so this section stays tidy as the app grows." else "Root access is required for hardware charging control.",
                            color = if (vm.hasRoot) TextSecondary else RedErr,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = vm.isChargeLimitEnabled,
                        onCheckedChange = { vm.setChargeLimit(it, vm.chargeLimitPct) },
                        enabled = vm.hasRoot
                    )
                    IconButton(onClick = onToggleExpanded) {
                        Icon(
                            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            tint = TextSecondary
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallInfoCard("Limit", if (vm.isChargeLimitEnabled) "${vm.chargeLimitPct.toInt()}%" else "Disabled", EmeraldGlow, Modifier.weight(1f))
                SmallInfoCard("Current", "${vm.chargeSpeedMa} mA", BrandPrimary, Modifier.weight(1f))
            }

            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    HorizontalDivider(color = DividerSoft)
                    Text("Charge stop limit", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Text("${vm.chargeLimitPct.toInt()}%", color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    androidx.compose.material3.Slider(
                        value = vm.chargeLimitPct,
                        onValueChange = { vm.setChargeLimit(vm.isChargeLimitEnabled, it) },
                        valueRange = 70f..95f,
                        steps = 4,
                        enabled = vm.isChargeLimitEnabled && vm.hasRoot
                    )

                    Text("Charge current limit", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            500 to "500 mA",
                            1000 to "1000 mA",
                            1500 to "1500 mA",
                            2000 to "2000 mA",
                            3000 to "Maximum"
                        ).forEach { (mA, label) ->
                            FilterChip(
                                selected = vm.chargeSpeedMa == mA,
                                onClick = { vm.setChargeSpeed(mA) },
                                enabled = vm.hasRoot,
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecoveryCard(vm: MainViewModel, hasControl: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = RedErr.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, RedErr.copy(alpha = 0.24f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.RestartAlt, contentDescription = null, tint = RedErr, modifier = Modifier.size(22.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Reset to stock defaults", color = RedErr, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Restore governor, sysctl, and doze settings to their original device defaults.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = { vm.resetToDefaults() },
                enabled = !vm.isOptimizing && hasControl,
                colors = ButtonDefaults.buttonColors(containerColor = RedErr),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Reset", color = Color.White)
            }
        }
    }
}

@Composable
private fun CompactSectionLabel(text: String) {
    Text(text, color = TextSecondary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun InteractiveToolItem(
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
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = GlassCard),
        border = BorderStroke(1.dp, if (enabled) GlassBorder else RedErr.copy(alpha = 0.16f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background((if (enabled) accent else TextDisabled).copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = if (enabled) accent else TextDisabled)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = if (enabled) TextPrimary else TextDisabled, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(desc, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }

            OutlinedButton(
                onClick = onClick,
                enabled = enabled && !isRunning,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (enabled) accent else TextDisabled)
            ) {
                if (isRunning) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.7.dp, color = accent)
                } else {
                    Text(if (enabled) "Run" else "Locked", color = if (enabled) accent else TextDisabled)
                }
            }
        }
    }
}

@Composable
private fun GovernorCard(vm: MainViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = GlassCard),
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("CPU governor", color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Current: ${vm.currentGov}", color = BrandPrimary, style = MaterialTheme.typography.bodySmall)

            if (!vm.hasRoot) {
                Text("Root access is required to change the governor.", color = RedErr, fontSize = 11.sp)
            } else if (vm.governors.isEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Reading available kernel governors...", color = TextSecondary, fontSize = 11.sp)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(5.dp), color = BrandPrimary, trackColor = CardSurface)
                }
            } else {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
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
