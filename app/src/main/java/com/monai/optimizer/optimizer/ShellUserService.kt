package com.monai.optimizer.optimizer

import com.monai.optimizer.IShellUserService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Dijalankan oleh Shizuku sebagai proses TERPISAH dengan UID shell (adb)
 * atau UID root (kalau Shizuku jalan lewat root) — bukan UID app biasa.
 * Karena proses ini SENDIRI sudah punya privilese shell, ProcessBuilder
 * biasa di sini otomatis punya akses yang sama seperti `adb shell`,
 * tanpa perlu reflection ke API internal Shizuku yang gampang berubah.
 *
 * PENTING: constructor no-arg ini WAJIB ada — Shizuku menginstansiasi
 * kelas ini lewat reflection di proses barunya sendiri.
 *
 * HIGH-4: setiap exec diberi TIMEOUT (5 dtk). Proses yang hang (mis. perintah
 * menunggu sesuatu di shell) di-destroyForcibly — thread binder tidak pernah
 * terblokir selamanya.
 */
class ShellUserService : IShellUserService.Stub() {

    private val executor = Executors.newCachedThreadPool { r ->
        Thread(r, "shell-exec").apply { isDaemon = true }
    }

    companion object {
        private const val EXEC_TIMEOUT_SECONDS = 5L
        private const val SEP = '\u0001'
    }

    override fun exec(cmd: String): String {
        return try {
            val process = ProcessBuilder("sh", "-c", cmd)
                .redirectErrorStream(true)
                .start()

            val future = executor.submit<String> {
                val output = process.inputStream.bufferedReader().readText()
                val code = process.waitFor()
                "$code$SEP${output.trim()}"
            }

            try {
                // Blokir maksimal EXEC_TIMEOUT_SECONDS — setelah itu destroyForcibly
                future.get(EXEC_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            } catch (e: java.util.concurrent.TimeoutException) {
                process.destroyForcibly()
                "-1$SEP${"TIMEOUT: command exceeded ${EXEC_TIMEOUT_SECONDS}s — $cmd".take(200)}"
            } catch (e: java.util.concurrent.ExecutionException) {
                process.destroyForcibly()
                "-1$SEP${e.cause?.message ?: "exec error"}"
            }
        } catch (e: Throwable) {
            "-1$SEP${e.message ?: "exec failed"}"
        }
    }

    override fun destroy() {
        try {
            executor.shutdownNow()
            android.os.Process.killProcess(android.os.Process.myPid())
        } catch (_: Throwable) {}
    }
}
