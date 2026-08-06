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

    // ── SHIZUKU BINDER & PERMISSION LISTENERS ──────────────────────────
    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        onShizukuPermResult?.invoke()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        onShizukuPermResult?.invoke()
    }

    private val permResultListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        onShizukuPermResult?.invoke()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Urutan Registrasi Listener Terpenting
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

                    onShizukuPermResult = { vm.init(ctx) }

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