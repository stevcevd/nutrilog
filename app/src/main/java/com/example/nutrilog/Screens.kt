package com.example.nutrilog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// ───────────────────────── Shared widgets ─────────────────────────

@Composable
fun NutrientBar(n: Nutrient, value: Double, goal: Double) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row {
            Text(n.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            val g = if (goal > 0) " / ${Format.num(goal)}" else ""
            val pct = if (goal > 0) "  (${(value / goal * 100).toInt()}%)" else ""
            Text("${Format.num(value)}$g ${n.unit}$pct", style = MaterialTheme.typography.bodySmall)
        }
        if (goal > 0) {
            val over = value > goal && n.key in Nutrients.limitKeys
            LinearProgressIndicator(
                progress = { (value / goal).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text, Modifier.padding(top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}

// ───────────────────────── Log tab ─────────────────────────

@Composable
fun LogScreen(
    client: HealthConnectClient?,
    hasPerms: Boolean,
    text: String,
    onText: (String) -> Unit,
    requestPerms: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<List<String>>(emptyList()) }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }
    var status by remember { mutableStateOf("") }

    fun parseAll(): List<NutritionRecord> {
        val recs = mutableListOf<NutritionRecord>()
        val errs = mutableListOf<String>()
        val prev = mutableListOf<String>()
        text.lines().forEachIndexed { i, line ->
            if (line.isBlank()) return@forEachIndexed
            try {
                val r = NutritionParser.parseLine(line)
                recs += r
                prev += "${Format.localStart(r).format(DateTimeFormatter.ofPattern("d MMM HH:mm"))} · ${Format.mealName(r)} · ${r.name} — " +
                    "${Format.num(r.energy?.inKilocalories ?: 0.0)} kcal, " +
                    "P ${Format.num(r.protein?.inGrams ?: 0.0)}g, " +
                    "C ${Format.num(r.totalCarbohydrate?.inGrams ?: 0.0)}g, " +
                    "F ${Format.num(r.totalFat?.inGrams ?: 0.0)}g"
            } catch (e: Exception) {
                errs += "Line ${i + 1}: ${e.message}"
            }
        }
        preview = prev
        errors = errs
        return recs
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Log food", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = text,
            onValueChange = onText,
            modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
            label = { Text("Paste entries (one per line)") },
            placeholder = { Text("2026-10-19 08:30 Breakfast (Banica & Yogurt): 1446 kcal | P 46.4g | C 137.6g | F 78.0g") }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                val t = "$now Snack (Name): 0 kcal | P 0g | C 0g | F 0g | Fiber 0g | Sugar 0g | Na 0mg"
                onText(if (text.isBlank()) t else text.trimEnd() + "\n" + t)
            }) { Text("Template") }
            OutlinedButton(onClick = { status = ""; parseAll() }) { Text("Preview") }
            Button(
                enabled = client != null && text.isNotBlank(),
                onClick = {
                    status = ""
                    val recs = parseAll()
                    if (errors.isEmpty() && recs.isNotEmpty() && client != null) {
                        if (!hasPerms) {
                            status = "Grant access first, then press Save again."
                            requestPerms()
                        } else {
                            scope.launch {
                                status = try {
                                    client.insertRecords(recs)
                                    onText("")
                                    preview = emptyList()
                                    "Saved ${recs.size} entr${if (recs.size == 1) "y" else "ies"} ✓"
                                } catch (e: Exception) {
                                    "Save failed: ${e.message}"
                                }
                            }
                        }
                    }
                }
            ) { Text("Save") }
        }

        errors.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
        if (preview.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Preview", fontWeight = FontWeight.SemiBold)
                    preview.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodyLarge)
    }
}

// ───────────────────────── Diary tab ─────────────────────────

