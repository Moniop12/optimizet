package com.monai.optimizer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.monai.optimizer.ui.theme.Cyan400
import com.monai.optimizer.ui.theme.Cyan500
import com.monai.optimizer.ui.theme.Cyan900
import com.monai.optimizer.ui.theme.DarkBg
import com.monai.optimizer.ui.theme.DarkCard
import com.monai.optimizer.ui.theme.DarkSurfaceVar
import com.monai.optimizer.ui.theme.GreenOk
import com.monai.optimizer.ui.theme.OrangeAcc
import com.monai.optimizer.ui.theme.TextDisabled
import com.monai.optimizer.ui.theme.TextPrimary
import com.monai.optimizer.ui.theme.TextSecondary

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
        // ── Header ────────────────────────────────────────────────────
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
                        fontSize = 30.sp
                    )
                )
                Text(
                    "AI CPU & RAM Optimizer",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            IconButton(onClick = { vm.refresh(ctx) }) {
                Icon(Icons.Filled.Refresh, "Refresh", tint = TextSecondary)
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

        ProfileCard(
            "Performance", "Max CPU speed · aggressive RAM & I/O tuning",
            Icons.Filled.FlashOn, OrangeAcc, OptProfile.PERFORMANCE, vm
        )
        ProfileCard(
            "Balanced", "Smart schedutil · smooth & battery-friendly",
            Icons.Filled.Tune, Cyan500, OptProfile.BALANCED, vm
        )
        ProfileCard(
            "Battery Saver", "Conservative CPU · deep doze · kill background",
            Icons.Filled.BatteryFull, GreenOk, OptProfile.BATTERY, vm
        )

        AnimatedVisibility(
            vm.isOptimizing,
            enter = fadeIn() + expandVertically(),
            exit  = fadeOut() + shrinkVertically()
        ) { ProgressCard(vm) }

        Spacer(Modifier.height(8.dp))
    }
}

// ── Device card ───────────────────────────────────────────────────────

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
                if (s == null)
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Cyan400)
            }
            if (s != null) {
                Text(
                    "${s.brand} ${s.model}",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    "Android ${s.android} (API ${s.api})  ·  ${s.cores} cores  ·  ${s.totalRamMb / 1024}GB RAM",
                    color = TextSecondary, style = MaterialTheme.typography.bodyMedium
                )
                if (s.chipset.isNotBlank())
                    Text(
                        "${s.chipset}  ·  ${s.maxFreqMhz}MHz",
                        color = TextSecondary, style = MaterialTheme.typography.bodyMedium
                    )

                HorizontalDivider(
                    color = DarkSurfaceVar,
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )

                // Permission chips
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip(
                        "ROOT ${if (s.hasRoot) "✓" else "✗"}",
                        s.hasRoot,
                        if (s.hasRoot) GreenOk else TextDisabled
                    )
                    StatusChip(
                        "SHIZUKU ${if (s.hasShizuku) "✓" else "✗"}",
                        s.hasShizuku,
                        if (s.hasShizuku) Cyan400 else TextDisabled
                    )
                    if (!s.hasShizuku) {
                        TextButton(
                            onClick = { vm.requestShizuku() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) { Text("Grant", color = Cyan400, fontSize = 11.sp) }
                    }
                }

                // Live root stats
                if (s.hasRoot) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        StatItem("CPU",  vm.cpuFreq)
                        StatItem("Temp", vm.cpuTemp)
                        StatItem("ZRAM", vm.zramInfo)
                        StatItem("Gov",  vm.currentGov.take(11))
                    }
                }
            } else {
                Text("Analyzing device…", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun StatusChip(label: String, active: Boolean, color: Color) {
    Surface(
        shape  = RoundedCornerShape(20.dp),
        color  = color.copy(alpha = if (active) 0.13f else 0.07f),
        border = BorderStroke(1.dp, color.copy(alpha = if (active) 0.4f else 0.18f))
    ) {
        Text(
            label,
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            color = color,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        Text(
            value, color = TextPrimary,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

// ── AI recommendation card ────────────────────────────────────────────

@Composable
fun AiRecCard(profile: OptProfile, vm: MainViewModel) {
    val (label, desc, accent) = when (profile) {
        OptProfile.PERFORMANCE -> Triple("Performance", "High-end device — optimize for max throughput", OrangeAcc)
        OptProfile.BALANCED    -> Triple("Balanced",    "Mid-range — balanced speed & efficiency",       Cyan500)
        OptProfile.BATTERY     -> Triple("Battery",     "Limited RAM detected — conserve resources",     GreenOk)
    }
    Card(
        Modifier.fillMaxWidth(),
        RoundedCornerShape(14.dp),
        CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.07f)),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    ) {
        Row(Modifier.padding(14.dp), Alignment.CenterVertically, Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(10.dp), color = accent.copy(alpha = 0.14f)) {
                Icon(Icons.Filled.AutoAwesome, null, Modifier.padding(10.dp).size(22.dp), tint = accent)
            }
            Column(Modifier.weight(1f)) {
                Text("AI Rec: $label", color = accent, style = MaterialTheme.typography.titleMedium)
                Text(desc, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            Button(
                onClick = { vm.applyProfile(profile) },
                enabled = !vm.isOptimizing,
                colors  = ButtonDefaults.buttonColors(containerColor = accent),
                shape   = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) { Text("Apply", color = Color.White, fontSize = 13.sp) }
        }
    }
}

// ── Profile card ──────────────────────────────────────────────────────

@Composable
fun ProfileCard(
    title: String, desc: String,
    icon: ImageVector, accent: Color,
    profile: OptProfile, vm: MainViewModel
) {
    val active = vm.activeProfile == profile
    Card(
        Modifier.fillMaxWidth(),
        RoundedCornerShape(13.dp),
        CardDefaults.cardColors(containerColor = if (active) accent.copy(alpha = 0.1f) else DarkCard),
        border = BorderStroke(1.dp, if (active) accent.copy(alpha = 0.45f) else DarkSurfaceVar)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(13.dp),
            Alignment.CenterVertically,
            Arrangement.spacedBy(11.dp)
        ) {
            Surface(shape = RoundedCornerShape(10.dp), color = accent.copy(alpha = 0.13f)) {
                Icon(icon, null, Modifier.padding(9.dp).size(22.dp), tint = accent)
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                    if (active) {
                        Surface(shape = RoundedCornerShape(4.dp), color = accent.copy(alpha = 0.2f)) {
                            Text(
                                "ACTIVE",
                                Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                color = accent,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
                Text(desc, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            Button(
                onClick = { vm.applyProfile(profile) },
                enabled = !vm.isOptimizing,
                colors  = ButtonDefaults.buttonColors(containerColor = accent.copy(alpha = 0.85f)),
                shape   = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) { Text("Apply", color = Color.White, fontSize = 13.sp) }
        }
    }
}

// ── Progress card ─────────────────────────────────────────────────────

@Composable
fun ProgressCard(vm: MainViewModel) {
    Card(
        Modifier.fillMaxWidth(),
        RoundedCornerShape(13.dp),
        CardDefaults.cardColors(containerColor = DarkCard),
        border = BorderStroke(1.dp, Cyan500.copy(alpha = 0.4f))
    ) {
        Column(Modifier.padding(16.dp), Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Cyan400)
                Text("Applying optimization…", color = Cyan400, style = MaterialTheme.typography.titleMedium)
            }
            LinearProgressIndicator(
                progress = { vm.progress },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color    = Cyan400,
                trackColor = Cyan900.copy(alpha = 0.4f)
            )
            Text(
                vm.statusMsg, color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}
