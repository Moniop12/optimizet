package com.monai.optimizer.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.monai.optimizer.optimizer.OptProfile
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
fun HomeScreen(vm: MainViewModel) {
    val ctx = LocalContext.current

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                vm.toggleLiveService(ctx)
            } else {
                Toast.makeText(ctx, "Notification permission is required for this feature.", Toast.LENGTH_SHORT).show()
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        HeroHeader(
            isLiveServiceRunning = vm.isLiveServiceRunning,
            isRefreshing = vm.isRefreshing,
            onToggleService = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    vm.toggleLiveService(ctx)
                }
            },
            onRefresh = { vm.refresh(ctx) }
        )

        DeviceOverviewCard(vm)

        vm.spec?.let { AiRecommendationCard(it.recommended, vm) }

        SectionLabel("Optimization profiles")
        ProfileCard(
            title = "Performance",
            desc = "Maximum CPU frequency and lower I/O latency for demanding workloads.",
            icon = Icons.Filled.FlashOn,
            accent = OrangeGlow,
            profile = OptProfile.PERFORMANCE,
            vm = vm
        )
        ProfileCard(
            title = "Balanced",
            desc = "Smart day-to-day tuning with stable responsiveness and efficiency.",
            icon = Icons.Filled.Tune,
            accent = BrandPrimary,
            profile = OptProfile.BALANCED,
            vm = vm
        )
        ProfileCard(
            title = "Battery Saver",
            desc = "Conservative tuning for cooler temperatures and longer standby time.",
            icon = Icons.Filled.BatteryFull,
            accent = EmeraldGlow,
            profile = OptProfile.BATTERY,
            vm = vm
        )

        AnimatedVisibility(vm.isOptimizing) {
            ProgressGlassCard(vm)
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun HeroHeader(
    isLiveServiceRunning: Boolean,
    isRefreshing: Boolean,
    onToggleService: () -> Unit,
    onRefresh: () -> Unit
) {
    val rotation by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isRefreshing) 360f else 0f,
        animationSpec = androidx.compose.animation.core.tween(500),
        label = "refresh"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = BrandPrimary.copy(alpha = 0.16f)
                    ) {
                        Text(
                            "Professional optimizer",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            color = BrandPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        "MonAi Optimizer",
                        color = TextPrimary,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Cleaner UI, faster access to controls, and room to grow for future modules.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = onToggleService) {
                        Icon(
                            Icons.Filled.Notifications,
                            contentDescription = "Live service",
                            tint = if (isLiveServiceRunning) EmeraldGlow else TextDisabled
                        )
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Refresh",
                            tint = BrandPrimary,
                            modifier = Modifier.rotate(rotation)
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeaderStatusPill(
                    title = "Service",
                    value = if (isLiveServiceRunning) "Online" else "Offline",
                    accent = if (isLiveServiceRunning) EmeraldGlow else TextDisabled
                )
                HeaderStatusPill(
                    title = "Profiles",
                    value = "3 Modes",
                    accent = OrangeGlow
                )
                HeaderStatusPill(
                    title = "Controls",
                    value = "Modular",
                    accent = CyanGlow
                )
            }
        }
    }
}

