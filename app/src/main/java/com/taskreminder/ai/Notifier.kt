package com.taskreminder.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon

object Notifier {
    const val CHANNEL = "task_reminders"
    const val ACTION_DONE = "com.taskreminder.ai.DONE"
    const val ACTION_SNOOZE = "com.taskreminder.ai.SNOOZE"

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val ch = NotificationChannel(CHANNEL, "تذكيرات المهام", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "إشعارات تذكّرك بمهامك في الوقت الذي حددته"
            enableVibration(true)
        }
        nm.createNotificationChannel(ch)
    }

    private fun action(ctx: Context, act: String, id: Long, code: Int): PendingIntent {
        val i = Intent(ctx, ActionReceiver::class.java).setAction(act).putExtra(Scheduler.EXTRA_ID, id)
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun show(ctx: Context, t: Task, withActions: Boolean = true) {
        ensureChannel(ctx)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val open = PendingIntent.getActivity(
            ctx, t.id.toInt(), Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = "ابدأ بمهمة «${t.title}» الآن"
        val b = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("حان وقت مهمتك!")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
        if (withActions) {
            val icon = Icon.createWithResource(ctx, R.drawable.ic_notif)
            val min = Prefs.snoozeMin(ctx)
            b.addAction(
                Notification.Action.Builder(icon, "تم ✓", action(ctx, ACTION_DONE, t.id, t.id.toInt() * 2)).build()
            )
            b.addAction(
                Notification.Action.Builder(icon, "تأجيل $min د", action(ctx, ACTION_SNOOZE, t.id, t.id.toInt() * 2 + 1)).build()
            )
        }
        nm.notify(t.id.toInt(), b.build())
    }

    fun cancel(ctx: Context, id: Long) {
        ctx.getSystemService(NotificationManager::class.java).cancel(id.toInt())
    }
}
