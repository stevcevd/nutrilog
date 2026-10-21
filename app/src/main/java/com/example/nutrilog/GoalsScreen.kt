package com.example.nutrilog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GoalsScreen(
    goals: Map<String, Double>,
    waterGoal: Double,
    onBack: () -> Unit,
    onSave: (Map<String, Double>, Double) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    val fields = remember(goals) {
        mutableStateMapOf<String, String>().apply {
            Nutrients.all.forEach { put(it.key, Format.num(goals[it.key] ?: 0.0)) }
        }
    }
    var water by remember(waterGoal) { mutableStateOf(Format.num(waterGoal)) }
    var open by remember { mutableStateOf(setOf(Nutrients.groups.first())) }
    var importText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    fun save() {
        val out = HashMap<String, Double>()
        var bad: String? = null
        Nutrients.all.forEach { n ->
            val v = (fields[n.key] ?: "").trim().replace(',', '.').toDoubleOrNull()
            if (v == null || v < 0) bad = n.name else out[n.key] = v
        }
        val w = water.trim().replace(',', '.').toDoubleOrNull()
        if (w == null || w < 0) bad = "Water"
        if (bad != null) {
            isError = true
            message = "Invalid number for $bad"
        } else {
            onSave(out, w!!)
            isError = false
            message = "Goals saved"
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SubHeader("Goals", onBack)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FancyButton("Save", Modifier.weight(1f), icon = R.drawable.ic_check) { save() }
            FancyButton("Copy text", Modifier.weight(1f), icon = R.drawable.ic_copy, filled = false) {
                clipboard.setText(AnnotatedString(Format.goalsString(goals)))
                isError = false
                message = "Goals string copied"
            }
            FancyButton("Reset", Modifier.weight(1f), icon = R.drawable.ic_undo, filled = false) {
                onSave(GoalsStore.defaults(), 2500.0)
                isError = false
                message = "Reset to your default targets"
            }
        }
        AnimatedVisibility(message.isNotEmpty(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Text(message, color = if (isError) cs.error else cs.primary, fontWeight = FontWeight.SemiBold)
        }

        Nutrients.groups.forEachIndexed { idx, grp ->
            Reveal(idx) {
                val isOpen = grp in open
                val rot by animateFloatAsState(if (isOpen) 90f else 0f, label = "grpchev")
                AppCard {
                    Row(
                        Modifier.fillMaxWidth().clickable { open = if (isOpen) open - grp else open + grp },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(grp, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text("${Nutrients.all.count { it.group == grp }} nutrients", fontSize = 12.sp, color = cs.onSurfaceVariant)
                        }
                        NIcon(R.drawable.ic_chevron_right, cs.onSurfaceVariant, 20.dp, Modifier.rotate(rot))
                    }
                    AnimatedVisibility(isOpen, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Nutrients.all.filter { it.group == grp }.forEach { n ->
                                OutlinedTextField(
                                    value = fields[n.key] ?: "",
                                    onValueChange = { fields[n.key] = it },
                                    label = { Text("${n.name} (${n.unit})") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }

        AppCard {
            Text("Hydration", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = water,
                onValueChange = { water = it },
                label = { Text("Daily water goal (ml)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
        }

        AppCard {
            Text("Import from text", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = importText,
                onValueChange = { importText = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
                shape = RoundedCornerShape(14.dp),
                label = { Text("Paste a \"Daily Targets\" line") }
            )
            Spacer(Modifier.height(12.dp))
            FancyButton("Import goals", icon = R.drawable.ic_download) {
                try {
                    onSave(NutritionParser.parseGoals(importText), parseWater(water, waterGoal))
                    importText = ""
                    isError = false
                    message = "Goals imported"
                } catch (e: Exception) {
                    isError = true
                    message = e.message ?: "Invalid goals text"
                }
            }
        }
        Spacer(Modifier.height(100.dp))
    }
}

private fun parseWater(s: String, fallback: Double): Double =
    s.trim().replace(',', '.').toDoubleOrNull() ?: fallback
