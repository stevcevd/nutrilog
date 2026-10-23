package com.example.nutrilog

import android.content.Context
import org.json.JSONArray
import java.time.LocalDateTime

/** Favourite entries live only on this phone. They are not part of backups. */
object Favorites {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("nutrilog", Context.MODE_PRIVATE)

    fun load(ctx: Context): List<String> = try {
        val a = JSONArray(prefs(ctx).getString("favorites", "[]"))
        List(a.length()) { a.getString(it) }
    } catch (e: Exception) { emptyList() }

    fun save(ctx: Context, list: List<String>) {
        prefs(ctx).edit().putString("favorites", JSONArray(list).toString()).apply()
    }

    /** Validates a line and returns it in canonical form. Throws with a readable message if invalid. */
    fun canonical(line: String): String = Format.entryLine(NutritionParser.parseLine(line))

    /** The favourite as a ready-to-save line stamped with the current date and time. */
    fun stamped(line: String): String? = try {
        Format.entryLine(NutritionParser.parseLine(line), LocalDateTime.now().withSecond(0).withNano(0))
    } catch (e: Exception) { null }

    fun label(line: String): String = try {
        val r = NutritionParser.parseLine(line)
        "${r.name} · ${Format.int(r.energy?.inKilocalories ?: 0.0)}"
    } catch (e: Exception) { line.take(24) }
}
