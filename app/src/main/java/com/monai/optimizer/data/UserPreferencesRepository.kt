package com.monai.optimizer.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.monai.optimizer.optimizer.OptProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "monai_user_prefs")

data class UserPreferencesState(
    val activeProfile: OptProfile?,
    val isChargeLimitEnabled: Boolean,
    val chargeLimitPct: Int,
    val chargeSpeedMa: Int,
    val isLiveServiceRunning: Boolean,
    val isThermalProtectEnabled: Boolean,
    val isBypassChargingEnabled: Boolean,
    val aiOptimizerEnabled: Boolean,
    val showNotifRam: Boolean,
    val showNotifCpu: Boolean,
    val showNotifPower: Boolean,
    val showNotifProfiles: Boolean,
    val resolutionPreset: String,
)

class UserPreferencesRepository(context: Context) {

    private val store = context.applicationContext.dataStore

    private object Keys {
        val ACTIVE_PROFILE = stringPreferencesKey("active_profile")
        val CHARGE_LIMIT_ENABLED = booleanPreferencesKey("is_charge_limit_enabled")
        val CHARGE_LIMIT_PCT = intPreferencesKey("charge_limit_pct")
        val CHARGE_SPEED_MA = intPreferencesKey("charge_speed_ma")
        val LIVE_SERVICE_RUNNING = booleanPreferencesKey("is_live_service_running")
        val THERMAL_PROTECT_ENABLED = booleanPreferencesKey("is_thermal_protect_enabled")
        val BYPASS_CHARGING_ENABLED = booleanPreferencesKey("is_bypass_charging_enabled")
        val AI_OPTIMIZER_ENABLED = booleanPreferencesKey("ai_optimizer_enabled")
        val SHOW_NOTIF_RAM = booleanPreferencesKey("show_notif_ram")
        val SHOW_NOTIF_CPU = booleanPreferencesKey("show_notif_cpu")
        val SHOW_NOTIF_POWER = booleanPreferencesKey("show_notif_power")
        val SHOW_NOTIF_PROFILES = booleanPreferencesKey("show_notif_profiles")
        val RESOLUTION_PRESET = stringPreferencesKey("resolution_preset")
    }

    companion object {
        const val DEFAULT_CHARGE_LIMIT_PCT = 80
        const val DEFAULT_CHARGE_SPEED_MA = 1500
        const val MAX_CHARGE_SPEED_MA = 5000
        const val MIN_CHARGE_SPEED_MA = 500
    }

    val preferencesFlow: Flow<UserPreferencesState> = store.data.map { prefs ->
        UserPreferencesState(
            activeProfile = prefs[Keys.ACTIVE_PROFILE]?.let { name ->
                runCatching { OptProfile.valueOf(name) }.getOrNull()
            },
            isChargeLimitEnabled = prefs[Keys.CHARGE_LIMIT_ENABLED] ?: false,
            chargeLimitPct = prefs[Keys.CHARGE_LIMIT_PCT] ?: DEFAULT_CHARGE_LIMIT_PCT,
            chargeSpeedMa = prefs[Keys.CHARGE_SPEED_MA] ?: DEFAULT_CHARGE_SPEED_MA,
            isLiveServiceRunning = prefs[Keys.LIVE_SERVICE_RUNNING] ?: false,
            isThermalProtectEnabled = prefs[Keys.THERMAL_PROTECT_ENABLED] ?: false,
            isBypassChargingEnabled = prefs[Keys.BYPASS_CHARGING_ENABLED] ?: false,
            aiOptimizerEnabled = prefs[Keys.AI_OPTIMIZER_ENABLED] ?: false,
            showNotifRam = prefs[Keys.SHOW_NOTIF_RAM] ?: true,
            showNotifCpu = prefs[Keys.SHOW_NOTIF_CPU] ?: true,
            showNotifPower = prefs[Keys.SHOW_NOTIF_POWER] ?: true,
            showNotifProfiles = prefs[Keys.SHOW_NOTIF_PROFILES] ?: true,
            resolutionPreset = prefs[Keys.RESOLUTION_PRESET] ?: "NATIVE",
        )
    }

    suspend fun setActiveProfile(profile: OptProfile?) {
        store.edit { prefs ->
            if (profile == null) prefs.remove(Keys.ACTIVE_PROFILE)
            else prefs[Keys.ACTIVE_PROFILE] = profile.name
        }
    }

    suspend fun setChargeLimit(enabled: Boolean, pct: Int) {
        store.edit { prefs ->
            prefs[Keys.CHARGE_LIMIT_ENABLED] = enabled
            prefs[Keys.CHARGE_LIMIT_PCT] = pct.coerceIn(50, 100)
        }
    }

    suspend fun setChargeSpeedMa(mA: Int) {
        store.edit { prefs ->
            prefs[Keys.CHARGE_SPEED_MA] = mA.coerceIn(MIN_CHARGE_SPEED_MA, MAX_CHARGE_SPEED_MA)
        }
    }

    suspend fun setLiveServiceRunning(running: Boolean) {
        store.edit { prefs -> prefs[Keys.LIVE_SERVICE_RUNNING] = running }
    }

    suspend fun setThermalProtectEnabled(enabled: Boolean) {
        store.edit { prefs -> prefs[Keys.THERMAL_PROTECT_ENABLED] = enabled }
    }

    suspend fun setBypassChargingEnabled(enabled: Boolean) {
        store.edit { prefs -> prefs[Keys.BYPASS_CHARGING_ENABLED] = enabled }
    }

    suspend fun setAiOptimizerEnabled(enabled: Boolean) {
        store.edit { prefs -> prefs[Keys.AI_OPTIMIZER_ENABLED] = enabled }
    }

    suspend fun setNotifRamVisible(visible: Boolean) {
        store.edit { prefs -> prefs[Keys.SHOW_NOTIF_RAM] = visible }
    }

    suspend fun setNotifCpuVisible(visible: Boolean) {
        store.edit { prefs -> prefs[Keys.SHOW_NOTIF_CPU] = visible }
    }

    suspend fun setNotifPowerVisible(visible: Boolean) {
        store.edit { prefs -> prefs[Keys.SHOW_NOTIF_POWER] = visible }
    }

    suspend fun setNotifProfilesVisible(visible: Boolean) {
        store.edit { prefs -> prefs[Keys.SHOW_NOTIF_PROFILES] = visible }
    }

    suspend fun setResolutionPreset(preset: String) {
        store.edit { prefs -> prefs[Keys.RESOLUTION_PRESET] = preset }
    }
}