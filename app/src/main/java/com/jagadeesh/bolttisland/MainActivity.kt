package com.jagadeesh.bolttisland

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("boltt_shift", Context.MODE_PRIVATE) }
    private var tab = "HOME"
    private val bg = Color.rgb(11, 16, 31)
    private val panel = Color.rgb(23, 31, 51)
    private val muted = Color.rgb(160, 174, 196)
    private val accent = Color.rgb(119, 107, 255)
    private val mint = Color.rgb(67, 214, 170)
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 44)
        }
        AlarmScheduler.rescheduleAll(this)
        render()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun shape(color: Int, radius: Int = 20) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun text(value: String, size: Float = 14f, color: Int = Color.WHITE, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }
    private fun vertical(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun horizontal(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
    }
    private fun gap(h: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }
    private fun card(): LinearLayout = vertical().apply {
        setPadding(dp(17), dp(16), dp(17), dp(16)); background = shape(panel, 22)
    }
    private fun button(label: String, primary: Boolean = false): TextView = text(label, 14f, Color.WHITE, true).apply {
        gravity = Gravity.CENTER; setPadding(dp(14), dp(13), dp(14), dp(13))
        background = shape(if (primary) accent else Color.rgb(42, 52, 75), 15)
        isClickable = true; isFocusable = true
    }
    private fun label(s: String) = text(s.uppercase(), 11f, Color.rgb(169, 158, 255), true)
    private fun addLine(parent: LinearLayout, child: View, height: Int = -2) {
        parent.addView(child, LinearLayout.LayoutParams(-1, height))
    }

    private fun render() {
        root = vertical().apply { setBackgroundColor(bg) }
        val header = vertical().apply { setPadding(dp(20), dp(20), dp(20), dp(12)) }
        header.addView(text("BOLTT SHIFT", 22f, Color.WHITE, true))
        header.addView(text("Your shift. Your time. Your rhythm.", 13f, muted))
        root.addView(header)
        val scroll = ScrollView(this).apply { clipToPadding = false; setPadding(dp(16), dp(4), dp(16), dp(14)) }
        content = vertical()
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = horizontal().apply {
            setPadding(dp(8), dp(8), dp(8), dp(10)); setBackgroundColor(Color.rgb(15, 22, 39))
        }
        listOf("HOME" to "Home", "ALARMS" to "Alarms", "SHIFT" to "Shift", "ROUTINE" to "Routine").forEach { (key, title) ->
            val b = text(title, 12f, if (tab == key) Color.WHITE else muted, tab == key).apply {
                gravity = Gravity.CENTER; setPadding(0, dp(13), 0, dp(13))
                if (tab == key) background = shape(Color.rgb(54, 53, 105), 14)
                setOnClickListener { tab = key; render() }
            }
            nav.addView(b, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        root.addView(nav)
        setContentView(root)
        when (tab) {
            "HOME" -> home()
            "ALARMS" -> alarmsPage()
            "SHIFT" -> shiftPage()
            "ROUTINE" -> routinePage()
        }
    }

    private fun shiftType() = prefs.getString("shift_type", "DAY") ?: "DAY"
    private fun shiftStart() = prefs.getString("shift_start", "07:00") ?: "07:00"
    private fun shiftEnd() = prefs.getString("shift_end", "19:00") ?: "19:00"
    private fun alarmTime(t: String): String = try {
        val p = t.split(":"); "%02d:%02d".format(p[0].toInt(), p[1].toInt())
    } catch (_: Exception) { t }

    private fun home() {
        val date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy"))
        content.addView(text(date, 13f, muted)); content.addView(gap(12))
        val hero = card()
        hero.addView(label("TODAY'S SHIFT"))
        hero.addView(gap(7))
        hero.addView(text(when (shiftType()) { "NIGHT" -> "Night shift 🌙"; "OFF" -> "Day off ☀️"; else -> "Day shift ☀️" }, 25f, Color.WHITE, true))
        hero.addView(gap(5))
        hero.addView(text(if (shiftType() == "OFF") "Take a proper break. Recharge." else "${shiftStart()}  →  ${shiftEnd()}", 16f, mint, true))
        hero.addView(gap(13))
        hero.addView(button("Edit shift plan", true).apply { setOnClickListener { tab = "SHIFT"; render() } })
        content.addView(hero); content.addView(gap(14))

        val next = AlarmStore.all(this).filter { it.enabled }.sortedBy { it.time }.firstOrNull()
        val alarmCard = card()
        alarmCard.addView(label("NEXT REMINDER"))
        alarmCard.addView(gap(7))
        alarmCard.addView(text(next?.title ?: "No alarms set yet", 20f, Color.WHITE, true))
        alarmCard.addView(text(next?.let { "${it.time} · Daily" } ?: "Add a shift or meal reminder to get started.", 13f, muted))
        alarmCard.addView(gap(12))
        alarmCard.addView(button("Manage alarms", false).apply { setOnClickListener { tab = "ALARMS"; render() } })
        content.addView(alarmCard); content.addView(gap(14))

        val quick = card()
        quick.addView(label("QUICK ACTIONS")); quick.addView(gap(10))
        quick.addView(button("＋  Add an alarm", true).apply { setOnClickListener { addAlarmDialog() } })
        quick.addView(gap(8))
        quick.addView(button("🍱  Set up meal reminders").apply { setOnClickListener { addMealSet() } })
        quick.addView(gap(8))
        quick.addView(button("＋  Add routine task").apply { setOnClickListener { addTaskDialog() } })
        content.addView(quick); content.addView(gap(14))
        content.addView(text("Small reminders. Smoother shifts.", 13f, muted))
    }

    private fun alarmsPage() {
        content.addView(text("Your reminders", 24f, Color.WHITE, true))
        content.addView(text("Daily alarms for shifts, meals and personal tasks.", 13f, muted))
        content.addView(gap(14))
        content.addView(button("＋  Create alarm", true).apply { setOnClickListener { addAlarmDialog() } })
        content.addView(gap(12))
        val all = AlarmStore.all(this).sortedBy { it.time }
        if (all.isEmpty()) content.addView(emptyCard("No reminders yet", "Create your first alarm or add the meal reminder set."))
        all.forEach { alarm ->
            val c = card()
            val row = horizontal()
            val info = vertical()
            info.addView(text(alarm.time, 25f, Color.WHITE, true))
            info.addView(text(alarm.title, 15f, Color.WHITE, true))
            info.addView(text(if (alarm.category == "FOOD") "MEAL REMINDER" else if (alarm.category == "SHIFT") "SHIFT REMINDER" else "PERSONAL", 10f, muted, true))
            row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            val toggle = Switch(this).apply {
                isChecked = alarm.enabled
                setOnCheckedChangeListener { _, checked -> AlarmStore.update(this@MainActivity, alarm.copy(enabled = checked)); render() }
            }
            row.addView(toggle)
            c.addView(row); c.addView(gap(10))
            val actions = horizontal()
            actions.addView(button("Edit").apply { setOnClickListener { editAlarmDialog(alarm) } }, LinearLayout.LayoutParams(0, dp(42), 1f))
            actions.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
            actions.addView(button("Delete").apply { setOnClickListener {
                AlarmStore.remove(this@MainActivity, alarm.id); render()
            } }, LinearLayout.LayoutParams(0, dp(42), 1f))
            c.addView(actions)
            content.addView(c); content.addView(gap(10))
        }
    }

    private fun shiftPage() {
        content.addView(text("Shift planner", 24f, Color.WHITE, true))
        content.addView(text("Set your current roster. Times are fully editable.", 13f, muted))
        content.addView(gap(14))
        val c = card()
        c.addView(label("SHIFT TYPE")); c.addView(gap(8))
        val types = horizontal()
        listOf("DAY", "NIGHT", "OFF").forEach { type ->
            types.addView(button(type, shiftType() == type).apply {
                setOnClickListener { prefs.edit().putString("shift_type", type).apply(); render() }
            }, LinearLayout.LayoutParams(0, dp(48), 1f))
            if (type != "OFF") types.addView(View(this), LinearLayout.LayoutParams(dp(7), 1))
        }
        c.addView(types); c.addView(gap(17))
        c.addView(label("SHIFT START")); c.addView(gap(6))
        c.addView(button("◷   ${shiftStart()}").apply { setOnClickListener { pickTime(shiftStart()) { prefs.edit().putString("shift_start", it).apply(); render() } } })
        c.addView(gap(12))
        c.addView(label("SHIFT END")); c.addView(gap(6))
        c.addView(button("◷   ${shiftEnd()}").apply { setOnClickListener { pickTime(shiftEnd()) { prefs.edit().putString("shift_end", it).apply(); render() } } })
        c.addView(gap(14))
        c.addView(button("Create shift start & end alarms", true).apply { setOnClickListener {
            upsertNamedAlarm("Shift starts", shiftStart(), "SHIFT")
            upsertNamedAlarm("Shift ends", shiftEnd(), "SHIFT")
            promptExactAlarmAccess(); render()
        } })
        content.addView(c); content.addView(gap(14))
        val info = card()
        info.addView(label("SHIFT ROUTINE"))
        info.addView(gap(6))
        info.addView(text("Use Alarms to add preparation, commute, break and return-home reminders. Use Routine to track daily tasks and overtime notes.", 13f, muted))
        content.addView(info)
    }

    private fun routinePage() {
        content.addView(text("Daily routine", 24f, Color.WHITE, true))
        content.addView(text("A simple checklist for workdays and rest days.", 13f, muted))
        content.addView(gap(14))
        content.addView(button("＋  Add task", true).apply { setOnClickListener { addTaskDialog() } })
        content.addView(gap(12))
        val tasks = loadTasks()
        if (tasks.length() == 0) content.addView(emptyCard("Your checklist is clear", "Add tasks like pack ID card, carry water, charge phone or prepare meals."))
        for (i in 0 until tasks.length()) {
            val item = tasks.optJSONObject(i) ?: continue
            val id = item.optLong("id")
            val c = card()
            val row = horizontal()
            val check = CheckBox(this).apply {
                text = item.optString("title"); setTextColor(Color.WHITE); isChecked = item.optBoolean("done")
                buttonTintList = android.content.res.ColorStateList.valueOf(mint)
                setOnCheckedChangeListener { _, checked -> updateTask(id, checked = checked); }
            }
            row.addView(check, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(text("⋮", 23f, muted).apply { setPadding(dp(12), dp(4), dp(12), dp(4)); setOnClickListener { taskOptions(id, item.optString("title")) } })
            c.addView(row)
            content.addView(c); content.addView(gap(8))
        }
        content.addView(gap(8))
        val note = card()
        note.addView(label("OVERTIME LOG"))
        val hours = prefs.getInt("ot_hours", 0)
        note.addView(gap(5))
        note.addView(text("$hours hours recorded", 22f, Color.WHITE, true))
        note.addView(gap(9))
        val row = horizontal()
        row.addView(button("＋ 1 hour").apply { setOnClickListener { prefs.edit().putInt("ot_hours", hours + 1).apply(); render() } }, LinearLayout.LayoutParams(0, dp(44), 1f))
        row.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
        row.addView(button("Reset").apply { setOnClickListener { prefs.edit().putInt("ot_hours", 0).apply(); render() } }, LinearLayout.LayoutParams(0, dp(44), 1f))
        note.addView(row); content.addView(note)
    }

    private fun emptyCard(title: String, desc: String): View {
        val c = card(); c.addView(text(title, 17f, Color.WHITE, true)); c.addView(text(desc, 13f, muted))
        return c
    }

    private fun addAlarmDialog() {
        val input = EditText(this).apply { hint = "Reminder name (e.g. Leave for work)"; setTextColor(Color.WHITE); setHintTextColor(muted) }
        AlertDialogBuilder(this).showInput("New reminder", input) {
            val title = input.text.toString().trim().ifBlank { "Daily reminder" }
            pickTime("07:00") { time -> AlarmStore.add(this, title, time, "CUSTOM"); promptExactAlarmAccess(); render() }
        }
    }
    private fun editAlarmDialog(a: ShiftAlarm) {
        val input = EditText(this).apply { setText(a.title); setTextColor(Color.WHITE); setHintTextColor(muted) }
        AlertDialogBuilder(this).showInput("Edit reminder", input) {
            val title = input.text.toString().trim().ifBlank { a.title }
            pickTime(a.time) { time -> AlarmStore.update(this, a.copy(title = title, time = time)); render() }
        }
    }
    private fun addMealSet() {
        val existing = AlarmStore.all(this).filter { it.category == "FOOD" }
        if (existing.isNotEmpty()) {
            Toast.makeText(this, "Meal reminders already exist. Edit them in Alarms.", Toast.LENGTH_LONG).show()
            tab = "ALARMS"; render(); return
        }
        AlarmStore.add(this, "Breakfast / meal 1", "07:30", "FOOD")
        AlarmStore.add(this, "Lunch / meal 2", "13:00", "FOOD")
        AlarmStore.add(this, "Dinner / meal 3", "20:30", "FOOD")
        promptExactAlarmAccess()\n        Toast.makeText(this, "3 meal reminders added. Adjust times for your shift.", Toast.LENGTH_LONG).show()
        tab = "ALARMS"; render()
    }
    private fun upsertNamedAlarm(title: String, time: String, category: String) {
        val old = AlarmStore.all(this).firstOrNull { it.title == title }
        if (old == null) AlarmStore.add(this, title, time, category)
        else AlarmStore.update(this, old.copy(time = time, enabled = true))
    }
    private fun promptExactAlarmAccess() {
        if (Build.VERSION.SDK_INT >= 31) {
            val manager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            if (!manager.canScheduleExactAlarms()) {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        android.net.Uri.parse("package:$packageName")))
                } catch (_: Exception) { }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AlarmScheduler.rescheduleAll(this)
    }

    private fun pickTime(current: String, done: (String) -> Unit) {
        val p = current.split(":")
        TimePickerDialog(this, { _, h, m -> done("%02d:%02d".format(h, m)) },
            p.getOrNull(0)?.toIntOrNull() ?: 7, p.getOrNull(1)?.toIntOrNull() ?: 0, true).show()
    }

    private fun loadTasks(): JSONArray = try { JSONArray(prefs.getString("tasks", "[]")) } catch (_: Exception) { JSONArray() }
    private fun saveTasks(a: JSONArray) { prefs.edit().putString("tasks", a.toString()).apply() }
    private fun addTaskDialog() {
        val input = EditText(this).apply { hint = "e.g. Pack ID card and water bottle"; setTextColor(Color.WHITE); setHintTextColor(muted) }
        AlertDialogBuilder(this).showInput("Add routine task", input) {
            val title = input.text.toString().trim()
            if (title.isNotEmpty()) {
                val a = loadTasks(); a.put(JSONObject().put("id", System.currentTimeMillis()).put("title", title).put("done", false)); saveTasks(a); render()
            }
        }
    }
    private fun updateTask(id: Long, checked: Boolean) {
        val a = loadTasks()
        for (i in 0 until a.length()) { val o = a.optJSONObject(i) ?: continue; if (o.optLong("id") == id) o.put("done", checked) }
        saveTasks(a)
    }
    private fun taskOptions(id: Long, title: String) {
        android.app.AlertDialog.Builder(this).setTitle(title).setItems(arrayOf("Rename", "Delete")) { _, which ->
            if (which == 0) {
                val input = EditText(this).apply { setText(title) }
                AlertDialogBuilder(this).showInput("Rename task", input) {
                    val a = loadTasks(); for (i in 0 until a.length()) { val o = a.optJSONObject(i) ?: continue; if (o.optLong("id") == id) o.put("title", input.text.toString()) }; saveTasks(a); render()
                }
            } else {
                val old = loadTasks(); val fresh = JSONArray()
                for (i in 0 until old.length()) { val o = old.optJSONObject(i) ?: continue; if (o.optLong("id") != id) fresh.put(o) }
                saveTasks(fresh); render()
            }
        }.show()
    }

    private class AlertDialogBuilder(private val activity: Activity) {
        fun showInput(title: String, input: EditText, action: () -> Unit) {
            android.app.AlertDialog.Builder(activity).setTitle(title).setView(input)
                .setNegativeButton("Cancel", null).setPositiveButton("Continue") { _, _ -> action() }.show()
        }
    }
}
