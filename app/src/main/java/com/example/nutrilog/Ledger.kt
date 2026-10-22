package com.example.nutrilog

import android.content.Context
import androidx.health.connect.client.records.NutritionRecord
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate

data class DayLedger(val kcal: Double, val protein: Double, val meals: Set<String>, val lastMealAt: Long)

/**
 * A tiny local summary of each day, kept in step with Health Connect whenever the app is open.
 * Reminders use it to decide whether to stay quiet, because Health Connect can't be read in the background.
 */
object Ledger {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("nutrilog", Context.MODE_PRIVATE)

    fun get(ctx: Context, day: LocalDate): DayLedger {
        val s = prefs(ctx).getString("ledger_$day", null) ?: return DayLedger(0.0, 0.0, emptySet(), 0L)
        return try {
            val o = JSONObject(s)
            val a = o.getJSONArray("meals")
            DayLedger(o.getDouble("kcal"), o.getDouble("p"), List(a.length()) { a.getString(it) }.toSet(), o.getLong("last"))
        } catch (e: Exception) {
            DayLedger(0.0, 0.0, emptySet(), 0L)
        }
    }

    fun syncDay(ctx: Context, day: LocalDate, records: List<NutritionRecord>) {
        val now = Instant.now()
        val o = JSONObject()
            .put("kcal", records.sumOf { it.energy?.inKilocalories ?: 0.0 })
            .put("p", records.sumOf { it.protein?.inGrams ?: 0.0 })
            .put("meals", JSONArray(records.map { Format.mealName(it) }.distinct()))
            .put("last", records.filter { !it.startTime.isAfter(now) }.maxOfOrNull { it.startTime.toEpochMilli() } ?: 0L)
        val p = prefs(ctx)
        val ed = p.edit().putString("ledger_$day", o.toString())
        val cutoff = "ledger_" + day.minusDays(10)
        p.all.keys.filter { it.startsWith("ledger_") && it < cutoff }.forEach { ed.remove(it) }
        ed.apply()
    }
}
