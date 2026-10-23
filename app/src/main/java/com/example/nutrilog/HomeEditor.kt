package com.example.nutrilog

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object HomeLayout {
    val sections = listOf(
        "hero" to "Today summary",
        "fasting" to "Since last meal",
        "highlights" to "Highlighted nutrients",
        "water" to "Hydration",
        "meals" to "Today's meals"
    )

    fun name(id: String) = sections.firstOrNull { it.first == id }?.second ?: id

    /** Keeps known sections only, in the saved order, and adds any new ones at the end. */
    fun normalize(order: List<String>): List<String> {
        val ids = sections.map { it.first }
        val known = order.filter { it in ids }.distinct()
        return known + ids.filter { it !in known }
    }
}

@Composable
private fun MoveButton(icon: Int, enabled: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(36.dp).alpha(if (enabled) 1f else 0.3f).clip(RoundedCornerShape(12.dp))
            .background(cs.surfaceVariant).bounceClick(enabled, onClick),
        contentAlignment = Alignment.Center
    ) { NIcon(icon, cs.onSurface, 18.dp) }
}

/** Calorie display, section order and visibility, and which nutrients are highlighted. */
@Composable
fun HomeEditor(settings: Settings) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppCard {
            SectionLabel("Calorie display")
            Text("How calories and macros are drawn on Home and in the Diary", fontSize = 12.sp, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FancyChip("Ring and bars", settings.ringStyle == 0) { settings.setRingStyle(0) }
                FancyChip("Classic rings", settings.ringStyle == 1) { settings.setRingStyle(1) }
            }
        }

        AppCard {
            SectionLabel("Sections")
            Text("Move sections up or down, or switch them off", fontSize = 12.sp, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            val order = settings.homeOrder
            order.forEachIndexed { i, id ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(HomeLayout.name(id), Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    MoveButton(R.drawable.ic_arrow_up, i > 0) {
                        val l = order.toMutableList(); java.util.Collections.swap(l, i, i - 1); settings.setHomeOrder(l)
                    }
                    Spacer(Modifier.width(6.dp))
                    MoveButton(R.drawable.ic_arrow_down, i < order.lastIndex) {
                        val l = order.toMutableList(); java.util.Collections.swap(l, i, i + 1); settings.setHomeOrder(l)
                    }
                    Spacer(Modifier.width(6.dp))
                    Switch(
                        checked = id !in settings.homeHidden,
                        onCheckedChange = { on -> settings.setHomeHidden(if (on) settings.homeHidden - id else settings.homeHidden + id) }
                    )
                }
            }
        }

        AppCard {
            SectionLabel("Highlighted nutrients")
            val cur = settings.highlights
            Text("${cur.size} of 9 chosen. Tap to add or remove, shown in the order you add them.", fontSize = 12.sp, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Nutrients.groups.forEach { grp ->
                val list = Nutrients.all.filter { it.group == grp && it.key != "kcal" }
                if (list.isNotEmpty()) {
                    Text(grp, Modifier.padding(top = 8.dp, bottom = 6.dp), fontSize = 12.sp, color = cs.onSurfaceVariant)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        list.forEach { n ->
                            FancyChip(n.name, n.key in cur) {
                                settings.setHighlights(
                                    if (n.key in cur) cur - n.key else if (cur.size < 9) cur + n.key else cur
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeSettingsScreen(settings: Settings, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SubHeader("Home screen", onBack)
        Text("Tip: you can also press and hold the greeting on Home to edit it there.", fontSize = 12.sp, color = cs.onSurfaceVariant)
        HomeEditor(settings)
        Spacer(Modifier.height(100.dp))
    }
}
