package com.example.nutrilog

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private fun depth(page: String) = when (page) {
    "me" -> 0
    "edit", "settings" -> 1
    else -> 2
}

private fun parent(page: String) = when (page) {
    "goals", "home", "themes", "reminders", "security", "backup" -> "settings"
    else -> "me"
}

/** The "Me" tab: profile and body data, with Settings reachable from here. */
@Composable
fun MeHost(
    client: HealthConnectClient?,
    hasPerms: Boolean,
    hasBody: Boolean,
    requestBody: () -> Unit,
    settings: Settings,
    goals: Map<String, Double>,
    waterGoal: Double,
    onSaveGoals: (Map<String, Double>, Double) -> Unit,
    refresh: Int,
    onChanged: () -> Unit,
    onRestoreSettings: (String?, Double?) -> Unit
) {
    var page by remember { mutableStateOf("me") }
    BackHandler(page != "me") { page = parent(page) }

    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val dir = if (depth(targetState) >= depth(initialState)) 1 else -1
            (slideInHorizontally(tween(300)) { it / 4 * dir } + fadeIn(tween(300))) togetherWith
                (slideOutHorizontally(tween(300)) { -it / 4 * dir } + fadeOut(tween(180)))
        },
        label = "mePages"
    ) { pg ->
        when (pg) {
            "me" -> MeMain(client, hasPerms, hasBody, requestBody, settings, refresh, { page = "edit" }, { page = "settings" })
            "edit" -> EditProfile(settings) { page = "me" }
            "settings" -> SettingsMain(settings, { page = it }, { page = "me" })
            "home" -> HomeSettingsScreen(settings) { page = "settings" }
            "themes" -> ThemesScreen(settings) { page = "settings" }
            "goals" -> GoalsScreen(goals, waterGoal, { page = "settings" }, onSaveGoals)
            "reminders" -> RemindersScreen(settings) { page = "settings" }
            "security" -> SecurityScreen(settings) { page = "settings" }
            else -> BackupScreen(client, hasPerms, settings, goals, waterGoal, { page = "settings" }, onChanged, onRestoreSettings)
        }
    }
}

@Composable
fun SubHeader(title: String, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(cs.surfaceVariant).bounceClick(true, onBack),
            contentAlignment = Alignment.Center
        ) { NIcon(R.drawable.ic_chevron_left, cs.onSurface, 22.dp) }
        Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    }
}

private fun weightText(kg: Double, units: Int) =
    if (units == 1) "${Format.r1(kg * 2.20462)} lb" else "${Format.r1(kg)} kg"

private fun heightText(m: Double, units: Int): String {
    if (units == 0) return "${Format.int(m * 100)} cm"
    val inches = m * 39.3701
    val ft = (inches / 12).toInt()
    val inch = Math.round(inches - ft * 12).toInt()
    return "$ft ft $inch in"
}

