package com.example.nutrilog

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.health.connect.client.HealthConnectClient
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
private fun LinkRow(icon: Int, title: String, subtitle: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    AppCard(padding = 0.dp) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(cs.primaryContainer),
                contentAlignment = Alignment.Center
            ) { NIcon(icon, cs.onPrimaryContainer, 22.dp) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 1)
            }
            NIcon(R.drawable.ic_chevron_right, cs.onSurfaceVariant, 20.dp)
        }
    }
}

@Composable
fun SettingsMain(settings: Settings, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val info = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0) } catch (e: Exception) { null } }
    val activeReminders = settings.reminders.count { it.enabled }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SubHeader("Settings", onBack)

        LinkRow(R.drawable.ic_goals, "Goals", "Daily targets for every nutrient") { onOpen("goals") }
        LinkRow(R.drawable.ic_bell, "Meal reminders", if (activeReminders == 0) "Off" else "$activeReminders active") { onOpen("reminders") }
        LinkRow(
            R.drawable.ic_lock, "Security",
            when (settings.lockMode) { 1 -> "PIN lock"; 2 -> "Biometric + PIN"; else -> "App lock is off" }
        ) { onOpen("security") }
        LinkRow(
            R.drawable.ic_backup, "Backups",
            if (settings.backupTree == null) "No folder chosen" else if (settings.lastBackupAt == 0L) "Never backed up" else "Last: " + settings.lastBackupMsg
        ) { onOpen("backup") }

        AppCard {
            Text("Fasting timer", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("Target for the ring on Home (0 hides it)", fontSize = 12.sp, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 4, 8, 12, 14, 16, 18, 20).forEach { h ->
                    FancyChip(if (h == 0) "Off" else "${h}h", settings.fastingTarget == h) { settings.setFastingTarget(h) }
                }
            }
        }

        AppCard {
            Text("Appearance", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("System", "Light", "Dark").forEachIndexed { i, l -> FancyChip(l, settings.themeMode == i) { settings.setTheme(i) } }
            }
            Spacer(Modifier.height(14.dp))
            Text("Units", fontSize = 13.sp, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Metric (kg, cm)", "Imperial (lb, ft)").forEachIndexed { i, l -> FancyChip(l, settings.units == i) { settings.setUnits(i) } }
            }
        }

        AppCard {
            Text("About", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            val ver = info?.versionName ?: "?"
            val build = info?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L
            Text("Version $ver, build $build", fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "To update, just install a newer APK over this one. Android recognises the same app and key, " +
                    "replaces it in place, and keeps all your data.",
                fontSize = 12.sp, color = cs.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(100.dp))
    }
}

// ───────────────────────── Reminders ─────────────────────────

@Composable
fun RemindersScreen(settings: Settings, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var list by remember { mutableStateOf(settings.reminders) }
    var needsNotif by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        )
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        needsNotif = !granted
    }
    fun commit(next: List<Reminder>) {
        list = next
        settings.saveReminders(next)
    }
    fun askNotif() {
        if (needsNotif && Build.VERSION.SDK_INT >= 33) {
            AppLock.skipNext = true
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SubHeader("Meal reminders", onBack)

        if (needsNotif) {
            AppCard {
                Text("Notifications are off", fontWeight = FontWeight.SemiBold)
                Text("Allow notifications so reminders can appear.", fontSize = 13.sp, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                FancyButton("Allow notifications", compact = true) { askNotif() }
            }
        }

        list.forEach { r ->
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = r.label,
                        onValueChange = { v -> commit(list.map { if (it.id == r.id) it.copy(label = v) else it }) },
                        singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f),
                        label = { Text("Name") }
                    )
                    Spacer(Modifier.width(10.dp))
                    Switch(checked = r.enabled, onCheckedChange = { on ->
                        commit(list.map { if (it.id == r.id) it.copy(enabled = on) else it })
                        if (on) askNotif()
                    })
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FancyButton(
                        "%02d:%02d".format(r.hour, r.minute), icon = R.drawable.ic_timer, compact = true, filled = false
                    ) {
                        AppLock.skipNext = true
                        TimePickerDialog(ctx, { _, h, m ->
                            commit(list.map { if (it.id == r.id) it.copy(hour = h, minute = m) else it })
                        }, r.hour, r.minute, true).show()
                    }
                    Spacer(Modifier.weight(1f))
                    FancyButton("", icon = R.drawable.ic_trash, compact = true, filled = false, danger = true) {
                        commit(list.filter { it.id != r.id })
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FancyButton("Add reminder", Modifier.weight(1f), icon = R.drawable.ic_plus, compact = true) {
                val id = (list.maxOfOrNull { it.id } ?: 0) + 1
                commit(list + Reminder(id, "Snack", 16, 0, true))
                askNotif()
            }
            FancyButton("Test", Modifier.weight(1f), icon = R.drawable.ic_bell, compact = true, filled = false) {
                Reminders.notify(ctx, Reminder(0, "a test", 0, 0, true))
            }
        }
        Text(
            "Reminders repeat daily. Android may deliver them a few minutes late to save battery.",
            fontSize = 12.sp, color = cs.onSurfaceVariant
        )
        Spacer(Modifier.height(100.dp))
    }
}

