package com.taskreminder.ai

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.GridLayout
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class EditTaskActivity : Activity() {

    private var taskId = -1L
    private var existing: Task? = null
    private var hour = 8
    private var minute = 0
    private var onceDay = LocalDate.now().toEpochDay()
    private val dayBoxes = arrayOfNulls<CheckBox>(7)

    private lateinit var etTitle: EditText
    private lateinit var btnTime: Button
    private lateinit var btnDate: Button
    private lateinit var rg: RadioGroup
    private lateinit var grid: GridLayout
    private lateinit var swEnabled: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Ui.theme(this))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)
        Ui.rtl(this)
        Ui.applyInsets(findViewById(R.id.root))
        Ui.systemBars(this)

        etTitle = findViewById(R.id.etTitle)
        btnTime = findViewById(R.id.btnTime)
        btnDate = findViewById(R.id.btnDate)
        rg = findViewById(R.id.rg)
        grid = findViewById(R.id.gridDays)
        swEnabled = findViewById(R.id.swEnabled)

        for (idx in Ui.DISPLAY_ORDER) {
            val cb = CheckBox(this).apply {
                text = Ui.DAY_NAMES[idx]
                setTextColor(getColor(if (Prefs.dark(this@EditTaskActivity)) R.color.d_text else R.color.l_text))
            }
            dayBoxes[idx] = cb
            val lp = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).apply { width = 0 }
            grid.addView(cb, lp)
        }

        taskId = intent.getLongExtra("id", -1L)
        existing = if (taskId >= 0) TaskDb.get(this).get(taskId) else null

        val ex = existing
        if (ex != null) {
            findViewById<TextView>(R.id.screenTitle).text = "تعديل المهمة"
            etTitle.setText(ex.title)
            hour = ex.hour
            minute = ex.minute
            onceDay = ex.onceDay
            swEnabled.isChecked = ex.enabled
            when (ex.repeat) {
                Task.ONCE -> rg.check(R.id.rbOnce)
                Task.DAILY -> rg.check(R.id.rbDaily)
                else -> rg.check(R.id.rbDays)
            }
            for (i in 0..6) dayBoxes[i]?.isChecked = ((ex.daysMask shr i) and 1) == 1
            findViewById<Button>(R.id.btnDelete).visibility = View.VISIBLE
        } else {
            // وقت افتراضي: بعد ساعة تقريبًا من الآن
            val next = LocalTime.now().plusHours(1)
            hour = next.hour
            minute = 0
        }

        rg.setOnCheckedChangeListener { _, _ -> updateVisibility() }
        btnTime.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                hour = h
                minute = m
                updateTexts()
            }, hour, minute, false).show()
        }
        btnDate.setOnClickListener {
            val d = LocalDate.ofEpochDay(onceDay)
            val dlg = DatePickerDialog(this, { _, y, m, day ->
                onceDay = LocalDate.of(y, m + 1, day).toEpochDay()
                updateTexts()
            }, d.year, d.monthValue - 1, d.dayOfMonth)
            dlg.datePicker.minDate = System.currentTimeMillis() - 1000
            dlg.show()
        }
        findViewById<Button>(R.id.btnSave).setOnClickListener { save() }
        findViewById<Button>(R.id.btnDelete).setOnClickListener { confirmDelete() }

        updateVisibility()
        updateTexts()
    }

    private fun updateVisibility() {
        btnDate.visibility = if (rg.checkedRadioButtonId == R.id.rbOnce) View.VISIBLE else View.GONE
        grid.visibility = if (rg.checkedRadioButtonId == R.id.rbDays) View.VISIBLE else View.GONE
    }

    private fun updateTexts() {
        btnTime.text = Ui.fmtTime(hour, minute)
        btnDate.text = "التاريخ: " + Ui.fmtDate(onceDay)
    }

    private fun save() {
        val title = etTitle.text.toString().trim()
        if (title.isEmpty()) {
            etTitle.error = "اكتب اسم المهمة"
            etTitle.requestFocus()
            return
        }
        val repeat = when (rg.checkedRadioButtonId) {
            R.id.rbOnce -> Task.ONCE
            R.id.rbDays -> Task.DAYS
            else -> Task.DAILY
        }
        var mask = 0
        for (i in 0..6) if (dayBoxes[i]?.isChecked == true) mask = mask or (1 shl i)
        if (repeat == Task.DAYS && mask == 0) {
            Toast.makeText(this, "اختر يومًا واحدًا على الأقل", Toast.LENGTH_SHORT).show()
            return
        }
        val enabled = swEnabled.isChecked
        if (repeat == Task.ONCE && enabled) {
            val at = LocalDateTime.of(LocalDate.ofEpochDay(onceDay), LocalTime.of(hour, minute))
            if (!at.isAfter(LocalDateTime.now())) {
                Toast.makeText(this, "هذا الوقت مضى. اختر وقتًا في المستقبل", Toast.LENGTH_LONG).show()
                return
            }
        }

        val db = TaskDb.get(this)
        val ex = existing
        val t = Task(
            id = ex?.id ?: 0,
            title = title,
            hour = hour,
            minute = minute,
            repeat = repeat,
            daysMask = if (repeat == Task.DAYS) mask else 0,
            onceDay = onceDay,
            enabled = enabled,
            snoozeUntil = 0,
            createdDay = ex?.createdDay ?: Logic.today()
        )
        val id = if (ex == null) db.insert(t) else { db.update(t); ex.id }
        Notifier.cancel(this, id)
        Scheduler.reschedule(this, id)
        Toast.makeText(this, "تم الحفظ ✓", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun confirmDelete() {
        val ex = existing ?: return
        val d = AlertDialog.Builder(this)
            .setTitle("حذف المهمة")
            .setMessage("هل تريد حذف «${ex.title}» نهائيًا؟")
            .setPositiveButton("حذف") { _, _ ->
                Actions.delete(this, ex.id)
                finish()
            }
            .setNegativeButton("إلغاء", null)
            .show()
        Ui.rtl(d)
    }
}
