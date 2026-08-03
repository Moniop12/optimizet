package com.monai.optimizer.optimizer

import java.io.File

object ChargingEngine {

    private fun getEnableNodes(): List<String> {
        val candidates = listOf(
            "/sys/class/power_supply/battery/charging_enabled",
            "/sys/class/power_supply/battery/battery_charging_enabled"
        )
        return candidates.filter { File(it).exists() }
    }

    private fun getSuspendNodes(): List<String> {
        val candidates = listOf("/sys/class/power_supply/battery/input_suspend")
        return candidates.filter { File(it).exists() }
    }

    private fun getCurrentNodes(): List<String> {
        val candidates = listOf(
            "/sys/class/power_supply/battery/constant_charge_current_max",
            "/sys/class/power_supply/battery/current_max",
            "/sys/class/power_supply/main/current_max"
        )
        return candidates.filter { File(it).exists() }
    }

    fun setChargingEnabled(enable: Boolean): CmdResult {
        val valEnable = if (enable) "1" else "0"
        val valSuspend = if (enable) "0" else "1"
        val cmds = mutableListOf<String>()
        for (node in getEnableNodes()) {
            cmds.add("chmod 666 $node 2>/dev/null && echo $valEnable > $node")
        }
        for (node in getSuspendNodes()) {
            cmds.add("chmod 666 $node 2>/dev/null && echo $valSuspend > $node")
        }
        return if (cmds.isEmpty()) CmdResult(false, "No charging node found", "setCharging")
        else RootEngine.su(cmds.joinToString("; "))
    }

    fun setChargeCurrentMaxMa(mA: Int): CmdResult {
        val uA = mA * 1000
        val cmds = mutableListOf<String>()
        for (node in getCurrentNodes()) {
            cmds.add("chmod 666 $node 2>/dev/null && echo $uA > $node")
        }
        return if (cmds.isEmpty()) CmdResult(false, "No current node found", "setCurrent")
        else RootEngine.su(cmds.joinToString("; "))
    }
}