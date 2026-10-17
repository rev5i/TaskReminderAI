package com.taskreminder.ai

import android.content.Context
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.Switch
import android.widget.TextView

data class Row(val task: Task, val done: Boolean, val appliesToday: Boolean)

class TaskAdapter(
    private val ctx: Context,
    private val onDone: (Task, Boolean) -> Unit,
    private val onEnabled: (Task, Boolean) -> Unit
) : BaseAdapter() {

    private var rows: List<Row> = emptyList()

    fun set(r: List<Row>) {
        rows = r
        notifyDataSetChanged()
    }

    override fun getCount() = rows.size
    override fun getItem(position: Int): Row = rows[position]
    override fun getItemId(position: Int) = rows[position].task.id

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val v = convertView ?: LayoutInflater.from(ctx).inflate(R.layout.item_task, parent, false)
        val r = rows[position]
        val t = r.task

        val check = v.findViewById<CheckBox>(R.id.check)
        val title = v.findViewById<TextView>(R.id.title)
        val sub = v.findViewById<TextView>(R.id.sub)
        val sw = v.findViewById<Switch>(R.id.sw)

        title.text = t.title
        title.paintFlags = if (r.done) title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        else title.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()

        sub.text = Ui.describe(t) + if (!t.enabled) " · متوقف مؤقتًا" else ""
        v.findViewById<View>(R.id.card).alpha = if (t.enabled) 1f else 0.55f

        check.setOnCheckedChangeListener(null)
        check.isChecked = r.done
        check.visibility = if (r.appliesToday) View.VISIBLE else View.INVISIBLE
        check.setOnCheckedChangeListener { _, checked -> onDone(t, checked) }

        sw.setOnCheckedChangeListener(null)
        sw.isChecked = t.enabled
        sw.setOnCheckedChangeListener { _, checked -> onEnabled(t, checked) }
        return v
    }
}
