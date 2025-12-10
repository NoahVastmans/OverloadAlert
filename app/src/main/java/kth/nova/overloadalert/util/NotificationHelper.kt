package kth.nova.overloadalert.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import kth.nova.overloadalert.R

class NotificationHelper(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val CHANNEL_ID = "overload_alert_channel"
    }

    fun createNotificationChannel() {
        val name = "Overload Alerts"
        val descriptionText = "Notifications for high-risk activities"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun showHighRiskNotification(message: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // You may need to create this asset
            .setContentTitle("High Risk Activity Detected")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        // An ID for the notification
        val notificationId = 1

        notificationManager.notify(notificationId, builder.build())
    }
}