// ───────────────────────── Security ─────────────────────────

@Composable
fun SecurityScreen(settings: Settings, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val bioOk = remember { biometricAvailable(ctx) }
    var stage by remember { mutableIntStateOf(0) }       // 0 idle, 1 choose, 2 confirm
    var firstPin by remember { mutableStateOf("") }
    var pendingMode by remember { mutableIntStateOf(1) }
    var pinLen by remember { mutableIntStateOf(4) }
    var errorTick by remember { mutableIntStateOf(0) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SubHeader("Security", onBack)

        if (stage != 0) {
            AppCard {
                if (stage == 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                        listOf(4, 6).forEach { n -> FancyChip("$n digits", pinLen == n) { pinLen = n } }
                    }
                }
                PinPad(
                    length = pinLen,
                    title = if (stage == 1) "Choose a PIN" else "Confirm your PIN",
                    subtitle = if (stage == 2 && errorTick > 0) "Those didn't match, try again" else null,
                    errorTick = errorTick,
                    resetKey = stage to pinLen
                ) { pin ->
                    if (stage == 1) {
                        firstPin = pin
                        stage = 2
                    } else if (pin == firstPin) {
                        settings.setPin(pin)
                        settings.setLockMode(pendingMode)
                        stage = 0
                        errorTick = 0
                    } else {
                        errorTick++
                    }
                }
                Spacer(Modifier.height(8.dp))
                FancyButton("Cancel", filled = false, compact = true) { stage = 0; errorTick = 0 }
            }
        } else {
            AppCard {
                Text("App lock", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val modes = listOf(0 to "Off", 1 to "PIN") + (if (bioOk) listOf(2 to "Biometric") else emptyList())
                    modes.forEach { (m, l) ->
                        FancyChip(l, settings.lockMode == m) {
                            if (m == 0) {
                                settings.setLockMode(0)
                                settings.clearPin()
                            } else if (!settings.pinSet) {
                                pendingMode = m; stage = 1
                            } else {
                                settings.setLockMode(m)
                            }
                        }
                    }
                }
                if (!bioOk) {
                    Spacer(Modifier.height(8.dp))
                    Text("No fingerprint or face unlock is set up on this phone.", fontSize = 12.sp, color = cs.onSurfaceVariant)
                }
                if (settings.pinSet) {
                    Spacer(Modifier.height(12.dp))
                    FancyButton("Change PIN", icon = R.drawable.ic_lock, compact = true, filled = false) {
                        pendingMode = settings.lockMode.coerceAtLeast(1); stage = 1
                    }
                }
            }

            if (settings.lockMode != 0) {
                AppCard {
                    Text("Lock after leaving the app", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "Immediately", 30 to "30 s", 60 to "1 min", 300 to "5 min").forEach { (s, l) ->
                            FancyChip(l, settings.lockTimeout == s) { settings.setLockTimeout(s) }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Hide in app switcher", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text("Blocks screenshots and blanks the recent-apps preview", fontSize = 12.sp, color = cs.onSurfaceVariant)
                        }
                        Switch(checked = settings.hideRecents, onCheckedChange = { settings.setHideRecents(it) })
                    }
                }
            }
        }
        Spacer(Modifier.height(100.dp))
    }
}

// ───────────────────────── Backups ─────────────────────────

