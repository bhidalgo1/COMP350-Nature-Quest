package com.example.notificationtest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat

class TimerNotificationService(private val context: Context) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun showNotification() {
        val messageKey = "key"

        val activityIntent = Intent(context, MainActivity::class.java)
        if (activityIntent.getStringExtra(messageKey) != null) {
            Toast.makeText(context, activityIntent.getStringExtra(messageKey).toString(), Toast.LENGTH_SHORT).show()
        }
        activityIntent.putExtra(messageKey, "timer finished")

        val activityPendingIntent = PendingIntent.getActivity(
            context,
            1,
            activityIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val notification = NotificationCompat.Builder(context, TIMER_CHANNEL_ID)
            .setSmallIcon(R.drawable.baseline_10k_24)
            .setContentTitle("Timer")
            .setContentText("Timer has finished")
            .setContentIntent(activityPendingIntent)
            .build()

        notificationManager.notify(1, notification)
    }

    companion object {
        const val TIMER_CHANNEL_ID = "timer_channel"
    }
}