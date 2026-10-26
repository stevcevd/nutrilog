package com.example.nutrilog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.health.connect.client.HealthConnectClient
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun whenText(w: Workout): String {
    val zone = ZoneId.systemDefault()
    val s = LocalDateTime.ofInstant(w.start, zone)
    val e = LocalDateTime.ofInstant(w.end, zone)
    return s.format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")) + " to " + e.format(DateTimeFormatter.ofPattern("HH:mm"))
}

@Composable
fun WorkoutsCard(workouts: List<Workout>, client: HealthConnectClient?) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var selected by remember { mutableStateOf<Workout?>(null) }

    AppCard {
        Text("Recent workouts", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Synced from Health Connect. Under each workout you can see which app recorded it. Tap one for details.",
            fontSize = 12.sp, color = cs.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        workouts.forEach { w ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { selected = w }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(w.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        LocalDateTime.ofInstant(w.start, ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")),
                        fontSize = 12.sp, color = cs.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(cs.primary))
                        Spacer(Modifier.width(6.dp))
                        Text("Recorded by ${AppSources.label(ctx, w.sourcePackage)}", fontSize = 11.sp, color = cs.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Text("${w.minutes} min", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.width(4.dp))
                NIcon(R.drawable.ic_chevron_right, cs.onSurfaceVariant, 18.dp)
            }
        }
    }

    selected?.let { w -> WorkoutDialog(w, client) { selected = null } }
}

@Composable
private fun FactRow(label: String, value: String) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, Modifier.weight(1f), fontSize = 13.sp, color = cs.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun WorkoutDialog(w: Workout, client: HealthConnectClient?, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var stats by remember { mutableStateOf<SessionStats?>(null) }
    LaunchedEffect(w) {
        if (client != null) {
            try { stats = readSessionStats(client, w.start, w.end) } catch (e: Exception) { }
        }
    }
    val appName = AppSources.label(ctx, w.sourcePackage)
    val launch = remember(w.sourcePackage) { AppSources.launchIntent(ctx, w.sourcePackage) }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            AppCard {
                Text(w.title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(whenText(w), fontSize = 13.sp, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                FactRow("Type", w.typeName)
                FactRow("Duration", "${w.minutes} min")
                stats?.activeKcal?.takeIf { it > 0 }?.let { FactRow("Active energy", "${Format.int(it)} kcal") }
                stats?.totalKcal?.takeIf { it > 0 }?.let { FactRow("Total energy", "${Format.int(it)} kcal") }
                stats?.steps?.takeIf { it > 0 }?.let { FactRow("Steps", Format.int(it.toDouble())) }
                if (!w.notes.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Notes", fontSize = 13.sp, color = cs.onSurfaceVariant)
                    Text(w.notes, fontSize = 13.sp)
                }

                Spacer(Modifier.height(12.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.surfaceVariant).padding(12.dp)) {
                    Text("Recorded by", fontSize = 11.sp, color = cs.onSurfaceVariant)
                    Text(appName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    if (w.sourcePackage.isNotBlank()) Text(w.sourcePackage, fontSize = 11.sp, color = cs.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Read from Health Connect. NutriLog can't edit this workout. Change it in the app that recorded it.",
                    fontSize = 11.sp, color = cs.onSurfaceVariant
                )

                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (launch != null) {
                        FancyButton("Open $appName", Modifier.weight(1f), compact = true, onClick = {
                            AppLock.skipNext = true
                            try { ctx.startActivity(launch) } catch (e: Exception) { }
                            onClose()
                        })
                    }
                    FancyButton("Close", Modifier.weight(1f), compact = true, filled = launch == null, onClick = onClose)
                }
                if (launch == null) {
                    Spacer(Modifier.height(6.dp))
                    Text("That app isn't available on this phone to open.", fontSize = 11.sp, color = cs.onSurfaceVariant)
                }
            }
        }
    }
}
