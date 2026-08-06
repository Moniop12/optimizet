package com.monai.optimizer.optimizer

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * ChargingEngine — kontrol sysfs charging dengan verifikasi node JUJUR (CRITICAL-4).
 *
 * Perbaikan utama vs versi sebelumnya:
 *  1. Setiap operasi tulis memverifikasi JUMLAH node yang benar-benar ditulis
 *     (`OK:<node>` di output). Jika 0 node tertulis → CmdResult(success=false)
 *     dengan pesan jujur "Kernel tidak mendukung ..." — TIDAK ADA sukses palsu
 *     dari pola `; echo done` yang lama.
 *  2. `step_charging_enabled` dan `siop_level` DIKELUARKAN dari daftar node arus:
 *     keduanya bukan node uA (boolean / enum level), menulis nilai mA ke sana
 *     adalah kesalahan nilai + berpotensi memicu proteksi kernel.
 *  3. Semua operasi tulis diserialkan dengan [writeLock] — satu penulis sysfs
 *     pada satu waktu (anti race antara UI toggle & loop monitoring service).
 *  4. Command builder adalah pure function (bisa di-unit-test tanpa root).
 */
object ChargingEngine {

    /** Node arus pengisian dalam mikroampere (uA) — hanya node yang AMAN untuk nilai uA. */
    private val CURRENT_NODES = listOf(
        "/sys/class/power_supply/battery/constant_charge_current_max",
        "/sys/class/power_supply/battery/current_max",
        "/sys/class/power_supply/main/current_max",
        "/sys/class/power_supply/battery/charging_current",
    )

    /** Node enable/disable pengisian (nilai 0/1). */
    private val ENABLE_NODES = listOf(
        "/sys/class/power_supply/battery/charging_enabled",
        "/sys/class/power_supply/battery/battery_charging_enabled",
        "/sys/class/power_supply/battery/store_mode",
        "/sys/class/power_supply/battery/mmi_charging_enable",
    )

    /** Node suspend input daya (nilai 0/1). */
    private val SUSPEND_NODES = listOf(
        "/sys/class/power_supply/battery/input_suspend",
    )

    /** Node bypass charging spesifik vendor (nilai 0/1). */
    private val BYPASS_NODES = listOf(
        "/sys/class/oplus_chg/battery/mmi_charging_enable",       // Oppo/Realme
        "/sys/class/qcom-battery/batt_charging_enable",           // Qualcomm
        "/sys/class/power_supply/battery/bypass_charging_enable", // Asus ROG
        "/sys/class/power_supply/battery/direct_charging_enable", // Universal Direct Charge
    )

    // Konstanta thermal — dipakai juga oleh ChargingController & MonAiService
    const val THERMAL_THROTTLE_MA = 500
    const val THERMAL_HIGH_C = 42.0
    const val THERMAL_RECOVER_C = 38.0

    // Serialisasi semua operasi tulis sysfs charging
    private val writeLock = Mutex()

    // ============ Command builders (pure — unit-testable) ============

    /**
     * Bangun satu baris shell per node yang:
     *  - hanya menulis jika node ADA (`[ -f $node ]`)
     *  - menandai hasil `OK:<node>` bila berhasil / `MISS:<node>` bila gagal/tidak ada
     *  - TIDAK pernah memaksa exit code 0 (tidak ada `; echo done` tersembunyi)
     */
    internal fun buildWriteCmd(nodes: List<String>, value: String): String =
        nodes.joinToString("; ") { node ->
            "[ -f $node ] && chmod 666 $node 2>/dev/null && echo $value > $node && echo \"OK:$node\" || echo \"MISS:$node\""
        }

    /** Hitung berapa node yang benar-benar berhasil ditulis dari output [buildWriteCmd]. */
    internal fun countWritten(output: String): Int =
        output.lines().count { it.startsWith("OK:") }

    /** Command untuk setChargingEnabled — verifikasi node ENABLE + SUSPEND. */
    fun setChargingEnabledCmd(enable: Boolean): String {
        val valEnable = if (enable) "1" else "0"
        val valSuspend = if (enable) "0" else "1"
        return listOf(
            buildWriteCmd(ENABLE_NODES, valEnable),
            buildWriteCmd(SUSPEND_NODES, valSuspend),
        ).joinToString("; ")
    }

    /** Command untuk setChargeCurrentMaxMa — hanya node uA yang aman. */
    fun setChargeCurrentMaxCmd(mA: Int): String {
        val uA = mA.coerceIn(100, 10_000) * 1000
        return buildWriteCmd(CURRENT_NODES, uA.toString())
    }

    /** Command untuk setBypassCharging — node vendor + fallback ENABLE/SUSPEND. */
    fun setBypassChargingCmd(enable: Boolean): String {
        val valCharging = if (enable) "0" else "1" // matikan arus sel baterai
        val valSuspend = if (enable) "1" else "0"  // alihkan daya langsung ke motherboard
        return listOf(
            buildWriteCmd(BYPASS_NODES, if (enable) "1" else "0"),
            buildWriteCmd(ENABLE_NODES, valCharging),
            buildWriteCmd(SUSPEND_NODES, valSuspend),
        ).joinToString("; ")
    }

    // ============ Eksekusi (suspend — dipanggil dari coroutine) ============

    /** Set charging enabled/disabled dengan verifikasi node jujur. */
    suspend fun setChargingEnabled(enable: Boolean): CmdResult = writeLock.withLock {
        val res = RootEngine.su(setChargingEnabledCmd(enable))
        val written = countWritten(res.output)
        if (written == 0) {
            CmdResult(false, "Kernel tidak mendukung kontrol pengisian (0 node ditulis)", res.cmd)
        } else {
            CmdResult(true, "$written node ditulis", res.cmd)
        }
    }

    /** Set arus maksimum pengisian (mA) dengan verifikasi node jujur. */
    suspend fun setChargeCurrentMaxMa(mA: Int): CmdResult = writeLock.withLock {
        val res = RootEngine.su(setChargeCurrentMaxCmd(mA))
        val written = countWritten(res.output)
        if (written == 0) {
            CmdResult(false, "Kernel tidak mendukung pembatasan arus (0 node ditulis)", res.cmd)
        } else {
            CmdResult(true, "$written node ditulis", res.cmd)
        }
    }

    /** Set bypass charging dengan verifikasi node jujur. */
    suspend fun setBypassCharging(enable: Boolean): CmdResult = writeLock.withLock {
        val res = RootEngine.su(setBypassChargingCmd(enable))
        val written = countWritten(res.output)
        if (written == 0) {
            CmdResult(false, "Kernel tidak mendukung Bypass Charging (0 node ditulis)", res.cmd)
        } else {
            CmdResult(true, "$written node ditulis", res.cmd)
        }
    }
}
