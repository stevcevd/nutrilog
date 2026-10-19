package com.example.nutrilog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.NutritionRecord
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface { App() } } }
    }
}

private suspend fun insert(client: HealthConnectClient, recs: List<NutritionRecord>): String =
    try {
        client.insertRecords(recs)
        "Saved ${recs.size} entr${if (recs.size == 1) "y" else "ies"} to Health Connect."
    } catch (e: Exception) {
        "Save failed: ${e.message}"
    }

@Composable
fun App() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val permissions = remember { setOf(HealthPermission.getWritePermission(NutritionRecord::class)) }

    val sdkStatus = remember { HealthConnectClient.getSdkStatus(context) }
    val client = remember {
        if (sdkStatus == HealthConnectClient.SDK_AVAILABLE) HealthConnectClient.getOrCreate(context) else null
    }

    var text by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<List<String>>(emptyList()) }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<List<NutritionRecord>>(emptyList()) }

    val launcher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(permissions) && client != null) {
            scope.launch {
                status = insert(client, pending)
                if (status.startsWith("Saved")) { text = ""; preview = emptyList() }
            }
        } else {
            status = "Permission denied. Allow Nutrition write access in Health Connect."
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
                prev += "${r.name} - ${r.energy?.inKilocalories?.toInt()} kcal, " +
                    "P ${r.protein?.inGrams ?: 0.0}g, C ${r.totalCarbohydrate?.inGrams ?: 0.0}g, F ${r.totalFat?.inGrams ?: 0.0}g"
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
        Text("NutriLog", style = MaterialTheme.typography.headlineMedium)

        if (client == null) {
            Text(
                "Health Connect is not available on this device (status $sdkStatus). " +
                    "Install or update the Health Connect app from the Play Store."
            )
        }

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
            label = { Text("Paste entry (one per line)") },
            placeholder = { Text("2026-10-19 08:30 Breakfast (Banica & Yogurt): 1446 kcal | P 46.4g | C 137.6g | F 78.0g") }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { status = ""; parseAll() }) { Text("Preview") }
            Button(
                enabled = client != null && text.isNotBlank(),
                onClick = {
                    status = ""
                    val recs = parseAll()
                    if (errors.isNotEmpty() || recs.isEmpty()) return@Button
                    pending = recs
                    scope.launch {
                        val granted = client!!.permissionController.getGrantedPermissions()
                        if (granted.containsAll(permissions)) {
                            status = insert(client, recs)
                            if (status.startsWith("Saved")) { text = ""; preview = emptyList() }
                        } else {
                            launcher.launch(permissions)
                        }
                    }
                }
            ) { Text("Save to Health Connect") }
        }

        errors.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
        preview.forEach { Text("• $it") }
        if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodyLarge)
    }
}
