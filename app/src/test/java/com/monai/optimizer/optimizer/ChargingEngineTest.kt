package com.monai.optimizer.optimizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test ChargingEngine — command builder + verifikasi node (CRITICAL-4).
 *
 * Memverifikasi bahwa:
 *  1. Command builder menghasilkan perintah yang JUJUR (tidak ada `; echo done`
 *     yang memaksa exit code 0).
 *  2. Node `step_charging_enabled` / `siop_level` TIDAK lagi dipakai untuk nilai uA.
 *  3. countWritten() menghitung node yang benar-benar ditulis.
 */
class ChargingEngineTest {

    @Test
    fun `charge current command uses only uA-safe nodes`() {
        val cmd = ChargingEngine.setChargeCurrentMaxCmd(1500)
        // Tidak boleh ada step_charging_enabled / siop_level di daftar node arus
        assertFalse("step_charging_enabled harus dihapus", cmd.contains("step_charging_enabled"))
        assertFalse("siop_level harus dihapus", cmd.contains("siop_level"))
        // Harus menulis nilai uA yang benar
        assertTrue("harus berisi 1500000 uA", cmd.contains("echo 1500000"))
    }

    @Test
    fun `charge current command is honest - no forced success`() {
        val cmd = ChargingEngine.setChargeCurrentMaxCmd(500)
        // dulu ada `; echo done` yang membuat exit code selalu 0 → sukses palsu
        assertFalse("tidak boleh ada echo done palsu", cmd.contains("; echo done"))
        // setiap node punya verifikasi OK/MISS
        assertTrue("harus ada penanda OK:", cmd.contains("OK:"))
        assertTrue("harus ada penanda MISS:", cmd.contains("MISS:"))
    }

    @Test
    fun `charge enable command toggles correct values`() {
        val enableCmd = ChargingEngine.setChargingEnabledCmd(true)
        val disableCmd = ChargingEngine.setChargingEnabledCmd(false)
        // enable: node ENABLE = 1, SUSPEND = 0
        assertTrue(enableCmd.contains("echo 1 > /sys/class/power_supply/battery/charging_enabled"))
        // disable: node ENABLE = 0, SUSPEND = 1
        assertTrue(disableCmd.contains("echo 0 > /sys/class/power_supply/battery/charging_enabled"))
        assertTrue(disableCmd.contains("echo 1 > /sys/class/power_supply/battery/input_suspend"))
    }

    @Test
    fun `countWritten counts only OK nodes`() {
        assertEquals(0, ChargingEngine.countWritten("MISS:/a\nMISS:/b"))
        assertEquals(2, ChargingEngine.countWritten("OK:/a\nMISS:/b\nOK:/c"))
        assertEquals(1, ChargingEngine.countWritten("OK:/a\nsomething else"))
    }

    @Test
    fun `bypass command keeps vendor nodes and fallback`() {
        val cmd = ChargingEngine.setBypassChargingCmd(true)
        assertTrue(cmd.contains("oplus_chg/battery/mmi_charging_enable"))
        assertTrue(cmd.contains("bypass_charging_enable"))
        // fallback ENABLE_NODES juga ikut
        assertTrue(cmd.contains("charging_enabled"))
    }
}
