package com.monai.optimizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.monai.optimizer.ui.MainApp
import com.monai.optimizer.ui.MainViewModel
import com.monai.optimizer.ui.theme.MonAiTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val shizuListener = Shizuku.OnRequestPermissionResultListener { _, _ -> }

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
                    LaunchedEffect(Unit) { vm.init(ctx) }
                    MainApp(vm)
                }
            }
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizuListener)
        super.onDestroy()
    }
}
