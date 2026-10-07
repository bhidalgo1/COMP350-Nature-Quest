package com.example.notificationtest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// Triggered when this receiver gets the pending intent
class CounterNotificationReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val service = CounterNotificationService(context) // Used to show and update notif
        service.showNotification(++Counter.value )
    }
}