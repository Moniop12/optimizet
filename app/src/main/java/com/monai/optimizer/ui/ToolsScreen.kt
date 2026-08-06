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
fun ToolsScreen(vm: MainViewModel, onOpenCharging: () -> Unit, onOpenFreezer: () -> Unit = {}) {
    val ctx = LocalContext.current
    val hasControl = vm.hasRoot || vm.hasShizuku
    var showNotifSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("System Tools", style = MaterialTheme.typography.headlineSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
        Text("Fine-grained hardware & OS control", color = TextSecondary, style = MaterialTheme.typography.bodySmall)

        if (vm.statusMsg.isNotEmpty()) {
            InfoBanner(
                vm.statusMsg,
                accent = when (vm.statusSuccess) { true -> GreenOk; false -> RedErr; null -> TextSecondary }
            )
        }

        SectionLabel("NOTIFICATION BAR")
        NavSummaryCard(
            icon = Icons.Filled.Notifications,
            accent = CyanGlow,
            title = "Customize Notification Bar",
            subtitle = "Manage visible modules & custom layout",
            onClick = { showNotifSheet = true }
        )

        SectionLabel("POWER & CHARGING")
        NavSummaryCard(
            icon = Icons.Filled.BatteryChargingFull,
            accent = EmeraldGlow,
            title = "Smart Charging & Bypass Power",
            subtitle = if (!vm.hasRoot) "Requires root access"
            else if (vm.isBypassChargingEnabled) "Bypass Charging ACTIVE (HP Dingin)"
            else if (vm.isChargeLimitEnabled) "Limit ${vm.chargeLimitPct.toInt()}% · ${vm.chargeSpeedMa} mA"
            else "Charge control idle",
            onClick = onOpenCharging
        )

        SectionLabel("APP MANAGEMENT")
        NavSummaryCard(
            icon = Icons.Filled.AcUnit,
            accent = CleanPurple,
            title = "System App Freezer",
            subtitle = if (!hasControl) "Requires Shizuku or Root"
            else if (vm.freezerApps.isEmpty()) "Suspend apps to stop all background activity"
            else "${vm.freezerApps.count { it.isLocked }} frozen · ${vm.freezerApps.size} total apps",
            onClick = onOpenFreezer
        )

        ToolActionRow(
            icon = Icons.Filled.Bedtime,
            title = "App Standby Buckets (Hibernation)",
            desc = "Setel aplikasi pihak-3 ke RESTRICTED (Bekukan total)",
            accent = CleanPurple,
            enabled = hasControl,
            isRunning = vm.runningTools["standby_buckets"] == true
        ) {
            vm.runTool(
                ctx, "standby_buckets", "App Standby Buckets",
                "for pkg in \$(pm list packages -3 | cut -d: -f2); do am set-standby-bucket \$pkg restricted 2>/dev/null; done; echo done"
            ) { ShizukuEngine.setStandbyBucketsRestricted() }
        }

        ToolActionRow(
            icon = Icons.Filled.HourglassEmpty,
            title = "Restrict Background Activity",
            desc = "appops: batasi RUN_IN_BACKGROUND app pihak-3",
            accent = EmeraldGlow,
            enabled = hasControl,
            isRunning = vm.runningTools["restrict_bg"] == true
        ) {
            vm.runTool(
                ctx, "restrict_bg", "Restrict Background Activity",
                "for pkg in \$(pm list packages -3 | cut -d: -f2); do appops set \$pkg RUN_IN_BACKGROUND ignore; appops set \$pkg RUN_ANY_IN_BACKGROUND ignore; done; echo done"
            ) { ShizukuEngine.restrictBackground() }
        }

        // ── FITUR BARU ANTI GIMIK: System-Wide Private DNS ───────────────
        SectionLabel("NETWORK & DNS TUNER")
        PrivateDnsCard(vm)

        SectionLabel("GAMING DISPLAY TUNER")
        ResolutionScalerCard(vm, hasControl)

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
            vm.runTool(ctx, "doze_mode", "Aggressive Doze", "dumpsys deviceidle force-idle") {
                val r = ShizukuEngine.aggressiveDoze()
                com.monai.optimizer.optimizer.SCmd(r.all { it.success }, r.joinToString(" | ") { it.output }, r.joinToString(" && ") { it.cmd })
            }
        }

        SectionLabel("DANGER ZONE")
        DangerResetCard(vm, hasControl)

        Spacer(Modifier.height(12.dp))
    }

    if (showNotifSheet) {
        ModalBottomSheet(
            onDismissRequest = { showNotifSheet = false },
            containerColor = AppSurface,
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

                HorizontalDivider(color = HairlineBorder, thickness = 0.8.dp)

                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("Show Free RAM Usage", color = TextPrimary, fontSize = 13.sp)
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
fun PrivateDnsCard(vm: MainViewModel) {
    val ctx = LocalContext.current
    AppCard {
        Column(Modifier.padding(12.dp), Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Filled.Dns, UtilityTeal, active = true)
                Column(Modifier.weight(1f)) {
                    Text("System-Wide Private DNS", color = TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Route all DNS securely, block ads or speed up net", color = TextSecondary, fontSize = 11.sp)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = false,
                    onClick = {
                        vm.runTool(ctx, "dns_auto", "DNS Auto", "settings put global private_dns_mode opportunistic") {
                            ShizukuEngine.sh("settings put global private_dns_mode opportunistic")
                        }
                    },
                    label = { Text("Auto", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = false,
                    onClick = {
                        vm.runTool(ctx, "dns_adg", "DNS AdGuard", "settings put global private_dns_mode hostname; settings put global private_dns_specifier dns.adguard.com") {
                            ShizukuEngine.sh("settings put global private_dns_mode hostname; settings put global private_dns_specifier dns.adguard.com")
                        }
                    },
                    label = { Text("AdGuard (Block Ads)", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = false,
                    onClick = {
                        vm.runTool(ctx, "dns_cf", "DNS Cloudflare", "settings put global private_dns_mode hostname; settings put global private_dns_specifier 1dot1dot1dot1.cloudflare-dns.com") {
                            ShizukuEngine.sh("settings put global private_dns_mode hostname; settings put global private_dns_specifier 1dot1dot1dot1.cloudflare-dns.com")
                        }
                    },
                    label = { Text("Cloudflare (Fast)", fontSize = 11.sp) }
                )
            }
        }
    }
}

@Composable
fun ResolutionScalerCard(vm: MainViewModel, enabled: Boolean) {
    AppCard {
        Column(Modifier.padding(12.dp), Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Filled.AspectRatio, UtilityTeal, size = 34.dp, iconSize = 16.dp, active = enabled)
                Column {
                    Text("Resolution & Aspect-Ratio Scaler", color = TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Skala Proposional: Presisi di semua rasio layar HP/Tablet tanpa kepotong",
                        color = TextSecondary, style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (!enabled) {
                Text("Requires Root or Shizuku permission", color = RedErr, fontSize = 10.sp)
            } else {
                Text(
                    "Resolusi Asli Layar: ${vm.nativeWidth}x${vm.nativeHeight} (${vm.nativeDensity} DPI)",
                    color = TextTertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = vm.resolutionPreset == "NATIVE",
                        onClick = { vm.applyDynamicResolutionScale("NATIVE", 1.0f) },
                        label = { Text("Default (100%)", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = vm.resolutionPreset == "SCALE_75",
                        onClick = { vm.applyDynamicResolutionScale("SCALE_75", 0.75f) },
                        label = { Text("HD Scale (75%)", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = vm.resolutionPreset == "SCALE_60",
                        onClick = { vm.applyDynamicResolutionScale("SCALE_60", 0.60f) },
                        label = { Text("Performance (60%)", fontSize = 11.sp) }
                    )
                }
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
                Text(
                    if (vm.hasRoot) "Restore governor, sysctl, resolution & doze to defaults"
                    else "Restore anim speed & process limit (Shizuku) — governor needs root",
                    color = TextSecondary, style = MaterialTheme.typography.bodySmall
                )
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