@Composable
fun DiaryScreen(
    client: HealthConnectClient?,
    hasPerms: Boolean,
    goals: Map<String, Double>,
    onRelog: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var date by remember { mutableStateOf(LocalDate.now()) }
    var records by remember { mutableStateOf<List<NutritionRecord>>(emptyList()) }
    var tick by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var showAll by remember { mutableStateOf(false) }

    LaunchedEffect(date, hasPerms, tick, client) {
        if (client != null && hasPerms) {
            try {
                val resp = client.readRecords(
                    ReadRecordsRequest(
                        NutritionRecord::class,
                        TimeRangeFilter.between(date.atStartOfDay(), date.plusDays(1).atStartOfDay())
                    )
                )
                records = resp.records.sortedBy { it.startTime }
                error = null
            } catch (e: Exception) {
                error = e.message
            }
        }
    }

    if (client == null || !hasPerms) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Grant Health Connect access to view your entries.")
        }
        return
    }

    val totals = remember(records) {
        Nutrients.all.associate { n -> n.key to records.sumOf { r -> n.read(r) ?: 0.0 } }
    }
    val mainKeys = listOf("kcal", "p", "c", "f", "fiber", "sugar")

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { date = date.minusDays(1) }) { Text("‹") }
                Text(
                    date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")),
                    Modifier.weight(1f), style = MaterialTheme.typography.titleMedium
                )
                TextButton(onClick = { date = date.plusDays(1) }) { Text("›") }
                TextButton(onClick = { date = LocalDate.now() }) { Text("Today") }
            }
            error?.let { Text("Couldn't read: $it", color = MaterialTheme.colorScheme.error) }
        }

        item {
            Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Daily progress", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    mainKeys.forEach { k ->
                        val n = Nutrients.byKey.getValue(k)
                        NutrientBar(n, totals[k] ?: 0.0, goals[k] ?: 0.0)
                    }
                    TextButton(onClick = { showAll = !showAll }) {
                        Text(if (showAll) "Hide other nutrients" else "Show all nutrients")
                    }
                    if (showAll) {
                        Nutrients.groups.forEach { g ->
                            val list = Nutrients.all.filter { it.group == g && it.key !in mainKeys }
                            if (list.isNotEmpty()) {
                                SectionTitle(g)
                                list.forEach { n -> NutrientBar(n, totals[n.key] ?: 0.0, goals[n.key] ?: 0.0) }
                            }
                        }
                    }
                }
            }
            Text(
                "Entries (${records.size})",
                Modifier.padding(top = 16.dp, bottom = 4.dp),
                style = MaterialTheme.typography.titleMedium
            )
            if (records.isEmpty()) Text("Nothing logged on this day.")
        }

        items(records, key = { it.metadata.id }) { r ->
            val id = r.metadata.id
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { expanded = if (expanded == id) null else id }) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "${Format.localStart(r).format(DateTimeFormatter.ofPattern("HH:mm"))} · ${Format.mealName(r)} · ${r.name ?: ""}",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${Format.num(r.energy?.inKilocalories ?: 0.0)} kcal · " +
                            "P ${Format.num(r.protein?.inGrams ?: 0.0)}g · " +
                            "C ${Format.num(r.totalCarbohydrate?.inGrams ?: 0.0)}g · " +
                            "F ${Format.num(r.totalFat?.inGrams ?: 0.0)}g",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (expanded == id) {
                        Spacer(Modifier.height(8.dp))
                        Nutrients.all.forEach { n ->
                            val v = n.read(r) ?: 0.0
                            if (v > 0.0) {
                                Row {
                                    Text(n.name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                    Text("${Format.num(v)} ${n.unit}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onRelog(Format.entryLine(r)) }) { Text("Copy to Log") }
                            OutlinedButton(onClick = {
                                scope.launch {
                                    try {
                                        client.deleteRecords(NutritionRecord::class, listOf(id), emptyList())
                                        expanded = null
                                        tick++
                                    } catch (e: Exception) {
                                        error = "Delete failed: ${e.message}"
                                    }
                                }
                            }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ───────────────────────── Goals tab ─────────────────────────

@Composable
fun GoalsScreen(goals: Map<String, Double>, onSave: (Map<String, Double>) -> Unit) {
    val clipboard = LocalClipboardManager.current
    val fields = remember(goals) {
        mutableStateMapOf<String, String>().apply {
            Nutrients.all.forEach { put(it.key, Format.num(goals[it.key] ?: 0.0)) }
        }
    }
    var importText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Daily goals", style = MaterialTheme.typography.headlineMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val out = HashMap<String, Double>()
                var bad: String? = null
                Nutrients.all.forEach { n ->
                    val v = (fields[n.key] ?: "").trim().replace(',', '.').toDoubleOrNull()
                    if (v == null || v < 0) bad = n.name else out[n.key] = v
                }
                if (bad != null) {
                    isError = true; message = "Invalid number for $bad"
                } else {
                    onSave(out); isError = false; message = "Goals saved ✓"
                }
            }) { Text("Save goals") }
            OutlinedButton(onClick = {
                clipboard.setText(AnnotatedString(Format.goalsString(goals)))
                isError = false; message = "Goals string copied"
            }) { Text("Copy as text") }
        }
        if (message.isNotEmpty()) {
            Text(message, color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }

        Nutrients.groups.forEach { g ->
            SectionTitle(g)
            Nutrients.all.filter { it.group == g }.forEach { n ->
                OutlinedTextField(
                    value = fields[n.key] ?: "",
                    onValueChange = { fields[n.key] = it },
                    label = { Text("${n.name} (${n.unit})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        SectionTitle("Import from text")
        OutlinedTextField(
            value = importText,
            onValueChange = { importText = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            label = { Text("Paste a \"Daily Targets\" line") }
        )
        Button(onClick = {
            try {
                onSave(NutritionParser.parseGoals(importText))
                importText = ""
                isError = false; message = "Goals imported ✓"
            } catch (e: Exception) {
                isError = true; message = e.message ?: "Invalid goals text"
            }
        }) { Text("Import goals") }
        Spacer(Modifier.height(24.dp))
    }
}
