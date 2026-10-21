package com.example.nutrilog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { NutriTheme { App() } }
    }
}

@Composable
fun App() {
    val context = LocalContext.current
    val perms = remember {
        setOf(
            HealthPermission.getWritePermission(NutritionRecord::class),
            HealthPermission.getReadPermission(NutritionRecord::class),
            HealthPermission.getWritePermission(HydrationRecord::class),
            HealthPermission.getReadPermission(HydrationRecord::class)
        )
    }
    val sdkStatus = remember { HealthConnectClient.getSdkStatus(context) }
    val client = remember {
        if (sdkStatus == HealthConnectClient.SDK_AVAILABLE) HealthConnectClient.getOrCreate(context) else null
    }

    var tab by remember { mutableIntStateOf(0) }
    var logText by remember { mutableStateOf("") }
    var hasPerms by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var goals by remember { mutableStateOf(GoalsStore.load(context)) }
    var waterGoal by remember { mutableStateOf(GoalsStore.loadWater(context)) }

    val launcher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted -> hasPerms = granted.containsAll(perms) }

    LaunchedEffect(client) {
        if (client != null) {
            hasPerms = client.permissionController.getGrantedPermissions().containsAll(perms)
        }
    }

    val items = listOf(
        "Home" to R.drawable.ic_home,
        "Log" to R.drawable.ic_log,
        "Diary" to R.drawable.ic_diary,
        "Stats" to R.drawable.ic_stats,
        "Goals" to R.drawable.ic_goals
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { FloatingNavBar(items, tab) { tab = it } }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().statusBarsPadding()) {
            if (client == null) {
                Banner("Health Connect isn't available (status $sdkStatus). Install or update the Health Connect app from the Play Store.", null, null)
            } else if (!hasPerms) {
                Banner("NutriLog needs Health Connect access for Nutrition and Hydration.", "Grant") { launcher.launch(perms) }
            }
            AnimatedContent(
                targetState = tab,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(320)) { it / 5 * dir } + fadeIn(tween(320))) togetherWith
                        (slideOutHorizontally(tween(320)) { -it / 5 * dir } + fadeOut(tween(200)))
                },
                label = "tabs"
            ) { t ->
                when (t) {
                    0 -> HomeScreen(client, hasPerms, goals, waterGoal, refresh) { tab = 1 }
                    1 -> LogScreen(client, hasPerms, logText, { logText = it }, refresh, { refresh++ }) { launcher.launch(perms) }
                    2 -> DiaryScreen(client, hasPerms, goals, refresh, { refresh++ }) { line -> logText = line; tab = 1 }
                    3 -> StatsScreen(client, hasPerms, goals, refresh)
                    else -> GoalsScreen(goals, waterGoal) { newGoals, newWater ->
                        GoalsStore.save(context, newGoals)
                        GoalsStore.saveWater(context, newWater)
                        goals = newGoals
                        waterGoal = newWater
                    }
                }
            }
        }
    }
}

@Composable
fun Banner(message: String, button: String?, onClick: (() -> Unit)?) {
    AppCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(message, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (button != null && onClick != null) {
                Spacer(Modifier.width(10.dp))
                FancyButton(button, compact = true, onClick = onClick)
            }
        }
    }
}
