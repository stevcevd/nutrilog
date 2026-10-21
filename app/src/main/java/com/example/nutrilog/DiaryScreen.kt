package com.example.nutrilog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.NutritionRecord
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DiaryScreen(
    client: HealthConnectClient?,
    hasPerms: Boolean,
    goals: Map<String, Double>,
    refresh: Int,
    onChanged: () -> Unit,
    onRelog: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var date by remember { mutableStateOf(LocalDate.now()) }
    var records by remember { mutableStateOf<List<NutritionRecord>>(emptyList()) }
    var tick by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var showAll by remember { mutableStateOf(false) }

    LaunchedEffect(date, hasPerms, tick, client, refresh) {
        if (client != null && hasPerms) {
            try {
                records = readNutrition(client, date, date.plusDays(1))
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
    val mainKeys = listOf("kcal", "p", "c", "f", "fiber", "sugar")

    if (client == null || !hasPerms) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Grant Health Connect access to view your entries.", color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    } else {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { ScreenHeader("Diary", date.format(DateTimeFormatter.ofPattern("MMMM yyyy"))) }
            item { DayStrip(date) { date = it; expanded = null } }
            item {
                error?.let { Text("Couldn't read: $it", color = cs.error, fontSize = 13.sp) }
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ActivityRings(
                            listOf(frac("kcal"), frac("p"), frac("c"), frac("f")),
                            listOf(cs.primary, Brand.Protein, Brand.Carbs, Brand.Fat),
                            Modifier.size(128.dp), stroke = 9.dp, gap = 3.dp
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CountUp(t("kcal"), fontSize = 22.sp)
                                Text("of ${Format.int(g("kcal"))}", fontSize = 11.sp, color = cs.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            listOf("p" to "Protein", "c" to "Carbs", "f" to "Fat").forEach { (k, label) ->
                                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(10.dp).clip(CircleShape).background(Brand.colorFor(k, cs.primary)))
                                    Spacer(Modifier.width(8.dp))
                                    Text(label, Modifier.weight(1f), fontSize = 14.sp)
                                    Text("${Format.r1(t(k))} / ${Format.num(g(k))} g", fontSize = 12.sp, color = cs.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Nutrients.byKey.getValue("fiber").let { NutrientBar(it, t("fiber"), g("fiber")) }
                    Nutrients.byKey.getValue("sugar").let { NutrientBar(it, t("sugar"), g("sugar")) }
                    Spacer(Modifier.height(6.dp))
                    FancyButton(
                        if (showAll) "Hide other nutrients" else "Show all nutrients",
                        filled = false, compact = true
                    ) { showAll = !showAll }
                    AnimatedVisibility(showAll, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        Column {
                            Nutrients.groups.forEach { grp ->
                                val list = Nutrients.all.filter { it.group == grp && it.key !in mainKeys }
                                if (list.isNotEmpty()) {
                                    Text(grp, Modifier.padding(top = 14.dp, bottom = 2.dp), fontWeight = FontWeight.SemiBold, color = cs.primary, fontSize = 14.sp)
                                    list.forEach { n -> NutrientBar(n, t(n.key), g(n.key)) }
                                }
                            }
                        }
                    }
                }
            }
            item { SectionLabel("Entries (${records.size})", Modifier.padding(top = 4.dp)) }
            if (records.isEmpty()) {
                item {
                    AppCard {
                        Text("Nothing logged on this day.", color = cs.onSurfaceVariant)
                    }
                }
            }
            items(records, key = { it.metadata.id }) { r ->
                val id = r.metadata.id
                EntryCard(
                    r, expanded == id,
                    onToggle = { expanded = if (expanded == id) null else id },
                    onCopy = { onRelog(Format.entryLine(r)) },
                    onDelete = {
                        scope.launch {
                            try {
                                client.deleteRecords(NutritionRecord::class, listOf(id), emptyList())
                                expanded = null
                                tick++
                                onChanged()
                            } catch (e: Exception) {
                                error = "Delete failed: ${e.message}"
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun DayStrip(selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val weekStart = selected.minusDays((selected.dayOfWeek.value - 1).toLong())
    val today = LocalDate.now()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(CircleShape).clickable { onSelect(selected.minusDays(7)) }, contentAlignment = Alignment.Center) {
            NIcon(R.drawable.ic_chevron_left, cs.onSurfaceVariant, 20.dp)
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 0 until 7) {
                val d = weekStart.plusDays(i.toLong())
                val sel = d == selected
                val bg by animateColorAsState(if (sel) cs.primary else Color.Transparent, label = "daybg")
                val fg by animateColorAsState(if (sel) cs.onPrimary else cs.onSurface, label = "dayfg")
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(bg)
                        .clickable { onSelect(d) }.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()).take(3),
                        fontSize = 10.sp, color = if (sel) fg else cs.onSurfaceVariant
                    )
                    Text(d.dayOfMonth.toString(), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = fg)
                    Box(
                        Modifier.padding(top = 3.dp).size(4.dp).clip(CircleShape)
                            .background(if (d == today) (if (sel) cs.onPrimary else cs.primary) else Color.Transparent)
                    )
                }
            }
        }
        Box(Modifier.size(34.dp).clip(CircleShape).clickable { onSelect(selected.plusDays(7)) }, contentAlignment = Alignment.Center) {
            NIcon(R.drawable.ic_chevron_right, cs.onSurfaceVariant, 20.dp)
        }
    }
}

@Composable
private fun EntryCard(
    r: NutritionRecord,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val meal = Format.mealName(r)
    val mc = Brand.mealColor(meal)
    val rot by animateFloatAsState(if (expanded) 90f else 0f, label = "chev")
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = cs.surface,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, cs.outline.copy(alpha = 0.18f)),
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Column(Modifier.clickable(onClick = onToggle).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(width = 52.dp, height = 46.dp).clip(RoundedCornerShape(15.dp)).background(mc.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(Format.localStart(r).format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = mc)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.name ?: "", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(
                        "$meal · P ${Format.r1(r.protein?.inGrams ?: 0.0)} · C ${Format.r1(r.totalCarbohydrate?.inGrams ?: 0.0)} · F ${Format.r1(r.totalFat?.inGrams ?: 0.0)}",
                        fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 1
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Format.int(r.energy?.inKilocalories ?: 0.0), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("kcal", fontSize = 11.sp, color = cs.onSurfaceVariant)
                }
                Spacer(Modifier.width(6.dp))
                NIcon(R.drawable.ic_chevron_right, cs.onSurfaceVariant, 18.dp, Modifier.rotate(rot))
            }
            AnimatedVisibility(expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = cs.outline.copy(alpha = 0.25f))
                    Spacer(Modifier.height(8.dp))
                    Nutrients.all.forEach { n ->
                        val v = n.read(r) ?: 0.0
                        if (v > 0.0) {
                            Row(Modifier.padding(vertical = 2.dp)) {
                                Text(n.name, Modifier.weight(1f), fontSize = 13.sp, color = cs.onSurfaceVariant)
                                Text("${Format.r1(v)} ${n.unit}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FancyButton("Copy to Log", Modifier.weight(1f), icon = R.drawable.ic_copy, compact = true, filled = false, onClick = onCopy)
                        FancyButton("Delete", Modifier.weight(1f), icon = R.drawable.ic_trash, compact = true, filled = false, danger = true, onClick = onDelete)
                    }
                }
            }
        }
    }
}
