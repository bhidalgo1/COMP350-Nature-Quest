package com.example.notificationtest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TimerNotificationReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val service = CounterNotificationService(context)
        service.showNotification(++Counter.value )
    }
}