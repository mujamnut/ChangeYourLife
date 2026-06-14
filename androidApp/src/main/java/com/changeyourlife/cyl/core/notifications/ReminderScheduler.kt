package com.changeyourlife.cyl.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.changeyourlife.cyl.domain.model.Reminder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(reminder: Reminder) {
        if (reminder.deletedAt != null || reminder.isDone || reminder.remindAt <= System.currentTimeMillis()) {
            cancel(reminder.id)
            return
        }

        alarmManager.set(
            AlarmManager.RTC_WAKEUP,
            reminder.remindAt,
            pendingIntent(
                reminderId = reminder.id,
                title = reminder.title,
            ),
        )
    }

    fun cancel(reminderId: String) {
        alarmManager.cancel(
            pendingIntent(
                reminderId = reminderId,
                title = "",
            ),
        )
    }

    private fun pendingIntent(
        reminderId: String,
        title: String,
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderNotificationConstants.ExtraReminderId, reminderId)
            putExtra(ReminderNotificationConstants.ExtraReminderTitle, title)
        }
        return PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
