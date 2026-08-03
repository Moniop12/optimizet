package com.monai.optimizer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.monai.optimizer.data.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * §6 fix — if the Live Service was running when the device rebooted
 * (persisted via [UserPreferencesRepository]), bring it back automatically
 * once the boot sequence completes.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = UserPreferencesRepository(appContext)
                val wasRunning = repo.preferencesFlow.first().isLiveServiceRunning
                if (wasRunning) {
                    val serviceIntent = Intent(appContext, MonAiService::class.java).apply {
                        action = MonAiService.ACTION_START
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        appContext.startForegroundService(serviceIntent)
                    } else {
                        appContext.startService(serviceIntent)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
