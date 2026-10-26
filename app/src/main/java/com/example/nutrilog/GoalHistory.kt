package com.example.nutrilog

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** The goals (and cutting/maintaining/bulking choice) that took effect on a given day. */
data class GoalEntry(val from: LocalDate, val goals: Map<String, Double>, val type: Int)

/**
 * Remembers which goals applied when, so changing goals later never re-grades past days.
 * The first entry (created when this feature arrives) covers all earlier days.
 */
object GoalHistory {
    private val BEGINNING: LocalDate = LocalDate.of(2000, 1, 1)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("nutrilog", Context.MODE_PRIVATE)

    fun load(ctx: Context): List<GoalEntry> {
        val s = prefs(ctx).getString("goal_history", null) ?: return emptyList()
        return try { parse(JSONArray(s)) } catch (e: Exception) { emptyList() }
    }

    private fun parse(a: JSONArray): List<GoalEntry> = List(a.length()) { i ->
        val o = a.getJSONObject(i)
        val g = o.getJSONObject("goals")
        val m = HashMap<String, Double>()
        g.keys().forEach { k -> m[k] = g.getDouble(k) }
        GoalEntry(LocalDate.parse(o.getString("from")), m, o.getInt("type"))
    }.sortedBy { it.from }

    private fun toJson(list: List<GoalEntry>): JSONArray {
        val a = JSONArray()
        list.forEach { e ->
            val g = JSONObject()
            e.goals.forEach { (k, v) -> g.put(k, v) }
            a.put(JSONObject().put("from", e.from.toString()).put("goals", g).put("type", e.type))
        }
        return a
    }

    private fun save(ctx: Context, list: List<GoalEntry>) {
        prefs(ctx).edit().putString("goal_history", toJson(list).toString()).apply()
    }

    fun export(ctx: Context): JSONArray = toJson(load(ctx))

    fun importArray(ctx: Context, a: JSONArray) {
        prefs(ctx).edit().putString("goal_history", a.toString()).apply()
    }

    fun ensureSeed(ctx: Context, goals: Map<String, Double>, type: Int) {
        if (load(ctx).isEmpty()) save(ctx, listOf(GoalEntry(BEGINNING, goals, type)))
    }

    /** Call whenever goals or the goal type change. Does nothing if nothing changed. */
    fun record(ctx: Context, goals: Map<String, Double>, type: Int) {
        val list = load(ctx).toMutableList()
        if (list.isEmpty()) {
            save(ctx, listOf(GoalEntry(BEGINNING, goals, type)))
            return
        }
        val last = list.last()
        if (last.goals == goals && last.type == type) return
        val today = LocalDate.now()
        if (last.from == today) list[list.lastIndex] = GoalEntry(today, goals, type) else list += GoalEntry(today, goals, type)
        save(ctx, list)
    }

    fun at(history: List<GoalEntry>, date: LocalDate, fallbackGoals: Map<String, Double>, fallbackType: Int): GoalEntry =
        history.lastOrNull { !it.from.isAfter(date) } ?: history.firstOrNull() ?: GoalEntry(BEGINNING, fallbackGoals, fallbackType)
}
