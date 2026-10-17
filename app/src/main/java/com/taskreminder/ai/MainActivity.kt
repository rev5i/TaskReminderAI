package com.taskreminder.ai

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate

class MainActivity : Activity() {

    private lateinit var db: TaskDb
    private lateinit var adapter: TaskAdapter
    private lateinit var tabs: List<TextView>
    private lateinit var tvSummary: TextView
    private lateinit var progress: ProgressBar
    private lateinit var banner: TextView
    private lateinit var empty: TextView
    private var tab = 0
    private var bannerAction = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Ui.theme(this))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Ui.rtl(this)
        Ui.applyInsets(findViewById(R.id.root))
        Ui.systemBars(this)

        db = TaskDb.get(this)
        Notifier.ensureChannel(this)

        tvSummary = findViewById(R.id.tvSummary)
        progress = findViewById(R.id.progress)
        banner = findViewById(R.id.banner)
        empty = findViewById(R.id.empty)
        val list = findViewById<ListView>(R.id.list)

        adapter = TaskAdapter(
            this,
            onDone = { t, checked ->
                if (checked) Actions.markDone(this, t.id) else Actions.unmarkDone(this, t.id)
                refresh()
            },
            onEnabled = { t, enabled ->
                Actions.setEnabled(this, t.id, enabled)
                refresh()
            }
        )
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ -> edit(adapter.getItem(pos).task.id) }
        list.setOnItemLongClickListener { _, _, pos, _ ->
            confirmDelete(adapter.getItem(pos).task)
            true
        }

        tabs = listOf(R.id.tab0, R.id.tab1, R.id.tab2, R.id.tab3).map { findViewById<TextView>(it) }
        tabs.forEachIndexed { i, tv ->
            tv.setOnClickListener {
                tab = i
                refresh()
            }
        }

        findViewById<Button>(R.id.fab).setOnClickListener { edit(-1L) }
        findViewById<Button>(R.id.btnStats).setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java))
        }
        findViewById<Button>(R.id.btnSettings).setOnClickListener { showSettings() }
        banner.setOnClickListener { onBannerClick() }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        // شبكة أمان: نعيد ضبط كل المنبهات (مثلًا بعد «إيقاف إجباري» أو منح إذن المنبهات).
        Scheduler.rescheduleAll(this)
        refresh()
        updateBanner()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updateBanner()
    }

    private fun edit(id: Long) {
        startActivity(Intent(this, EditTaskActivity::class.java).putExtra("id", id))
    }

    private fun refresh() {
        val today = LocalDate.now()
        val todayDay = today.toEpochDay()
        val all = db.all()
        val doneIds = db.doneIds(todayDay)
        val todayRows = all
            .filter { it.enabled && Logic.appliesOn(it, today) }
            .map { Row(it, it.id in doneIds, true) }
        val total = todayRows.size
        val done = todayRows.count { it.done }

        tvSummary.text = if (total == 0) "لا توجد مهام لليوم" else "أنجزت $done من $total مهام اليوم"
        progress.progress = if (total == 0) 0 else done * 100 / total
        findViewById<TextView>(R.id.tvDate).text = Ui.dayLabel(today)

        val rows: List<Row>
        val emptyText: String
        when (tab) {
            0 -> { rows = todayRows; emptyText = "لا توجد مهام لليوم.\nاضغط «مهمة جديدة» لإضافة أول مهمة." }
            1 -> { rows = todayRows.filter { !it.done }; emptyText = if (total == 0) "لا توجد مهام لليوم." else "أحسنت! أنجزت كل مهام اليوم 🎉" }
            2 -> { rows = todayRows.filter { it.done }; emptyText = "لم تُكمل أي مهمة بعد اليوم." }
            else -> {
                rows = all.map { Row(it, it.id in doneIds, Logic.appliesOn(it, today)) }
                emptyText = "لم تُضف أي مهمة بعد."
            }
        }
        adapter.set(rows)
        empty.text = emptyText
        empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE

        tabs.forEachIndexed { i, tv ->
            tv.isSelected = i == tab
            tv.setTextColor(if (i == tab) getColor(R.color.white) else textColor())
        }
    }

    private fun textColor(): Int = getColor(if (Prefs.dark(this)) R.color.d_text else R.color.l_text)

    private fun updateBanner() {
        val nm = getSystemService(NotificationManager::class.java)
        when {
            !nm.areNotificationsEnabled() -> {
                bannerAction = 1
                banner.text = "⚠ الإشعارات متوقفة فلن تصلك التذكيرات. اضغط هنا لتفعيلها."
            }
            !Scheduler.canExact(this) -> {
                bannerAction = 2
                banner.text = "⚠ فعّل إذن «المنبهات والتذكيرات» ليصلك التذكير في الدقيقة المحددة بالضبط. اضغط هنا."
            }
            else -> bannerAction = 0
        }
        banner.visibility = if (bannerAction == 0) View.GONE else View.VISIBLE
    }

    private fun onBannerClick() {
        try {
            when (bannerAction) {
                1 -> startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                )
                2 -> if (Build.VERSION.SDK_INT >= 31) startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
                )
            }
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "افتح إعدادات التطبيق من إعدادات الهاتف", Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmDelete(t: Task) {
        val d = AlertDialog.Builder(this)
            .setTitle("حذف المهمة")
            .setMessage("هل تريد حذف «${t.title}»؟")
            .setPositiveButton("حذف") { _, _ ->
                Actions.delete(this, t.id)
                refresh()
            }
            .setNegativeButton("إلغاء", null)
            .show()
        Ui.rtl(d)
    }

    private fun showSettings() {
        val dark = Prefs.dark(this)
        val items = arrayOf(
            if (dark) "التبديل إلى الوضع الفاتح" else "التبديل إلى الوضع الداكن",
            "مدة التأجيل: ${Prefs.snoozeMin(this)} دقائق",
            "السماح بالعمل في الخلفية (توفير البطارية)",
            "إعدادات الإشعارات",
            "إرسال إشعار تجريبي",
            "عن التطبيق والخصوصية"
        )
        val d = AlertDialog.Builder(this)
            .setTitle("الإعدادات")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        Prefs.setDark(this, !dark)
                        recreate()
                    }
                    1 -> pickSnooze()
                    2 -> openSafely(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    3 -> openSafely(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    )
                    4 -> {
                        val t = Task(
                            id = 0, title = "مهمة تجريبية", hour = 0, minute = 0,
                            repeat = Task.ONCE, createdDay = Logic.today()
                        )
                        Notifier.show(this, t, withActions = false)
                        Toast.makeText(this, "أُرسل إشعار تجريبي", Toast.LENGTH_SHORT).show()
                    }
                    5 -> about()
                }
            }
            .show()
        Ui.rtl(d)
    }

    private fun pickSnooze() {
        val options = intArrayOf(5, 10, 15, 30)
        val d = AlertDialog.Builder(this)
            .setTitle("مدة التأجيل")
            .setItems(options.map { "$it دقائق" }.toTypedArray()) { _, i ->
                Prefs.setSnoozeMin(this, options[i])
            }
            .show()
        Ui.rtl(d)
    }

    private fun openSafely(i: Intent) {
        try {
            startActivity(i)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "هذا الخيار غير متاح على جهازك", Toast.LENGTH_LONG).show()
        }
    }

    private fun about() {
        val d = AlertDialog.Builder(this)
            .setTitle("Task Reminder AI")
            .setMessage(
                "• جميع مهامك مخزّنة على هذا الجهاز فقط، دون حساب ودون اتصال بالإنترنت.\n" +
                    "• التطبيق لا يطلب إذن الإنترنت أصلًا.\n" +
                    "• التذكيرات تعمل والتطبيق مغلق، وتُعاد جدولتها بعد إعادة تشغيل الهاتف.\n" +
                    "• إن لم تصلك التذكيرات: فعّل الإشعارات وإذن «المنبهات والتذكيرات»، واسمح للتطبيق بالعمل في الخلفية، " +
                    "وفي بعض الهواتف (شاومي، هواوي، سامسونج، أوبو...) فعّل «التشغيل التلقائي» من إعدادات الهاتف."
            )
            .setPositiveButton("حسنًا", null)
            .show()
        Ui.rtl(d)
    }
}
