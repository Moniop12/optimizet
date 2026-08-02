package com.monai.optimizer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monai.optimizer.ui.components.*
import com.monai.optimizer.ui.theme.*

@Composable
fun ChargingScreen(vm: MainViewModel, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // Top bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = TextPrimary)
            }
            Column {
                Text("Smart Charging Control", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                Text("Battery limit & current tuning", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!vm.hasRoot) {
                InfoBanner("Hardware charging control requires Root access", RedErr)
            }

            // Enable / disable charge limit
            AppCard(accent = EmeraldGlow) {
                Column(Modifier.padding(16.dp), Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Filled.BatteryChargingFull, EmeraldGlow)
                            Column {
                                Text("Charge Limit", color = TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text("Stop charging automatically at a set level", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Switch(
                            checked = vm.isChargeLimitEnabled,
                            onCheckedChange = { vm.setChargeLimit(it, vm.chargeLimitPct) },
                            enabled = vm.hasRoot,
                            colors = SwitchDefaults.colors(checkedTrackColor = EmeraldGlow)
                        )
                    }

                    if (vm.hasRoot) {
                        HorizontalDivider(color = GlassBorder, thickness = 0.8.dp)

                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("Stop Charging At", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                            Text("${vm.chargeLimitPct.toInt()}%", color = EmeraldGlow, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }
                        Slider(
                            value = vm.chargeLimitPct,
                            onValueChange = { vm.setChargeLimit(vm.isChargeLimitEnabled, it) },
                            valueRange = 70f..95f,
                            steps = 4,
                            enabled = vm.isChargeLimitEnabled,
                            colors = SliderDefaults.colors(thumbColor = EmeraldGlow, activeTrackColor = EmeraldGlow)
                        )
                    }
                }
            }

            // Charging speed
            AppCard(accent = CyanGlow) {
                Column(Modifier.padding(16.dp), Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Filled.Bolt, CyanGlow)
                        Column {
                            Text("Charging Speed", color = TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("Current limit (mA) applied while charging", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    if (!vm.hasRoot) {
                        Text("Requires root access", color = RedErr, fontSize = 11.sp)
                    } else {
                        Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(6.dp)) {
                            listOf(
                                500 to "500 mA · Slow",
                                1000 to "1000 mA",
                                1500 to "1500 mA",
                                2000 to "2000 mA · Fast",
                                3000 to "Max Speed"
                            ).forEach { (mA, label) ->
                                FilterChip(
                                    selected = vm.chargeSpeedMa == mA,
                                    onClick = { vm.setChargeSpeed(mA) },
                                    label = { Text(label, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CyanGlow.copy(alpha = 0.18f))
                                )
                            }
                        }
                    }
                }
            }

            // Live status
            AppCard {
                Column(Modifier.padding(16.dp), Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Filled.Speed, PurpleGlow)
                        Text("Live Status", color = TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        StatBlock("LIMIT", if (vm.isChargeLimitEnabled) "${vm.chargeLimitPct.toInt()}%" else "Off")
                        StatBlock("SPEED", "${vm.chargeSpeedMa} mA")
                        StatBlock("ACCESS", if (vm.hasRoot) "Root" else if (vm.hasShizuku) "ADB" else "Limited")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
