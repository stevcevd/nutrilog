package com.example.nutrilog

import java.time.LocalDate

object AiPrompt {
    /** A ready-made prompt that makes any chatbot answer in NutriLog's paste format. */
    fun build(): String {
        val labels = Nutrients.all.filter { it.key != "kcal" }
            .joinToString(", ") { "${it.tag} = ${it.name.lowercase()} (${it.unit})" }
        return """
You are my nutrition logger. For every meal or food I describe (or show in a photo), reply with ONLY one line per entry. No introduction, no explanation, no bullet points, no code block.

Format of each line:
YYYY-MM-DD HH:MM Meal (Name): N kcal | P 0g | C 0g | F 0g | ...

Rules:
- Meal is exactly one of: Breakfast, Lunch, Dinner, Snack.
- Name is short, written inside the parentheses, and contains no colon.
- Calories come first and are required, written as "N kcal".
- Every other nutrient is optional. Leave out anything that is 0 or unknown.
- Use only these labels and units: $labels.
- Write "P_" for phosphorus because "P" means protein.
- Numbers use a dot for decimals and no thousands separators.
- Today's date is ${LocalDate.now()}. If I don't give a time, use a sensible one (Breakfast 08:00, Lunch 13:00, Dinner 19:00, Snack 16:00).
- If I give portions, estimate sensibly. If something is truly ambiguous, ask me one short question first.

Examples:
${LocalDate.now()} 08:30 Breakfast (Banica & Yogurt): 1446 kcal | P 46.4g | C 137.6g | F 78g | Fiber 4.2g | Sugar 12g | Na 900mg
${LocalDate.now()} 16:00 Snack (Banana & almonds): 260 kcal | P 6g | C 38g | F 11g | Fiber 6g | K 520mg | Mg 85mg

My entries:
""".trimIndent()
    }
}

/** Pulls valid entry lines out of messy pasted text (chat replies, bullets, code fences). */
object Paste {
    private val start = Regex(
        """\d{4}-\d{2}-\d{2}\s+\d{1,2}:\d{2}\s+(Breakfast|Lunch|Dinner|Snack)\s*\(""",
        RegexOption.IGNORE_CASE
    )

    fun clean(raw: String): String = raw.lines().mapNotNull { line ->
        val m = start.find(line) ?: return@mapNotNull null
        line.substring(m.range.first).trim().trimEnd('`', '*')
    }.joinToString("\n")
}
