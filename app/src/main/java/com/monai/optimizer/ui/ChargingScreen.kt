package com.monai.optimizer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monai.optimizer.data.UserPreferencesRepository
import com.monai.optimizer.ui.components.*
import com.monai.optimizer.ui.theme.*

@Composable
fun ChargingScreen(vm: MainViewModel, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
    ) {
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
                Text("Hardware limits, pass-through & current tuning", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!vm.hasRoot) {
                InfoBanner("Hardware charging control requires Root access", StatusError)
            }

            AppCard {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {

                    // ── Bypass Charging ──
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconBadge(Icons.Filled.Power, PurpleGlow, size = 34.dp, iconSize = 16.dp, active = vm.isBypassChargingEnabled)
                        Column(Modifier.weight(1f)) {
                            Text("Bypass Charging (Direct Power)", color = TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("Supply power directly to motherboard without charging battery (Cool Device)", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = vm.isBypassChargingEnabled,
                            onCheckedChange = { vm.setBypassCharging(it) },
                            enabled = vm.hasRoot
                        )
                    }

                    HorizontalDivider(color = HairlineBorder, thickness = 0.8.dp)

                    // ── Charge Limit ──
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconBadge(Icons.Filled.BatteryChargingFull, EmeraldGlow, size = 34.dp, iconSize = 16.dp, active = vm.isChargeLimitEnabled)
                        Column(Modifier.weight(1f)) {
                            Text("Charge Limit", color = TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("Automatically stop charging at set percentage limit", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = vm.isChargeLimitEnabled,
                            onCheckedChange = { vm.setChargeLimit(it, vm.chargeLimitPct) },
                            enabled = vm.hasRoot
                        )
                    }

                    if (vm.hasRoot && vm.isChargeLimitEnabled) {
                        var dragPct by remember(vm.chargeLimitPct) { mutableFloatStateOf(vm.chargeLimitPct) }
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("Stop Charging At", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                            Text("${dragPct.toInt()}%", color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }
                        Slider(
                            value = dragPct,
                            onValueChange = { dragPct = it },
                            onValueChangeFinished = { vm.setChargeLimit(vm.isChargeLimitEnabled, dragPct) },
                            valueRange = 70f..95f,
                            steps = 4,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    HorizontalDivider(color = HairlineBorder, thickness = 0.8.dp)

                    // ── Thermal Protection ──
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconBadge(Icons.Filled.Thermostat, OrangeGlow, size = 34.dp, iconSize = 16.dp, active = vm.isThermalProtectEnabled)
                        Column(Modifier.weight(1f)) {
                            Text("Thermal Protection", color = TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("Throttle charging current to 500mA above 42°C", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = vm.isThermalProtectEnabled,
                            onCheckedChange = { vm.setThermalProtect(it) },
                            enabled = vm.hasRoot
                        )
                    }

                    HorizontalDivider(color = HairlineBorder, thickness = 0.8.dp)

                    // ── Charging Speed ──
                    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Filled.Bolt, EmeraldGlow, size = 34.dp, iconSize = 16.dp, active = true)
                            Column {
                                Text("Charging Speed", color = TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text("Maximum charging current limit in mA", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        if (!vm.hasRoot) {
                            Text("Requires Root access", color = StatusError, fontSize = 11.sp)
                        } else {
                            var sliderPos by remember(vm.chargeSpeedMa) { mutableFloatStateOf(vm.chargeSpeedMa.toFloat()) }

                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("Current Limit", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "${sliderPos.toInt()} mA" + if (sliderPos >= 3000f) " · Fast" else "",
                                    color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Slider(
                                value = sliderPos,
                                onValueChange = { sliderPos = it },
                                onValueChangeFinished = { vm.setChargeSpeed(sliderPos.toInt()) },
                                valueRange = UserPreferencesRepository.MIN_CHARGE_SPEED_MA.toFloat()..UserPreferencesRepository.MAX_CHARGE_SPEED_MA.toFloat()
                            )
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("${UserPreferencesRepository.MIN_CHARGE_SPEED_MA} mA", color = TextTertiary, fontSize = 10.sp)
                                Text("${UserPreferencesRepository.MAX_CHARGE_SPEED_MA} mA", color = TextTertiary, fontSize = 10.sp)
                            }

                            val presets = listOf(
                                500 to "0.5A", 1000 to "1.0A", 1500 to "1.5A",
                                2000 to "2.0A", 3000 to "3.0A", 5000 to "Max"
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(presets) { (mA, label) ->
                                    FilterChip(
                                        selected = vm.chargeSpeedMa == mA,
                                        onClick = { sliderPos = mA.toFloat(); vm.setChargeSpeed(mA) },
                                        label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = HairlineBorder, thickness = 0.8.dp)

                    // ── Live Status ──
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        Arrangement.SpaceBetween
                    ) {
                        StatBlock("BYPASS", if (vm.isBypassChargingEnabled) "Active" else "Off")
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