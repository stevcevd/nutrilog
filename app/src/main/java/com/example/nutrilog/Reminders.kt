package com.example.nutrilog

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class Reminder(val id: Int, val label: String, val hour: Int, val minute: Int, val enabled: Boolean)

object Reminders {
    private const val CHANNEL = "meal_reminders"

    fun defaults() = listOf(
        Reminder(1, "Breakfast", 8, 0, false),
        Reminder(2, "Lunch", 13, 0, false),
        Reminder(3, "Dinner", 19, 0, false)
    )

    fun parse(s: String?): List<Reminder> {
        if (s == null) return defaults()
        return try {
            val a = JSONArray(s)
            List(a.length()) { i ->
                val o = a.getJSONObject(i)
                Reminder(o.getInt("id"), o.getString("label"), o.getInt("h"), o.getInt("m"), o.getBoolean("on"))
            }
        } catch (e: Exception) { defaults() }
    }

    fun toJson(list: List<Reminder>): String {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("id", it.id).put("label", it.label).put("h", it.hour).put("m", it.minute).put("on", it.enabled))
        }
        return a.toString()
    }

    private fun pending(ctx: Context, id: Int): PendingIntent = PendingIntent.getBroadcast(
        ctx, id, Intent(ctx, ReminderReceiver::class.java).putExtra("id", id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun nextTrigger(hour: Int, minute: Int): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, hour); c.set(Calendar.MINUTE, minute)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        if (c.timeInMillis <= System.currentTimeMillis() + 5000) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    fun schedule(ctx: Context, r: Reminder) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger(r.hour, r.minute), pending(ctx, r.id))
    }

    private fun cancel(ctx: Context, id: Int) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pending(ctx, id))
    }

    fun apply(ctx: Context, old: List<Reminder>, new: List<Reminder>) {
        old.forEach { cancel(ctx, it.id) }
        new.filter { it.enabled }.forEach { schedule(ctx, it) }
    }

    fun scheduleAll(ctx: Context, list: List<Reminder>) {
        list.filter { it.enabled }.forEach { schedule(ctx, it) }
    }

    fun notify(ctx: Context, r: Reminder) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Meal reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle("Time to log: ${r.label}")
            .setContentText("Open NutriLog and paste your entry.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            if (NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
                NotificationManagerCompat.from(ctx).notify(1000 + r.id, n)
            }
        } catch (e: SecurityException) { }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("id", -1)
        val list = Reminders.parse(context.getSharedPreferences("nutrilog", Context.MODE_PRIVATE).getString("reminders", null))
        val r = list.firstOrNull { it.id == id && it.enabled } ?: return
        Reminders.notify(context, r)
        Reminders.schedule(context, r)   // next day
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val list = Reminders.parse(context.getSharedPreferences("nutrilog", Context.MODE_PRIVATE).getString("reminders", null))
        Reminders.scheduleAll(context, list)
    }
}
