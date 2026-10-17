package com.taskreminder.ai

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.ZoneId

/** جدولة المنبهات عبر AlarmManager (تعمل والتطبيق مغلق). */
object Scheduler {
    const val ACTION_FIRE = "com.taskreminder.ai.FIRE"
    const val EXTRA_ID = "task_id"

    private fun pending(ctx: Context, id: Long): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(
            ctx, id.toInt(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun canExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    fun cancel(ctx: Context, id: Long) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pending(ctx, id))
    }

    /** يضبط (أو يلغي) المنبه التالي للمهمة. */
    fun schedule(ctx: Context, t: Task, nowMs: Long = System.currentTimeMillis()) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val db = TaskDb.get(ctx)
        val pi = pending(ctx, t.id)
        val at = Logic.nextTrigger(t, nowMs, ZoneId.systemDefault()) { day -> db.isDone(t.id, day) }
        if (at == null) {
            am.cancel(pi)
            return
        }
        if (canExact(ctx)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            // بدون إذن «المنبهات والتذكيرات» قد يتأخر التنبيه بضع دقائق.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun reschedule(ctx: Context, id: Long) {
        val t = TaskDb.get(ctx).get(id)
        if (t == null) cancel(ctx, id) else schedule(ctx, t)
    }

    /** بعد إعادة التشغيل / تغيير الوقت / فتح التطبيق. */
    fun rescheduleAll(ctx: Context) {
        for (t in TaskDb.get(ctx).all()) schedule(ctx, t)
    }
}
