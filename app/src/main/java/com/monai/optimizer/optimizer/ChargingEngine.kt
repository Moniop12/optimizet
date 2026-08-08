package com.monai.optimizer.optimizer

object ChargingEngine {

    // KELUARKAN siop_level dan step_charging_enabled agar tidak salah tulis nilai mA
    // Diperluas (referensi cakupan vendor node dari Project Raco/kobo.c) supaya lebih
    // banyak chipset/vendor (QC, MTK, OPPO/VOOC, Samsung) kebaca. TIDAK memasukkan node
    // yang mematikan proteksi keamanan (jeita/safety_timer/thermal mitigation) — itu
    // sengaja tidak diambil karena berisiko ke keamanan baterai (panas/degradasi/fire risk).
    private val CURRENT_NODES = listOf(
        "/sys/class/power_supply/battery/constant_charge_current_max",
        "/sys/class/power_supply/battery/current_max",
        "/sys/class/power_supply/main/current_max",
        "/sys/class/power_supply/battery/charging_current",
        "/sys/class/power_supply/usb/current_max",
        "/sys/class/power_supply/battery/input_current_limit",
        "/sys/class/power_supply/battery/input_current_max",
        "/sys/class/power_supply/battery/fast_charge_current_limit",
        "/sys/class/qcom-battery/restricted_current",
        "/sys/devices/mtk-battery/fc_current_limit"
    )

    private val ENABLE_NODES = listOf(
        "/sys/class/power_supply/battery/charging_enabled",
        "/sys/class/power_supply/battery/battery_charging_enabled",
        "/sys/class/power_supply/battery/store_mode",
        "/sys/class/power_supply/battery/mmi_charging_enable",
        "/sys/class/power_supply/battery/batt_slate_mode"
    )

    private val SUSPEND_NODES = listOf(
        "/sys/class/power_supply/battery/input_suspend"
    )

    // Node bypass charging diperluas — beberapa vendor OPPO/Realme/OnePlus (oplus_chg)
    // dan QC punya nama node yang beda-beda; makin banyak fallback, makin banyak device kebaca.
    private val BYPASS_NODES = listOf(
        "/sys/class/oplus_chg/battery/mmi_charging_enable",
        "/sys/class/qcom-battery/batt_charging_enable",
        "/sys/class/power_supply/battery/bypass_charging_enable",
        "/sys/class/power_supply/battery/direct_charging_enable",
        "/sys/class/power_supply/battery/charge_control_limit_max",
        "/sys/class/power_supply/vooc/fast_chg_ing"
    )

    // FIX (K4): chmod 666 dihapus. Proses yang menjalankan blok ini sudah UID root
    // (dieksekusi via `su`), jadi root selalu punya izin tulis ke node sysfs tanpa
    // perlu membuka izin 666 (yang membuka celah tulis untuk SEMUA app lain di device).

    fun setChargingEnabled(enable: Boolean): CmdResult {
        val valEnable = if (enable) "1" else "0"
        val valSuspend = if (enable) "0" else "1"

        val cmds = mutableListOf<String>()
        for (node in ENABLE_NODES) {
            cmds.add("[ -f $node ] && echo $valEnable > $node && echo OK")
        }
        for (node in SUSPEND_NODES) {
            cmds.add("[ -f $node ] && echo $valSuspend > $node && echo OK")
        }

        val fullCmd = cmds.joinToString("; ")
        val res = RootEngine.su(fullCmd)

        // Verifikasi apakah ada node yang berhasil diubah
        val writtenCount = res.output.lines().count { it.trim() == "OK" }
        return if (writtenCount > 0) {
            CmdResult(true, res.output, fullCmd)
        } else {
            CmdResult(false, "Kernel tidak mendukung kontrol sakelar charging", fullCmd)
        }
    }

    // FIX (K1): Dulu kode MENULIS DULU ke semua node fallback (charging_enabled=0,
    // input_suspend=1), BARU mengecek apakah node bypass ada. Di perangkat tanpa node
    // bypass, tulisan itu tetap mematikan sakelar charging walau UI akhirnya bilang
    // "Kernel does not support Bypass Charging" — charging bisa mati permanen.
    // Sekarang: cek dulu node bypass ADA, baru tulis apapun.
    fun setBypassCharging(enable: Boolean): CmdResult {
        val checkNode = RootEngine.su(
            "ls " + BYPASS_NODES.joinToString(" ") + " 2>/dev/null | head -n 1"
        )
        if (checkNode.output.isBlank()) {
            return CmdResult(false, "Kernel tidak mendukung Bypass Charging (Node tidak ditemukan)", "bypass_check")
        }

        val valCharging = if (enable) "0" else "1"
        val valSuspend = if (enable) "1" else "0"

        val cmds = mutableListOf<String>()
        for (node in BYPASS_NODES) {
            cmds.add("[ -f $node ] && echo ${if (enable) "1" else "0"} > $node && echo OK")
        }
        for (node in ENABLE_NODES) {
            cmds.add("[ -f $node ] && echo $valCharging > $node && echo OK")
        }
        for (node in SUSPEND_NODES) {
            cmds.add("[ -f $node ] && echo $valSuspend > $node && echo OK")
        }

        val fullCmd = cmds.joinToString("; ")
        val res = RootEngine.su(fullCmd)
        val writtenCount = res.output.lines().count { it.trim() == "OK" }
        return if (writtenCount > 0) {
            CmdResult(true, res.output, fullCmd)
        } else {
            CmdResult(false, "Gagal menulis node bypass charging", fullCmd)
        }
    }

    // Diagnostik cakupan device: cek node mana aja yang benar-benar ada di HP ini.
    // Berguna buat debugging kompatibilitas / ditampilkan di LogScreen kalau charging
    // tweak gagal total (misal "Kernel tidak mendukung...") biar user tahu node apa yang hilang.
    fun detectSupportedNodes(): CmdResult {
        val allNodes = (CURRENT_NODES + ENABLE_NODES + SUSPEND_NODES + BYPASS_NODES).distinct()
        val checkCmd = allNodes.joinToString(" ; ") { "[ -f $it ] && echo FOUND:$it" }
        val res = RootEngine.su(checkCmd)
        return CmdResult(res.success, res.output.ifBlank { "Tidak ada node charging yang cocok terdeteksi di device ini" }, checkCmd)
    }

    fun setChargeCurrentMaxMa(mA: Int): CmdResult {
        val uA = mA * 1000
        val cmds = mutableListOf<String>()
        for (node in CURRENT_NODES) {
            cmds.add("[ -f $node ] && echo $uA > $node && echo OK")
        }

        val fullCmd = cmds.joinToString("; ")
        val res = RootEngine.su(fullCmd)

        val writtenCount = res.output.lines().count { it.trim() == "OK" }
        return if (writtenCount > 0) {
            CmdResult(true, res.output, fullCmd)
        } else {
            CmdResult(false, "Kernel tidak mendukung pembatasan arus charging", fullCmd)
        }
    }
}