package com.monai.optimizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.monai.optimizer.ui.MainApp
import com.monai.optimizer.ui.MainViewModel
import com.monai.optimizer.ui.theme.MonAiTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private var onShizukuPermResult: (() -> Unit)? = null

    // ── 1. Binder Received (KRITIS — ini yang hilang sebelumnya) ─────────
    //
    // Shizuku TIDAK otomatis deliver binder-nya ke app kita. App harus
    // daftarkan listener ini dulu. Tanpa ini, Shizuku.pingBinder() selalu
    // return false meskipun Shizuku / Shizuku+ jelas-jelas running.
    //
    // "Sticky" artinya: kalau Shizuku sudah running sebelum listener
    // didaftarkan (misalnya app dibuka setelah Shizuku running),
    // listener langsung terpanggil saat registrasi — tidak perlu tunggu
    // event berikutnya.
    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        onShizukuPermResult?.invoke()
    }

    // ── 2. Binder Dead (Shizuku mati / restart) ──────────────────────────
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        // Binder mati — nanti saat Shizuku restart binderReceivedListener
        // akan terpanggil lagi otomatis.
    }

    // ── 3. Permission Result (user tap Allow/Deny di dialog Shizuku) ─────
    private val permResultListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        onShizukuPermResult?.invoke()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Urutan registrasi penting: daftarkan binder listener SEBELUM
        // setContent agar binder sudah tersedia saat ViewModel pertama init.
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permResultListener)

        setContent {
            MonAiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = MaterialTheme.colorScheme.background
                ) {
                    val vm: MainViewModel = viewModel()
                    val ctx = applicationContext

                    // Callback yang dipanggil oleh ketiga listener di atas.
                    onShizukuPermResult = { vm.init(ctx) }

                    // Re-check setiap kali app kembali ke foreground —
                    // untuk mendeteksi: root baru di-grant, Shizuku distart
                    // dari luar, atau permission baru dikasih.
                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) vm.init(ctx)
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }

                    MainApp(vm)
                }
            }
        }
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permResultListener)
        onShizukuPermResult = null
        super.onDestroy()
    }
}
