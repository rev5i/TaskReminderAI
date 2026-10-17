package com.taskreminder.ai

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import java.time.LocalDate
import java.util.Locale

object Ui {
    /** الإثنين = 0 ... الأحد = 6 (مطابق لـ Task.daysMask). */
    val DAY_NAMES = arrayOf("الإثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد")

    /** ترتيب العرض: السبت أولًا كما هو معتاد في الأسبوع العربي. */
    val DISPLAY_ORDER = intArrayOf(5, 6, 0, 1, 2, 3, 4)

    fun theme(c: Context): Int = if (Prefs.dark(c)) R.style.AppDark else R.style.AppLight

    /** يفرض الاتجاه من اليمين إلى اليسار مهما كانت لغة الجهاز. */
    fun rtl(a: Activity) {
        a.window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    fun rtl(d: Dialog) {
        d.window?.decorView?.layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    /** أندرويد 15 يفرض العرض من الحافة للحافة؛ نضيف حشوة لشريطي النظام. */
    fun applyInsets(root: View) {
        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener { v, insets ->
                val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                v.setPadding(b.left, b.top, b.right, b.bottom)
                WindowInsets.CONSUMED
            }
        }
    }

    /** لون أيقونات شريطي الحالة والتنقل حسب الوضع. */
    fun systemBars(a: Activity) {
        if (Build.VERSION.SDK_INT >= 30) {
            val light = !Prefs.dark(a)
            val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            a.window.insetsController?.setSystemBarsAppearance(if (light) mask else 0, mask)
        }
    }

    fun fmtTime(h: Int, m: Int): String {
        val h12 = if (h % 12 == 0) 12 else h % 12
        return String.format(Locale.US, "%d:%02d %s", h12, m, if (h < 12) "ص" else "م")
    }

    fun fmtDate(epochDay: Long): String {
        val d = LocalDate.ofEpochDay(epochDay)
        return String.format(Locale.US, "%04d/%02d/%02d", d.year, d.monthValue, d.dayOfMonth)
    }

    fun repeatText(t: Task): String = when (t.repeat) {
        Task.ONCE -> "مرة واحدة · " + fmtDate(t.onceDay)
        Task.DAILY -> "كل يوم"
        else ->
            if (t.daysMask == Task.ALL_DAYS) "كل يوم"
            else DISPLAY_ORDER.filter { ((t.daysMask shr it) and 1) == 1 }
                .joinToString("، ") { DAY_NAMES[it] }
    }

    fun describe(t: Task): String = fmtTime(t.hour, t.minute) + " · " + repeatText(t)

    fun dayLabel(d: LocalDate): String =
        DAY_NAMES[d.dayOfWeek.value - 1] + String.format(Locale.US, " %d/%d", d.dayOfMonth, d.monthValue)
}
