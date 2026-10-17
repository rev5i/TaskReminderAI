package com.taskreminder.ai

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import java.time.LocalDate
import java.util.Locale

class StatsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Ui.theme(this))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)
        Ui.rtl(this)
        Ui.applyInsets(findViewById(R.id.root))
        Ui.systemBars(this)

        val db = TaskDb.get(this)
        val tasks = db.all()
        val comps = db.allCompletions()
        val s = Logic.summarize(tasks, LocalDate.now()) { id, day -> Pair(id, day) in comps }

        findViewById<TextView>(R.id.tvPercent).text = String.format(Locale.US, "%d%%", s.percent)
        findViewById<TextView>(R.id.tvPercentLabel).text =
            if (s.totalInstances == 0) "أضف مهامًا لتبدأ التتبّع"
            else String.format(Locale.US, "نسبة إنجازك (%d من %d)", s.doneInstances, s.totalInstances)
        findViewById<TextView>(R.id.tvCommitted).text = String.format(Locale.US, "%d", s.committedDays)
        findViewById<TextView>(R.id.tvStreak).text = String.format(Locale.US, "%d", s.streak)

        val list = findViewById<LinearLayout>(R.id.weekList)
        val inflater = LayoutInflater.from(this)
        for (d in s.last7) {
            val row = inflater.inflate(R.layout.item_stat_row, list, false)
            row.findViewById<TextView>(R.id.day).text = Ui.dayLabel(d.day)
            row.findViewById<ProgressBar>(R.id.bar).progress = if (d.total == 0) 0 else d.done * 100 / d.total
            row.findViewById<TextView>(R.id.count).text =
                if (d.total == 0) "—" else String.format(Locale.US, "%d/%d", d.done, d.total)
            list.addView(row)
        }

        findViewById<TextView>(R.id.tvNote).text =
            "تُحتسب المهام المفعّلة فقط. اليوم يُعدّ «ملتزمًا به» إذا أنجزت كل مهامه. " +
                "عند حذف مهمة يُحذف سجلّها أيضًا."
    }
}
