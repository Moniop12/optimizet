package com.monai.optimizer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
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
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "MonAi",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = CyanGlow,
                        fontWeight = FontWeight.Black,
                        fontSize = 28.sp
                    )
                )
                Text("AI Engine & Hardware Tuner", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }

            val rotation by animateFloatAsState(
                targetValue = if (vm.isRefreshing) 360f else 0f,
                animationSpec = tween(durationMillis = 600, easing = LinearEasing),
                label = "Spin"
            )
            IconButton(onClick = { vm.refresh(ctx) }) {
                Icon(Icons.Filled.Refresh, "Refresh", tint = CyanGlow, modifier = Modifier.rotate(rotation))
            }
        }

        DeviceGlassCard(vm)

        vm.spec?.let { AiRecCard(it.recommended, vm) }

        Text(
            "OPTIMIZATION PROFILES",
            color = TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 2.dp)
        )

        ProfileCard("Performance", "Max CPU Frequency · Low Latency I/O", Icons.Filled.FlashOn, OrangeGlow, OptProfile.PERFORMANCE, vm)
        ProfileCard("Balanced", "Smart Schedutil · Balanced Efficiency", Icons.Filled.Tune, CyanGlow, OptProfile.BALANCED, vm)
        ProfileCard("Battery Saver", "Powersave CPU · Deep Doze Engine", Icons.Filled.BatteryFull, EmeraldGlow, OptProfile.BATTERY, vm)

        AnimatedVisibility(vm.isOptimizing) { ProgressGlassCard(vm) }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
fun DeviceGlassCard(vm: MainViewModel) {
    val s = vm.spec
    Card(
        Modifier.fillMaxWidth(),
        RoundedCornerShape(14.dp),
        CardDefaults.cardColors(containerColor = GlassCard),
        border = BorderStroke(1.dp, Brush.horizontalGradient(listOf(GlassBorder, Color.Transparent)))
    ) {
        Column(Modifier.padding(14.dp), Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Device Architecture", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                if (s == null) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = CyanGlow)
            }

            if (s != null) {
                Text("${s.brand} ${s.model}", color = TextPrimary, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                
                Text(
                    "Android ${s.android} (API ${s.api})  •  ${s.cores} Cores\n" +
                    "Physical RAM: ${s.physicalRamGb} GB (${s.totalRamMb} MB Usable)",
                    color = TextSecondary, style = MaterialTheme.typography.bodySmall
                )

                if (s.chipset.isNotBlank()) {
                    Text("${s.chipset}  •  Max ${s.maxFreqMhz}MHz", color = TextSecondary, fontSize = 11.sp)
                }

                HorizontalDivider(color = GlassBorder, thickness = 0.8.dp)

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusChip("ROOT ${if (s.hasRoot) "✓" else "✗"}", s.hasRoot, if (s.hasRoot) EmeraldGlow else RedErr)
                    StatusChip("SHIZUKU ${if (s.hasShizuku) "✓" else "✗"}", s.hasShizuku, if (s.hasShizuku) CyanGlow else TextDisabled)
                    if (!s.hasShizuku && !s.hasRoot) {
                        TextButton(onClick = { vm.requestShizuku() }, contentPadding = PaddingValues(0.dp)) {
                            Text("Grant ADB", color = CyanGlow, fontSize = 11.sp)
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 2.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("RAM Usage (${vm.liveAvailRamMb}MB Free)", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                        Text("${vm.ramUsedPercent}%", color = if (vm.ramUsedPercent > 85) RedErr else CyanGlow, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }
                    LinearProgressIndicator(
                        progress = { vm.ramUsedPercent / 100f },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                        color = if (vm.ramUsedPercent > 85) RedErr else CyanGlow,
                        trackColor = DarkBg
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
                Text("Analyzing hardware configuration...", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable fun StatusChip(label: String, active: Boolean, color: Color) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = if (active) 0.12f else 0.05f),
        border = BorderStroke(1.dp, color.copy(alpha = if (active) 0.4f else 0.15f))
    ) {
        Text(label, Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextSecondary, fontSize = 10.sp)
        Text(value, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable fun AiRecCard(profile: OptProfile, vm: MainViewModel) {
    val (label, desc, accent) = when (profile) {
        OptProfile.PERFORMANCE -> Triple("Performance", "High-End Hardware — Maksimal Throughput", OrangeGlow)
        OptProfile.BALANCED    -> Triple("Balanced", "Mid-Range — Keseimbangan Performa", CyanGlow)
        OptProfile.BATTERY     -> Triple("Battery Saver", "RAM Terbatas — Conserve System", EmeraldGlow)
    }
    Card(
        Modifier.fillMaxWidth(), RoundedCornerShape(12.dp),
        CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.3f))
    ) {
        Row(Modifier.padding(12.dp), Arrangement.spacedBy(10.dp), Alignment.CenterVertically) {
            Icon(Icons.Filled.AutoAwesome, null, Modifier.size(22.dp), tint = accent)
            Column(Modifier.weight(1f)) {
                Text("AI Rec: $label", color = accent, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(desc, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = { vm.applyProfile(profile) },
                enabled = !vm.isOptimizing,
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) { Text("Apply", color = Color.White, fontSize = 12.sp) }
        }
    }
}

@Composable fun ProfileCard(title: String, desc: String, icon: ImageVector, accent: Color, profile: OptProfile, vm: MainViewModel) {
    val active = vm.activeProfile == profile
    Card(
        Modifier.fillMaxWidth(), RoundedCornerShape(10.dp),
        CardDefaults.cardColors(containerColor = if (active) accent.copy(alpha = 0.1f) else GlassCard),
        border = BorderStroke(1.dp, if (active) accent else GlassBorder)
    ) {
        Row(Modifier.padding(10.dp), Arrangement.spacedBy(10.dp), Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = accent)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, color = TextPrimary, style = MaterialTheme.typography.titleSmall)
                    if (active) Text("ACTIVE", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Text(desc, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = { vm.applyProfile(profile) },
                enabled = !vm.isOptimizing,
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) { Text("Apply", fontSize = 11.sp) }
        }
    }
}

@Composable fun ProgressGlassCard(vm: MainViewModel) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(10.dp), CardDefaults.cardColors(containerColor = GlassCard)) {
        Column(Modifier.padding(12.dp), Arrangement.spacedBy(6.dp)) {
            Text(vm.statusMsg, color = CyanGlow, style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(progress = { vm.progress }, modifier = Modifier.fillMaxWidth().height(4.dp), color = CyanGlow)
        }
    }
}