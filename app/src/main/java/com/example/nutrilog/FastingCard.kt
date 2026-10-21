package com.example.nutrilog

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.NutritionRecord
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Time since the last meal, ticking live. The ring fills toward the target set in Settings. */
@Composable
fun FastingCard(client: HealthConnectClient?, hasPerms: Boolean, refresh: Int, targetHours: Int) {
    val cs = MaterialTheme.colorScheme
    var last by remember { mutableStateOf<NutritionRecord?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(Instant.now()) }

    LaunchedEffect(client, hasPerms, refresh) {
        if (client != null && hasPerms) {
            try {
                val today = LocalDate.now()
                last = readNutrition(client, today.minusDays(3), today.plusDays(1))
                    .filter { !it.startTime.isAfter(Instant.now()) }
                    .maxByOrNull { it.startTime }
            } catch (e: Exception) { }
            loaded = true
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = Instant.now()
        }
    }

    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val l = last
            val elapsed = if (l != null) Duration.between(l.startTime, now) else Duration.ZERO
            val progress = if (targetHours > 0 && l != null) elapsed.toMinutes() / (targetHours * 60f) else 0f
            RingChart(progress, if (progress >= 1f) Brand.Carbs else cs.primary, Modifier.size(84.dp), stroke = 9.dp) {
                NIcon(R.drawable.ic_timer, cs.onSurfaceVariant, 26.dp)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Since last meal", fontSize = 13.sp, color = cs.onSurfaceVariant)
                if (l == null) {
                    Text(if (loaded) "No meals in 3 days" else "...", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                } else {
                    val h = elapsed.toHours()
                    val m = elapsed.toMinutes() % 60
                    Text("${h}h ${m}m", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${l.name ?: "Meal"} at ${Format.localStart(l).format(DateTimeFormatter.ofPattern("HH:mm"))}",
                        fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 1
                    )
                    if (targetHours > 0) {
                        Text(
                            if (progress >= 1f) "Target of ${targetHours}h reached" else "Target ${targetHours}h",
                            fontSize = 12.sp, color = if (progress >= 1f) Brand.Carbs else cs.primary
                        )
                    }
                }
            }
        }
    }
}
