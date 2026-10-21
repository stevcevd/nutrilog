package com.example.nutrilog

import android.content.Context

object GoalsStore {
    const val DEFAULT =
        "2026-10-19 Daily Targets: 3300 kcal | P 205g | C 400g | F 95g | SatF 30g | MonoF 35g | PolyF 20g | TransF 0g | Fiber 40g | Sugar 50g | Chol 300mg | Na 2300mg | K 3500mg | Ca 1000mg | Fe 18mg | Mg 420mg | P_ 700mg | Zn 11mg | Cu 0.9mg | Mn 2.3mg | Se 55mcg | Cr 35mcg | Mo 45mcg | I 150mcg | Cl 3400mg | Caff 400mg | VitA 900mcg | VitB6 1.7mg | VitB12 2.4mcg | VitC 90mg | VitD 15mcg | VitE 15mg | VitK 120mcg | Biotin 30mcg | Folate 400mcg | FolicAcid 400mcg | Niacin 16mg | PantAcid 5mg | Ribo 1.3mg | Thiam 1.2mg"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("nutrilog", Context.MODE_PRIVATE)

    fun load(ctx: Context): Map<String, Double> {
        val s = prefs(ctx).getString("goals", null) ?: DEFAULT
        return try {
            NutritionParser.parseGoals(s)
        } catch (e: Exception) {
            NutritionParser.parseGoals(DEFAULT)
        }
    }

    fun save(ctx: Context, goals: Map<String, Double>) {
        prefs(ctx).edit().putString("goals", Format.goalsString(goals)).apply()
    }

    fun defaults(): Map<String, Double> = NutritionParser.parseGoals(DEFAULT)

    fun loadWater(ctx: Context): Double = prefs(ctx).getFloat("water_goal", 2500f).toDouble()

    fun saveWater(ctx: Context, ml: Double) {
        prefs(ctx).edit().putFloat("water_goal", ml.toFloat()).apply()
    }
}