@Composable
fun BackupScreen(
    client: HealthConnectClient?,
    hasPerms: Boolean,
    settings: Settings,
    goals: Map<String, Double>,
    waterGoal: Double,
    onBack: () -> Unit,
    onChanged: () -> Unit,
    onRestoreSettings: (String?, Double?) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<BackupFile>>(emptyList()) }
    var tick by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var plan by remember { mutableStateOf<RestorePlan?>(null) }
    var includeOld by remember { mutableStateOf(false) }
    var includeSettings by remember { mutableStateOf(true) }
    val tree = settings.backupTree

    LaunchedEffect(tree, tick, settings.lastBackupAt) { files = Backup.list(ctx, tree) }

    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            ctx.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            settings.setBackupTree(uri.toString())
            tick++
        }
    }

    fun startRestore(uri: Uri) {
        if (client == null || !hasPerms) { message = "Grant Health Connect access first"; return }
        scope.launch {
            busy = true
            try {
                val data = Backup.read(ctx, uri)
                plan = Backup.plan(client, settings, data)
                includeOld = false
                message = ""
            } catch (e: Exception) {
                message = "Restore failed: ${e.message}"
            }
            busy = false
        }
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) startRestore(uri)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SubHeader("Backups", onBack)

        AppCard {
            Text("How it works", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Health Connect is always trusted first and is what the app shows. A backup file keeps a copy " +
                    "of your entries, water, goals and profile, including history older than Health Connect lets the app read. " +
                    "Restoring only adds what Health Connect is missing.",
                fontSize = 13.sp, color = cs.onSurfaceVariant
            )
        }

        AppCard {
            Text("Backup folder", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                Backup.folderName(ctx, tree) ?: "No folder chosen",
                fontSize = 14.sp, color = if (tree == null) cs.onSurfaceVariant else cs.primary
            )
            Spacer(Modifier.height(10.dp))
            FancyButton(if (tree == null) "Choose folder" else "Change folder", icon = R.drawable.ic_backup, compact = true, filled = tree == null) {
                AppLock.skipNext = true
                treeLauncher.launch(null)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Android gives the app access to just this folder, which is the safe way to write files. " +
                    "If you reinstall the app, choose the folder again.",
                fontSize = 12.sp, color = cs.onSurfaceVariant
            )
        }

        AppCard {
            Text("How often", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Manual", 1 to "After each change", 24 to "Daily", 168 to "Weekly", 720 to "Monthly").forEach { (h, l) ->
                    FancyChip(l, settings.backupEveryHours == h) { settings.setBackupEvery(h) }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("Keep the latest", fontSize = 13.sp, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 5, 10, 20).forEach { n -> FancyChip("$n files", settings.backupKeep == n) { settings.setBackupKeep(n) } }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Health Connect only allows reading while the app is open, so scheduled backups run when you open NutriLog and one is due.",
                fontSize = 12.sp, color = cs.onSurfaceVariant
            )
        }

        AppCard {
            FancyButton(
                if (busy) "Working..." else "Back up now", icon = R.drawable.ic_check,
                enabled = !busy && tree != null && client != null && hasPerms
            ) {
                scope.launch {
                    busy = true
                    val msg = try { Backup.run(ctx, client!!, settings, goals, waterGoal) } catch (e: Exception) { "Backup failed: ${e.message}" }
                    settings.setLastBackup(System.currentTimeMillis(), msg)
                    message = msg
                    busy = false
                    tick++
                }
            }
            if (settings.lastBackupAt > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Last: " + Instant.ofEpochMilli(settings.lastBackupAt).atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")) + " - " + settings.lastBackupMsg,
                    fontSize = 12.sp, color = cs.onSurfaceVariant
                )
            }
            if (message.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(message, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = cs.primary)
            }
        }

        plan?.let { pl ->
            AppCard {
                Text("Ready to restore", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("Missing from Health Connect (last 30 days): ${pl.missingRecent.size} entries, ${pl.missingWater.size} water logs", fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text("Older than 30 days: ${pl.missingOld.size} entries (can't be checked against Health Connect)", fontSize = 13.sp, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Include entries older than 30 days", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("Entries logged by older app versions may be duplicated", fontSize = 11.sp, color = cs.onSurfaceVariant)
                    }
                    Switch(includeOld, { includeOld = it })
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Also restore goals, profile and reminders", Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Switch(includeSettings, { includeSettings = it })
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FancyButton("Restore", Modifier.weight(1f), icon = R.drawable.ic_download, compact = true, enabled = !busy) {
                        scope.launch {
                            busy = true
                            try {
                                val n = Backup.apply(client!!, pl, includeOld)
                                if (includeSettings) {
                                    pl.data.settings?.let { settings.importJson(it) }
                                    onRestoreSettings(pl.data.goals, pl.data.waterGoal)
                                }
                                message = "Restored $n records"
                                plan = null
                                onChanged()
                            } catch (e: Exception) {
                                message = "Restore failed: ${e.message}"
                            }
                            busy = false
                        }
                    }
                    FancyButton("Cancel", Modifier.weight(1f), compact = true, filled = false) { plan = null }
                }
            }
        }

        AppCard {
            Text("Backup files", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            if (files.isEmpty()) {
                Text("None yet.", fontSize = 13.sp, color = cs.onSurfaceVariant)
            }
            files.take(8).forEach { f ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(f.name.removePrefix("nutrilog-backup-").removeSuffix(".json"), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("${f.size / 1024 + 1} KB", fontSize = 11.sp, color = cs.onSurfaceVariant)
                    }
                    FancyButton("Restore", compact = true, filled = false, enabled = !busy) { startRestore(f.uri) }
                }
            }
            Spacer(Modifier.height(8.dp))
            FancyButton("Restore from a file...", icon = R.drawable.ic_download, compact = true, filled = false) {
                AppLock.skipNext = true
                fileLauncher.launch(arrayOf("*/*"))
            }
        }
        Spacer(Modifier.height(100.dp))
    }
}
