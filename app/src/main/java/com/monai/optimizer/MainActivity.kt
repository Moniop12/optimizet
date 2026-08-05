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

    // Sebelumnya listener ini didaftarkan tapi isinya kosong ({ _, _ -> }) —
    // jadi walau user udah kasih izin Shizuku, app gak pernah tau, harus
    // force-stop dulu biar status-nya ke-refresh. Sekarang beneran
    // memicu re-check begitu hasil izin (grant/deny) datang.
    private var onShizukuPermResult: (() -> Unit)? = null
    private val shizuListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        onShizukuPermResult?.invoke()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(shizuListener)
        setContent {
            MonAiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = MaterialTheme.colorScheme.background
                ) {
                    val vm: MainViewModel = viewModel()
                    val ctx = applicationContext
                    onShizukuPermResult = { vm.init(ctx) }

                    // Re-check root & Shizuku SETIAP kali app kembali ke depan —
                    // bukan cuma sekali pas pertama buka. Ini nutup kasus: user
                    // approve izin root di dialog Magisk/KernelSU, atau
                    // buka app Shizuku buat nyalain servicenya, lalu balik lagi
                    // ke sini — sebelumnya harus force-stop dulu baru kedeteksi.
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
        Shizuku.removeRequestPermissionResultListener(shizuListener)
        onShizukuPermResult = null
        super.onDestroy()
    }
}
