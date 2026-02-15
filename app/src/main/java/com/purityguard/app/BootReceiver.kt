package com.purityguard.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val settings = SettingsStore(context)
            if (settings.isProtectionEnabled()) {
                context.startService(Intent(context, PurityVpnService::class.java).putExtra("action", "start"))
            }
        }
    }
}
