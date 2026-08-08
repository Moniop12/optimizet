package com.monai.optimizer.optimizer

import com.monai.optimizer.IShellUserService

class ShellUserService : IShellUserService.Stub() {

    override fun exec(cmd: String): String {
        return try {
            val process = ProcessBuilder("sh", "-c", cmd).redirectErrorStream(true).start()
            var output = ""
            val reader = Thread { output = process.inputStream.bufferedReader().readText() }
            reader.start()

            // Timeout 120 detik agar proses kompilasi ART tidak terputus di tengah jalan
            if (process.waitFor(120, java.util.concurrent.TimeUnit.SECONDS)) {
                reader.join()
                "${process.exitValue()}\u0001${output.trim()}"
            } else {
                process.destroyForcibly()
                reader.interrupt()
                "-1\u0001Command execution timed out (120s)"
            }
        } catch (e: Throwable) {
            "-1\u0001${e.message ?: "exec failed"}"
        }
    }

    override fun destroy() {
        try { android.os.Process.killProcess(android.os.Process.myPid()) } catch (_: Throwable) {}
    }
}