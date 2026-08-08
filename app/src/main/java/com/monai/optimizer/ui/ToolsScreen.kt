package com.monai.optimizer.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monai.optimizer.optimizer.SCmd
import com.monai.optimizer.optimizer.ShizukuEngine
import com.monai.optimizer.ui.components.*
import com.monai.optimizer.ui.theme.*

private enum class ToolCategoryFilter(val label: String, val icon: ImageVector) {
    ALL("All", Icons.Filled.GridView),
    PERFORMANCE("Performance", Icons.Filled.Speed),
    BATTERY("Battery", Icons.Filled.BatteryChargingFull),
    NETWORK("Network", Icons.Filled.Dns),
    APPS("Apps", Icons.Filled.Apps),
    MAINTENANCE("Maintenance", Icons.Filled.CleaningServices)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    vm: MainViewModel,
    onOpenCharging: () -> Unit,
    onOpenFreezer: () -> Unit,
    onOpenPerformanceHub: () -> Unit,
    onOpenNetworkHub: () -> Unit,
    onOpenMaintenanceHub: () -> Unit,
    onOpenAppHub: () -> Unit
) {
    val ctx = LocalContext.current
    val hasControl = vm.hasRoot || vm.hasShizuku
    var selectedCategory by remember { mutableStateOf(ToolCategoryFilter.ALL) }
    var showNotifSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("System Tweaks Dashboard", style = MaterialTheme.typography.headlineSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
        Text("Categorized hardware & OS optimization hubs", color = TextSecondary, style = MaterialTheme.typography.bodySmall)

        if (vm.statusMsg.isNotEmpty()) {
            InfoBanner(
                vm.statusMsg,
                accent = when (vm.statusSuccess) { true -> GreenOk; false -> RedErr; null -> TextSecondary }
            )
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(ToolCategoryFilter.values()) { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    leadingIcon = { Icon(cat.icon, null, modifier = Modifier.size(14.dp)) },
                    label = { Text(cat.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                )
            }
        }

        SectionLabel("OPTIMIZATION HUBS")

        if (selectedCategory == ToolCategoryFilter.ALL || selectedCategory == ToolCategoryFilter.PERFORMANCE) {
            NavSummaryCard(
                icon = Icons.Filled.Speed,
                accent = CyanGlow,
                title = "Performance Hub",
                subtitle = if (vm.artCompiledAppsMap.isNotEmpty()) "${vm.artCompiledAppsMap.size} apps optimized · Dexopt, Resolution & Governor"
                else "ART Dexopt Compiler, Resolution Scaler, CPU Governor & I/O Scheduler",
                onClick = onOpenPerformanceHub
            )
        }

        if (selectedCategory == ToolCategoryFilter.ALL || selectedCategory == ToolCategoryFilter.BATTERY) {
            NavSummaryCard(
                icon = Icons.Filled.BatteryChargingFull,
                accent = EmeraldGlow,
                title = "Battery Hub",
                subtitle = if (vm.batteryHealthPct > 0) "Health: ${"%.1f".format(vm.batteryHealthPct)}% · Cycle Count: ${vm.batteryCycleCount}"
                else "Charge Limits, Bypass Power & Hardware Battery Health",
                onClick = onOpenCharging
            )
        }

        if (selectedCategory == ToolCategoryFilter.ALL || selectedCategory == ToolCategoryFilter.NETWORK) {
            NavSummaryCard(
                icon = Icons.Filled.Dns,
                accent = UtilityTeal,
                title = "Network Hub",
                subtitle = "System-Wide Private DNS & TCP Congestion Control (BBR Low Ping)",
                onClick = onOpenNetworkHub
            )
        }

        if (selectedCategory == ToolCategoryFilter.ALL || selectedCategory == ToolCategoryFilter.APPS) {
            NavSummaryCard(
                icon = Icons.Filled.Apps,
                accent = CleanPurple,
                title = "App Hub",
                subtitle = if (!hasControl) "Requires Shizuku or Root"
                else "${vm.freezerApps.count { it.isLocked }} frozen · Standby Buckets & AppOps",
                onClick = onOpenAppHub
            )
        }

        if (selectedCategory == ToolCategoryFilter.ALL || selectedCategory == ToolCategoryFilter.MAINTENANCE) {
            NavSummaryCard(
                icon = Icons.Filled.CleaningServices,
                accent = PurpleGlow,
                title = "Maintenance Hub",
                subtitle = "Kill Background Apps, Clear Caches & Aggressive Doze",
                onClick = onOpenMaintenanceHub
            )
        }

        SectionLabel("DASHBOARD CUSTOMIZATION")
        NavSummaryCard(
            icon = Icons.Filled.Notifications,
            accent = TextSecondary,
            title = "Notification Dashboard Settings",
            subtitle = "Manage status bar notification modules and active layout",
            onClick = { showNotifSheet = true }
        )

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
fun AppHubScreen(vm: MainViewModel, onOpenFreezer: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val hasControl = vm.hasRoot || vm.hasShizuku

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = TextPrimary) }
            Column {
                Text("App Hub", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                Text("Freezer, Standby Buckets & AppOps Background Restrict", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }

        Column(
            Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NavSummaryCard(
                icon = Icons.Filled.AcUnit,
                accent = CleanPurple,
                title = "System App Freezer",
                subtitle = "Disable or suspend background apps completely",
                onClick = onOpenFreezer
            )

            ToolActionRow(
                icon = Icons.Filled.Bedtime,
                title = "App Standby Buckets",
                desc = "Force third-party apps into Restricted hibernation bucket",
                accent = CleanPurple,
                enabled = hasControl,
                isRunning = vm.runningTools["standby_buckets"] == true,
                onCardClick = {},
                onRunClick = {
                    vm.runTool(
                        ctx, "standby_buckets", "Standby Buckets",
                        "for pkg in \$(pm list packages -3 | cut -d: -f2); do am set-standby-bucket \$pkg restricted 2>/dev/null; done; echo done"
                    ) { ShizukuEngine.setStandbyBucketsRestricted() }
                }
            )

            ToolActionRow(
                icon = Icons.Filled.HourglassEmpty,
                title = "Restrict Background Activity",
                desc = "Block RUN_IN_BACKGROUND AppOps permission for user apps",
                accent = EmeraldGlow,
                enabled = vm.hasRoot,
                isRunning = vm.runningTools["restrict_bg"] == true,
                onCardClick = {},
                onRunClick = {
                    vm.runTool(
                        ctx, "restrict_bg", "Restrict Background Activity",
                        "for pkg in \$(pm list packages -3 | cut -d: -f2); do appops set \$pkg RUN_IN_BACKGROUND ignore 2>/dev/null; appops set \$pkg RUN_ANY_IN_BACKGROUND ignore 2>/dev/null; done; echo done"
                    ) { ShizukuEngine.restrictBackground() }
                }
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceHubScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val hasControl = vm.hasRoot || vm.hasShizuku
    var showAppPickerSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (vm.freezerApps.isEmpty()) vm.loadFreezerApps(ctx)
    }

    // Hindari Layar Mati saat dexopt jalan lama
    if (vm.isDexoptRunning) {
        val activity = ctx as? Activity
        DisposableEffect(Unit) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = TextPrimary) }
            Column {
                Text("Performance Hub", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                Text("ART Dexopt Compiler, Resolution & Kernel Tuning", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }

        Column(
            Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AppCard {
                Column(Modifier.padding(12.dp), Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IconBadge(Icons.Filled.FlashOn, CyanGlow, active = true)
                        Column(Modifier.weight(1f)) {
                            Text("ART Dexopt Speed Compiler", color = TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Force ART bytecode compilation for selected apps/games to boost speed", color = TextSecondary, fontSize = 11.sp)
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = vm.dexoptMode == "speed-profile",
                            onClick = { vm.dexoptMode = "speed-profile" },
                            enabled = !vm.isDexoptRunning,
                            label = { Text("Profile (Google Def)", fontSize = 10.sp) }
                        )
                        FilterChip(
                            selected = vm.dexoptMode == "speed",
                            onClick = { vm.dexoptMode = "speed" },
                            enabled = !vm.isDexoptRunning,
                            label = { Text("Speed (Max FPS)", fontSize = 10.sp) }
                        )
                        FilterChip(
                            selected = vm.dexoptMode == "everything",
                            onClick = { vm.dexoptMode = "everything" },
                            enabled = !vm.isDexoptRunning,
                            label = { Text("Everything (Full)", fontSize = 10.sp) }
                        )
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { showAppPickerSheet = true },
                            enabled = !vm.isDexoptRunning,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Select Apps (${vm.selectedDexoptPkgs.size})", fontSize = 11.sp)
                        }

                        if (vm.isDexoptRunning) {
                            Button(
                                onClick = { vm.stopDexoptCompilation() },
                                colors = ButtonDefaults.buttonColors(containerColor = RedErr, contentColor = Color.White),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("STOP", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        } else {
                            Button(
                                onClick = { vm.compileAppsArtDexopt(ctx) },
                                enabled = hasControl && vm.selectedDexoptPkgs.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = CyanGlow, contentColor = Color.White),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Compile Selected", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }

                            OutlinedButton(
                                onClick = { vm.restoreOriginalArtDexopt(vm.selectedDexoptPkgs.toList()) },
                                enabled = hasControl && vm.selectedDexoptPkgs.isNotEmpty(),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, RedErr.copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = RedErr)
                            ) {
                                Text("Reset Dexopt", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    if (vm.isDexoptRunning) {
                        LinearProgressIndicator(
                            progress = { vm.dexoptProgress },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                            color = CyanGlow,
                            trackColor = AppSurfaceVariant
                        )
                    }

                    if (vm.dexoptTerminalLogs.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F172A))
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            vm.dexoptTerminalLogs.forEach { logLine ->
                                Text(
                                    logLine,
                                    color = when {
                                        logLine.startsWith("[OK]") -> EmeraldGlow
                                        logLine.startsWith("[FAIL]") -> RedErr
                                        logLine.startsWith("[ABORT]") -> OrangeGlow
                                        logLine.startsWith(">") -> CyanGlow
                                        else -> TextPrimary
                                    },
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            ResolutionScalerCard(vm, hasControl)
            GovernorCard(vm)

            AppCard {
                Column(Modifier.padding(12.dp), Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IconBadge(Icons.Filled.Storage, UtilityTeal, active = vm.hasRoot)
                        Column {
                            Text("Storage I/O Read-Ahead Buffer", color = TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Optimize internal flash storage read buffer for fast asset loading", color = TextSecondary, fontSize = 11.sp)
                        }
                    }

                    if (!vm.hasRoot) {
                        Text("Requires Root access", color = RedErr, fontSize = 10.sp)
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = vm.ioReadAheadPreset == "128",
                                onClick = { vm.applyIoReadAhead("128") },
                                label = { Text("128KB (Stock)", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = vm.ioReadAheadPreset == "512",
                                onClick = { vm.applyIoReadAhead("512") },
                                label = { Text("512KB (Fast)", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = vm.ioReadAheadPreset == "1024",
                                onClick = { vm.applyIoReadAhead("1024") },
                                label = { Text("1024KB (Max)", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showAppPickerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAppPickerSheet = false },
            containerColor = AppSurface,
            contentColor = TextPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("Select Apps to Compile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { vm.selectAllUserDexoptPkgs() }) {
                            Text("Select User Apps", fontSize = 11.sp)
                        }
                        TextButton(onClick = { vm.clearDexoptPkgSelection() }) {
                            Text("Clear", fontSize = 11.sp, color = RedErr)
                        }
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search app...", fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                val availableApps = remember(vm.freezerApps, searchQuery) {
                    vm.freezerApps.filter {
                        searchQuery.isEmpty() || it.name.contains(searchQuery, true) || it.pkg.contains(searchQuery, true)
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(availableApps, key = { it.pkg }) { app ->
                        val isSelected = app.pkg in vm.selectedDexoptPkgs
                        val compiledMode = vm.artCompiledAppsMap[app.pkg]

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) CyanGlow.copy(alpha = 0.1f) else AppSurfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { vm.toggleSelectDexoptPkg(app.pkg) }
                            )
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(app.name, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = TextPrimary)
                                    if (compiledMode != null) {
                                        Text("[Optimized: ${compiledMode.uppercase()}]", fontSize = 9.sp, color = EmeraldGlow, fontWeight = FontWeight.Bold)
                                    } else {
                                        Text("[Stock]", fontSize = 9.sp, color = TextTertiary)
                                    }
                                }
                                Text(app.pkg, fontSize = 10.sp, color = TextTertiary)
                            }
                        }
                    }
                }

                Button(
                    onClick = { showAppPickerSheet = false },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Done (${vm.selectedDexoptPkgs.size} Selected)", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun NetworkHubScreen(vm: MainViewModel, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = TextPrimary) }
            Column {
                Text("Network Hub", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                Text("Private DNS & TCP Congestion Control for low ping gaming", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }

        Column(
            Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PrivateDnsCard(vm)

            AppCard {
                Column(Modifier.padding(12.dp), Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IconBadge(Icons.Filled.Router, UtilityTeal, active = vm.hasRoot)
                        Column {
                            Text("TCP Congestion Control Algorithm", color = TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("BBR (Google) reduces bufferbloat and ping spikes in online games", color = TextSecondary, fontSize = 11.sp)
                        }
                    }

                    if (!vm.hasRoot) {
                        Text("Requires Root access", color = RedErr, fontSize = 10.sp)
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = vm.tcpCongestionPreset == "cubic",
                                onClick = { vm.applyTcpCongestion("cubic") },
                                label = { Text("Cubic (Standard)", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = vm.tcpCongestionPreset == "bbr",
                                onClick = { vm.applyTcpCongestion("bbr") },
                                label = { Text("Google BBR (Low Ping)", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun MaintenanceHubScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val hasControl = vm.hasRoot || vm.hasShizuku

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = TextPrimary) }
            Column {
                Text("Maintenance Hub", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                Text("Kill Background Apps, Cache Trim & Doze Management", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }

        Column(
            Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ToolActionRow(
                icon = Icons.Filled.Memory,
                title = "Kill Background Apps",
                desc = "Force-stop inactive background processes",
                accent = CyanGlow,
                enabled = hasControl,
                isRunning = vm.runningTools["ram_clean"] == true,
                onCardClick = {},
                onRunClick = {
                    vm.runTool(ctx, "ram_clean", "Kill Background Apps", "am kill-all; cmd activity kill-all") { ShizukuEngine.killBgApps() }
                }
            )

            ToolActionRow(
                icon = Icons.Filled.Delete,
                title = "Clear System Caches",
                desc = "Trim app cache storage across all installed applications",
                accent = PurpleGlow,
                enabled = hasControl,
                isRunning = vm.runningTools["clear_cache"] == true,
                onCardClick = {},
                onRunClick = {
                    vm.runTool(ctx, "clear_cache", "Clear System Caches", "cmd package trim-caches 999G") { ShizukuEngine.trimMemory() }
                }
            )

            ToolActionRow(
                icon = Icons.Filled.DeleteSweep,
                title = "Clear Page Cache (Slows App Launches)",
                desc = "Releases memory but forces OS to reload assets from storage",
                accent = PurpleGlow,
                enabled = vm.hasRoot,
                isRunning = vm.runningTools["drop_cache"] == true,
                onCardClick = {},
                onRunClick = {
                    vm.runTool(ctx, "drop_cache", "Drop Page Cache", "sync; echo 3 > /proc/sys/vm/drop_caches") { SCmd(false, "", "") }
                }
            )

            ToolActionRow(
                icon = Icons.Filled.ModeNight,
                title = "Aggressive Doze Mode",
                desc = "Instantly force Android Power Manager into deep idle battery state",
                accent = AmberWarn,
                enabled = hasControl,
                isRunning = vm.runningTools["doze_mode"] == true,
                onCardClick = {},
                onRunClick = {
                    vm.runTool(ctx, "doze_mode", "Aggressive Doze", "dumpsys deviceidle force-idle") {
                        val r = ShizukuEngine.aggressiveDoze()
                        SCmd(r.all { it.success }, r.joinToString(" | ") { it.output }, r.joinToString(" && ") { it.cmd })
                    }
                }
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── REUSABLE HELPER COMPONENTS ──

@Composable
fun ToolActionRow(
    icon: ImageVector,
    title: String,
    desc: String,
    accent: Color,
    enabled: Boolean,
    isRunning: Boolean,
    onCardClick: () -> Unit = {},
    onRunClick: () -> Unit
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBadge(icon, accent, size = 34.dp, iconSize = 16.dp, active = enabled)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        title,
                        color = if (enabled) TextPrimary else TextDisabled,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        desc,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2
                    )
                }
            }

            FilledTonalButton(
                onClick = onRunClick,
                enabled = enabled && !isRunning,
                shape = RoundedCornerShape(9.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (enabled) AccentMuted else AppSurfaceVariant,
                    contentColor = if (enabled) Accent else TextDisabled
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp)
            ) {
                if (isRunning) {
                    CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 1.6.dp, color = Accent)
                } else {
                    Text(if (enabled) "Run" else "Locked", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
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
                    Text("Secure DNS routing to block ads or decrease latency", color = TextSecondary, fontSize = 11.sp)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = vm.privateDnsPreset == "AUTO",
                    onClick = {
                        vm.applyPrivateDns("AUTO", "settings put global private_dns_mode opportunistic") {
                            ShizukuEngine.sh("settings put global private_dns_mode opportunistic")
                        }
                    },
                    label = { Text("Auto", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = vm.privateDnsPreset == "ADGUARD",
                    onClick = {
                        vm.applyPrivateDns("ADGUARD", "settings put global private_dns_mode hostname; settings put global private_dns_specifier dns.adguard.com") {
                            ShizukuEngine.sh("settings put global private_dns_mode hostname; settings put global private_dns_specifier dns.adguard.com")
                        }
                    },
                    label = { Text("AdGuard (Block Ads)", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = vm.privateDnsPreset == "CLOUDFLARE",
                    onClick = {
                        vm.applyPrivateDns("CLOUDFLARE", "settings put global private_dns_mode hostname; settings put global private_dns_specifier 1dot1dot1dot1.cloudflare-dns.com") {
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
                        "Proportional resolution scaler for high gaming FPS",
                        color = TextSecondary, style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (!enabled) {
                Text("Requires Root or Shizuku permission", color = RedErr, fontSize = 10.sp)
            } else {
                Text(
                    "Native Display: ${vm.nativeWidth}x${vm.nativeHeight} (${vm.nativeDensity} DPI)",
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
                    "Safe Reset: Restores Governor, Dexopt, Resolution, DNS, TCP & Doze back to factory state",
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
            ) { Text("Reset All", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
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
                Text("Requires Root access", color = RedErr, fontSize = 10.sp)
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