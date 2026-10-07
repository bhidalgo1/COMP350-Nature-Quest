package com.example.notificationtest
import android.app.Application
import android.os.Build
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

class Notif: Application() {
    override fun onCreate() {
        super.onCreate()
        createCounterNotificationChannel()
        createTimerNotificationChannel()
    }

    /* Notification channel
    Basically how notifications are sorted
    YouTube, for example, has notif channels for when an account you're subscribed to
    has uploaded a video, when a livestream starts, when someone makes a comment to
    a video you posted, etc. When someone you're subbed to uploads a video, the
    notification for that event goes to the "Subcriptions" channel
    They can be turned off to exclude certain notifications
    Without the use of notif channels, you either receive every notif or none
    This app uses two notif channels
    */
    private fun createCounterNotificationChannel() { // Counter
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CounterNotificationService.COUNTER_CHANNEL_ID,
                "Counter",
                NotificationManager.IMPORTANCE_DEFAULT // Notifs with higher importance will show in a more prominent way
            )
            channel.description = "Used for the increment counter notifications"
            // See stuff above by tap hold on app, select "App info", select Notifications, click categories

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createTimerNotificationChannel() { // Timer
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                TimerNotificationService.TIMER_CHANNEL_ID,
                "Timer",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.description = "Show when timer finishes"

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}