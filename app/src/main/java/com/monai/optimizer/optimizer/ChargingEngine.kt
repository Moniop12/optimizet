package com.monai.optimizer.optimizer

object ChargingEngine {

    private val CURRENT_NODES = listOf(
        "/sys/class/power_supply/battery/constant_charge_current_max",
        "/sys/class/power_supply/battery/current_max",
        "/sys/class/power_supply/main/current_max",
        "/sys/class/power_supply/battery/charging_current",
        "/sys/class/power_supply/battery/step_charging_enabled",
        "/sys/class/power_supply/battery/siop_level"
    )

    private val ENABLE_NODES = listOf(
        "/sys/class/power_supply/battery/charging_enabled",
        "/sys/class/power_supply/battery/battery_charging_enabled",
        "/sys/class/power_supply/battery/store_mode",
        "/sys/class/power_supply/battery/mmi_charging_enable"
    )

    private val SUSPEND_NODES = listOf(
        "/sys/class/power_supply/battery/input_suspend"
    )

    fun setChargingEnabled(enable: Boolean): CmdResult {
        val valEnable = if (enable) "1" else "0"
        val valSuspend = if (enable) "0" else "1"

        val cmds = mutableListOf<String>()
        for (node in ENABLE_NODES) {
            cmds.add("[ -f $node ] && chmod 666 $node 2>/dev/null && echo $valEnable > $node")
        }
        for (node in SUSPEND_NODES) {
            cmds.add("[ -f $node ] && chmod 666 $node 2>/dev/null && echo $valSuspend > $node")
        }

        val fullCmd = cmds.joinToString("; ") + "; echo done"
        return RootEngine.su(fullCmd)
    }

    // ── FITUR BARU: Bypass Charging (Direct Power Supply) ──────────────
    fun setBypassCharging(enable: Boolean): CmdResult {
        val valCharging = if (enable) "0" else "1"  // Matikan arus sel baterai
        val valSuspend = if (enable) "1" else "0"   // Alihkan daya langsung ke motherboard

        val cmds = mutableListOf<String>()
        for (node in ENABLE_NODES) {
            cmds.add("[ -f $node ] && chmod 666 $node 2>/dev/null && echo $valCharging > $node")
        }
        for (node in SUSPEND_NODES) {
            cmds.add("[ -f $node ] && chmod 666 $node 2>/dev/null && echo $valSuspend > $node")
        }

        val fullCmd = cmds.joinToString("; ") + "; echo done"
        return RootEngine.su(fullCmd)
    }

    fun setChargeCurrentMaxMa(mA: Int): CmdResult {
        val uA = mA * 1000
        val cmds = mutableListOf<String>()
        for (node in CURRENT_NODES) {
            cmds.add("[ -f $node ] && chmod 666 $node 2>/dev/null && echo $uA > $node")
        }

        val fullCmd = cmds.joinToString("; ") + "; echo done"
        return RootEngine.su(fullCmd)
    }
}