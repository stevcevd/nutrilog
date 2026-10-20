package com.example.nutrilog

import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.units.Mass
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Nutrient(
    val tag: String,
    val name: String,
    val unit: String,
    val group: String,
    val read: (NutritionRecord) -> Double?
) {
    val key: String get() = tag.lowercase()
}

private fun mass(tag: String, name: String, unit: String, group: String, sel: (NutritionRecord) -> Mass?) =
    Nutrient(tag, name, unit, group) { r ->
        sel(r)?.let {
            when (unit) {
                "g" -> it.inGrams
                "mg" -> it.inMilligrams
                else -> it.inMicrograms
            }
        }
    }

object Units {
    fun factor(unit: String): Double = when (unit.lowercase()) {
        "mg" -> 1e-3
        "mcg", "µg", "ug" -> 1e-6
        else -> 1.0
    }
    fun convert(v: Double, from: String, to: String) = v * factor(from) / factor(to)
}

object Nutrients {
    private const val M = "Energy & macros"
    private const val F = "Fats"
    private const val MIN = "Minerals"
    private const val V = "Vitamins"
    private const val O = "Other"

    val all: List<Nutrient> = listOf(
        Nutrient("kcal", "Calories", "kcal", M) { it.energy?.inKilocalories },
        mass("P", "Protein", "g", M) { it.protein },
        mass("C", "Carbohydrates", "g", M) { it.totalCarbohydrate },
        mass("F", "Total fat", "g", M) { it.totalFat },
        mass("Fiber", "Fiber", "g", M) { it.dietaryFiber },
        mass("Sugar", "Sugar", "g", M) { it.sugar },
        mass("SatF", "Saturated fat", "g", F) { it.saturatedFat },
        mass("MonoF", "Monounsaturated fat", "g", F) { it.monounsaturatedFat },
        mass("PolyF", "Polyunsaturated fat", "g", F) { it.polyunsaturatedFat },
        mass("TransF", "Trans fat", "g", F) { it.transFat },
        mass("Chol", "Cholesterol", "mg", F) { it.cholesterol },
        mass("Na", "Sodium", "mg", MIN) { it.sodium },
        mass("K", "Potassium", "mg", MIN) { it.potassium },
        mass("Ca", "Calcium", "mg", MIN) { it.calcium },
        mass("Fe", "Iron", "mg", MIN) { it.iron },
        mass("Mg", "Magnesium", "mg", MIN) { it.magnesium },
        mass("P_", "Phosphorus", "mg", MIN) { it.phosphorus },
        mass("Zn", "Zinc", "mg", MIN) { it.zinc },
        mass("Cu", "Copper", "mg", MIN) { it.copper },
        mass("Mn", "Manganese", "mg", MIN) { it.manganese },
        mass("Se", "Selenium", "mcg", MIN) { it.selenium },
        mass("Cr", "Chromium", "mcg", MIN) { it.chromium },
        mass("Mo", "Molybdenum", "mcg", MIN) { it.molybdenum },
        mass("I", "Iodine", "mcg", MIN) { it.iodine },
        mass("Cl", "Chloride", "mg", MIN) { it.chloride },
        mass("VitA", "Vitamin A", "mcg", V) { it.vitaminA },
        mass("VitB6", "Vitamin B6", "mg", V) { it.vitaminB6 },
        mass("VitB12", "Vitamin B12", "mcg", V) { it.vitaminB12 },
        mass("VitC", "Vitamin C", "mg", V) { it.vitaminC },
        mass("VitD", "Vitamin D", "mcg", V) { it.vitaminD },
        mass("VitE", "Vitamin E", "mg", V) { it.vitaminE },
        mass("VitK", "Vitamin K", "mcg", V) { it.vitaminK },
        mass("Biotin", "Biotin", "mcg", V) { it.biotin },
        mass("Folate", "Folate", "mcg", V) { it.folate },
        mass("FolicAcid", "Folic acid", "mcg", V) { it.folicAcid },
        mass("Niacin", "Niacin (B3)", "mg", V) { it.niacin },
        mass("PantAcid", "Pantothenic acid (B5)", "mg", V) { it.pantothenicAcid },
        mass("Ribo", "Riboflavin (B2)", "mg", V) { it.riboflavin },
        mass("Thiam", "Thiamin (B1)", "mg", V) { it.thiamin },
        mass("Caff", "Caffeine", "mg", O) { it.caffeine }
    )

    val byKey: Map<String, Nutrient> = all.associateBy { it.key }
    val groups: List<String> = all.map { it.group }.distinct()

    /** Nutrients where going over the goal is "bad" (shown in red). */
    val limitKeys = setOf("satf", "transf", "sugar", "chol", "na", "caff")
}

object Format {
    private val df = DecimalFormat("0.##", DecimalFormatSymbols(Locale.US))
    private val lineTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun num(v: Double): String = df.format(v)

    fun goalsString(goals: Map<String, Double>): String =
        "${LocalDate.now()} Daily Targets: ${num(goals["kcal"] ?: 0.0)} kcal" +
            Nutrients.all.filter { it.key != "kcal" }
                .joinToString("") { " | ${it.tag} ${num(goals[it.key] ?: 0.0)}${it.unit}" }

    fun mealName(r: NutritionRecord): String = when (r.mealType) {
        MealType.MEAL_TYPE_BREAKFAST -> "Breakfast"
        MealType.MEAL_TYPE_LUNCH -> "Lunch"
        MealType.MEAL_TYPE_DINNER -> "Dinner"
        else -> "Snack"
    }

    fun localStart(r: NutritionRecord): LocalDateTime =
        LocalDateTime.ofInstant(r.startTime, ZoneId.systemDefault())

    /** Turns a stored record back into the paste format (non-zero nutrients only). */
    fun entryLine(r: NutritionRecord): String {
        val sb = StringBuilder()
        sb.append(localStart(r).format(lineTime)).append(' ')
            .append(mealName(r)).append(" (").append(r.name ?: "").append("): ")
            .append(num(r.energy?.inKilocalories ?: 0.0)).append(" kcal")
        Nutrients.all.filter { it.key != "kcal" }.forEach { n ->
            val v = n.read(r) ?: 0.0
            if (v > 0.0) sb.append(" | ").append(n.tag).append(' ').append(num(v)).append(n.unit)
        }
        return sb.toString()
    }
}
