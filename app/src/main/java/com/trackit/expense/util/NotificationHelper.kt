package com.trackit.expense.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.trackit.expense.MainActivity
import com.trackit.expense.R

/**
 * Utility for managing notification channels and posting alerts.
 */
object NotificationHelper {

    const val CHANNEL_EXPENSE_ENTRY = "expense_entry"
    const val CHANNEL_BUDGET_ALERTS = "budget_alerts"
    const val CHANNEL_REMINDERS = "reminders"

    /** Initialise all required notification channels. Called at app startup. */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val channels = listOf(
                NotificationChannel(
                    CHANNEL_EXPENSE_ENTRY,
                    "Payment Detection",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts shown immediately after a transaction is detected."
                },
                NotificationChannel(
                    CHANNEL_BUDGET_ALERTS,
                    "Budget Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications for monthly budget thresholds (50%, 80%, etc.)."
                },
                NotificationChannel(
                    CHANNEL_REMINDERS,
                    "Reminders",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Reminders to log skipped or unlogged transactions."
                }
            )
            manager.createNotificationChannels(channels)
        }
    }

    /**
     * Posts a notification to a specific channel.
     * Checks for POST_NOTIFICATIONS permission on Android 13+.
     */
    fun showNotification(
        context: Context,
        channelId: String,
        notificationId: Int,
        title: String,
        message: String,
        deepLink: String? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) return
        }

        val intent = if (deepLink != null) {
            Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).apply {
                @Suppress("DEPRECATION")
                `package` = context.packageName
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        } else {
            Intent(context, MainActivity::class.java)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Placeholder
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(when(channelId) {
                CHANNEL_EXPENSE_ENTRY -> NotificationCompat.PRIORITY_HIGH
                CHANNEL_REMINDERS -> NotificationCompat.PRIORITY_LOW
                else -> NotificationCompat.PRIORITY_DEFAULT
            })

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }
}
