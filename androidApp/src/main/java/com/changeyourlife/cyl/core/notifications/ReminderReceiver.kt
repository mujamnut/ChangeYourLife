package com.changeyourlife.cyl.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.CATEGORY_REMINDER
import androidx.core.app.NotificationCompat.DEFAULT_ALL
import androidx.core.app.NotificationCompat.PRIORITY_HIGH
import androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC
import androidx.core.app.NotificationManagerCompat
import com.changeyourlife.cyl.presentation.MainActivity

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ensureChannel(context)

        val reminderId = intent.getStringExtra(ReminderNotificationConstants.ExtraReminderId)
            ?: return
        val title = intent.getStringExtra(ReminderNotificationConstants.ExtraReminderTitle)
            ?.takeIf { it.isNotBlank() }
            ?: "Reminder"
        val launchIntent = Intent(context, MainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(
            context,
            reminderId.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, ReminderNotificationConstants.ChannelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("ChangeYourLife")
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(title))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(CATEGORY_REMINDER)
            .setDefaults(DEFAULT_ALL)
            .setPriority(PRIORITY_HIGH)
            .setVisibility(VISIBILITY_PUBLIC)
            .build()

        NotificationManagerCompat.from(context).notify(reminderId.hashCode(), notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            ReminderNotificationConstants.ChannelId,
            ReminderNotificationConstants.ChannelName,
            NotificationManager.IMPORTANCE_HIGH,
        )
        channel.description = "Date reminders from CYL databases"
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }
}
