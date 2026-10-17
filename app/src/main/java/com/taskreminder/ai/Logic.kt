package com.taskreminder.ai

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class DayStat(val day: LocalDate, val total: Int, val done: Int) {
    val complete: Boolean get() = total > 0 && done == total
}

data class Summary(
    val committedDays: Int,
    val trackedDays: Int,
    val totalInstances: Int,
    val doneInstances: Int,
    val streak: Int,
    val last7: List<DayStat>
) {
    val percent: Int get() = if (totalInstances == 0) 0 else doneInstances * 100 / totalInstances
}

/** منطق التكرار والجدولة والإحصاءات (بدون أي اعتماد على Android). */
object Logic {

    fun today(): Long = LocalDate.now().toEpochDay()

    /** هل تنطبق المهمة على هذا اليوم؟ */
    fun appliesOn(t: Task, day: LocalDate): Boolean {
        val d = day.toEpochDay()
        return when (t.repeat) {
            Task.ONCE -> d == t.onceDay
            Task.DAILY -> d >= t.createdDay
            else -> d >= t.createdDay && ((t.daysMask shr (day.dayOfWeek.value - 1)) and 1) == 1
        }
    }

    /**
     * وقت التنبيه القادم (ميلي ثانية) أو null إن لم يوجد.
     * - المهمة المتوقفة: لا تنبيه.
     * - التأجيل الساري له الأولوية.
     * - الأيام التي أُنجزت فيها المهمة مسبقًا تُتخطى.
     */
    fun nextTrigger(
        t: Task,
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        isDone: (Long) -> Boolean = { false }
    ): Long? {
        if (!t.enabled) return null
        if (t.snoozeUntil > nowMs) return t.snoozeUntil
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMs), zone)
        val today = now.toLocalDate()
        for (i in 0..7) {
            val d = today.plusDays(i.toLong())
            if (!appliesOn(t, d)) continue
            if (isDone(d.toEpochDay())) continue
            val at = ZonedDateTime.of(d, LocalTime.of(t.hour, t.minute), zone)
            if (at.isAfter(now)) return at.toInstant().toEpochMilli()
        }
        return null
    }

    /** إحصاءات الإنجاز. تُحتسب المهام المفعّلة فقط. */
    fun summarize(tasks: List<Task>, today: LocalDate, isDone: (Long, Long) -> Boolean): Summary {
        val active = tasks.filter { it.enabled }

        fun stat(d: LocalDate): DayStat {
            val sched = active.filter { appliesOn(it, d) }
            return DayStat(d, sched.size, sched.count { isDone(it.id, d.toEpochDay()) })
        }

        val last7 = (6 downTo 0).map { stat(today.minusDays(it.toLong())) }
        if (active.isEmpty()) return Summary(0, 0, 0, 0, 0, last7)

        val firstDay = active.minOf { if (it.repeat == Task.ONCE) minOf(it.createdDay, it.onceDay) else it.createdDay }
        val start = maxOf(firstDay, today.toEpochDay() - 364)

        var committed = 0
        var tracked = 0
        var total = 0
        var done = 0
        val stats = ArrayList<DayStat>()
        var d = start
        while (d <= today.toEpochDay()) {
            val s = stat(LocalDate.ofEpochDay(d))
            stats.add(s)
            if (s.total > 0) {
                total += s.total
                done += s.done
                tracked++
                if (s.complete) committed++
            }
            d++
        }

        // السلسلة الحالية: أيام متتالية أُنجزت فيها كل المهام. اليوم غير المكتمل لا يكسر السلسلة.
        var streak = 0
        var idx = stats.size - 1
        if (idx >= 0 && stats[idx].total > 0 && !stats[idx].complete) idx--
        while (idx >= 0) {
            val s = stats[idx]
            if (s.total > 0) {
                if (s.complete) streak++ else break
            }
            idx--
        }
        return Summary(committed, tracked, total, done, streak, last7)
    }
}
