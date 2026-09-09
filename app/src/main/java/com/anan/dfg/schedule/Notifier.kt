package com.anan.dfg.schedule

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.anan.dfg.MainActivity
import com.anan.dfg.R
import com.anan.dfg.data.CheckWindow
import com.anan.dfg.data.ChecklistItem
import com.anan.dfg.data.ReminderRule
import com.anan.dfg.util.Format

object Notifier {

    const val CHANNEL_ID = "missed_check"
    const val EXTRA_ITEM_ID = "itemId"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notif_channel_desc)
            enableVibration(true)
        }
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun notifyMissed(
        context: Context,
        item: ChecklistItem,
        rule: ReminderRule,
        window: CheckWindow,
    ) {
        if (!canPost(context)) return
        ensureChannel(context)

        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ITEM_ID, item.id)
        }
        val pending = PendingIntent.getActivity(
            context,
            item.id.toInt(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val res = context.resources
        val windowText = Format.window(res, window)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_check)
            .setContentTitle(context.getString(R.string.notif_title, item.name))
            .setContentText(context.getString(R.string.notif_text, windowText))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    context.getString(R.string.notif_text, windowText) +
                        "\n" + Format.rule(res, rule),
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(notificationId(rule.id, window.endMillis), notification)
        } catch (e: SecurityException) {
            // The user just revoked notification access; nothing to do.
        }
    }

    /** Different windows of the same rule must not overwrite each other. */
    private fun notificationId(ruleId: Long, deadline: Long): Int =
        (ruleId * 1_000_003 + deadline / 60_000).toInt()
}
