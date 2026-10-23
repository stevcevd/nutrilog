package com.example.nutrilog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    var editing by remember { mutableStateOf(false) }
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
    val waterMl = water.sumOf { it.volume.inMilliliters }

    val hour = remember { LocalTime.now().hour }
    val greeting = when {
        hour < 5 -> "Good night"
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }

    val addWaterAction: (Double) -> Unit = { ml ->
        scope.launch {
            try { addWater(client!!, ml); tick++ } catch (e: Exception) { error = "Water: ${e.message}" }
        }
    }
    val undoWater: () -> Unit = {
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

    // while editing, show hidden sections too so they can be switched back on
    val order = settings.homeOrder.filter { editing || it !in settings.homeHidden }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.weight(1f).pointerInput(Unit) { detectTapGestures(onLongPress = { editing = true }) }
            ) {
                Text(today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), fontSize = 13.sp, color = cs.onSurfaceVariant)
                val who = settings.profile.nick.ifBlank { settings.profile.first }
                Text(if (who.isBlank()) greeting else "$greeting, $who", fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Avatar(AvatarSpec.parse(settings.profile.avatar), 48.dp, Modifier.bounceClick(true, onGoMe))
        }
        error?.let { Text("Couldn't read data: $it", color = cs.error, fontSize = 13.sp) }

        AnimatedVisibility(editing, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Editing Home. Changes apply right away.", fontSize = 13.sp, color = cs.primary, fontWeight = FontWeight.SemiBold)
                HomeEditor(settings)
                FancyButton("Done", icon = R.drawable.ic_check) { editing = false }
            }
        }

        order.forEachIndexed { idx, id ->
            Reveal(idx) {
                when (id) {
                    "hero" -> HeroSection(settings, t, g)
                    "fasting" -> FastingCard(client, hasPerms, refresh, settings.fastingTarget)
                    "highlights" -> HighlightsSection(settings, t, g)
                    "water" -> WaterSection(client != null && hasPerms, water.isNotEmpty(), waterMl, waterGoal, addWaterAction, undoWater)
                    "meals" -> MealsSection(records, onGoLog)
                }
            }
        }
        Spacer(Modifier.height(100.dp))
    }
}

@Composable
private fun HeroSection(settings: Settings, t: (String) -> Double, g: (String) -> Double) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF059669), Color(0xFF0F766E), Color(0xFF155E75))))
            .drawBehind {
                drawCircle(Color.White.copy(alpha = 0.07f), size.width * 0.5f, Offset(size.width * 0.95f, 0f))
                drawCircle(Color.White.copy(alpha = 0.05f), size.width * 0.35f, Offset(0f, size.height))
            }
            .padding(20.dp)
    ) {
        CalorieSummary(
            style = settings.ringStyle, hero = true, showRemaining = true,
            kcal = t("kcal"), kcalGoal = g("kcal"),
            p = t("p"), pGoal = g("p"), c = t("c"), cGoal = g("c"), f = t("f"), fGoal = g("f")
        )
    }
}

@Composable
private fun HighlightsSection(settings: Settings, t: (String) -> Double, g: (String) -> Double) {
    val keys = settings.highlights
    if (keys.isEmpty()) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        keys.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { k -> MiniStat(Nutrients.byKey.getValue(k), t(k), g(k), Modifier.weight(1f)) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun WaterSection(
    canAct: Boolean,
    hasLogs: Boolean,
    waterMl: Double,
    waterGoal: Double,
    onAdd: (Double) -> Unit,
    onUndo: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WaterGlass(if (waterGoal > 0) (waterMl / waterGoal).toFloat() else 0f, Modifier.width(62.dp).height(104.dp))
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
                        FancyButton("+${ml.toInt()}", compact = true, accent = Brand.Water, onAccent = Color(0xFF04202D), enabled = canAct) { onAdd(ml) }
                    }
                    if (hasLogs) {
                        FancyButton("", icon = R.drawable.ic_undo, compact = true, filled = false, accent = Brand.Water, onClick = onUndo)
                    }
                }
            }
        }
    }
}

@Composable
private fun MealsSection(records: List<NutritionRecord>, onGoLog: () -> Unit) {
    val cs = MaterialTheme.colorScheme
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
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(r.name ?: "", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

private fun fmtVal(n: Nutrient, v: Double) = if (n.unit == "g") Format.r1(v) else Format.int(v)

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
            Text(n.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${fmtVal(n, value)} / ${fmtVal(n, goal)} ${n.unit}",
                fontSize = 11.sp, color = cs.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 1
            )
        }
    }
}
