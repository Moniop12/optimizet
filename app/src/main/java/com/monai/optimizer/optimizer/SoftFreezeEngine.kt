package com.monai.optimizer.optimizer

/**
 * Soft Freeze — freeze instan berbasis SIGSTOP (referensi: RSWAP di Project Raco).
 *
 * Beda dengan hard-freeze yang sudah ada (RootEngine/ShizukuEngine.freezeApp → `pm disable-user`):
 *  - Hard-freeze  : app dinonaktifkan di package manager, hilang dari launcher, permanen sampai di-enable lagi.
 *  - Soft-freeze  : proses cuma di-pause (SIGSTOP) — masih "ada" di RAM, resume-nya instan (SIGCONT),
 *                   cocok buat "bekukan sementara pas lagi butuh RAM/CPU" tanpa efek samping ke state app.
 *
 * Auto-release: kalau app soft-frozen nggak di-resume manual dalam durasi tertentu, background watcher
 * bakal kill proses itu (bukan biarin nyangkut selamanya) — pola yang sama dipakai RSWAP Raco (mereka pakai
 * 48 jam via `sleep 172800`, di sini dibikin configurable dan default lebih singkat karena dipakai on-demand,
 * bukan swap-eviction jangka panjang).
 */
object SoftFreezeEngine {

    private const val DEFAULT_AUTOKILL_SECONDS = 21600 // 6 jam — cukup panjang buat "lupa buka lagi", tapi nggak nyangkut selamanya
    private const val TRACK_DIR = "/data/local/tmp/monai_softfreeze"

    /**
     * Pause semua proses milik [pkg] via SIGSTOP. oom_score_adj diturunkan supaya
     * Low Memory Killer sistem nggak keburu bunuh proses yang lagi dipause ini.
     * Sekaligus pasang watcher nohup yang bakal SIGKILL proses itu otomatis kalau
     * lupa di-resume dalam [autoKillSeconds] detik, biar nggak jadi "zombie" permanen.
     */
    fun softFreeze(pkg: String, autoKillSeconds: Int = DEFAULT_AUTOKILL_SECONDS): CmdResult {
        val cmd = buildString {
            append("mkdir -p $TRACK_DIR; ")
            append("touch $TRACK_DIR/$pkg; ")
            // Pause semua PID app ini
            append("for p in \$(pidof $pkg); do ")
            append("echo -900 > /proc/\$p/oom_score_adj 2>/dev/null; ")
            append("kill -STOP \$p; ")
            append("done; ")
            // Watcher: kalau file penanda masih ada setelah autoKillSeconds, berarti belum di-resume -> kill paksa
            append(
                "nohup sh -c 'sleep $autoKillSeconds; " +
                    "if [ -f $TRACK_DIR/$pkg ]; then " +
                    "for p in \$(pidof $pkg); do kill -9 \$p; done; " +
                    "rm -f $TRACK_DIR/$pkg; fi' >/dev/null 2>&1 & "
            )
            append("echo OK")
        }
        val res = RootEngine.su(cmd)
        return CmdResult(res.output.contains("OK"), res.output, cmd)
    }

    /** Resume proses yang di-soft-freeze (SIGCONT) + hapus penanda watcher biar auto-kill dibatalkan. */
    fun softResume(pkg: String): CmdResult {
        val cmd = buildString {
            append("for p in \$(pidof $pkg); do ")
            append("kill -CONT \$p; ")
            append("echo 100 > /proc/\$p/oom_score_adj 2>/dev/null; ")
            append("done; ")
            append("rm -f $TRACK_DIR/$pkg; ")
            append("echo OK")
        }
        val res = RootEngine.su(cmd)
        return CmdResult(res.output.contains("OK"), res.output, cmd)
    }

    /** Cek pkg mana aja yang saat ini dalam status soft-frozen (berdasarkan file penanda). */
    fun listSoftFrozen(): Set<String> {
        val res = RootEngine.su("mkdir -p $TRACK_DIR; ls $TRACK_DIR 2>/dev/null")
        return res.output.lines().map { it.trim() }.filter { it.isNotBlank() }.toSet()
    }

    /** Cek langsung dari /proc apakah proses pkg lagi dalam state "T" (stopped). */
    fun isProcessStopped(pkg: String): Boolean {
        val res = RootEngine.su(
            "for p in \$(pidof $pkg); do grep -q '^State:.*T ' /proc/\$p/status 2>/dev/null && echo STOPPED; done"
        )
        return res.output.contains("STOPPED")
    }
}
