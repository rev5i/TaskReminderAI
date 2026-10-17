package com.taskreminder.ai

import android.content.Context

object Prefs {
    private fun p(c: Context) = c.applicationContext.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    /** الوضع الداكن هو الافتراضي. */
    fun dark(c: Context): Boolean = p(c).getBoolean("dark", true)
    fun setDark(c: Context, v: Boolean) = p(c).edit().putBoolean("dark", v).apply()

    fun snoozeMin(c: Context): Int = p(c).getInt("snooze_min", 10)
    fun setSnoozeMin(c: Context, v: Int) = p(c).edit().putInt("snooze_min", v).apply()
}
