package com.monai.optimizer.optimizer

import com.monai.optimizer.IShellUserService

class ShellUserService : IShellUserService.Stub() {

    override fun exec(cmd: String): String {
        return try {
            val process = ProcessBuilder("sh", "-c", cmd)
                .redirectErrorStream(true)
                .start()

            var output = ""
            var code = -1

            val worker = Thread {
                try {
                    output = process.inputStream.bufferedReader().readText()
                    code = process.waitFor()
                } catch (_: Throwable) {}
            }
            worker.isDaemon = true
            worker.start()

            // Timeout 5 detik agar Shizuku tidak hang selamanya
            worker.join(5000)
            if (process.isAlive) {
                process.destroyForcibly()
                return "-1\u0001Command execution timed out (5s)"
            }

            "$code\u0001${output.trim()}"
        } catch (e: Throwable) {
            "-1\u0001${e.message ?: "exec failed"}"
        }
    }

    override fun destroy() {
        try { android.os.Process.killProcess(android.os.Process.myPid()) } catch (_: Throwable) {}
    }
}