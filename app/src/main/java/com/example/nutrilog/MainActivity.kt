package com.example.nutrilog

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.fragment.app.FragmentActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private lateinit var settings: Settings
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        if (savedInstanceState == null && settings.lockMode != 0 && settings.pinSet) AppLock.locked = true

        setContent {
            val dark = when (settings.themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            DisposableEffect(dark) {
                val transparent = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
                )
                onDispose { }
            }
            LaunchedEffect(settings.hideRecents, settings.lockMode) {
                if (settings.hideRecents && settings.lockMode != 0) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
            NutriTheme(dark) {
                Box(Modifier.fillMaxSize()) {
                    App(settings)
                    AnimatedVisibility(AppLock.locked, enter = fadeIn(tween(150)), exit = fadeOut(tween(250))) {
                        LockScreen(settings) { AppLock.locked = false }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        if (stoppedAt == 0L) return
        if (AppLock.skipNext) {
            AppLock.skipNext = false
            return
        }
        if (settings.lockMode != 0 && settings.pinSet &&
            SystemClock.elapsedRealtime() - stoppedAt >= settings.lockTimeout * 1000L
        ) {
            AppLock.locked = true
        }
    }
}

@Composable
fun App(settings: Settings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val corePerms = remember {
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
    var hasBody by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var goals by remember { mutableStateOf(GoalsStore.load(context)) }
    var waterGoal by remember { mutableStateOf(GoalsStore.loadWater(context)) }
    var backupBusy by remember { mutableStateOf(false) }

    suspend fun refreshPerms() {
        if (client != null) {
            val g = client.permissionController.getGrantedPermissions()
            hasPerms = g.containsAll(corePerms)
            hasBody = g.any { it in BODY_PERMISSIONS }
        }
    }

    val launcher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { _ -> scope.launch { refreshPerms() } }

    val requestCore = { AppLock.skipNext = true; launcher.launch(corePerms) }
    val requestBody = { AppLock.skipNext = true; launcher.launch(BODY_PERMISSIONS) }

    LaunchedEffect(client) { refreshPerms() }
    LaunchedEffect(Unit) { Reminders.scheduleAll(context, settings.reminders) }

    // Scheduled / after-change backups (Health Connect can only be read while the app is open)
    LaunchedEffect(refresh, hasPerms, client, settings.backupTree, settings.backupEveryHours) {
        if (client != null && hasPerms && !backupBusy && Backup.due(settings, refresh > 0)) {
            backupBusy = true
            val msg = try {
                Backup.run(context, client, settings, goals, waterGoal)
            } catch (e: Exception) {
                "Backup failed: ${e.message}"
            }
            settings.setLastBackup(System.currentTimeMillis(), msg)
            backupBusy = false
        }
    }

    val onRestoreSettings: (String?, Double?) -> Unit = { goalsStr, wg ->
        if (goalsStr != null) {
            runCatching { NutritionParser.parseGoals(goalsStr) }.getOrNull()?.let {
                GoalsStore.save(context, it)
                goals = it
            }
        }
        if (wg != null) {
            GoalsStore.saveWater(context, wg)
            waterGoal = wg
        }
    }

    val items = listOf(
        "Home" to R.drawable.ic_home,
        "Log" to R.drawable.ic_log,
        "Diary" to R.drawable.ic_diary,
        "Stats" to R.drawable.ic_stats,
        "Me" to R.drawable.ic_me
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
                Banner("NutriLog needs Health Connect access for Nutrition and Hydration.", "Grant") { requestCore() }
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
                    0 -> HomeScreen(client, hasPerms, goals, waterGoal, settings, refresh) { tab = 1 }
                    1 -> LogScreen(client, hasPerms, logText, { logText = it }, refresh, { refresh++ }) { requestCore() }
                    2 -> DiaryScreen(client, hasPerms, goals, settings, refresh, { refresh++ }) { line -> logText = line; tab = 1 }
                    3 -> StatsScreen(client, hasPerms, goals, refresh)
                    else -> MeHost(
                        client, hasPerms, hasBody, { requestBody() }, settings, goals, waterGoal,
                        { newGoals, newWater ->
                            GoalsStore.save(context, newGoals)
                            GoalsStore.saveWater(context, newWater)
                            goals = newGoals
                            waterGoal = newWater
                        },
                        refresh, { refresh++ }, onRestoreSettings
                    )
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
