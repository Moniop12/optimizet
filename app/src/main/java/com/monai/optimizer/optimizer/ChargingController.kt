package com.monai.optimizer.optimizer

import com.monai.optimizer.data.UserPreferencesRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * ChargingController — SATU penulis state charging (NEW-HIGH-B / HIGH-6 / HIGH-12).
 *
 * Sebelumnya ada DUA penulis yang menulis node sysfs yang sama tanpa koordinasi:
 *  - MainViewModel.setChargeLimit/setThermalProtect/setChargeSpeed (saat toggle)
 *  - MonAiService.startMonitoringLoop (setiap tick)
 * Akibatnya nilai bisa saling timpa (mis. user set 500 mA sementara loop thermal
 * menulis 500 mA lalu me-restore chargeSpeedMa yang sudah diubah user).
 *
 * Sekarang SEMUA operasi charging (toggle dari UI, loop monitoring service,
 * fail-safe restore) melewati controller ini. Controller adalah SINGLETON —
 * MainViewModel & MonAiService berbagi instance yang SAMA, sehingga tidak ada
 * lagi dua penulis / dua sumber state.
 */
class ChargingController private constructor(private val prefs: UserPreferencesRepository) {

    // ===== State (dibaca dari DataStore + ditulis di sini) =====

    @Volatile var isChargeLimitEnabled: Boolean = false
        private set
    @Volatile var chargeLimitPct: Int = UserPreferencesRepository.DEFAULT_CHARGE_LIMIT_PCT
        private set
    @Volatile var chargeSpeedMa: Int = UserPreferencesRepository.DEFAULT_CHARGE_SPEED_MA
        private set
    @Volatile var isThermalProtectEnabled: Boolean = false
        private set
    @Volatile var isBypassChargingEnabled: Boolean = false
        private set
    @Volatile var isChargePausedByLimit: Boolean = false
        private set
    @Volatile var isThermalThrottled: Boolean = false
        private set

    private val opLock = Mutex()

    data class ChargingSnapshot(
        val isChargeLimitEnabled: Boolean,
        val chargeLimitPct: Int,
        val chargeSpeedMa: Int,
        val isThermalProtectEnabled: Boolean,
        val isBypassChargingEnabled: Boolean,
        val isChargePausedByLimit: Boolean,
        val isThermalThrottled: Boolean,
    )

    /** Snapshot atomik untuk dibaca UI/notifikasi — hindari baca state parsial. */
    fun snapshot(): ChargingSnapshot = ChargingSnapshot(
        isChargeLimitEnabled = isChargeLimitEnabled,
        chargeLimitPct = chargeLimitPct,
        chargeSpeedMa = chargeSpeedMa,
        isThermalProtectEnabled = isThermalProtectEnabled,
        isBypassChargingEnabled = isBypassChargingEnabled,
        isChargePausedByLimit = isChargePausedByLimit,
        isThermalThrottled = isThermalThrottled,
    )

    /** Sinkronkan dari DataStore (dipanggil saat prefs flow memancarkan state). */
    fun syncFromPrefs(
        chargeLimitEnabled: Boolean,
        chargeLimitPct: Int,
        chargeSpeedMa: Int,
        thermalProtect: Boolean,
        bypassCharging: Boolean,
    ) {
        this.isChargeLimitEnabled = chargeLimitEnabled
        this.chargeLimitPct = chargeLimitPct
        this.chargeSpeedMa = chargeSpeedMa
        this.isThermalProtectEnabled = thermalProtect
        this.isBypassChargingEnabled = bypassCharging
    }

    /** Sinkronkan flag runtime (dipakai delegasi kompatibilitas dari MonAiService companion). */
    fun syncRuntimeFlags(pausedByLimit: Boolean? = null, thermalThrottled: Boolean? = null) {
        if (pausedByLimit != null) this.isChargePausedByLimit = pausedByLimit
        if (thermalThrottled != null) this.isThermalThrottled = thermalThrottled
    }

    // ===== Aksi (semua melalui Mutex — satu penulis sysfs) =====

    /** Toggle/ubah charge limit. Menulis sysfs HANYA jika root tersedia. */
    suspend fun setChargeLimit(enabled: Boolean, pct: Int): CmdResult = opLock.withLock {
        isChargeLimitEnabled = enabled
        chargeLimitPct = pct.coerceIn(50, 100)
        prefs.setChargeLimit(enabled, chargeLimitPct)

        if (!enabled) {
            // Matikan limit → pastikan charging aktif kembali
            isChargePausedByLimit = false
            return@withLock if (RootEngine.hasRoot()) ChargingEngine.setChargingEnabled(true)
            else CmdResult(true, "Charge limit disabled (tanpa root)", "disable-limit")
        }
        CmdResult(true, "Charge limit ${chargeLimitPct}%", "enable-limit")
    }

