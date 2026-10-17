package com.taskreminder.ai

import android.content.Context

/** عمليات مشتركة بين الواجهة والإشعارات. */
object Actions {
    fun markDone(ctx: Context, id: Long) {
        val db = TaskDb.get(ctx)
        if (db.get(id) == null) return
        db.markDone(id, Logic.today())
        db.setSnooze(id, 0)
        Notifier.cancel(ctx, id)
        Scheduler.reschedule(ctx, id)
    }

    fun unmarkDone(ctx: Context, id: Long) {
        TaskDb.get(ctx).unmarkDone(id, Logic.today())
        Scheduler.reschedule(ctx, id)
    }

    fun snooze(ctx: Context, id: Long) {
        val db = TaskDb.get(ctx)
        if (db.get(id) == null) return
        db.setSnooze(id, System.currentTimeMillis() + Prefs.snoozeMin(ctx) * 60_000L)
        Notifier.cancel(ctx, id)
        Scheduler.reschedule(ctx, id)
    }

    fun setEnabled(ctx: Context, id: Long, enabled: Boolean) {
        TaskDb.get(ctx).setEnabled(id, enabled)
        if (!enabled) Notifier.cancel(ctx, id)
        Scheduler.reschedule(ctx, id)
    }

    fun delete(ctx: Context, id: Long) {
        Scheduler.cancel(ctx, id)
        Notifier.cancel(ctx, id)
        TaskDb.get(ctx).delete(id)
    }
}
