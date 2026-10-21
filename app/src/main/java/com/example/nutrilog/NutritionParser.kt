package com.example.nutrilog

import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.metadata.Metadata
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
    private val goalsHeader = Regex(
        """^(?:\d{4}-\d{2}-\d{2}\s+)?Daily Targets\s*:\s*(.*)$""",
        RegexOption.IGNORE_CASE
    )
    private val kcalRe = Regex("""^([0-9]+(?:[.,][0-9]+)?)\s*kcal$""", RegexOption.IGNORE_CASE)
    private val fieldRe = Regex(
        """^([A-Za-z0-9_]+)\s+([0-9]+(?:[.,][0-9]+)?)\s*(mcg|µg|ug|mg|g)?$""",
        RegexOption.IGNORE_CASE
    )
    private val timeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd H:mm")

    private fun num(s: String) = s.replace(',', '.').toDouble()

    /** "1446 kcal | P 46g | ..." -> map key -> value in each nutrient's display unit. */
    private fun parseValues(rest: String): Map<String, Double> {
        val parts = rest.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) throw IllegalArgumentException("Missing kcal")
        val kcalMatch = kcalRe.matchEntire(parts[0])
            ?: throw IllegalArgumentException("First value must be calories, e.g. 1446 kcal")
        val out = HashMap<String, Double>()
        out["kcal"] = num(kcalMatch.groupValues[1])
        for (p in parts.drop(1)) {
            val m = fieldRe.matchEntire(p)
                ?: throw IllegalArgumentException("Can't read \"$p\" (use e.g. Fiber 4.2g)")
            val key = m.groupValues[1].lowercase()
            val nutrient = Nutrients.byKey[key]
            if (nutrient == null || key == "kcal") {
                throw IllegalArgumentException("Unknown label \"${m.groupValues[1]}\"")
            }
            val unit = m.groupValues[3].ifEmpty { nutrient.unit }
            out[key] = Units.convert(num(m.groupValues[2]), unit, nutrient.unit)
        }
        return out
    }

    /** Parses "YYYY-MM-DD Daily Targets: ..." (date optional). */
    fun parseGoals(line: String): Map<String, Double> {
        val h = goalsHeader.matchEntire(line.trim())
            ?: throw IllegalArgumentException("Expected: YYYY-MM-DD Daily Targets: N kcal | P 200g | ...")
        return parseValues(h.groupValues[1])
    }

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

        val mealLabel = when (mealStr.lowercase()) {
            "breakfast" -> "Breakfast"
            "lunch" -> "Lunch"
            "dinner" -> "Dinner"
            else -> "Snack"
        }
        val meal = when (mealLabel) {
            "Breakfast" -> MealType.MEAL_TYPE_BREAKFAST
            "Lunch" -> MealType.MEAL_TYPE_LUNCH
            "Dinner" -> MealType.MEAL_TYPE_DINNER
            else -> MealType.MEAL_TYPE_SNACK
        }

        val values = parseValues(rest)

        fun m(k: String): Mass? {
            val v = values[k] ?: return null
            if (v <= 0.0) return null
            val unit = Nutrients.byKey.getValue(k).unit
            return Mass.grams(v * Units.factor(unit))
        }

        val key = Format.keyOf(start, mealLabel, name, values.getValue("kcal"))

        return NutritionRecord(
            metadata = Metadata(clientRecordId = Format.hash(key)),
            startTime = startInstant,
            startZoneOffset = offset,
            endTime = endInstant,
            endZoneOffset = offset,
            name = name.trim(),
            mealType = meal,
            energy = Energy.kilocalories(values.getValue("kcal")),
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
