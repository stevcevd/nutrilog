package com.example.nutrilog

import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object NutritionParser {

    private val header = Regex(
        """^(\d{4}-\d{2}-\d{2})\s+(\d{1,2}:\d{2})\s+(Breakfast|Lunch|Dinner|Snack)\s*\((.*)\)\s*:\s*(.*)$""",
        RegexOption.IGNORE_CASE
    )
    private val kcalRe = Regex("""^([0-9]+(?:[.,][0-9]+)?)\s*kcal$""", RegexOption.IGNORE_CASE)
    private val fieldRe = Regex(
        """^([A-Za-z0-9_]+)\s+([0-9]+(?:[.,][0-9]+)?)\s*(mcg|µg|ug|mg|g)?$""",
        RegexOption.IGNORE_CASE
    )
    private val timeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd H:mm")

    // label (lowercase) -> default unit if none written
    private val defaultUnit = mapOf(
        "p" to "g", "c" to "g", "f" to "g", "satf" to "g", "monof" to "g", "polyf" to "g",
        "transf" to "g", "fiber" to "g", "sugar" to "g", "chol" to "mg", "na" to "mg",
        "k" to "mg", "ca" to "mg", "fe" to "mg", "mg" to "mg", "p_" to "mg", "zn" to "mg",
        "cu" to "mg", "mn" to "mg", "se" to "mcg", "cr" to "mcg", "mo" to "mcg", "i" to "mcg",
        "cl" to "mg", "caff" to "mg", "vita" to "mcg", "vitb6" to "mg", "vitb12" to "mcg",
        "vitc" to "mg", "vitd" to "mcg", "vite" to "mg", "vitk" to "mcg", "biotin" to "mcg",
        "folate" to "mcg", "folicacid" to "mcg", "niacin" to "mg", "pantacid" to "mg",
        "ribo" to "mg", "thiam" to "mg"
    )

    private fun toGrams(v: Double, unit: String): Double = when (unit.lowercase()) {
        "mg" -> v / 1_000.0
        "mcg", "µg", "ug" -> v / 1_000_000.0
        else -> v
    }

    private fun num(s: String) = s.replace(',', '.').toDouble()

    /** Parses one line. Throws IllegalArgumentException with a readable message. */
    fun parseLine(line: String): NutritionRecord {
        val h = header.matchEntire(line.trim())
            ?: throw IllegalArgumentException("Expected: YYYY-MM-DD HH:MM Meal (Name): N kcal | ...")

        val (date, time, mealStr, name, rest) = h.destructured

        val start = try {
            LocalDateTime.parse("$date $time", timeFmt)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid date/time: $date $time")
        }
        val zone = ZoneId.systemDefault()
        val startInstant = start.atZone(zone).toInstant()
        val endInstant = startInstant.plusSeconds(60)
        val offset = zone.rules.getOffset(startInstant)

        val meal = when (mealStr.lowercase()) {
            "breakfast" -> MealType.MEAL_TYPE_BREAKFAST
            "lunch" -> MealType.MEAL_TYPE_LUNCH
            "dinner" -> MealType.MEAL_TYPE_DINNER
            else -> MealType.MEAL_TYPE_SNACK
        }

        val parts = rest.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) throw IllegalArgumentException("Missing kcal")
        val kcalMatch = kcalRe.matchEntire(parts[0])
            ?: throw IllegalArgumentException("First value must be calories, e.g. 1446 kcal")
        val kcal = num(kcalMatch.groupValues[1])

        val grams = HashMap<String, Double>()
        for (p in parts.drop(1)) {
            val m = fieldRe.matchEntire(p)
                ?: throw IllegalArgumentException("Can't read \"$p\" (use e.g. Fiber 4.2g)")
            val key = m.groupValues[1].lowercase()
            val unit = m.groupValues[3].ifEmpty { defaultUnit[key] ?: "g" }
            if (key !in defaultUnit) throw IllegalArgumentException("Unknown label \"${m.groupValues[1]}\"")
            grams[key] = toGrams(num(m.groupValues[2]), unit)
        }

        fun m(k: String): Mass? = grams[k]?.takeIf { it > 0.0 }?.let { Mass.grams(it) }

        return NutritionRecord(
            startTime = startInstant,
            startZoneOffset = offset,
            endTime = endInstant,
            endZoneOffset = offset,
            name = name.trim(),
            mealType = meal,
            energy = Energy.kilocalories(kcal),
            protein = m("p"),
            totalCarbohydrate = m("c"),
            totalFat = m("f"),
            saturatedFat = m("satf"),
            monounsaturatedFat = m("monof"),
            polyunsaturatedFat = m("polyf"),
            transFat = m("transf"),
            dietaryFiber = m("fiber"),
            sugar = m("sugar"),
            cholesterol = m("chol"),
            sodium = m("na"),
            potassium = m("k"),
            calcium = m("ca"),
            iron = m("fe"),
            magnesium = m("mg"),
            phosphorus = m("p_"),
            zinc = m("zn"),
            copper = m("cu"),
            manganese = m("mn"),
            selenium = m("se"),
            chromium = m("cr"),
            molybdenum = m("mo"),
            iodine = m("i"),
            chloride = m("cl"),
            caffeine = m("caff"),
            vitaminA = m("vita"),
            vitaminB6 = m("vitb6"),
            vitaminB12 = m("vitb12"),
            vitaminC = m("vitc"),
            vitaminD = m("vitd"),
            vitaminE = m("vite"),
            vitaminK = m("vitk"),
            biotin = m("biotin"),
            folate = m("folate"),
            folicAcid = m("folicacid"),
            niacin = m("niacin"),
            pantothenicAcid = m("pantacid"),
            riboflavin = m("ribo"),
            thiamin = m("thiam")
        )
    }
}
