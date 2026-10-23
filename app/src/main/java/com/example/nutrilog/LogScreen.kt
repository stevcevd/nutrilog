package com.example.nutrilog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.NutritionRecord
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
private fun FavChip(text: String, onTap: () -> Unit, onLong: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val tap by rememberUpdatedState(onTap)
    val long by rememberUpdatedState(onLong)
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(cs.primaryContainer)
            .pointerInput(Unit) { detectTapGestures(onLongPress = { long() }, onTap = { tap() }) }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NIcon(R.drawable.ic_star, cs.onPrimaryContainer, 16.dp)
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = cs.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

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
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var preview by remember { mutableStateOf<List<String>>(emptyList()) }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var statusOk by remember { mutableStateOf(true) }
    var recent by remember { mutableStateOf<List<NutritionRecord>>(emptyList()) }

    var favs by remember { mutableStateOf(Favorites.load(ctx)) }
    var addingFav by remember { mutableStateOf(false) }
    var favText by remember { mutableStateOf("") }
    var favError by remember { mutableStateOf("") }
    var removeCandidate by remember { mutableStateOf<String?>(null) }

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

    fun appendLine(line: String) = onText(if (text.isBlank()) line else text.trimEnd() + "\n" + line)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenHeader("Log food", "Paste, preview, save")

        // 1. Entries
        Reveal(0) {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Entries", Modifier.weight(1f))
                    FancyButton("Paste", compact = true, filled = false) {
                        val clean = Paste.clean(clipboard.getText()?.text ?: "")
                        if (clean.isBlank()) {
                            statusOk = false
                            status = "No valid entry lines found on the clipboard"
                        } else {
                            appendLine(clean)
                            statusOk = true
                            status = "Pasted ${clean.lines().size} line(s)"
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    FancyButton("Template", compact = true, filled = false) {
                        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        appendLine("$now Snack (Name): 0 kcal | P 0g | C 0g | F 0g | Fiber 0g | Sugar 0g | Na 0mg")
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
            AppCard { errors.forEach { Text(it, color = cs.error, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp)) } }
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

        // 2. Favourites
        Reveal(1) {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        SectionLabel("Favourites")
                        Text("Tap to add with the current date and time. Press and hold to remove.", fontSize = 12.sp, color = cs.onSurfaceVariant)
                    }
                    FancyButton(if (addingFav) "Close" else "Add", icon = if (addingFav) null else R.drawable.ic_plus, compact = true, filled = false) {
                        addingFav = !addingFav
                        favError = ""
                        if (addingFav) {
                            favText = text.lines().firstOrNull { it.isNotBlank() }
                                ?: "${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))} Snack (Name): 0 kcal | P 0g | C 0g | F 0g"
                        }
                    }
                }

                AnimatedVisibility(addingFav, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Column {
                        Spacer(Modifier.height(10.dp))
                        Text("Write the entry once. Its time is replaced whenever you use it.", fontSize = 12.sp, color = cs.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = favText,
                            onValueChange = { favText = it; favError = "" },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                            shape = RoundedCornerShape(16.dp)
                        )
                        if (favError.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(favError, color = cs.error, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(10.dp))
                        FancyButton("Save favourite", icon = R.drawable.ic_star, compact = true) {
                            try {
                                val line = favText.lines().firstOrNull { it.isNotBlank() } ?: throw IllegalArgumentException("Nothing to save")
                                val canon = Favorites.canonical(line)
                                val key = Favorites.label(canon)
                                val next = favs.filter { Favorites.label(it) != key } + canon
                                favs = next
                                Favorites.save(ctx, next)
                                addingFav = false
                            } catch (e: Exception) {
                                favError = e.message ?: "That entry isn't valid"
                            }
                        }
                    }
                }

                AnimatedVisibility(removeCandidate != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Column {
                        Spacer(Modifier.height(10.dp))
                        Text("Remove \"${removeCandidate?.let { Favorites.label(it) } ?: ""}\" from favourites?", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FancyButton("Remove", Modifier.weight(1f), icon = R.drawable.ic_trash, compact = true, filled = false, danger = true) {
                                val rc = removeCandidate
                                if (rc != null) {
                                    val next = favs.filter { it != rc }
                                    favs = next
                                    Favorites.save(ctx, next)
                                }
                                removeCandidate = null
                            }
                            FancyButton("Keep", Modifier.weight(1f), compact = true, filled = false) { removeCandidate = null }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                if (favs.isEmpty()) {
                    Text("No favourites yet. Add one and it shows up here for one-tap logging.", fontSize = 13.sp, color = cs.onSurfaceVariant)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(favs) { line ->
                            FavChip(
                                Favorites.label(line),
                                onTap = { Favorites.stamped(line)?.let { appendLine(it) } },
                                onLong = { removeCandidate = line }
                            )
                        }
                    }
                }
            }
        }

        // 3. Recent
        AnimatedVisibility(recent.isNotEmpty()) {
            Column {
                SectionLabel("Recent foods")
                Text("Tap to add with the current time", fontSize = 12.sp, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recent) { r ->
                        FancyChip("${r.name} · ${Format.int(r.energy?.inKilocalories ?: 0.0)}", selected = false) {
                            appendLine(Format.entryLine(r, LocalDateTime.now().withSecond(0).withNano(0)))
                        }
                    }
                }
            }
        }

        // 4. AI helper, last
        Reveal(2) {
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
        Spacer(Modifier.height(100.dp))
    }
}
