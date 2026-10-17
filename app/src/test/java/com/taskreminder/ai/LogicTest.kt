package com.taskreminder.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class LogicTest {
    private val zone = ZoneId.of("Africa/Casablanca")

    private fun ms(y: Int, m: Int, d: Int, h: Int, min: Int, s: Int = 0): Long =
        LocalDateTime.of(y, m, d, h, min, s).atZone(zone).toInstant().toEpochMilli()

    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()

    // 2026-10-17 هو يوم سبت
    private fun daily(h: Int, m: Int, created: Long = day(2026, 10, 1)) =
        Task(id = 1, title = "t", hour = h, minute = m, repeat = Task.DAILY, createdDay = created)

    @Test fun daily_later_today() {
        val at = Logic.nextTrigger(daily(21, 0), ms(2026, 10, 17, 20, 0), zone)
        assertEquals(ms(2026, 10, 17, 21, 0), at)
    }

    @Test fun daily_already_passed_goes_to_tomorrow() {
        val at = Logic.nextTrigger(daily(8, 0), ms(2026, 10, 17, 20, 0), zone)
        assertEquals(ms(2026, 10, 18, 8, 0), at)
    }

    @Test fun exactly_at_time_goes_to_next_day() {
        val at = Logic.nextTrigger(daily(8, 0), ms(2026, 10, 17, 8, 0), zone)
        assertEquals(ms(2026, 10, 18, 8, 0), at)
    }

    @Test fun daily_skips_today_if_done() {
        val at = Logic.nextTrigger(daily(21, 0), ms(2026, 10, 17, 20, 0), zone) { it == day(2026, 10, 17) }
        assertEquals(ms(2026, 10, 18, 21, 0), at)
    }

    @Test fun specific_days_mon_and_wed() {
        // الإثنين=بت0، الأربعاء=بت2
        val t = Task(id = 2, title = "t", hour = 7, minute = 30, repeat = Task.DAYS,
            daysMask = (1 shl 0) or (1 shl 2), createdDay = day(2026, 10, 1))
        // السبت 17/10 -> التالي الإثنين 19/10
        assertEquals(ms(2026, 10, 19, 7, 30), Logic.nextTrigger(t, ms(2026, 10, 17, 12, 0), zone))
        // الإثنين 19/10 بعد الوقت -> الأربعاء 21/10
        assertEquals(ms(2026, 10, 21, 7, 30), Logic.nextTrigger(t, ms(2026, 10, 19, 9, 0), zone))
        // الأربعاء بعد الوقت -> الإثنين 26/10
        assertEquals(ms(2026, 10, 26, 7, 30), Logic.nextTrigger(t, ms(2026, 10, 21, 9, 0), zone))
    }

    @Test fun once_future_and_past() {
        val t = Task(id = 3, title = "t", hour = 10, minute = 0, repeat = Task.ONCE,
            onceDay = day(2026, 10, 20), createdDay = day(2026, 10, 17))
        assertEquals(ms(2026, 10, 20, 10, 0), Logic.nextTrigger(t, ms(2026, 10, 17, 9, 0), zone))
        assertNull(Logic.nextTrigger(t, ms(2026, 10, 20, 10, 1), zone))
        assertNull(Logic.nextTrigger(t, ms(2026, 10, 25, 10, 1), zone))
    }

    @Test fun once_done_has_no_trigger() {
        val t = Task(id = 3, title = "t", hour = 10, minute = 0, repeat = Task.ONCE,
            onceDay = day(2026, 10, 20), createdDay = day(2026, 10, 17))
        assertNull(Logic.nextTrigger(t, ms(2026, 10, 17, 9, 0), zone) { it == day(2026, 10, 20) })
    }

    @Test fun paused_has_no_trigger() {
        assertNull(Logic.nextTrigger(daily(21, 0).copy(enabled = false), ms(2026, 10, 17, 20, 0), zone))
    }

    @Test fun snooze_wins_then_expires() {
        val now = ms(2026, 10, 17, 20, 0)
        val snoozed = daily(21, 0).copy(snoozeUntil = now + 10 * 60_000)
        assertEquals(now + 10 * 60_000, Logic.nextTrigger(snoozed, now, zone))
        // التأجيل انتهى -> يعود للجدول العادي
        assertEquals(ms(2026, 10, 17, 21, 0), Logic.nextTrigger(snoozed, now + 11 * 60_000, zone))
    }

    @Test fun midnight_and_year_rollover() {
        val at = Logic.nextTrigger(daily(0, 5), ms(2026, 12, 31, 23, 59), zone)
        assertEquals(ms(2027, 1, 1, 0, 5), at)
    }

    @Test fun appliesOn_respects_created_day() {
        val t = daily(8, 0, created = day(2026, 10, 10))
        assertTrue(!Logic.appliesOn(t, LocalDate.of(2026, 10, 9)))
        assertTrue(Logic.appliesOn(t, LocalDate.of(2026, 10, 10)))
    }

    @Test fun stats_full_commitment_and_streak() {
        val today = LocalDate.of(2026, 10, 17)
        val t = daily(8, 0, created = day(2026, 10, 14)) // 14,15,16,17
        val done = setOf(14L, 15L, 16L, 17L).map { day(2026, 10, it.toInt()) }.toSet()
        val s = Logic.summarize(listOf(t), today) { id, d -> id == 1L && d in done }
        assertEquals(4, s.committedDays)
        assertEquals(4, s.trackedDays)
        assertEquals(100, s.percent)
        assertEquals(4, s.streak)
        assertEquals(7, s.last7.size)
    }

    @Test fun stats_partial_and_broken_streak() {
        val today = LocalDate.of(2026, 10, 17)
        val t = daily(8, 0, created = day(2026, 10, 14))
        val done = setOf(day(2026, 10, 14), day(2026, 10, 16)) // 15 فائت، 17 لم يُنجز بعد
        val s = Logic.summarize(listOf(t), today) { id, d -> id == 1L && d in done }
        assertEquals(2, s.committedDays)
        assertEquals(50, s.percent)           // 2 من 4
        assertEquals(1, s.streak)             // 16 فقط (اليوم غير المكتمل لا يكسر السلسلة، 15 يكسرها)
    }

    @Test fun stats_two_tasks_one_missing_breaks_day() {
        val today = LocalDate.of(2026, 10, 17)
        val a = daily(8, 0, created = day(2026, 10, 16)).copy(id = 1)
        val b = daily(9, 0, created = day(2026, 10, 16)).copy(id = 2)
        val done = setOf(1L to day(2026, 10, 16), 2L to day(2026, 10, 16), 1L to day(2026, 10, 17))
        val s = Logic.summarize(listOf(a, b), today) { id, d -> (id to d) in done }
        assertEquals(1, s.committedDays)   // 16 فقط
        assertEquals(75, s.percent)        // 3 من 4
        assertEquals(1, s.streak)
    }

    @Test fun stats_ignores_paused_and_empty() {
        val today = LocalDate.of(2026, 10, 17)
        val s = Logic.summarize(listOf(daily(8, 0).copy(enabled = false)), today) { _, _ -> true }
        assertEquals(0, s.percent)
        assertEquals(0, s.committedDays)
        assertNotNull(s.last7)
    }
}
