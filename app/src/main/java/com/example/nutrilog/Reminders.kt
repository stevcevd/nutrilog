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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
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

    /** Which meal a reminder is about, judged from its name. */
    fun mealOf(label: String): String? {
        val l = label.lowercase()
        return when {
            "breakfast" in l -> "Breakfast"
            "lunch" in l -> "Lunch"
            "dinner" in l || "supper" in l -> "Dinner"
            "snack" in l -> "Snack"
            else -> null
        }
    }

    fun template(label: String): String {
        val meal = mealOf(label) ?: "Snack"
        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        return "$now $meal (Name): 0 kcal | P 0g | C 0g | F 0g | Fiber 0g | Sugar 0g | Na 0mg"
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("nutrilog", Context.MODE_PRIVATE)

    private fun pending(ctx: Context, id: Int, snoozed: Boolean): PendingIntent = PendingIntent.getBroadcast(
        ctx, if (snoozed) id + 5000 else id,
        Intent(ctx, ReminderReceiver::class.java).putExtra("id", id).putExtra("snoozed", snoozed),
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
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger(r.hour, r.minute), pending(ctx, r.id, false))
    }

    fun snooze(ctx: Context, id: Int, minutes: Int) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + minutes * 60_000L, pending(ctx, id, true))
    }

    private fun cancel(ctx: Context, id: Int) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.cancel(pending(ctx, id, false))
        am.cancel(pending(ctx, id, true))
    }

    fun apply(ctx: Context, old: List<Reminder>, new: List<Reminder>) {
        old.forEach { cancel(ctx, it.id) }
        new.filter { it.enabled }.forEach { schedule(ctx, it) }
    }

    fun scheduleAll(ctx: Context, list: List<Reminder>) {
        list.filter { it.enabled }.forEach { schedule(ctx, it) }
    }

    /** Returns why a reminder should stay quiet, or null if it should show. Uses the local daily ledger. */
    fun skipReason(ctx: Context, r: Reminder): String? {
        val p = prefs(ctx)
        if (!p.getBoolean("smart", true)) return null
        val led = Ledger.get(ctx, LocalDate.now())
        val meal = mealOf(r.label)
        if (meal != null && meal != "Snack" && meal in led.meals) return "$meal already logged"
        val gapMs = p.getInt("smart_gap", 60) * 60_000L
        val since = System.currentTimeMillis() - led.lastMealAt
        if (led.lastMealAt > 0 && since in 0..gapMs) return "ate recently"
        if (p.getBoolean("smart_goal", true)) {
            val goal = GoalsStore.load(ctx)["kcal"] ?: 0.0
            if (goal > 0 && led.kcal >= goal * 0.95) return "calorie goal reached"
        }
        return null
    }

    fun notify(ctx: Context, r: Reminder) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Meal reminders", NotificationManager.IMPORTANCE_DEFAULT))

        val goals = GoalsStore.load(ctx)
        val led = Ledger.get(ctx, LocalDate.now())
        val kcalGoal = goals["kcal"] ?: 0.0
        val text = if (kcalGoal > 0) {
            "${Format.int(maxOf(kcalGoal - led.kcal, 0.0))} kcal and ${Format.int(maxOf((goals["p"] ?: 0.0) - led.protein, 0.0))} g protein left today."
        } else {
            "Open NutriLog and log your entry."
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val openIntent = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("open_tab", 1)
            .putExtra("meal", r.label)
        val open = PendingIntent.getActivity(ctx, r.id, openIntent, flags)
        val snooze = PendingIntent.getBroadcast(ctx, r.id + 9000, Intent(ctx, SnoozeReceiver::class.java).putExtra("id", r.id), flags)

        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle("Time to log: ${r.label}")
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_plus, "Log now", open)
            .addAction(R.drawable.ic_timer, "Snooze 30 min", snooze)
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
        val snoozed = intent.getBooleanExtra("snoozed", false)
        val list = Reminders.parse(context.getSharedPreferences("nutrilog", Context.MODE_PRIVATE).getString("reminders", null))
        val r = list.firstOrNull { it.id == id } ?: return
        if (!snoozed && !r.enabled) return
        if (Reminders.skipReason(context, r) == null) Reminders.notify(context, r)
        if (!snoozed) Reminders.schedule(context, r)   // next day
    }
}

class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("id", -1)
        NotificationManagerCompat.from(context).cancel(1000 + id)
        Reminders.snooze(context, id, 30)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val list = Reminders.parse(context.getSharedPreferences("nutrilog", Context.MODE_PRIVATE).getString("reminders", null))
        Reminders.scheduleAll(context, list)
    }
}
