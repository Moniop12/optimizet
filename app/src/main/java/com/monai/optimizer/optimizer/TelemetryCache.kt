package com.monai.optimizer.optimizer

/**
 * TelemetryCache — SATU sumber telemetri (HIGH-16).
 *
 * Sebelumnya service (loop 3 dtk) dan UI (ticker 2 dtk) masing-masing menjalankan
 * shell sendiri → 2 loop + 2× biaya shell. Sekarang:
 *  - Service (satu-satunya penulis) mempublikasikan hasil `getSystemStatsBatch()`
 *    ke cache ini setiap tick.
 *  - UI ticker MEMBACA cache ini — TANPA shell sama sekali saat service hidup.
 *  - Saat service mati, UI ticker fallback ke pembacaan langsung (satu shell per tick).
 */
object TelemetryCache {

    /** Snapshot batch statistik terakhir yang dipublikasikan service. */
    @Volatile private var lastBatch: String? = null

    /** Waktu (epoch ms) publikasi terakhir — untuk deteksi staleness. */
    @Volatile private var lastPublishedAt: Long = 0L

    /** Service memanggil ini setiap tick monitoring (3 dtk). */
    fun publishBatch(batch: String) {
        lastBatch = batch
        lastPublishedAt = System.currentTimeMillis()
    }

    /** UI membacanya; null jika belum ada data atau sudah basi (> 15 dtk). */
    fun readBatch(): String? {
        val batch = lastBatch ?: return null
        if (System.currentTimeMillis() - lastPublishedAt > STALE_MS) return null
        return batch
    }

    /** Service memanggil ini saat berhenti — UI harus fallback ke shell sendiri. */
    fun invalidate() {
        lastBatch = null
        lastPublishedAt = 0L
    }

    private const val STALE_MS = 15_000L
}
