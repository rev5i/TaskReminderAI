package com.taskreminder.ai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** يُستدعى عند حلول وقت التذكير. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Scheduler.ACTION_FIRE) return
        val id = intent.getLongExtra(Scheduler.EXTRA_ID, -1L)
        if (id < 0) return
        val db = TaskDb.get(ctx)
        val t = db.get(id) ?: return
        if (!t.enabled) return
        db.setSnooze(id, 0)
        if (!db.isDone(id, Logic.today())) Notifier.show(ctx, t)
        // نجدول التذكير التالي (مع هامش ثانيتين لتفادي التكرار إن وصل المنبه مبكرًا قليلًا).
        db.get(id)?.let { Scheduler.schedule(ctx, it, System.currentTimeMillis() + 2000) }
    }
}

/** أزرار «تم» و«تأجيل» في الإشعار. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getLongExtra(Scheduler.EXTRA_ID, -1L)
        if (id < 0) return
        when (intent.action) {
            Notifier.ACTION_DONE -> Actions.markDone(ctx, id)
            Notifier.ACTION_SNOOZE -> Actions.snooze(ctx, id)
        }
    }
}

/** يعيد جدولة كل التذكيرات بعد إعادة التشغيل أو تحديث التطبيق أو تغيير الوقت/المنطقة الزمنية. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Notifier.ensureChannel(ctx)
        Scheduler.rescheduleAll(ctx)
    }
}
