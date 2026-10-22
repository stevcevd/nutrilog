package com.example.nutrilog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.NutritionRecord
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun LogScreen(
    client: HealthConnectClient?,
    hasPerms: Boolean,
    text: String,
    onText: (String) -> Unit,
    refresh: Int,
    onSaved: () -> Unit,
    requestPerms: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var preview by remember { mutableStateOf<List<String>>(emptyList()) }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var statusOk by remember { mutableStateOf(true) }
    var recent by remember { mutableStateOf<List<NutritionRecord>>(emptyList()) }

    LaunchedEffect(client, hasPerms, refresh) {
        if (client != null && hasPerms) {
            try {
                val today = LocalDate.now()
                recent = readNutrition(client, today.minusDays(30), today.plusDays(1))
                    .asReversed()
                    .distinctBy { "${it.name}|${Format.num(it.energy?.inKilocalories ?: 0.0)}" }
                    .take(12)
            } catch (e: Exception) { }
        }
    }

    fun parseAll(): List<NutritionRecord> {
        val recs = mutableListOf<NutritionRecord>()
        val errs = mutableListOf<String>()
        val prev = mutableListOf<String>()
        text.lines().forEachIndexed { i, line ->
            if (line.isBlank()) return@forEachIndexed
            try {
                val r = NutritionParser.parseLine(line)
                recs += r
                prev += "${Format.localStart(r).format(DateTimeFormatter.ofPattern("d MMM HH:mm"))} · ${Format.mealName(r)} · ${r.name}\n" +
                    "${Format.int(r.energy?.inKilocalories ?: 0.0)} kcal · P ${Format.r1(r.protein?.inGrams ?: 0.0)}g · " +
                    "C ${Format.r1(r.totalCarbohydrate?.inGrams ?: 0.0)}g · F ${Format.r1(r.totalFat?.inGrams ?: 0.0)}g"
            } catch (e: Exception) {
                errs += "Line ${i + 1}: ${e.message}"
            }
        }
        preview = prev
        errors = errs
        return recs
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenHeader("Log food", "Paste, preview, save")

        // AI helper
        Reveal(0) {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("AI helper", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Copy a prompt, paste it into any chatbot, then describe your meal. Paste its reply back here.",
                            fontSize = 12.sp, color = cs.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    FancyButton("Copy prompt", icon = R.drawable.ic_copy, compact = true) {
                        clipboard.setText(AnnotatedString(AiPrompt.build()))
                        statusOk = true
                        status = "AI prompt copied"
                    }
                }
            }
        }

        // Entry box
        Reveal(1) {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Entries", Modifier.weight(1f))
                    FancyButton("Paste", compact = true, filled = false) {
                        val clean = Paste.clean(clipboard.getText()?.text ?: "")
                        if (clean.isBlank()) {
                            statusOk = false
                            status = "No valid entry lines found on the clipboard"
                        } else {
                            onText(if (text.isBlank()) clean else text.trimEnd() + "\n" + clean)
                            statusOk = true
                            status = "Pasted ${clean.lines().size} line(s)"
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    FancyButton("Template", compact = true, filled = false) {
                        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        val t = "$now Snack (Name): 0 kcal | P 0g | C 0g | F 0g | Fiber 0g | Sugar 0g | Na 0mg"
                        onText(if (text.isBlank()) t else text.trimEnd() + "\n" + t)
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = onText,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                    shape = RoundedCornerShape(18.dp),
                    placeholder = { Text("2026-10-19 08:30 Breakfast (Banica & Yogurt): 1446 kcal | P 46.4g | C 137.6g | F 78g", fontSize = 13.sp) }
                )
            }
        }

        AnimatedVisibility(recent.isNotEmpty()) {
            Column {
                SectionLabel("Recent foods")
                Text("Tap to add with the current time", fontSize = 12.sp, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recent) { r ->
                        FancyChip("${r.name} · ${Format.int(r.energy?.inKilocalories ?: 0.0)}", selected = false) {
                            val line = Format.entryLine(r, LocalDateTime.now().withSecond(0).withNano(0))
                            onText(if (text.isBlank()) line else text.trimEnd() + "\n" + line)
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            FancyButton("Preview", Modifier.weight(1f), icon = R.drawable.ic_eye, filled = false) { status = ""; parseAll() }
            FancyButton("Save", Modifier.weight(1f), icon = R.drawable.ic_check, enabled = client != null && text.isNotBlank()) {
                status = ""
                val recs = parseAll()
                if (errors.isEmpty() && recs.isNotEmpty() && client != null) {
                    if (!hasPerms) {
                        statusOk = false
                        status = "Grant access first, then press Save again."
                        requestPerms()
                    } else {
                        scope.launch {
                            try {
                                client.insertRecords(recs)
                                onText("")
                                preview = emptyList()
                                statusOk = true
                                status = "Saved ${recs.size} entr${if (recs.size == 1) "y" else "ies"}"
                                onSaved()
                            } catch (e: Exception) {
                                statusOk = false
                                status = "Save failed: ${e.message}"
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(errors.isNotEmpty(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            AppCard {
                errors.forEach { Text(it, color = cs.error, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp)) }
            }
        }
        AnimatedVisibility(preview.isNotEmpty(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            AppCard {
                SectionLabel("Preview")
                Spacer(Modifier.height(6.dp))
                preview.forEach { Text(it, fontSize = 13.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(vertical = 5.dp)) }
            }
        }
        AnimatedVisibility(status.isNotEmpty(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Text(status, color = if (statusOk) cs.primary else cs.error, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(100.dp))
    }
}
