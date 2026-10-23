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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
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
    settings: Settings,
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
    var editingId by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }
    var editError by remember { mutableStateOf("") }
    var editBusy by remember { mutableStateOf(false) }

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

    fun saveEdit(old: NutritionRecord) {
        if (client == null) return
        scope.launch {
            editBusy = true
            try {
                val line = editText.lines().firstOrNull { it.isNotBlank() } ?: throw IllegalArgumentException("Nothing to save")
                // fresh record id so the replacement is always a brand new record
                val rec = NutritionParser.parseLine(line, idSalt = "#e" + System.currentTimeMillis())
                if (Format.entryLine(rec) == Format.entryLine(old)) {
                    editError = "No changes to save"
                } else {
                    val oldKey = Format.entryKey(old)
                    client.insertRecords(listOf(rec))
                    client.deleteRecords(NutritionRecord::class, listOf(old.metadata.id), emptyList())
                    if (oldKey != Format.entryKey(rec)) settings.addTombstone(oldKey)
                    editingId = null
                    editError = ""
                    tick++
                    onChanged()
                }
            } catch (e: Exception) {
                editError = e.message ?: "Couldn't save"
            }
            editBusy = false
        }
    }

    val totals = remember(records) { totalsOf(records) }
    val g = { k: String -> goals[k] ?: 0.0 }
    val t = { k: String -> totals[k] ?: 0.0 }
    val frac = { k: String -> if (g(k) > 0) (t(k) / g(k)).toFloat() else 0f }
    val mainKeys = listOf("kcal", "p", "c", "f") + settings.highlights

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
            item { DayStrip(date) { date = it; expanded = null; editingId = null } }
            item {
                error?.let { Text("Couldn't read: $it", color = cs.error, fontSize = 13.sp) }
                AppCard {
                    CalorieSummary(
                        style = settings.ringStyle, hero = false, showRemaining = false,
                        kcal = t("kcal"), kcalGoal = g("kcal"),
                        p = t("p"), pGoal = g("p"), c = t("c"), cGoal = g("c"), f = t("f"), fGoal = g("f")
                    )
                    val extra = settings.highlights.filter { it !in listOf("p", "c", "f") }
                    if (extra.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        extra.forEach { k -> NutrientBar(Nutrients.byKey.getValue(k), t(k), g(k)) }
                    }
                    Spacer(Modifier.height(6.dp))
                    FancyButton(if (showAll) "Hide other nutrients" else "Show all nutrients", filled = false, compact = true) { showAll = !showAll }
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
                item { AppCard { Text("Nothing logged on this day.", color = cs.onSurfaceVariant) } }
            }
            items(records, key = { it.metadata.id }) { r ->
                val id = r.metadata.id
                EntryCard(
                    r = r,
                    expanded = expanded == id,
                    editing = editingId == id,
                    editText = editText,
                    editError = editError,
                    busy = editBusy,
                    onToggle = { if (editingId != id) expanded = if (expanded == id) null else id },
                    onEditText = { editText = it; editError = "" },
                    onStartEdit = { editingId = id; editText = Format.entryLine(r); editError = "" },
                    onCancelEdit = { editingId = null; editError = "" },
                    onSaveEdit = { saveEdit(r) },
                    onCopy = { onRelog(Format.entryLine(r)) },
                    onDelete = {
                        scope.launch {
                            try {
                                settings.addTombstone(Format.entryKey(r))
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
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(bg).clickable { onSelect(d) }.padding(vertical = 8.dp),
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
    editing: Boolean,
    editText: String,
    editError: String,
    busy: Boolean,
    onToggle: () -> Unit,
    onEditText: (String) -> Unit,
    onStartEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onSaveEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val meal = Format.mealName(r)
    val mc = Brand.mealColor(meal)
    val open = expanded || editing
    val rot by animateFloatAsState(if (open) 90f else 0f, label = "chev")
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = cs.surface,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, if (editing) cs.primary else cs.outline.copy(alpha = 0.18f)),
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
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(r.name ?: "", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            AnimatedVisibility(open, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = cs.outline.copy(alpha = 0.25f))
                    Spacer(Modifier.height(8.dp))
                    if (editing) {
                        Text("Edit the line, same format as Log. Change the time, name, calories or any nutrient.", fontSize = 12.sp, color = cs.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = editText,
                            onValueChange = onEditText,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                            shape = RoundedCornerShape(16.dp)
                        )
                        if (editError.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(editError, fontSize = 13.sp, color = cs.error, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FancyButton(if (busy) "Saving..." else "Save changes", Modifier.weight(1f), icon = R.drawable.ic_check, compact = true, enabled = !busy, onClick = onSaveEdit)
                            FancyButton("Cancel", Modifier.weight(1f), compact = true, filled = false, onClick = onCancelEdit)
                        }
                    } else {
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FancyButton("Edit", Modifier.weight(1f), icon = R.drawable.ic_edit, compact = true, onClick = onStartEdit)
                            FancyButton("Copy", Modifier.weight(1f), icon = R.drawable.ic_copy, compact = true, filled = false, onClick = onCopy)
                            FancyButton("Delete", Modifier.weight(1f), icon = R.drawable.ic_trash, compact = true, filled = false, danger = true, onClick = onDelete)
                        }
                    }
                }
            }
        }
    }
}
