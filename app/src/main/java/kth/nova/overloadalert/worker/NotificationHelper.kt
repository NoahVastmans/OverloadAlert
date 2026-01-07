package kth.nova.overloadalert.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import kth.nova.overloadalert.MainActivity
import kth.nova.overloadalert.R

/**
 * Helper class for managing and displaying notifications related to overload alerts.
 *
 * This class handles the creation of the notification channel and provides methods
 * to trigger specific types of notifications, such as warnings for high training loads
 * or encouragement messages. It also handles the intent creation to navigate back to
 * the [MainActivity] when a notification is tapped.
 *
 * @property context The application context used to access system services and resources.
 */
class NotificationHelper(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val CHANNEL_ID = "overload_alert_channel"
    }

    fun createNotificationChannel() {
        val name = "Overload Alerts"
        val descriptionText = "Notifications for training load and injury risk"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun createLaunchAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    fun showWarningNotification(title: String, message: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // TODO: Replace with a proper warning icon
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(createLaunchAppIntent())
            .setAutoCancel(true) // Dismiss notification on tap

        notificationManager.notify(1, builder.build())
    }

    fun showEncouragementNotification(title: String, message: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // TODO: Replace with a proper positive icon
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(createLaunchAppIntent())
            .setAutoCancel(true) // Dismiss notification on tap

        notificationManager.notify(2, builder.build())
    }
}