package com.example.notificationtest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import android.content.Intent
import android.os.Build

class CounterNotificationService(private val context: Context) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun showNotification(counter: Int) {
        val activityIntent = Intent(context, MainActivity::class.java)

        // Pending intent, wrapper around normal intent, allows an outside application or whatever to execute code of your app
        val activityPendingIntent = PendingIntent.getActivity(
            context,
            1, // Android uses to identify specific pending intent
            activityIntent,

            // Flags define what should be done with that pending intent
            // FLAG_IMMUTABLE, whatever receives this intent cannot directly manipulate that intent
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        // Upon tapping notif, send to broadcast receiver
        val incrementIntent = PendingIntent.getBroadcast(
            context,
            2, // Different to distinguish
            Intent(context, CounterNotificationReceiver::class.java),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val notification = NotificationCompat.Builder(context, COUNTER_CHANNEL_ID)
            .setSmallIcon(R.drawable.baseline_10k_24)
            .setContentTitle("Increment counter")
            .setContentText("The count is $counter")
            .setContentIntent(activityPendingIntent) // Intent sent when the user taps notif, requires pending intent
            .addAction(
                R.drawable.baseline_10k_24,
                "Increment",
                incrementIntent
            )
            .build()

        notificationManager.notify(1, notification) // Using multiple notifs need a way to refer to a specific notif
    }

    companion object {
        // Send notif to the identified channel below
        const val COUNTER_CHANNEL_ID = "counter_channel"
    }
}