    /** Toggle thermal protection. */
    suspend fun setThermalProtect(enabled: Boolean): CmdResult = opLock.withLock {
        isThermalProtectEnabled = enabled
        prefs.setThermalProtectEnabled(enabled)

        if (!enabled && isThermalThrottled) {
            isThermalThrottled = false
            return@withLock if (RootEngine.hasRoot()) ChargingEngine.setChargeCurrentMaxMa(chargeSpeedMa)
            else CmdResult(true, "Thermal protect disabled (tanpa root)", "disable-thermal")
        }
        CmdResult(true, if (enabled) "Thermal protect ${ChargingEngine.THERMAL_HIGH_C}°C" else "Thermal protect off", "set-thermal")
    }

    /** Ubah kecepatan arus charging (mA). */
    suspend fun setChargeSpeed(mA: Int): CmdResult = opLock.withLock {
        val clamped = mA.coerceIn(
            UserPreferencesRepository.MIN_CHARGE_SPEED_MA,
            UserPreferencesRepository.MAX_CHARGE_SPEED_MA,
        )
        chargeSpeedMa = clamped
        prefs.setChargeSpeedMa(clamped)

        if (RootEngine.hasRoot()) {
            // Jika sedang di-throttle thermal, jangan timpa nilai throttle —
            // nilai user tetap disimpan dan dipakai saat thermal pulih.
            if (!isThermalThrottled) {
                return@withLock ChargingEngine.setChargeCurrentMaxMa(clamped)
            }
            CmdResult(true, "Kecepatan disimpan ($clamped mA) — throttle thermal aktif", "set-speed")
        } else {
            CmdResult(true, "Charging speed $clamped mA disimpan (tanpa root)", "set-speed")
        }
    }

    /** Set bypass charging. */
    suspend fun setBypassCharging(enabled: Boolean): CmdResult = opLock.withLock {
        isBypassChargingEnabled = enabled
        prefs.setBypassChargingEnabled(enabled)

        if (RootEngine.hasRoot()) {
            return@withLock ChargingEngine.setBypassCharging(enabled)
        }
        CmdResult(true, "Bypass charging ${if (enabled) "enabled" else "disabled"} (tanpa root)", "set-bypass")
    }

    /**
     * Logika loop monitoring (dipanggil service tiap tick):
     *  - Charge limit: pause charging saat pct >= limit, resume saat pct < limit - hysteresis
     *  - Thermal: throttle ke 500 mA saat > 42°C, restore saat < 38°C
     */
    suspend fun applyMonitoringLogic(percentage: Int, tempC: Double, isCharging: Boolean) {
        if (!RootEngine.hasRoot()) return

        opLock.withLock {
            // Charge Limit dengan hysteresis
            if (isChargeLimitEnabled) {
                if (isCharging && percentage >= chargeLimitPct && !isChargePausedByLimit) {
                    ChargingEngine.setChargingEnabled(false)
                    isChargePausedByLimit = true
                } else if (percentage < (chargeLimitPct - HYSTERESIS_PCT) && isChargePausedByLimit) {
                    ChargingEngine.setChargingEnabled(true)
                    isChargePausedByLimit = false
                }
            }

            // Thermal protection
            if (isThermalProtectEnabled && isCharging) {
                if (tempC > ChargingEngine.THERMAL_HIGH_C && !isThermalThrottled) {
                    ChargingEngine.setChargeCurrentMaxMa(ChargingEngine.THERMAL_THROTTLE_MA)
                    isThermalThrottled = true
                } else if (tempC < ChargingEngine.THERMAL_RECOVER_C && isThermalThrottled) {
                    ChargingEngine.setChargeCurrentMaxMa(chargeSpeedMa)
                    isThermalThrottled = false
                }
            }
        }
    }

    /** Fail-safe: pastikan charging aktif kembali (dipanggil saat service berhenti/crash). */
    suspend fun restoreChargingFailSafe() {
        if (!RootEngine.hasRoot()) return
        opLock.withLock {
            if (isChargePausedByLimit || isThermalThrottled) {
                ChargingEngine.setChargingEnabled(true)
                ChargingEngine.setChargeCurrentMaxMa(chargeSpeedMa)
            }
            isChargePausedByLimit = false
            isThermalThrottled = false
        }
    }

    companion object {
        const val HYSTERESIS_PCT = 3

        @Volatile
        private var instance: ChargingController? = null

        /** Ambil instance singleton — buat jika belum ada (dengan repository). */
        fun getInstance(prefs: UserPreferencesRepository): ChargingController {
            val existing = instance
            if (existing != null) return existing
            synchronized(this) {
                val current = instance
                if (current != null) return current
                val created = ChargingController(prefs)
                instance = created
                return created
            }
        }

        /** Reset singleton (untuk unit test). */
        fun resetForTest() {
            synchronized(this) { instance = null }
        }
    }
}