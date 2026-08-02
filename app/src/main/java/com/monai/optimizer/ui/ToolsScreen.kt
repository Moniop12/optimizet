package com.monai.optimizer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.ModeNight
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monai.optimizer.optimizer.RootEngine
import com.monai.optimizer.optimizer.ShizukuEngine
import com.monai.optimizer.ui.theme.AmberWarn
import com.monai.optimizer.ui.theme.Cyan400
import com.monai.optimizer.ui.theme.Cyan500
import com.monai.optimizer.ui.theme.Cyan900
import com.monai.optimizer.ui.theme.DarkBg
import com.monai.optimizer.ui.theme.DarkCard
import com.monai.optimizer.ui.theme.DarkSurfaceVar
import com.monai.optimizer.ui.theme.OrangeAcc
import com.monai.optimizer.ui.theme.RedErr
import com.monai.optimizer.ui.theme.TextPrimary
import com.monai.optimizer.ui.theme.TextSecondary

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
        Text(
            "Fine-grained system control",
            color = TextSecondary, style = MaterialTheme.typography.bodyMedium
        )

        // Status message
        if (vm.statusMsg.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = DarkCard,
                border = BorderStroke(1.dp, Cyan900.copy(alpha = 0.5f))
            ) {
                Text(
                    vm.statusMsg,
                    Modifier.padding(12.dp),
                    color = Cyan400,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // ── ROOT TOOLS ────────────────────────────────────────────────
        if (vm.hasRoot) {
            SecLabel("CPU  •  ROOT")
            GovernorCard(vm)

            ToolItem(Icons.Filled.Memory, "Kill Background Apps",
                "am kill-all — reclaim RAM instantly", OrangeAcc) {
                vm.doRoot("Kill BG") { RootEngine.killBgApps() }
            }
            ToolItem(Icons.Filled.DeleteSweep, "Drop Page Cache",
                "sync; echo 3 > /proc/sys/vm/drop_caches", OrangeAcc) {
                vm.doRoot("Drop Cache") { RootEngine.dropCaches() }
            }
            ToolItem(Icons.Filled.Delete, "Clear App Caches",
                "Remove /data/data/*/cache content", OrangeAcc) {
                vm.doRoot("Clear Cache") { RootEngine.clearCaches() }
            }

            SecLabel("NETWORK  •  ROOT")
            ToolItem(Icons.Filled.Speed, "Enable TCP BBR",
                "Better congestion control + larger net buffers", Cyan500) {
                vm.doRootMulti("TCP BBR") { RootEngine.enableBBR() }
            }
        } else {
            NoRootInfo()
        }

        // ── SHIZUKU TOOLS ─────────────────────────────────────────────
        if (vm.hasShizuku) {
            SecLabel("ANIMATION & UI  •  SHIZUKU")
            AnimScaleCard(vm)

            ToolItem(Icons.Filled.Stop, "Trim Memory (ADB)",
                "am send-trim-memory all 80", Cyan500) {
                vm.doShz("Trim Mem") { ShizukuEngine.trimMemory() }
            }
            ToolItem(Icons.Filled.ModeNight, "Aggressive Doze",
                "Force device into deep idle mode", AmberWarn) {
                vm.doShzMulti("Doze") { ShizukuEngine.aggressiveDoze() }
            }
            ToolItem(Icons.Filled.Wifi, "Throttle WiFi Scan",
                "settings put global wifi_scan_throttle_enabled 1", Cyan500) {
                vm.doShz("WiFi Throttle") {
                    ShizukuEngine.applyPerformance().first { it.cmd.contains("wifi") }
                }
            }
        } else {
            NoShizukuInfo(vm)
        }

        // ── No access banner ──────────────────────────────────────────
        if (!vm.hasRoot && !vm.hasShizuku) {
            NoAccessInfo(vm)
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable fun SecLabel(t: String) {
    Text(
        t, color = TextSecondary, style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
fun ToolItem(
    icon: ImageVector, title: String, desc: String,
    accent: Color, onClick: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        RoundedCornerShape(12.dp),
        CardDefaults.cardColors(containerColor = DarkCard),
        border = BorderStroke(1.dp, DarkSurfaceVar)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            Arrangement.spacedBy(11.dp),
            Alignment.CenterVertically,
        ) {
            Surface(shape = RoundedCornerShape(9.dp), color = accent.copy(alpha = 0.12f)) {
                Icon(icon, null, Modifier.padding(8.dp).size(20.dp), tint = accent)
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                Text(desc, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(
                onClick = onClick,
                shape  = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp)
            ) { Text("Run", fontSize = 12.sp, color = accent) }
        }
    }
}

@Composable
fun GovernorCard(vm: MainViewModel) {
    if (vm.governors.isNotEmpty()) {
        Card(
            Modifier.fillMaxWidth(), RoundedCornerShape(12.dp),
            CardDefaults.cardColors(containerColor = DarkCard),
            border = BorderStroke(1.dp, DarkSurfaceVar)
        ) {
            Column(Modifier.padding(12.dp), Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column {
                        Text("CPU Governor", color = TextPrimary,
                            style = MaterialTheme.typography.titleMedium)
                        Text("Active: ${vm.currentGov}", color = Cyan400,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    Icon(Icons.Filled.Settings, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    Arrangement.spacedBy(6.dp)
                ) {
                    vm.governors.forEach { gov ->
                        FilterChip(
                            selected = gov == vm.currentGov,
                            onClick  = { vm.setGovernor(gov) },
                            label    = { Text(gov, fontSize = 12.sp) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Cyan900.copy(alpha = 0.7f),
                                selectedLabelColor     = Cyan400,
                                containerColor         = DarkSurfaceVar,
                                labelColor             = TextSecondary,
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AnimScaleCard(vm: MainViewModel) {
    var sel by remember { mutableStateOf(1f) }
    val opts = listOf(0f to "Off", 0.5f to "0.5×", 1f to "1×", 1.5f to "1.5×", 2f to "2×")
    Card(
        Modifier.fillMaxWidth(), RoundedCornerShape(12.dp),
        CardDefaults.cardColors(containerColor = DarkCard),
        border = BorderStroke(1.dp, DarkSurfaceVar)
    ) {
        Column(Modifier.padding(12.dp), Arrangement.spacedBy(8.dp)) {
            Text("Animation Scale", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
            Text(
                "window / transition / animator_duration_scale",
                color = TextSecondary, style = MaterialTheme.typography.bodyMedium
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(6.dp)) {
                opts.forEach { (s, lbl) ->
                    FilterChip(
                        selected = sel == s,
                        onClick  = {
                            sel = s
                            vm.doShz("AnimScale $lbl") { ShizukuEngine.setAnimScale(s) }
                        },
                        label  = { Text(lbl, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Cyan900.copy(alpha = 0.7f),
                            selectedLabelColor     = Cyan400,
                            containerColor         = DarkSurfaceVar,
                            labelColor             = TextSecondary,
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun NoRootInfo() {
    Surface(
        shape = RoundedCornerShape(12.dp), color = DarkCard,
        border = BorderStroke(1.dp, RedErr.copy(alpha = 0.3f))
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), Arrangement.spacedBy(10.dp), Alignment.CenterVertically) {
            Icon(Icons.Filled.Lock, null, tint = RedErr, modifier = Modifier.size(20.dp))
            Column {
                Text("Root not detected", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Grant KernelSU / Magisk root access to unlock CPU & RAM tools",
                    color = TextSecondary, style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun NoShizukuInfo(vm: MainViewModel) {
    if (!vm.hasRoot) {
        Surface(
            shape = RoundedCornerShape(12.dp), color = DarkCard,
            border = BorderStroke(1.dp, AmberWarn.copy(alpha = 0.3f))
        ) {
            Row(Modifier.fillMaxWidth().padding(14.dp), Arrangement.spacedBy(10.dp), Alignment.CenterVertically) {
                Icon(Icons.Filled.PowerSettingsNew, null, tint = AmberWarn, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f)) {
                    Text("Shizuku not active", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Open Shizuku app → Start service → tap Grant here",
                        color = TextSecondary, style = MaterialTheme.typography.bodyMedium
                    )
                }
                Button(
                    onClick = { vm.requestShizuku() },
                    colors  = ButtonDefaults.buttonColors(containerColor = AmberWarn),
                    shape   = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) { Text("Grant", color = Color.Black, fontSize = 12.sp) }
            }
        }
    }
}

@Composable
fun NoAccessInfo(vm: MainViewModel) {
    Surface(
        shape = RoundedCornerShape(12.dp), color = DarkCard,
        border = BorderStroke(1.dp, DarkSurfaceVar)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), Arrangement.spacedBy(6.dp)) {
            Text("No access granted", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
            Text(
                "Grant Root (KernelSU) or Shizuku to unlock all features.\n" +
                "Shizuku = tap Grant above setelah start service di app Shizuku.",
                color = TextSecondary, style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
