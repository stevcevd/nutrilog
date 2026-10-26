package com.example.nutrilog

import java.time.LocalDate
import java.time.temporal.ChronoUnit

// ───────── Weekly gap report ─────────

data class Gap(val n: Nutrient, val avg: Double, val goal: Double, val pct: Double)

/** One counted day: its nutrient totals and the goals that applied on that day. */
class DayIntake(val totals: Map<String, Double>, val goals: Map<String, Double>)

/** Returns (nutrients falling short, limit-type nutrients over their limit). Each day is judged against its own goals. */
fun computeGaps(days: List<DayIntake>): Pair<List<Gap>, List<Gap>> {
    if (days.isEmpty()) return Pair(emptyList(), emptyList())
    val under = ArrayList<Gap>()
    val over = ArrayList<Gap>()
    Nutrients.all.filter { it.key != "kcal" }.forEach { n ->
        val withGoal = days.filter { (it.goals[n.key] ?: 0.0) > 0.0 }
        if (withGoal.isEmpty()) return@forEach
        val avg = days.sumOf { it.totals[n.key] ?: 0.0 } / days.size
        val goal = withGoal.sumOf { it.goals[n.key] ?: 0.0 } / withGoal.size
        val pct = withGoal.sumOf { (it.totals[n.key] ?: 0.0) / (it.goals[n.key] ?: 1.0) } / withGoal.size
        val gap = Gap(n, avg, goal, pct)
        if (n.key in Nutrients.limitKeys) {
            if (pct > 1.0) over += gap
        } else {
            under += gap
        }
    }
    return Pair(under.sortedBy { it.pct }, over.sortedByDescending { it.pct })
}

object FoodIdeas {
    val map = mapOf(
        "p" to "chicken, eggs, Greek yogurt, tuna",
        "c" to "rice, oats, potatoes, fruit",
        "f" to "olive oil, nuts, avocado, eggs",
        "fiber" to "oats, beans, berries, whole grains",
        "monof" to "olive oil, almonds, avocado",
        "polyf" to "walnuts, salmon, sunflower seeds",
        "k" to "potatoes, bananas, yogurt, beans",
        "ca" to "yogurt, cheese, milk, sardines",
        "fe" to "red meat, lentils, spinach, pumpkin seeds",
        "mg" to "pumpkin seeds, almonds, spinach, dark chocolate",
        "p_" to "dairy, fish, meat, legumes",
        "zn" to "beef, pumpkin seeds, chickpeas, cheese",
        "cu" to "cashews, dark chocolate, liver, seeds",
        "mn" to "oats, nuts, pineapple, whole grains",
        "se" to "Brazil nuts, tuna, eggs, sardines",
        "cr" to "broccoli, whole grains, potatoes",
        "mo" to "legumes, grains, nuts",
        "i" to "iodized salt, fish, dairy, eggs",
        "cl" to "table salt, olives, tomato juice",
        "vita" to "sweet potato, carrots, eggs, liver",
        "vitb6" to "chicken, potatoes, bananas, salmon",
        "vitb12" to "meat, fish, eggs, dairy",
        "vitc" to "citrus, peppers, kiwi, strawberries",
        "vitd" to "oily fish, eggs, fortified milk, sunlight",
        "vite" to "almonds, sunflower seeds, olive oil",
        "vitk" to "spinach, kale, broccoli, parsley",
        "biotin" to "eggs, nuts, salmon, sweet potato",
        "folate" to "lentils, leafy greens, asparagus",
        "folicacid" to "fortified grains and cereals",
        "niacin" to "chicken, tuna, peanuts, mushrooms",
        "pantacid" to "mushrooms, eggs, avocado, yogurt",
        "ribo" to "dairy, eggs, almonds, mushrooms",
        "thiam" to "pork, whole grains, beans, sunflower seeds"
    )
}

// ───────── Weight vs calories, judged against the goal ─────────

/** Healthy weekly weight change as a percentage of body weight. */
data class RateRange(val min: Double, val ideal: Double, val max: Double)

