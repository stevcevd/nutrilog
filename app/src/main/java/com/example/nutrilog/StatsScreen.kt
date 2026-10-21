package com.example.nutrilog

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.NutritionRecord
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class Day(val date: LocalDate, val totals: Map<String, Double>, val count: Int)

@Composable
fun StatsScreen(client: HealthConnectClient?, hasPerms: Boolean, goals: Map<String, Double>, refresh: Int) {
    val cs = MaterialTheme.colorScheme
    var range by remember { mutableIntStateOf(7) }
    var key by remember { mutableStateOf("kcal") }
    var bars by remember { mutableStateOf(false) }
    var records by remember { mutableStateOf<List<NutritionRecord>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(client, hasPerms, refresh, range) {
        if (client != null && hasPerms) {
            try {
                val today = LocalDate.now()
                records = readNutrition(client, today.minusDays((range - 1).toLong()), today.plusDays(1))
                error = null
            } catch (e: Exception) {
                error = e.message
            }
        }
    }

    if (client == null || !hasPerms) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Grant Health Connect access to see your stats.", color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        return
    }

    val today = LocalDate.now()
    val days = remember(records, range) {
        (range - 1 downTo 0).map { off ->
            val d = today.minusDays(off.toLong())
            val rs = records.filter { it.day() == d }
            Day(d, totalsOf(rs), rs.size)
        }
    }
    val logged = days.filter { it.count > 0 }
    val nLogged = logged.size
    fun avg(k: String): Double = if (nLogged == 0) 0.0 else logged.sumOf { it.totals[k] ?: 0.0 } / nLogged
    val goal = { k: String -> goals[k] ?: 0.0 }

    var streak = 0
    for (d in days.asReversed()) {
        if (d.count > 0) streak++ else if (d.date != today) break
    }

    val shortLabels = days.mapIndexed { i, d ->
        if (range <= 7) d.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()).take(3)
        else if (i % 5 == 0 || i == days.lastIndex) d.date.dayOfMonth.toString() else ""
    }
    val fullLabels = days.map { it.date.format(DateTimeFormatter.ofPattern("EEE, d MMM")) }

    val trendKeys = listOf("kcal", "p", "c", "f", "fiber", "sugar", "na")
    val trendN = Nutrients.byKey.getValue(key)
    val trendValues = days.map { it.totals[key] ?: 0.0 }
    val trendColor = if (key == "kcal") cs.primary else Brand.colorFor(key, cs.secondary)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenHeader("Stats", "How you're trending")
        error?.let { Text("Couldn't read: $it", color = cs.error, fontSize = 13.sp) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7, 14, 30).forEach { r -> FancyChip("$r days", range == r) { range = r } }
        }

        // ── Summary tiles ──
        Reveal(0) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Avg calories", avg("kcal"), "kcal / day", cs.primary, Modifier.weight(1f))
                    StatTile("Avg protein", avg("p"), "g / day", Brand.Protein, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Days logged", nLogged.toDouble(), "of $range", Brand.Carbs, Modifier.weight(1f))
                    StatTile("Streak", streak.toDouble(), if (streak == 1) "day" else "days", Brand.Fat, Modifier.weight(1f))
                }
            }
        }

        // ── Trend ──
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
                    trendKeys.forEach { k ->
                        FancyChip(Nutrients.byKey.getValue(k).name, key == k) { key = k }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Crossfade(targetState = bars to key, label = "trend") { (b, k) ->
                    val vals = days.map { it.totals[k] ?: 0.0 }
                    val col = if (k == "kcal") cs.primary else Brand.colorFor(k, cs.secondary)
                    val nn = Nutrients.byKey.getValue(k)
                    if (b) {
                        BarChart(vals, shortLabels, fullLabels, goal(k), col, nn.unit, highlightOver = k in Nutrients.limitKeys || k == "kcal")
                    } else {
                        AreaChart(vals, shortLabels, fullLabels, goal(k), col, nn.unit)
                    }
                }
            }
        }

        // ── Macro split ──
        Reveal(2) {
            AppCard {
                SectionLabel("Macro split")
                Text("Share of calories, daily average", fontSize = 12.sp, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                val pK = avg("p") * 4
                val cK = avg("c") * 4
                val fK = avg("f") * 9
                val tot = pK + cK + fK
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DonutChart(
                        listOf(pK.toFloat(), cK.toFloat(), fK.toFloat()),
                        listOf(Brand.Protein, Brand.Carbs, Brand.Fat),
                        Modifier.size(140.dp), stroke = 20.dp
                    ) {
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

        // ── Meals ──
        Reveal(3) {
            AppCard {
                SectionLabel("Calories by meal")
                Spacer(Modifier.height(12.dp))
                val byMeal = listOf("Breakfast", "Lunch", "Dinner", "Snack").map { m ->
                    m to records.filter { Format.mealName(it) == m }.sumOf { it.energy?.inKilocalories ?: 0.0 }
                }
                val total = byMeal.sumOf { it.second }
                if (total <= 0) {
                    Text("No data in this range.", color = cs.onSurfaceVariant, fontSize = 14.sp)
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

        // ── Consistency heatmap ──
        Reveal(4) {
            AppCard {
                SectionLabel("Calorie consistency")
                Text("Darker = closer to goal, amber = over", fontSize = 12.sp, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Heatmap(days.map { Triple(it.date, it.totals["kcal"] ?: 0.0, it.count) }, goal("kcal"))
            }
        }

        // ── Insights ──
        Reveal(5) {
            AppCard {
                SectionLabel("Insights")
                Spacer(Modifier.height(8.dp))
                val proteinDays = logged.count { (it.totals["p"] ?: 0.0) >= 0.9 * goal("p") }
                val kcalDays = logged.count {
                    val v = it.totals["kcal"] ?: 0.0
                    goal("kcal") > 0 && v >= 0.85 * goal("kcal") && v <= 1.15 * goal("kcal")
                }
                Insight("Protein goal (90%+) reached on $proteinDays of $nLogged logged days", Brand.Protein)
                Insight("Calories within 15% of goal on $kcalDays days", cs.primary)
                Insight("Average fiber ${Format.r1(avg("fiber"))} g vs goal ${Format.num(goal("fiber"))} g", Brand.Fiber)
                Insight("Average sodium ${Format.int(avg("na"))} mg vs limit ${Format.int(goal("na"))} mg", Brand.Carbs)
            }
        }
        Spacer(Modifier.height(100.dp))
    }
}

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
private fun Insight(text: String, color: Color) {
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 14.sp)
    }
}

@Composable
private fun Heatmap(cells: List<Triple<LocalDate, Double, Int>>, goal: Double) {
    val cs = MaterialTheme.colorScheme
    if (cells.isEmpty()) return
    val lead = cells.first().first.dayOfWeek.value - 1
    val padded: List<Triple<LocalDate, Double, Int>?> = List(lead) { null } + cells
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
                        val (d, kcal, count) = c
                        val pct = if (goal > 0) kcal / goal else 0.0
                        val target = when {
                            count == 0 -> cs.surfaceVariant
                            pct > 1.15 -> Brand.Carbs
                            pct >= 0.85 -> cs.primary
                            else -> cs.primary.copy(alpha = 0.35f)
                        }
                        val bg by animateColorAsState(target, label = "heat")
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(bg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                d.dayOfMonth.toString(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                color = if (count == 0) cs.onSurfaceVariant else if (pct in 0.85..1.15) cs.onPrimary else cs.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