@Composable
private fun HeaderStatusPill(title: String, value: String, accent: Color) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(title, color = TextSecondary, fontSize = 10.sp)
            Text(value, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceOverviewCard(vm: MainViewModel) {
    val s = vm.spec
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = GlassCard),
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Device overview", color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Core hardware snapshot and live utilization.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                if (s == null) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = BrandPrimary)
                }
            }

            if (s == null) {
                Text("Analyzing hardware configuration...", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    "${s.brand} ${s.model}",
                    color = TextPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Android ${s.android} · API ${s.api} · ${s.cores} CPU cores · ${s.physicalRamGb} GB physical RAM",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (s.chipset.isNotBlank()) {
                    Text("${s.chipset} · Peak ${s.maxFreqMhz} MHz", color = TextSecondary, fontSize = 12.sp)
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip("Root ${if (s.hasRoot) "enabled" else "off"}", s.hasRoot, if (s.hasRoot) EmeraldGlow else RedErr)
                    StatusChip("Shizuku ${if (s.hasShizuku) "ready" else "off"}", s.hasShizuku, if (s.hasShizuku) BrandPrimary else TextDisabled)
                    if (!s.hasShizuku && !s.hasRoot) {
                        AssistChip(
                            onClick = { vm.requestShizuku() },
                            label = { Text("Grant ADB") },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = BrandPrimary.copy(alpha = 0.12f),
                                labelColor = BrandPrimary
                            )
                        )
                    }
                }

                HorizontalDivider(color = DividerSoft)

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricTile("Free RAM", "${vm.liveAvailRamMb} MB", Icons.Filled.Memory, BrandPrimary, Modifier.weight(1f))
                    MetricTile("Governor", vm.currentGov, Icons.Filled.Speed, OrangeGlow, Modifier.weight(1f))
                    MetricTile("ZRAM", vm.zramInfo, Icons.Filled.Storage, EmeraldGlow, Modifier.weight(1f))
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("RAM usage", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                        Text(
                            "${vm.ramUsedPercent}%",
                            color = if (vm.ramUsedPercent > 85) RedErr else BrandPrimary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    LinearProgressIndicator(
                        progress = { vm.ramUsedPercent / 100f },
                        modifier = Modifier.fillMaxWidth().height(7.dp),
                        color = if (vm.ramUsedPercent > 85) RedErr else BrandPrimary,
                        trackColor = DarkBg
                    )
                }

                if (s.hasRoot) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricTile("CPU freq", vm.cpuFreq, Icons.Filled.Speed, BrandPrimary, Modifier.weight(1f))
                        MetricTile("Thermals", vm.cpuTemp, Icons.Filled.AutoAwesome, AmberWarn, Modifier.weight(1f))
                        MetricTile("Cache", "${vm.cacheSizeMb} MB", Icons.Filled.Storage, CyanGlow, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Text(label, color = TextSecondary, fontSize = 11.sp)
            Text(
                value,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun StatusChip(label: String, active: Boolean, color: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = if (active) 0.12f else 0.06f),
        border = BorderStroke(1.dp, color.copy(alpha = if (active) 0.3f else 0.16f))
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun AiRecommendationCard(profile: OptProfile, vm: MainViewModel) {
    val recommendation = remember(profile) {
        when (profile) {
            OptProfile.PERFORMANCE -> Triple("Performance", "High-end hardware detected. Prioritize maximum throughput and responsiveness.", OrangeGlow)
            OptProfile.BALANCED -> Triple("Balanced", "Mid-range setup detected. This mode keeps the device smooth without overcommitting power.", BrandPrimary)
            OptProfile.BATTERY -> Triple("Battery Saver", "Memory or thermal headroom is limited. Favor stability and endurance.", EmeraldGlow)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = recommendation.third.copy(alpha = 0.10f)),
        border = BorderStroke(1.dp, recommendation.third.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(recommendation.third.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = recommendation.third)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("AI recommendation", color = recommendation.third, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(recommendation.first, color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(recommendation.second, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = { vm.applyProfile(profile) },
                enabled = !vm.isOptimizing,
                colors = ButtonDefaults.buttonColors(containerColor = recommendation.third),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Apply", color = Color.White)
            }
        }
    }
}

@Composable
private fun ProfileCard(
    title: String,
    desc: String,
    icon: ImageVector,
    accent: Color,
    profile: OptProfile,
    vm: MainViewModel
) {
    val active = vm.activeProfile == profile
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (active) accent.copy(alpha = 0.10f) else GlassCard),
        border = BorderStroke(1.dp, if (active) accent.copy(alpha = 0.55f) else GlassBorder)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(accent.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (active) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = accent.copy(alpha = 0.14f)
                        ) {
                            Text(
                                "ACTIVE",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = accent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Text(desc, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            if (active) {
                OutlinedButton(
                    onClick = { vm.applyProfile(profile) },
                    enabled = !vm.isOptimizing,
                    border = BorderStroke(1.dp, accent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Re-apply", color = accent)
                }
            } else {
                Button(
                    onClick = { vm.applyProfile(profile) },
                    enabled = !vm.isOptimizing,
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Apply", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = TextSecondary,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 2.dp)
    )
}

@Composable
private fun ProgressGlassCard(vm: MainViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = GlassCard),
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(vm.statusMsg, color = BrandPrimary, style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(
                progress = { vm.progress },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = BrandPrimary,
                trackColor = CardSurface
            )
        }
    }
}
