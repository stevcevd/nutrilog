package com.example.nutrilog

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.NutritionRecord
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/**
 * One calendar day with the goals that applied on that day.
 * Only finished days count towards averages, and a finished day under 40% of that day's
 * calorie goal is treated as "not fully logged" and skipped.
 */
private data class Day(
    val date: LocalDate,
    val totals: Map<String, Double>,
    val count: Int,
    val goals: Map<String, Double>,
    val goalType: Int,
    val isToday: Boolean
) {
    val kcal: Double get() = totals["kcal"] ?: 0.0
    val goalKcal: Double get() = goals["kcal"] ?: 0.0
    val skipped: Boolean get() = !isToday && count > 0 && goalKcal > 0 && kcal < 0.4 * goalKcal
    val valid: Boolean get() = !isToday && count > 0 && !skipped
}

private data class HeatCell(val date: LocalDate, val kcal: Double, val goal: Double, val status: Int) // 0 none, 1 skipped, 2 today, 3 counted

private fun buildDays(records: List<NutritionRecord>, history: List<GoalEntry>, fallbackGoals: Map<String, Double>, fallbackType: Int): List<Day> {
    val today = LocalDate.now()
    val byDay = records.groupBy { it.day() }
    return (55 downTo 0).map { off ->
        val d = today.minusDays(off.toLong())
        val rs = byDay[d] ?: emptyList()
        val e = GoalHistory.at(history, d, fallbackGoals, fallbackType)
        Day(d, totalsOf(rs), rs.size, e.goals, e.type, d == today)
    }
}

@Composable
fun StatsScreen(
    client: HealthConnectClient?,
    hasPerms: Boolean,
    hasBody: Boolean,
    requestBody: () -> Unit,
    goals: Map<String, Double>,
    waterGoal: Double,
    settings: Settings,
    refresh: Int,
    onSaveGoals: (Map<String, Double>, Double) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var sub by remember { mutableIntStateOf(0) }
    var records by remember { mutableStateOf<List<NutritionRecord>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(client, hasPerms, refresh) {
        if (client != null && hasPerms) {
            try {
                val today = LocalDate.now()
                records = readNutrition(client, today.minusDays(55), today.plusDays(1))
                error = null
            } catch (e: Exception) {
                error = e.message
            }
        }
    }
    val history = remember(goals, settings.profile.goalType, refresh) { GoalHistory.load(ctx) }
    val allDays = remember(records, history, goals, settings.profile.goalType) {
        buildDays(records, history, goals, settings.profile.goalType)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ScreenHeader("Stats", "Finished days only")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Overview", "Weight vs calories", "Weekly report").forEachIndexed { i, l -> FancyChip(l, sub == i) { sub = i } }
        }
        Spacer(Modifier.height(10.dp))
        error?.let { Text("Couldn't read: $it", color = cs.error, fontSize = 13.sp) }

        if (client == null || !hasPerms) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Grant Health Connect access to see your stats.", color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        } else {
            AnimatedContent(
                targetState = sub,
                modifier = Modifier.weight(1f),
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                label = "statsTabs"
            ) { s ->
                when (s) {
                    0 -> OverviewTab(allDays, records)
                    1 -> BodyTab(client, hasBody, requestBody, allDays, goals, waterGoal, settings, onSaveGoals)
                    else -> ReportTab(allDays)
                }
            }
        }
    }
}

// ───────────────────────── Overview ─────────────────────────

