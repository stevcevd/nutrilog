package com.example.nutrilog

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.NutritionRecord

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { NutriTheme { App() } }
    }
}

@Composable
fun NutriTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = if (Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else {
        if (dark) darkColorScheme() else lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun App() {
    val context = LocalContext.current
    val perms = remember {
        setOf(
            HealthPermission.getWritePermission(NutritionRecord::class),
            HealthPermission.getReadPermission(NutritionRecord::class)
        )
    }
    val sdkStatus = remember { HealthConnectClient.getSdkStatus(context) }
    val client = remember {
        if (sdkStatus == HealthConnectClient.SDK_AVAILABLE) HealthConnectClient.getOrCreate(context) else null
    }

    var tab by remember { mutableIntStateOf(0) }
    var logText by remember { mutableStateOf("") }
    var hasPerms by remember { mutableStateOf(false) }
    var goals by remember { mutableStateOf(GoalsStore.load(context)) }

    val launcher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted -> hasPerms = granted.containsAll(perms) }

    LaunchedEffect(client) {
        if (client != null) {
            hasPerms = client.permissionController.getGrantedPermissions().containsAll(perms)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf("Log", "Diary", "Goals").forEachIndexed { i, label ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Text(when (i) { 0 -> "✏️"; 1 -> "📖"; else -> "🎯" }) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (client == null) {
                Banner("Health Connect isn't available (status $sdkStatus). Install or update the Health Connect app from the Play Store.", null, null)
            } else if (!hasPerms) {
                Banner("NutriLog needs Health Connect read & write access for Nutrition.", "Grant access") {
                    launcher.launch(perms)
                }
            }
            when (tab) {
                0 -> LogScreen(client, hasPerms, logText, { logText = it }) { launcher.launch(perms) }
                1 -> DiaryScreen(client, hasPerms, goals) { line -> logText = line; tab = 0 }
                else -> GoalsScreen(goals) { newGoals ->
                    GoalsStore.save(context, newGoals)
                    goals = newGoals
                }
            }
        }
    }
}

@Composable
fun Banner(message: String, button: String?, onClick: (() -> Unit)?) {
    Card(
        Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            if (button != null && onClick != null) {
                Spacer(Modifier.width(8.dp))
                Button(onClick = onClick) { Text(button) }
            }
        }
    }
}