@Composable
private fun BodyTile(label: String, value: String, sub: String?, color: Color, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    AppCard(modifier, padding = 14.dp) {
        Box(Modifier.width(22.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 12.sp, color = cs.onSurfaceVariant)
        Text(value, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        if (sub != null) Text(sub, fontSize = 11.sp, color = cs.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun MeMain(
    client: HealthConnectClient?,
    hasPerms: Boolean,
    hasBody: Boolean,
    requestBody: () -> Unit,
    settings: Settings,
    refresh: Int,
    onEdit: () -> Unit,
    onSettings: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val p = settings.profile
    var body by remember { mutableStateOf<BodySnapshot?>(null) }
    var eaten by remember { mutableStateOf(0.0) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(client, hasBody, hasPerms, refresh) {
        if (client != null && hasBody) {
            try {
                body = readBody(client)
                if (hasPerms) {
                    val t = LocalDate.now()
                    eaten = totalsOf(readNutrition(client, t, t.plusDays(1)))["kcal"] ?: 0.0
                }
                error = null
            } catch (e: Exception) { error = e.message }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenHeader("Me", "Profile and body")

        Reveal(0) {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(AvatarSpec.parse(p.avatar), 88.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (p.fullName.isBlank()) "Set up your profile" else p.fullName,
                            fontSize = 20.sp, fontWeight = FontWeight.Bold
                        )
                        if (p.nick.isNotBlank()) Text("@${p.nick}", fontSize = 14.sp, color = cs.primary, fontWeight = FontWeight.Medium)
                        if (p.email.isNotBlank()) Text(p.email, fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 1)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            p.age()?.let { MiniTag("$it y") }
                            MiniTag(listOf("Cutting", "Maintaining", "Bulking")[p.goalType.coerceIn(0, 2)])
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FancyButton("Edit profile", Modifier.weight(1f), icon = R.drawable.ic_edit, compact = true, onClick = onEdit)
                    FancyButton("Settings", Modifier.weight(1f), icon = R.drawable.ic_settings, compact = true, filled = false, onClick = onSettings)
                }
            }
        }

        if (!hasBody) {
            Reveal(1) {
                AppCard {
                    Text("Body and activity", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Weight, body fat, height, steps, calories burned and workouts are read from Health Connect. " +
                            "Nothing is typed in here. Your scale or watch app keeps writing it, NutriLog just shows it.",
                        fontSize = 13.sp, color = cs.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    FancyButton("Connect body data", icon = R.drawable.ic_scale, enabled = client != null, onClick = requestBody)
                }
            }
        } else {
            val b = body
            error?.let { Text("Couldn't read: $it", color = cs.error, fontSize = 13.sp) }
            if (b == null) {
                Text("Reading from Health Connect...", color = cs.onSurfaceVariant)
            } else {
                // Weight
                Reveal(1) {
                    AppCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Weight", fontSize = 13.sp, color = cs.onSurfaceVariant)
                                Text(
                                    b.weightKg?.let { weightText(it, settings.units) } ?: "No data",
                                    fontSize = 30.sp, fontWeight = FontWeight.Bold
                                )
                            }
                            b.weightChange30?.let { ch ->
                                val shown = if (settings.units == 1) ch * 2.20462 else ch
                                Text(
                                    (if (shown >= 0) "+" else "") + Format.r1(shown) + (if (settings.units == 1) " lb" else " kg") + " (90d)",
                                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                    color = if (shown >= 0) Brand.Carbs else Brand.Protein
                                )
                            }
                        }
                        if (b.weightSeries.size >= 2) {
                            Spacer(Modifier.height(10.dp))
                            val mult = if (settings.units == 1) 2.20462 else 1.0
                            val n = b.weightSeries.size
                            AreaChart(
                                values = b.weightSeries.map { it.second * mult },
                                shortLabels = b.weightSeries.mapIndexed { i, (d, _) ->
                                    if (i == 0 || i == n - 1 || i == n / 2) d.format(DateTimeFormatter.ofPattern("d MMM")) else ""
                                },
                                fullLabels = b.weightSeries.map { it.first.format(DateTimeFormatter.ofPattern("EEE, d MMM")) },
                                goal = null, color = Brand.Protein, unit = if (settings.units == 1) "lb" else "kg"
                            )
                        }
                    }
                }
                // Composition
                Reveal(2) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BodyTile("Body fat", b.bodyFatPct?.let { "${Format.r1(it)}%" } ?: "-", null, Brand.Fat, Modifier.weight(1f))
                            BodyTile("BMI", b.bmi?.let { Format.r1(it) } ?: "-", null, Brand.Carbs, Modifier.weight(1f))
                            BodyTile("Height", b.heightM?.let { heightText(it, settings.units) } ?: "-", null, Brand.Protein, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BodyTile("Lean mass", b.leanKg?.let { weightText(it, settings.units) } ?: "-", null, cs.primary, Modifier.weight(1f))
                            BodyTile("Bone mass", b.boneKg?.let { weightText(it, settings.units) } ?: "-", null, Brand.Water, Modifier.weight(1f))
                            BodyTile("BMR", b.bmr?.let { "${Format.int(it)} kcal" } ?: "-", null, Brand.Fiber, Modifier.weight(1f))
                        }
                    }
                }
                // Activity today
                Reveal(3) {
                    AppCard {
                        Text("Activity today", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ActivityStat(R.drawable.ic_walk, "Steps", Format.int(b.steps.toDouble()), Modifier.weight(1f))
                            ActivityStat(R.drawable.ic_flame, "Active", "${Format.int(b.activeKcal)} kcal", Modifier.weight(1f))
                            ActivityStat(R.drawable.ic_bolt, "Burned", "${Format.int(b.totalKcal)} kcal", Modifier.weight(1f))
                        }
                        if (b.totalKcal > 0 && hasPerms) {
                            val net = eaten - b.totalKcal
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Energy balance: ${if (net >= 0) "+" else "-"}${Format.int(abs(net))} kcal " +
                                    "(${Format.int(eaten)} eaten, ${Format.int(b.totalKcal)} burned so far)",
                                fontSize = 13.sp, color = cs.onSurfaceVariant
                            )
                        }
                    }
                }
                if (b.workouts.isNotEmpty()) {
                    Reveal(4) { WorkoutsCard(b.workouts, client) }
                }
            }
        }
        Spacer(Modifier.height(100.dp))
    }
}

