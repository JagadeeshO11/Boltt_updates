package com.jagadeesh.bolttisland

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class ShiftAlarm(val id: Int, val title: String, val time: String, val category: String, val enabled: Boolean = true)

object AlarmStore {
    private const val PREFS = "boltt_shift"
    private const val KEY = "alarms"
    fun all(context: Context): List<ShiftAlarm> {
        val raw = context.getSharedPreferences(PREFS, 0).getString(KEY, "[]") ?: "[]"
        return try {
            val a = JSONArray(raw); (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                ShiftAlarm(o.optInt("id"), o.optString("title"), o.optString("time"), o.optString("category", "CUSTOM"), o.optBoolean("enabled", true))
            }
        } catch (_: Exception) { emptyList() }
    }
    private fun save(context: Context, items: List<ShiftAlarm>) {
        val a = JSONArray()
        items.forEach { a.put(JSONObject().put("id", it.id).put("title", it.title).put("time", it.time).put("category", it.category).put("enabled", it.enabled)) }
        context.getSharedPreferences(PREFS, 0).edit().putString(KEY, a.toString()).apply()
    }
    fun add(context: Context, title: String, time: String, category: String) {
        val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt().coerceAtLeast(1)
        val item = ShiftAlarm(id, title, time, category)
        save(context, all(context) + item); AlarmScheduler.schedule(context, item)
    }
    fun update(context: Context, item: ShiftAlarm) {
        save(context, all(context).map { if (it.id == item.id) item else it })
        if (item.enabled) AlarmScheduler.schedule(context, item) else AlarmScheduler.cancel(context, item.id)
    }
    fun remove(context: Context, id: Int) {
        save(context, all(context).filterNot { it.id == id }); AlarmScheduler.cancel(context, id)
    }
    fun byId(context: Context, id: Int) = all(context).firstOrNull { it.id == id }
}

object AlarmScheduler {
    fun schedule(context: Context, alarm: ShiftAlarm) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).putExtra("alarm_id", alarm.id)
        val pi = PendingIntent.getBroadcast(context, alarm.id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val parts = alarm.time.split(":")
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, parts.getOrNull(0)?.toIntOrNull() ?: 7)
            set(Calendar.MINUTE, parts.getOrNull(1)?.toIntOrNull() ?: 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
        }
        try {
            if (Build.VERSION.SDK_INT >= 23) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pi)
            else manager.setExact(AlarmManager.RTC_WAKEUP, target.timeInMillis, pi)
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pi)
        }
    }
    fun cancel(context: Context, id: Int) {
        val pi = PendingIntent.getBroadcast(context, id, Intent(context, AlarmReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi)
    }
    fun rescheduleAll(context: Context) { AlarmStore.all(context).filter { it.enabled }.forEach { schedule(context, it) } }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("alarm_id", -1)
        val alarm = AlarmStore.byId(context, id) ?: return
        if (!alarm.enabled) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = "boltt_shift_alarms"
        if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(channel) == null) {
            manager.createNotificationChannel(NotificationChannel(channel, "Boltt Shift reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Shift, food and routine reminders"
                enableVibration(true)
            })
        }
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(alarm.title)
            .setContentText(if (alarm.category == "FOOD") "Meal time. Take a proper food break." else "Your scheduled reminder is due.")
            .setContentIntent(open).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM).build()
        manager.notify(alarm.id, notification)
        AlarmScheduler.schedule(context, alarm)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED || intent?.action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) {
            AlarmScheduler.rescheduleAll(context)
        }
    }
}
