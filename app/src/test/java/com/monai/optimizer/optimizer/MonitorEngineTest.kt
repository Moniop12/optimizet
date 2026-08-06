package com.monai.optimizer.optimizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test MonitorEngine — parser /proc/stat (fixture tanpa file sistem).
 *
 * HIGH-11: verifikasi bahwa kalkulasi CPU usage benar dan tidak crash
 * pada input yang rusak/kosong (regresi parser).
 */
class MonitorEngineTest {

    /** Fixture /proc/stat normal. */
    private fun statLine(user: Long, nice: Long, system: Long, idle: Long, iowait: Long, irq: Long, softirq: Long) =
        "cpu  $user $nice $system $idle $iowait $irq $softirq 0 0 0"

    @Test
    fun `empty input returns zero`() {
        MonitorEngine.resetCpuDelta()
        assertEquals(0, MonitorEngine.getCpuUsagePercent(""))
        assertEquals(0, MonitorEngine.getCpuUsagePercent(null))
    }

    @Test
    fun `malformed line returns zero`() {
        MonitorEngine.resetCpuDelta()
        assertEquals(0, MonitorEngine.getCpuUsagePercent("cpu  1 2 3"))
    }

    @Test
    fun `no delta on first call returns zero`() {
        MonitorEngine.resetCpuDelta()
        // panggilan pertama: prev == 0 → diffTotal == 0 → 0
        assertEquals(0, MonitorEngine.getCpuUsagePercent(statLine(100, 0, 50, 200, 10, 5, 5)))
    }

    @Test
    fun `busy cpu computes 100 percent`() {
        MonitorEngine.resetCpuDelta()
        // sample 1 — inisialisasi
        MonitorEngine.getCpuUsagePercent(statLine(100, 0, 50, 200, 10, 5, 5))
        // sample 2 — idle TIDAK bertambah, user naik 100 → busy 100%
        val pct = MonitorEngine.getCpuUsagePercent(statLine(200, 0, 50, 200, 10, 5, 5))
        assertEquals(100, pct)
    }

    @Test
    fun `idle cpu computes zero percent`() {
        MonitorEngine.resetCpuDelta()
        MonitorEngine.getCpuUsagePercent(statLine(100, 0, 50, 200, 10, 5, 5))
        // idle bertambah 200, sisanya tetap → busy 0%
        val pct = MonitorEngine.getCpuUsagePercent(statLine(100, 0, 50, 400, 10, 5, 5))
        assertEquals(0, pct)
    }

    @Test
    fun `half busy computes 50 percent`() {
        MonitorEngine.resetCpuDelta()
        MonitorEngine.getCpuUsagePercent(statLine(100, 0, 50, 200, 10, 5, 5))
        // user +100 DAN idle +100 → total delta 200, busy 100 → 50%
        val pct = MonitorEngine.getCpuUsagePercent(statLine(200, 0, 50, 300, 10, 5, 5))
        assertEquals(50, pct)
    }

    @Test
    fun `result clamped to 0-100`() {
        MonitorEngine.resetCpuDelta()
        MonitorEngine.getCpuUsagePercent(statLine(100, 0, 50, 200, 10, 5, 5))
        // idle NEGATIF (counter reset kernel) → busy > 100% → di-clamp
        val pct = MonitorEngine.getCpuUsagePercent(statLine(300, 0, 50, 100, 10, 5, 5))
        assertTrue("pct=$pct harus ≤ 100", pct in 0..100)
    }
}