@Composable
private fun MiniTag(text: String) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.clip(RoundedCornerShape(50)).background(cs.primaryContainer).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(text, fontSize = 11.sp, color = cs.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ActivityStat(icon: Int, label: String, value: String, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(cs.surfaceVariant).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NIcon(icon, cs.primary, 22.dp)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, fontSize = 11.sp, color = cs.onSurfaceVariant)
    }
}

@Composable
private fun EditProfile(settings: Settings, onDone: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var p by remember { mutableStateOf(settings.profile) }
    var error by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SubHeader("Edit profile", onDone)
        AvatarEditor(AvatarSpec.parse(p.avatar)) { p = p.copy(avatar = it.encode()) }

        AppCard {
            Text("About you", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(p.first, { p = p.copy(first = it) }, label = { Text("First name") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(p.last, { p = p.copy(last = it) }, label = { Text("Last name") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(p.nick, { p = p.copy(nick = it.replace(" ", "")) }, label = { Text("Nickname") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    p.email, { p = p.copy(email = it.trim()) }, label = { Text("Email") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Birthday", fontSize = 13.sp, color = cs.onSurfaceVariant)
                    Text(if (p.birth.isBlank()) "Not set" else p.birth, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
                FancyButton("Pick date", compact = true, filled = false) {
                    val d = try { LocalDate.parse(p.birth) } catch (e: Exception) { LocalDate.now().minusYears(25) }
                    AppLock.skipNext = true
                    DatePickerDialog(ctx, { _, y, m, day -> p = p.copy(birth = LocalDate.of(y, m + 1, day).toString()) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                }
            }
        }

        AppCard {
            Text("Sex", fontSize = 13.sp, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Not set", "Male", "Female", "Other").forEachIndexed { i, l -> FancyChip(l, p.sex == i) { p = p.copy(sex = i) } }
            }
            Spacer(Modifier.height(14.dp))
            Text("Current goal", fontSize = 13.sp, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Cutting", "Maintaining", "Bulking").forEachIndexed { i, l -> FancyChip(l, p.goalType == i) { p = p.copy(goalType = i) } }
            }
        }

        if (error.isNotEmpty()) Text(error, color = cs.error, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FancyButton("Save", Modifier.weight(1f), icon = R.drawable.ic_check) {
                if (p.email.isNotBlank() && !p.email.contains("@")) {
                    error = "That email doesn't look right"
                } else {
                    settings.saveProfile(p)
                    onDone()
                }
            }
            FancyButton("Cancel", Modifier.weight(1f), filled = false, onClick = onDone)
        }
        Spacer(Modifier.height(100.dp))
    }
}
