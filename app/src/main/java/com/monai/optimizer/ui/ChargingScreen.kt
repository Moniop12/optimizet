package com.monai.optimizer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
                    // Title + switch share a single line so the switch always centers against
                    // ONE line of text, never the whole 2-line description block (fixes the
                    // switch visually "sinking" below-center on long descriptions).
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Filled.BatteryChargingFull, EmeraldGlow)
                            Text("Charge Limit", color = TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        }
                        Switch(
                            checked = vm.isChargeLimitEnabled,
                            onCheckedChange = { vm.setChargeLimit(it, vm.chargeLimitPct) },
                            enabled = vm.hasRoot,
                            colors = SwitchDefaults.colors(checkedTrackColor = EmeraldGlow)
                        )
                    }
                    Text(
                        "Stop charging automatically at a set level",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 50.dp, top = (-4).dp)
                    )

                    if (vm.hasRoot) {
                        HorizontalDivider(color = GlassBorder, thickness = 0.8.dp)

                        // Local drag state — the slider tracks this instantly (60fps, no lag),
                        // and only commits to the ViewModel/DataStore once the finger lifts.
                        // Previously this called vm.setChargeLimit() on every pixel of drag,
                        // which triggered a coroutine + DataStore write per frame -> stutter.
                        var dragPct by remember(vm.chargeLimitPct) { mutableFloatStateOf(vm.chargeLimitPct) }

                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("Stop Charging At", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                            Text("${dragPct.toInt()}%", color = EmeraldGlow, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }
                        Slider(
                            value = dragPct,
                            onValueChange = { dragPct = it },
                            onValueChangeFinished = { vm.setChargeLimit(vm.isChargeLimitEnabled, dragPct) },
                            valueRange = 70f..95f,
                            steps = 4,
                            enabled = vm.isChargeLimitEnabled,
                            colors = SliderDefaults.colors(thumbColor = EmeraldGlow, activeTrackColor = EmeraldGlow)
                        )
                    }
                }
            }

            // Thermal protection
            AppCard(accent = OrangeGlow) {
                Column(Modifier.padding(16.dp), Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Filled.Thermostat, OrangeGlow)
                            Text("Thermal Protection", color = TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        }
                        Switch(
                            checked = vm.isThermalProtectEnabled,
                            onCheckedChange = { vm.setThermalProtect(it) },
                            enabled = vm.hasRoot,
                            colors = SwitchDefaults.colors(checkedTrackColor = OrangeGlow)
                        )
                    }
                    Text(
                        "Auto-throttle current above 42°C, restore below 38°C",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 50.dp)
                    )
                }
            }

            // Charging speed — free slider, up to fast-charging territory
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
                        var sliderPos by remember(vm.chargeSpeedMa) { mutableFloatStateOf(vm.chargeSpeedMa.toFloat()) }

                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("Current Limit", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                            Text(
                                "${sliderPos.toInt()} mA" + if (sliderPos >= 3000f) " · Fast Charging" else "",
                                color = CyanGlow, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Slider(
                            value = sliderPos,
                            onValueChange = { sliderPos = it },
                            onValueChangeFinished = { vm.setChargeSpeed(sliderPos.toInt()) },
                            valueRange = UserPreferencesRepository.MIN_CHARGE_SPEED_MA.toFloat()..UserPreferencesRepository.MAX_CHARGE_SPEED_MA.toFloat(),
                            colors = SliderDefaults.colors(thumbColor = CyanGlow, activeTrackColor = CyanGlow)
                        )
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("${UserPreferencesRepository.MIN_CHARGE_SPEED_MA} mA · Slow", color = TextTertiary, fontSize = 10.sp)
                            Text("${UserPreferencesRepository.MAX_CHARGE_SPEED_MA} mA · Max", color = TextTertiary, fontSize = 10.sp)
                        }

                        // Quick presets - convenience shortcuts, still fully overridable by the slider above.
                        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(6.dp)) {
                            listOf(1000, 1500, 2000, 3000, 5000).forEach { mA ->
                                FilterChip(
                                    selected = vm.chargeSpeedMa == mA,
                                    onClick = { sliderPos = mA.toFloat(); vm.setChargeSpeed(mA) },
                                    label = { Text("$mA", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CyanGlow.copy(alpha = 0.18f)),
                                    modifier = Modifier.weight(1f)
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
