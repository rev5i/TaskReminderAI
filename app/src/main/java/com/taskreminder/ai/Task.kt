package com.taskreminder.ai

/**
 * نموذج المهمة. لا يعتمد على Android حتى يمكن اختبار المنطق على JVM مباشرة.
 *
 * @param daysMask  بت لكل يوم: البت 0 = الإثنين ... البت 6 = الأحد (مطابق لـ DayOfWeek.value - 1)
 * @param onceDay   اليوم (epochDay) للمهام التي تُنفَّذ مرة واحدة
 * @param snoozeUntil وقت انتهاء التأجيل بالميلي ثانية (0 = لا يوجد تأجيل)
 */
data class Task(
    val id: Long = 0,
    val title: String,
    val hour: Int,
    val minute: Int,
    val repeat: Int,
    val daysMask: Int = 0,
    val onceDay: Long = 0,
    val enabled: Boolean = true,
    val snoozeUntil: Long = 0,
    val createdDay: Long
) {
    companion object {
        const val ONCE = 0
        const val DAILY = 1
        const val DAYS = 2
        const val ALL_DAYS = 127
    }
}
