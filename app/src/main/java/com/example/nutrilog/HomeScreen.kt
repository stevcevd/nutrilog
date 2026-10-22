package com.example.nutrilog

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    client: HealthConnectClient?,
    hasPerms: Boolean,
    goals: Map<String, Double>,
    waterGoal: Double,
    settings: Settings,
    refresh: Int,
    onGoMe: () -> Unit,
    onGoLog: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var records by remember { mutableStateOf<List<NutritionRecord>>(emptyList()) }
    var water by remember { mutableStateOf<List<HydrationRecord>>(emptyList()) }
    var tick by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val today = remember { LocalDate.now() }

    LaunchedEffect(client, hasPerms, refresh, tick) {
        if (client != null && hasPerms) {
            try {
                records = readNutrition(client, today, today.plusDays(1))
                water = readWater(client, today)
                error = null
            } catch (e: Exception) {
                error = e.message
            }
        }
    }

    val totals = remember(records) { totalsOf(records) }
    val g = { k: String -> goals[k] ?: 0.0 }
    val t = { k: String -> totals[k] ?: 0.0 }
    val frac = { k: String -> if (g(k) > 0) (t(k) / g(k)).toFloat() else 0f }
    val waterMl = water.sumOf { it.volume.inMilliliters }

    val hour = remember { LocalTime.now().hour }
    val greeting = when {
        hour < 5 -> "Good night"
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), fontSize = 13.sp, color = cs.onSurfaceVariant)
                val who = settings.profile.nick.ifBlank { settings.profile.first }
                Text(if (who.isBlank()) greeting else "$greeting, $who", fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Avatar(AvatarSpec.parse(settings.profile.avatar), 48.dp, Modifier.bounceClick(true, onGoMe))
        }
        error?.let { Text("Couldn't read data: $it", color = cs.error, fontSize = 13.sp) }

        // ── Hero ──
        Reveal(0) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF059669), Color(0xFF0F766E), Color(0xFF155E75))))
                    .drawBehind {
                        drawCircle(Color.White.copy(alpha = 0.07f), size.width * 0.5f, Offset(size.width * 0.95f, 0f))
                        drawCircle(Color.White.copy(alpha = 0.05f), size.width * 0.35f, Offset(0f, size.height))
                    }
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ActivityRings(
                        progress = listOf(frac("kcal"), frac("p"), frac("c"), frac("f")),
                        colors = listOf(Color.White, Color(0xFF93C5FD), Color(0xFFFCD34D), Color(0xFFF9A8D4)),
                        modifier = Modifier.size(150.dp),
                        stroke = 11.dp, gap = 4.dp,
                        track = Color.White.copy(alpha = 0.18f)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CountUp(t("kcal"), fontSize = 26.sp, color = Color.White)
                            Text("kcal eaten", fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f))
                        }
                    }
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Remaining", fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f))
                        Row(verticalAlignment = Alignment.Bottom) {
                            CountUp(maxOf(g("kcal") - t("kcal"), 0.0), fontSize = 30.sp, color = Color.White)
                            Text(" kcal", fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f), modifier = Modifier.padding(bottom = 4.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                        HeroMacro("Protein", t("p"), g("p"), Color(0xFF93C5FD))
                        HeroMacro("Carbs", t("c"), g("c"), Color(0xFFFCD34D))
                        HeroMacro("Fat", t("f"), g("f"), Color(0xFFF9A8D4))
                    }
                }
            }
        }

        Reveal(1) { FastingCard(client, hasPerms, refresh, settings.fastingTarget) }

        // ── Mini rings ──
        Reveal(1) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("fiber", "sugar", "na").forEach { k ->
                    MiniStat(Nutrients.byKey.getValue(k), t(k), g(k), Modifier.weight(1f))
                }
            }
        }

        // ── Water ──
        Reveal(2) {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WaterGlass(waterMl.let { if (waterGoal > 0) (it / waterGoal).toFloat() else 0f }, Modifier.width(62.dp).height(104.dp))
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Hydration", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.Bottom) {
                            CountUp(waterMl, fontSize = 28.sp, color = Brand.Water)
                            Text(" / ${Format.int(waterGoal)} ml", fontSize = 13.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            listOf(250.0, 500.0).forEach { ml ->
                                FancyButton(
                                    "+${ml.toInt()}", compact = true, accent = Brand.Water, onAccent = Color(0xFF04202D),
                                    enabled = client != null && hasPerms
                                ) {
                                    scope.launch {
                                        try { addWater(client!!, ml); tick++ } catch (e: Exception) { error = "Water: ${e.message}" }
                                    }
                                }
                            }
                            if (water.isNotEmpty()) {
                                FancyButton("", icon = R.drawable.ic_undo, compact = true, filled = false, accent = Brand.Water) {
                                    scope.launch {
                                        try {
                                            val last = water.maxByOrNull { it.startTime }
                                            if (last != null && client != null) {
                                                settings.addTombstone("w|${last.startTime.epochSecond}")
                                                client.deleteRecords(HydrationRecord::class, listOf(last.metadata.id), emptyList())
                                                tick++
                                            }
                                        } catch (e: Exception) { error = "Undo failed: ${e.message}" }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Meals today ──
        Reveal(3) {
            AppCard {
                Text("Today's meals", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                if (records.isEmpty()) {
                    Text("Nothing logged yet today.", color = cs.onSurfaceVariant, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    FancyButton("Log a meal", icon = R.drawable.ic_plus, onClick = onGoLog)
                } else {
                    records.forEach { r ->
                        val meal = Format.mealName(r)
                        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(4.dp).height(38.dp).clip(RoundedCornerShape(2.dp)).background(Brand.mealColor(meal)))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.name ?: "", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
                                Text(
                                    "${Format.localStart(r).format(DateTimeFormatter.ofPattern("HH:mm"))} · $meal",
                                    fontSize = 12.sp, color = cs.onSurfaceVariant
                                )
                            }
                            Text("${Format.int(r.energy?.inKilocalories ?: 0.0)} kcal", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(100.dp))
    }
}

@Composable
private fun HeroMacro(name: String, value: Double, goal: Double, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(name, Modifier.weight(1f), fontSize = 13.sp, color = Color.White)
        Text("${Format.int(value)} / ${Format.int(goal)} g", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
    }
}

@Composable
private fun MiniStat(n: Nutrient, value: Double, goal: Double, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val f = if (goal > 0) (value / goal).toFloat() else 0f
    val over = goal > 0 && value > goal && n.key in Nutrients.limitKeys
    val c = if (over) Brand.Warn else Brand.colorFor(n.key, cs.primary)
    AppCard(modifier, padding = 14.dp) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            RingChart(f, c, Modifier.size(70.dp), stroke = 8.dp) {
                Text("${(f * 100).toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text(n.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "${Format.int(value)} / ${Format.int(goal)} ${n.unit}",
                fontSize = 11.sp, color = cs.onSurfaceVariant, textAlign = TextAlign.Center
            )
        }
    }
}
