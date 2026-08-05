package com.monai.optimizer.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.monai.optimizer.optimizer.OptProfile
import com.monai.optimizer.ui.components.*
import com.monai.optimizer.ui.theme.*

@Composable
fun HomeScreen(vm: MainViewModel) {
    val ctx = LocalContext.current

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                vm.toggleLiveService(ctx)
            } else {
                Toast.makeText(ctx, "Notification permission is required to enable live service", Toast.LENGTH_SHORT).show()
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(Accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Speed, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Column {
                    Text(
                        "MonProject",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text("System Optimizer", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        vm.toggleLiveService(ctx)
                    }
                }) {
                    Icon(
                        if (vm.isLiveServiceRunning) Icons.Filled.NotificationsActive else Icons.Default.Notifications,
                        "Live Service",
                        tint = if (vm.isLiveServiceRunning) EmeraldGlow else TextTertiary
                    )
                }

                val rotation by animateFloatAsState(
                    targetValue = if (vm.isRefreshing) 360f else 0f,
                    animationSpec = tween(durationMillis = 500, easing = LinearEasing),
                    label = "Spin"
                )
                IconButton(onClick = { vm.refresh(ctx) }) {
                    Icon(Icons.Filled.Refresh, "Refresh", tint = TextSecondary, modifier = Modifier.rotate(rotation))
                }
            }
        }

        DeviceOverviewCard(vm)

        // REAL SYSTEM UI & RENDERING TWEAKS CARD
        SystemTweaksCard(vm)

        SectionLabel("MANUAL PROFILES")
        ProfileSelectorRow(vm)

        AnimatedVisibility(vm.isOptimizing) { ProgressCard(vm) }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
fun SystemTweaksCard(vm: MainViewModel) {
    val ctx = LocalContext.current

    AppCard {
        Column(Modifier.padding(14.dp), Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Filled.AutoAwesome, CyanGlow, active = true)
                    Column {
                        Text("Smooth UI & Rendering Engine", color = TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            if (vm.hasRoot) "SurfaceFlinger, Skia GPU & Anim 0.5x"
                            else if (vm.hasShizuku) "Anim speed only — full GPU tweak needs root"
                            else "Requires root or Shizuku",
                            color = TextSecondary, style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Switch(
                    checked = vm.aiOptimizerEnabled,
                    onCheckedChange = { vm.toggleAiOptimizer(ctx) },
                    enabled = vm.hasRoot || vm.hasShizuku,
                    colors = SwitchDefaults.colors(checkedTrackColor = CyanGlow)
                )
            }

            // Indicator Badge Ramping
            Row(Modifier.fillMaxWidth()) {
                Text(
                    "• Status Engine: ${if (vm.aiOptimizerEnabled) (if (vm.hasRoot) "ACTIVE (Smooth Scroll + Anim 0.5x)" else "ACTIVE (Anim 0.5x, Shizuku)") else "OFF"}",
                    color = if (vm.aiOptimizerEnabled) CyanGlow else TextTertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun DeviceOverviewCard(vm: MainViewModel) {
    val s = vm.spec
    AppCard {
        Column(Modifier.padding(16.dp), Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                SectionLabel("DEVICE", modifier = Modifier.padding(0.dp))
                if (s == null) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = CyanGlow)
            }

            if (s != null) {
                Text("${s.brand} ${s.model}", color = TextPrimary, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))

                Text(
                    "Android ${s.android} (API ${s.api}) · ${s.cores} Cores · ${s.physicalRamGb} GB RAM",
                    color = TextSecondary, style = MaterialTheme.typography.bodySmall
                )

                if (s.chipset.isNotBlank()) {
                    Text("${s.chipset} · Max ${s.maxFreqMhz}MHz", color = TextTertiary, fontSize = 11.sp)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(if (s.hasRoot) "ROOT" else "NO ROOT", s.hasRoot, if (s.hasRoot) EmeraldGlow else TextTertiary)
                    StatusPill(if (s.hasShizuku) "SHIZUKU" else "NO ADB", s.hasShizuku, if (s.hasShizuku) CyanGlow else TextTertiary)
                    if (!s.hasShizuku && !s.hasRoot) {
                        TextButton(onClick = { vm.requestShizuku() }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                            Text("Grant ADB", color = CyanGlow, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                HorizontalDivider(color = GlassBorder, thickness = 0.8.dp)

                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("RAM Usage · ${vm.liveAvailRamMb} MB free", color = TextTertiary, style = MaterialTheme.typography.labelSmall)
                        Text("${vm.ramUsedPercent}%", color = if (vm.ramUsedPercent > 85) RedErr else CyanGlow, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }
                    LinearProgressIndicator(
                        progress = { vm.ramUsedPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (vm.ramUsedPercent > 85) RedErr else CyanGlow,
                        trackColor = DarkSurfaceVar
                    )
                }

                if (s.hasRoot) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        StatBlock("CPU FREQ", vm.cpuFreq)
                        StatBlock("TEMP", vm.cpuTemp)
                        StatBlock("ZRAM", vm.zramInfo)
                        StatBlock("GOVERNOR", vm.currentGov)
                    }
                }
            } else {
                Text("Analyzing hardware configuration…", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun ProfileSelectorRow(vm: MainViewModel) {
    val profiles = listOf(
        Triple(OptProfile.PERFORMANCE, "Performance" to "Max CPU frequency · Low latency I/O", Icons.Filled.FlashOn to EmeraldGlow),
        Triple(OptProfile.BALANCED, "Balanced" to "Smart schedutil · Balanced efficiency", Icons.Filled.Tune to CyanGlow),
        Triple(OptProfile.BATTERY, "Battery Saver" to "Powersave CPU · Deep doze engine", Icons.Filled.BatteryFull to EmeraldGlow),
    )

    AppCard {
        Column(Modifier.padding(12.dp), Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                profiles.forEach { (profile, labelDesc, iconAccent) ->
                    val active = vm.activeProfile == profile
                    val (icon, accent) = iconAccent
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (active) accent.copy(alpha = 0.14f) else DarkSurfaceVar)
                            .border(
                                width = 1.dp,
                                color = if (active) accent else GlassBorder,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable(enabled = !vm.isOptimizing) { vm.applyProfile(profile) }
                            .padding(vertical = 12.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box {
                            Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
                            if (active) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 8.dp, y = (-6).dp)
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(accent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(8.dp))
                                }
                            }
                        }
                        Text(
                            labelDesc.first,
                            color = if (active) accent else TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            val activeDesc = profiles.firstOrNull { it.first == vm.activeProfile }?.second?.second
                ?: "Select a profile to tune CPU governor, memory and doze behavior."

            Text(
                activeDesc,
                color = TextTertiary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
    }
}

@Composable
fun ProgressCard(vm: MainViewModel) {
    AppCard {
        Column(Modifier.padding(14.dp), Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = CyanGlow)
                Text(vm.statusMsg, color = CyanGlow, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            }
            LinearProgressIndicator(
                progress = { vm.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = CyanGlow,
                trackColor = DarkSurfaceVar
            )
        }
    }
}