@Composable
private fun OverviewTab(allDays: List<Day>, records: List<NutritionRecord>) {
    val cs = MaterialTheme.colorScheme
    var range by remember { mutableIntStateOf(7) }
    var key by remember { mutableStateOf("kcal") }
    var bars by remember { mutableStateOf(false) }

    val finished = remember(allDays, range) { allDays.filter { !it.isToday }.takeLast(range) }
    val todayDay = allDays.last()
    val shown = finished + todayDay
    val counted = finished.filter { it.valid }
    val skippedN = finished.count { it.skipped }
    val nCounted = counted.size
    fun avg(k: String): Double = if (nCounted == 0) 0.0 else counted.sumOf { it.totals[k] ?: 0.0 } / nCounted

    var streak = 0
    for (d in allDays.asReversed()) {
        if (d.count > 0) streak++ else if (!d.isToday) break
    }

    val shortLabels = shown.mapIndexed { i, d ->
        if (range <= 7) {
            if (d.isToday) "Today" else d.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()).take(3)
        } else if (i % 5 == 0 || i == shown.lastIndex) d.date.dayOfMonth.toString() else ""
    }
    val fullLabels = shown.map { if (it.isToday) "Today (so far)" else it.date.format(DateTimeFormatter.ofPattern("EEE, d MMM")) }
    val trendKeys = listOf("kcal", "p", "c", "f", "fiber", "sugar", "na")
    val countedDates = remember(counted) { counted.map { it.date }.toSet() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7, 14, 30).forEach { r -> FancyChip("$r days", range == r) { range = r } }
        }

        Reveal(0) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Avg calories", avg("kcal"), "kcal / day", cs.primary, Modifier.weight(1f))
                    StatTile("Avg protein", avg("p"), "g / day", Brand.Protein, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Days counted", nCounted.toDouble(), "of $range" + if (skippedN > 0) " · $skippedN skipped" else "", Brand.Carbs, Modifier.weight(1f))
                    StatTile("Streak", streak.toDouble(), if (streak == 1) "day" else "days", Brand.Fat, Modifier.weight(1f))
                }
            }
        }

        Reveal(1) {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Trend", Modifier.weight(1f))
                    FancyChip("Line", !bars) { bars = false }
                    Spacer(Modifier.width(6.dp))
                    FancyChip("Bars", bars) { bars = true }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    trendKeys.forEach { k -> FancyChip(Nutrients.byKey.getValue(k).name, key == k) { key = k } }
                }
                Spacer(Modifier.height(12.dp))
                Crossfade(targetState = bars to key, label = "trend") { (b, k) ->
                    val vals = shown.map { it.totals[k] ?: 0.0 }
                    val goalSeries = shown.map { it.goals[k] ?: 0.0 }
                    val col = if (k == "kcal") cs.primary else Brand.colorFor(k, cs.secondary)
                    val nn = Nutrients.byKey.getValue(k)
                    if (b) {
                        BarChart(
                            vals, shortLabels, fullLabels, null, col, nn.unit,
                            highlightOver = k in Nutrients.limitKeys || k == "kcal", goalSeries = goalSeries, partialLast = true
                        )
                    } else {
                        AreaChart(vals, shortLabels, fullLabels, null, col, nn.unit, goalSeries = goalSeries, partialLast = true)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("The last point is today so far. It is shown but not counted in any average.", fontSize = 11.sp, color = cs.onSurfaceVariant)
            }
        }

        Reveal(2) {
            AppCard {
                SectionLabel("Macro split")
                Text("Share of calories, average of counted days", fontSize = 12.sp, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                val pK = avg("p") * 4
                val cK = avg("c") * 4
                val fK = avg("f") * 9
                val tot = pK + cK + fK
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DonutChart(listOf(pK.toFloat(), cK.toFloat(), fK.toFloat()), listOf(Brand.Protein, Brand.Carbs, Brand.Fat), Modifier.size(140.dp), stroke = 20.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CountUp(avg("kcal"), fontSize = 20.sp)
                            Text("kcal avg", fontSize = 11.sp, color = cs.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.width(20.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(
                            Triple("Protein", pK, Brand.Protein) to avg("p"),
                            Triple("Carbs", cK, Brand.Carbs) to avg("c"),
                            Triple("Fat", fK, Brand.Fat) to avg("f")
                        ).forEach { (info, grams) ->
                            val (name, kc, col) = info
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(col))
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text("${Format.int(grams)} g avg", fontSize = 11.sp, color = cs.onSurfaceVariant)
                                }
                                Text(if (tot > 0) "${(kc / tot * 100).toInt()}%" else "0%", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        Reveal(3) {
            AppCard {
                SectionLabel("Calories by meal")
                Spacer(Modifier.height(12.dp))
                val rangeRecords = records.filter { it.day() in countedDates }
                val byMeal = listOf("Breakfast", "Lunch", "Dinner", "Snack").map { m ->
                    m to rangeRecords.filter { Format.mealName(it) == m }.sumOf { it.energy?.inKilocalories ?: 0.0 }
                }
                val total = byMeal.sumOf { it.second }
                if (total <= 0) {
                    Text("No counted days in this range.", color = cs.onSurfaceVariant, fontSize = 14.sp)
                } else {
                    Row(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(8.dp))) {
                        byMeal.filter { it.second > 0 }.forEach { (m, v) ->
                            Box(Modifier.weight(v.toFloat()).fillMaxHeight().background(Brand.mealColor(m)))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    byMeal.forEach { (m, v) ->
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(Brand.mealColor(m)))
                            Spacer(Modifier.width(8.dp))
                            Text(m, Modifier.weight(1f), fontSize = 14.sp)
                            Text("${Format.int(v)} kcal", fontSize = 13.sp, color = cs.onSurfaceVariant)
                            Text("  ${(v / total * 100).toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Reveal(4) {
            AppCard {
                SectionLabel("Calorie consistency")
                Text("Darker = closer to that day's goal, amber = over, grey = skipped, tinted = today so far", fontSize = 12.sp, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Heatmap(
                    shown.map { d ->
                        val status = when {
                            d.isToday -> 2
                            d.count == 0 -> 0
                            d.skipped -> 1
                            else -> 3
                        }
                        HeatCell(d.date, d.kcal, d.goalKcal, status)
                    }
                )
            }
        }

        Reveal(5) {
            AppCard {
                SectionLabel("How days are counted")
                Spacer(Modifier.height(4.dp))
                Text(
                    "Only finished days count. Today is left out until tomorrow, and a finished day under 40% of that day's " +
                        "calorie goal is treated as not fully logged and skipped. Each day is judged against the goals " +
                        "that applied on that day, so changing your goals never changes the past.",
                    fontSize = 12.sp, color = cs.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text("Today so far: ${Format.int(todayDay.kcal)} of ${Format.int(todayDay.goalKcal)} kcal", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(100.dp))
    }
}

// ───────────────────────── Weight vs calories ─────────────────────────

@Composable
private fun BodyTab(
    client: HealthConnectClient,
    hasBody: Boolean,
    requestBody: () -> Unit,
    allDays: List<Day>,
    goals: Map<String, Double>,
    waterGoal: Double,
    settings: Settings,
    onSaveGoals: (Map<String, Double>, Double) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var weeks by remember { mutableIntStateOf(4) }
    var points by remember { mutableStateOf<List<Pair<java.time.Instant, Double>>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var applied by remember { mutableStateOf("") }

    LaunchedEffect(client, hasBody) {
        if (hasBody) {
            try { points = readWeightPoints(client, 60) } catch (e: Exception) { }
            loaded = true
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (!hasBody) {
            AppCard {
                Text("Weight vs calories", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "This compares your weight trend with what you eat, judged against your goal. " +
                        "Weight comes from Health Connect, nothing is typed in here.",
                    fontSize = 13.sp, color = cs.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                FancyButton("Connect body data", icon = R.drawable.ic_scale, onClick = requestBody)
            }
        } else {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now()
            val yesterday = today.minusDays(1)
            val windowStart = yesterday.minusDays(weeks * 7L - 1)
            val goalType = settings.profile.goalType
            val kcalGoal = goals["kcal"] ?: 0.0

            val weighDaily = points
                .map { it.first.atZone(zone).toLocalDate() to it.second }
                .groupBy({ it.first }, { it.second })
                .map { (d, v) -> d to v.average() }
                .sortedBy { it.first }

            // Only judge the phase you are in now: find when the current goal type started.
            var phaseStart = allDays.first().date
            for (d in allDays.asReversed()) {
                if (d.goalType != goalType) { phaseStart = d.date.plusDays(1); break }
            }
            val switched = phaseStart.isAfter(windowStart)
            val effStart = if (switched) phaseStart else windowStart

            val kcalAll = allDays.filter { it.valid && !it.date.isBefore(windowStart) }.associate { it.date to it.kcal }
            val kcalPhase = kcalAll.filterKeys { !it.isBefore(effStart) }
            val weightsPhase = weighDaily.filter { !it.first.isBefore(effStart) }
            val daysInPhase = (ChronoUnit.DAYS.between(effStart, yesterday) + 1).toInt().coerceAtLeast(1)
            val verdict = GoalLogic.analyze(goalType, weightsPhase, kcalPhase, daysInPhase, kcalGoal)
            val buckets = GoalLogic.buckets(weeks, yesterday, weighDaily, kcalAll)
            val kcalGoals = buckets.map { b -> allDays.firstOrNull { it.date == b.end }?.goalKcal ?: kcalGoal }
            val phaseIndex = if (switched) buckets.indexOfFirst { !phaseStart.isBefore(it.start) && !phaseStart.isAfter(it.end) }.takeIf { it >= 0 } else null

            val lb = settings.units == 1
            val mult = if (lb) 2.20462 else 1.0
            val wUnit = if (lb) "lb" else "kg"
            val goalName = GoalLogic.goalName(goalType)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(4, 8).forEach { w -> FancyChip("$w weeks", weeks == w) { weeks = w } }
            }

            Reveal(0) {
                AppCard {
                    SectionLabel("Weight vs calories")
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        LegendDot(Brand.Emerald, "Avg kcal per day")
                        LegendDot(Brand.Protein, "Avg weight ($wUnit)")
                    }
                    Spacer(Modifier.height(8.dp))
                    if (!loaded) {
                        Text("Reading from Health Connect...", color = cs.onSurfaceVariant, fontSize = 13.sp)
                    } else if (buckets.all { it.kcal == null && it.weightKg == null }) {
                        Text("No data in this window yet.", color = cs.onSurfaceVariant, fontSize = 13.sp)
                    } else {
                        WeightCalChart(
                            kcal = buckets.map { it.kcal },
                            weight = buckets.map { it.weightKg?.times(mult) },
                            shortLabels = buckets.map { it.label },
                            kcalGoals = kcalGoals,
                            weightUnit = wUnit,
                            phaseIndex = phaseIndex
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "The dashed amber line is your calorie goal at the time." +
                                (if (phaseIndex != null) " The dotted vertical line is when you switched to $goalName." else ""),
                            fontSize = 11.sp, color = cs.onSurfaceVariant
                        )
                    }
                }
            }

            Reveal(1) {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Goal check", Modifier.weight(1f))
                        FancyChip(goalName, true) { }
                    }
                    Spacer(Modifier.height(10.dp))
                    val dayN = (ChronoUnit.DAYS.between(phaseStart, today) + 1).toInt()
                    if (!verdict.ok) {
                        if (switched && dayN < 7) {
                            Text("Collecting data since you switched to $goalName, day $dayN of 7.", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text("Earlier weeks aren't mixed in, because a different phase expects a different weight change.", fontSize = 12.sp, color = cs.onSurfaceVariant)
                        } else {
                            Text(verdict.message, fontSize = 14.sp, color = cs.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                            Text("Keep logging food and weighing in, this fills in on its own.", fontSize = 12.sp, color = cs.onSurfaceVariant)
                        }
                    } else {
                        if (switched) {
                            Text(
                                "Counting from ${phaseStart.format(DateTimeFormatter.ofPattern("d MMM"))}, when you switched to $goalName.",
                                fontSize = 12.sp, color = cs.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        val dot = when (verdict.status) { 0 -> Brand.Emerald; 1 -> Brand.Carbs; else -> Brand.Warn }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(dot))
                            Spacer(Modifier.width(10.dp))
                            Text(verdict.headline, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        val kgShown = verdict.slopeKgWeek * mult
                        Text(
                            "Weight is changing by ${if (kgShown >= 0) "+" else ""}${Format.num(Math.round(kgShown * 100) / 100.0)} $wUnit per week " +
                                "(${if (verdict.ratePct >= 0) "+" else ""}${Format.num(Math.round(verdict.ratePct * 100) / 100.0)}% of body weight).",
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "A ${goalName.lowercase()} plan aims for ${fmtPct(verdict.range.min)} to ${fmtPct(verdict.range.max)} per week.",
                            fontSize = 13.sp, color = cs.onSurfaceVariant
                        )

                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MiniFact("You eat", "${Format.int(verdict.avgIntake)} kcal", Modifier.weight(1f))
                            MiniFact("Maintenance", "~${Format.int(verdict.maintenance)} kcal", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(12.dp))
                        val delta = verdict.suggested - kcalGoal
                        Text("Suggested daily calories", fontSize = 13.sp, color = cs.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(Format.int(verdict.suggested), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = cs.primary)
                            Text(" kcal", fontSize = 14.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                        }
                        if (verdict.status == 0) {
                            Text("Your pace matches your goal, so keep calories about here.", fontSize = 13.sp, color = cs.onSurfaceVariant)
                        } else if (kcalGoal > 0) {
                            Text(
                                "That is ${if (delta >= 0) "+" else "-"}${Format.int(abs(delta))} kcal compared with your current goal of ${Format.int(kcalGoal)}.",
                                fontSize = 13.sp, color = cs.onSurfaceVariant
                            )
                        }
                        if (abs(delta) >= 50) {
                            Spacer(Modifier.height(12.dp))
                            FancyButton("Set calorie goal to ${Format.int(verdict.suggested)}", icon = R.drawable.ic_check, compact = true) {
                                onSaveGoals(goals + ("kcal" to verdict.suggested), waterGoal)
                                applied = "Calorie goal updated from today. Past days keep their old goal."
                            }
                        }
                        if (applied.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(applied, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = cs.primary)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Based on ${verdict.loggedDays} counted days of ${verdict.totalDays}" +
                                (if (verdict.loggedDays < verdict.totalDays * 0.7) " (log more days for a better estimate)" else "") +
                                ". Today and days under 40% of that day's goal are left out. Uses about 7,700 kcal per kg. " +
                                "Water weight moves the scale a lot, so judge trends over 2+ weeks. This is a guide, not medical advice.",
                            fontSize = 11.sp, color = cs.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(100.dp))
    }
}

private fun fmtPct(v: Double) = (if (v > 0) "+" else "") + Format.num(v) + "%"

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MiniFact(label: String, value: String, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(cs.surfaceVariant).padding(12.dp)) {
        Text(label, fontSize = 11.sp, color = cs.onSurfaceVariant)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

// ───────────────────────── Weekly report ─────────────────────────

@Composable
private fun ReportTab(allDays: List<Day>) {
    val cs = MaterialTheme.colorScheme
    val last7 = remember(allDays) { allDays.filter { !it.isToday }.takeLast(7) }
    val counted = last7.filter { it.valid }
    val skippedN = last7.count { it.skipped }
    val (under, over) = remember(allDays) { computeGaps(counted.map { DayIntake(it.totals, it.goals) }) }
    val onTarget = under.count { it.pct >= 0.9 }
    val todayDay = allDays.last()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (counted.isEmpty()) {
            AppCard {
                Text("No counted days in the last 7 days.", fontWeight = FontWeight.SemiBold)
                Text(
                    "Only finished days count, and days under 40% of your calorie goal are skipped. " +
                        "Log full days and your weekly report appears here.",
                    fontSize = 13.sp, color = cs.onSurfaceVariant
                )
            }
        } else {
            Reveal(0) {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val frac = if (under.isEmpty()) 0f else onTarget.toFloat() / under.size
                        RingChart(frac, Brand.Emerald, Modifier.size(96.dp), stroke = 11.dp) {
                            Text("${(frac * 100).toInt()}%", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(18.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Last 7 finished days", fontSize = 13.sp, color = cs.onSurfaceVariant)
                            Text("$onTarget of ${under.size} nutrients on target", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "Based on ${counted.size} counted day${if (counted.size == 1) "" else "s"}" +
                                    (if (skippedN > 0) " ($skippedN skipped as not fully logged)" else "") +
                                    ". On target means averaging at least 90% of your goal.",
                                fontSize = 12.sp, color = cs.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Reveal(1) {
                AppCard {
                    SectionLabel("Biggest gaps")
                    Text("Lowest versus your goals, each day judged by its own goals", fontSize = 12.sp, color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    val shown = under.filter { it.pct < 0.9 }.take(8)
                    if (shown.isEmpty()) {
                        Text("No gaps. Everything is at 90% of goal or better.", fontSize = 14.sp, color = cs.primary, fontWeight = FontWeight.SemiBold)
                    }
                    shown.forEach { g ->
                        val color = if (g.pct < 0.5) Brand.Warn else if (g.pct < 0.8) Brand.Carbs else Brand.Emerald
                        NutrientBar(g.n, g.avg, g.goal, colorOverride = color)
                        FoodIdeas.map[g.n.key]?.let {
                            Text("Try: $it", fontSize = 11.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                        }
                    }
                    if (shown.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text("Food ideas are general suggestions, not medical advice.", fontSize = 11.sp, color = cs.onSurfaceVariant)
                    }
                }
            }

            if (over.isNotEmpty()) {
                Reveal(2) {
                    AppCard {
                        SectionLabel("Over the limit")
                        Text("Nutrients you want to keep down", fontSize = 12.sp, color = cs.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        over.forEach { g -> NutrientBar(g.n, g.avg, g.goal) }
                    }
                }
            }
        }

        Reveal(3) {
            AppCard {
                SectionLabel("Today so far")
                Text("Not counted in the report until the day is finished", fontSize = 12.sp, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                listOf("kcal", "p").forEach { k ->
                    NutrientBar(Nutrients.byKey.getValue(k), todayDay.totals[k] ?: 0.0, todayDay.goals[k] ?: 0.0)
                }
            }
        }
        Spacer(Modifier.height(100.dp))
    }
}

// ───────────────────────── shared bits ─────────────────────────

@Composable
private fun StatTile(label: String, value: Double, unit: String, color: Color, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    AppCard(modifier, padding = 16.dp) {
        Box(Modifier.width(28.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.height(10.dp))
        Text(label, fontSize = 12.sp, color = cs.onSurfaceVariant)
        CountUp(value, fontSize = 26.sp, color = cs.onSurface)
        Text(unit, fontSize = 11.sp, color = cs.onSurfaceVariant)
    }
}

@Composable
private fun Heatmap(cells: List<HeatCell>) {
    val cs = MaterialTheme.colorScheme
    if (cells.isEmpty()) return
    val lead = cells.first().date.dayOfWeek.value - 1
    val padded: List<HeatCell?> = List(lead) { null } + cells
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("M", "T", "W", "T", "F", "S", "S").forEach {
            Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, color = cs.onSurfaceVariant)
        }
    }
    Spacer(Modifier.height(6.dp))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        padded.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until 7) {
                    val c = week.getOrNull(i)
                    if (c == null) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val pct = if (c.goal > 0) c.kcal / c.goal else 0.0
                        val target = when {
                            c.status == 0 -> cs.surfaceVariant
                            c.status == 1 -> cs.outline.copy(alpha = 0.35f)
                            c.status == 2 -> cs.primary.copy(alpha = 0.14f)
                            pct > 1.15 -> Brand.Carbs
                            pct >= 0.85 -> cs.primary
                            else -> cs.primary.copy(alpha = 0.35f)
                        }
                        val bg by animateColorAsState(target, label = "heat")
                        val textColor = when {
                            c.status == 3 && pct in 0.85..1.15 -> cs.onPrimary
                            c.status == 3 -> cs.onSurface
                            c.status == 2 -> cs.onSurface
                            else -> cs.onSurfaceVariant
                        }
                        Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(bg), contentAlignment = Alignment.Center) {
                            Text(c.date.dayOfMonth.toString(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                        }
                    }
                }
            }
        }
    }
}
