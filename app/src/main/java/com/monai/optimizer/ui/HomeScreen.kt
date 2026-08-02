package com.monai.optimizer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monai.optimizer.optimizer.OptProfile
import com.monai.optimizer.ui.theme.*

@Composable
fun HomeScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(
            Modifier.fillMaxWidth(),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "MonAi",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        color = Cyan400,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 32.sp
                    )
                )
                Text("Real-Time AI Optimizer", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = { vm.refresh(ctx) }) {
                Icon(Icons.Filled.Refresh, "Refresh", tint = Cyan400)
            }
        }

        DeviceCard(vm)

        vm.spec?.let { AiRecCard(it.recommended, vm) }

        Text(
            "OPTIMIZATION PROFILES",
            color = TextSecondary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 4.dp)
        )

        ProfileCard("Performance", "Max CPU Frequency · Conservative Swap", Icons.Filled.FlashOn, OrangeAcc, OptProfile.PERFORMANCE, vm)
        ProfileCard("Balanced", "Smart Schedutil · Smooth Balance", Icons.Filled.Tune, Cyan500, OptProfile.BALANCED, vm)
        ProfileCard("Battery Saver", "Powersave CPU · Deep Doze Engine", Icons.Filled.BatteryFull, GreenOk, OptProfile.BATTERY, vm)

        AnimatedVisibility(vm.isOptimizing) { ProgressCard(vm) }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun DeviceCard(vm: MainViewModel) {
    val s = vm.spec
    Card(
        Modifier.fillMaxWidth(),
        RoundedCornerShape(16.dp),
        CardDefaults.cardColors(containerColor = DarkCard),
        border = BorderStroke(1.dp, DarkSurfaceVar)
    ) {
        Column(Modifier.padding(16.dp), Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Device Status", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                if (s == null) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Cyan400)
            }

            if (s != null) {
                Text("${s.brand} ${s.model}", color = TextPrimary, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                
                Text(
                    "Android ${s.android} (API ${s.api})  •  ${s.cores} Cores\n" +
                    "Physical RAM: ${s.physicalRamGb} GB (${s.totalRamMb} MB Usable)",
                    color = TextSecondary, style = MaterialTheme.typography.bodyMedium
                )

                if (s.chipset.isNotBlank()) {
                    Text("${s.chipset}  •  Max ${s.maxFreqMhz}MHz", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }

                HorizontalDivider(color = DarkSurfaceVar, thickness = 1.dp)

                // Permissions Badge
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusChip("ROOT ${if (s.hasRoot) "✓" else "✗"}", s.hasRoot, if (s.hasRoot) GreenOk else RedErr)
                    StatusChip("SHIZUKU ${if (s.hasShizuku) "✓" else "✗"}", s.hasShizuku, if (s.hasShizuku) Cyan400 else TextDisabled)
                    if (!s.hasShizuku) {
                        TextButton(onClick = { vm.requestShizuku() }) {
                            Text("Grant ADB", color = Cyan400, fontSize = 11.sp)
                        }
                    }
                }

                // Live Stats Gauge
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("RAM Usage (${vm.liveAvailRamMb}MB Free)", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                        Text("${vm.ramUsedPercent}%", color = if (vm.ramUsedPercent > 85) RedErr else Cyan400, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }
                    LinearProgressIndicator(
                        progress = { vm.ramUsedPercent / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = if (vm.ramUsedPercent > 85) RedErr else Cyan400,
                        trackColor = DarkSurfaceVar
                    )
                }

                if (s.hasRoot) {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), Arrangement.SpaceBetween) {
                        StatItem("CPU Freq", vm.cpuFreq)
                        StatItem("Temp", vm.cpuTemp)
                        StatItem("ZRAM", vm.zramInfo)
                        StatItem("Governor", vm.currentGov)
                    }
                }
            } else {
                Text("Analyzing hardware configuration...", color = TextSecondary)
            }
        }
    }
}

@Composable fun StatusChip(label: String, active: Boolean, color: Color) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = if (active) 0.15f else 0.08f),
        border = BorderStroke(1.dp, color.copy(alpha = if (active) 0.5f else 0.2f))
    ) {
        Text(label, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        Text(value, color = TextPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable fun AiRecCard(profile: OptProfile, vm: MainViewModel) {
    val (label, desc, accent) = when (profile) {
        OptProfile.PERFORMANCE -> Triple("Performance", "Perangkat High-End — Maksimalkan Throughput", OrangeAcc)
        OptProfile.BALANCED    -> Triple("Balanced", "Mid-Range — Keseimbangan Performa & Baterai", Cyan500)
        OptProfile.BATTERY     -> Triple("Battery Saver", "RAM Terbatas — Hemat Sumber Daya", GreenOk)
    }
    Card(
        Modifier.fillMaxWidth(), RoundedCornerShape(14.dp),
        CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    ) {
        Row(Modifier.padding(14.dp), Arrangement.spacedBy(12.dp), Alignment.CenterVertically) {
            Icon(Icons.Filled.AutoAwesome, null, Modifier.size(24.dp), tint = accent)
            Column(Modifier.weight(1f)) {
                Text("AI Rec: $label", color = accent, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(desc, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = { vm.applyProfile(profile) },
                enabled = !vm.isOptimizing,
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                shape = RoundedCornerShape(8.dp)
            ) { Text("Apply", color = Color.White) }
        }
    }
}

@Composable fun ProfileCard(title: String, desc: String, icon: ImageVector, accent: Color, profile: OptProfile, vm: MainViewModel) {
    val active = vm.activeProfile == profile
    Card(
        Modifier.fillMaxWidth(), RoundedCornerShape(12.dp),
        CardDefaults.cardColors(containerColor = if (active) accent.copy(alpha = 0.12f) else DarkCard),
        border = BorderStroke(1.dp, if (active) accent else DarkSurfaceVar)
    ) {
        Row(Modifier.padding(12.dp), Arrangement.spacedBy(12.dp), Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(22.dp), tint = accent)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                    if (active) Text("ACTIVE", color = accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                Text(desc, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = { vm.applyProfile(profile) },
                enabled = !vm.isOptimizing,
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                shape = RoundedCornerShape(8.dp)
            ) { Text("Apply") }
        }
    }
}

@Composable fun ProgressCard(vm: MainViewModel) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), CardDefaults.cardColors(containerColor = DarkCard)) {
        Column(Modifier.padding(14.dp), Arrangement.spacedBy(8.dp)) {
            Text(vm.statusMsg, color = Cyan400, style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(progress = { vm.progress }, modifier = Modifier.fillMaxWidth().height(4.dp), color = Cyan400)
        }
    }
}