package com.monai.optimizer.optimizer

import com.monai.optimizer.IShellUserService

/**
 * Dijalankan oleh Shizuku sebagai proses TERPISAH dengan UID shell (adb)
 * atau UID root (kalau Shizuku jalan lewat root) — bukan UID app biasa.
 * Karena proses ini SENDIRI sudah punya privilese shell, ProcessBuilder
 * biasa di sini otomatis punya akses yang sama seperti `adb shell`,
 * tanpa perlu reflection ke API internal Shizuku yang gampang berubah.
 *
 * PENTING: constructor no-arg ini WAJIB ada — Shizuku menginstansiasi
 * kelas ini lewat reflection di proses barunya sendiri.
 */
class ShellUserService : IShellUserService.Stub() {

    override fun exec(cmd: String): String {
        return try {
            val process = ProcessBuilder("sh", "-c", cmd)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val code = process.waitFor()
            "$code\u0001${output.trim()}"
        } catch (e: Throwable) {
            "-1\u0001${e.message ?: "exec failed"}"
        }
    }

    override fun destroy() {
        try { android.os.Process.killProcess(android.os.Process.myPid()) } catch (_: Throwable) {}
    }
}