data class Verdict(
    val ok: Boolean,
    val message: String = "",
    val slopeKgWeek: Double = 0.0,
    val ratePct: Double = 0.0,
    val avgIntake: Double = 0.0,
    val maintenance: Double = 0.0,
    val suggested: Double = 0.0,
    val status: Int = 0,              // 0 on track, 1 slightly off, 2 clearly off
    val headline: String = "",
    val range: RateRange = RateRange(0.0, 0.0, 0.0),
    val loggedDays: Int = 0,
    val totalDays: Int = 0
)

data class WeekBucket(val label: String, val kcal: Double?, val weightKg: Double?, val start: LocalDate, val end: LocalDate)

object GoalLogic {
    private const val KCAL_PER_KG = 7700.0

    fun range(goalType: Int): RateRange = when (goalType) {
        0 -> RateRange(-1.0, -0.5, -0.25)     // cutting
        2 -> RateRange(0.10, 0.25, 0.50)      // bulking
        else -> RateRange(-0.25, 0.0, 0.25)   // maintaining
    }

    fun goalName(goalType: Int) = listOf("Cutting", "Maintaining", "Bulking")[goalType.coerceIn(0, 2)]

    fun buckets(weeks: Int, end: LocalDate, weights: List<Pair<LocalDate, Double>>, kcalByDay: Map<LocalDate, Double>): List<WeekBucket> =
        (0 until weeks).map { i ->
            val weekEnd = end.minusDays(((weeks - 1 - i) * 7).toLong())
            val weekStart = weekEnd.minusDays(6)
            val k = kcalByDay.filter { (d, v) -> !d.isBefore(weekStart) && !d.isAfter(weekEnd) && v > 0 }.values
            val w = weights.filter { !it.first.isBefore(weekStart) && !it.first.isAfter(weekEnd) }.map { it.second }
            WeekBucket(
                weekStart.format(java.time.format.DateTimeFormatter.ofPattern("d MMM")),
                if (k.isEmpty()) null else k.average(),
                if (w.isEmpty()) null else w.average(),
                weekStart,
                weekEnd
            )
        }

    fun analyze(
        goalType: Int,
        weights: List<Pair<LocalDate, Double>>,
        kcalByDay: Map<LocalDate, Double>,
        totalDays: Int,
        goalKcal: Double
    ): Verdict {
        val r = range(goalType)
        val logged = kcalByDay.filter { it.value > 0 }
        if (weights.size < 3) return Verdict(false, "Need at least 3 weigh-ins in this window (found ${weights.size}).")
        val first = weights.minOf { it.first }
        val last = weights.maxOf { it.first }
        if (ChronoUnit.DAYS.between(first, last) < 7) return Verdict(false, "Need weigh-ins that span at least a week.")
        if (logged.size < 5) return Verdict(false, "Need at least 5 days of logged food (found ${logged.size}).")

        val xs = weights.map { ChronoUnit.DAYS.between(first, it.first).toDouble() }
        val ys = weights.map { it.second }
        val mx = xs.average()
        val my = ys.average()
        val sxx = xs.sumOf { (it - mx) * (it - mx) }
        val slope = if (sxx == 0.0) 0.0 else xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / sxx   // kg per day
        val perWeek = slope * 7
        val ratePct = perWeek / my * 100

        val avgIntake = logged.values.average()
        val maintenance = avgIntake - slope * KCAL_PER_KG
        val idealKgWeek = r.ideal / 100.0 * my
        val target = maintenance + idealKgWeek * KCAL_PER_KG / 7
        val suggested = (Math.round(target / 50.0) * 50.0).coerceIn(1200.0, 6000.0)

        val below = ratePct < r.min
        val above = ratePct > r.max
        val distance = if (below) r.min - ratePct else if (above) ratePct - r.max else 0.0
        val status = if (distance == 0.0) 0 else if (distance < 0.25) 1 else 2
        val headline = when {
            !below && !above -> "On track"
            goalType == 0 && below -> "Losing faster than planned"
            goalType == 0 -> if (ratePct > 0.1) "Gaining while cutting" else "Not losing yet"
            goalType == 2 && below -> if (ratePct < -0.1) "Losing weight while bulking" else "Gaining too slowly"
            goalType == 2 -> "Gaining faster than planned"
            below -> "Losing weight"
            else -> "Gaining weight"
        }
        return Verdict(true, "", perWeek, ratePct, avgIntake, maintenance, suggested, status, headline, r, logged.size, totalDays)
    }
}
