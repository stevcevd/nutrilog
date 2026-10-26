package com.example.nutrilog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.min

/** Deep gradient used on the Home summary card; always dark enough for white text. */
val LocalHero = staticCompositionLocalOf { listOf(Color(0xFF059669), Color(0xFF0F766E), Color(0xFF155E75)) }

private fun hsl(h: Float, s: Float, l: Float): Color {
    val c = (1f - abs(2f * l - 1f)) * s
    val hp = (((h % 360f) + 360f) % 360f) / 60f
    val x = c * (1f - abs(hp % 2f - 1f))
    val rgb = when {
        hp < 1f -> Triple(c, x, 0f)
        hp < 2f -> Triple(x, c, 0f)
        hp < 3f -> Triple(0f, c, x)
        hp < 4f -> Triple(0f, x, c)
        hp < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = l - c / 2f
    return Color(rgb.first + m, rgb.second + m, rgb.third + m)
}

data class ThemeDef(val name: String, val hue: Float, val sat: Float)

object AppThemes {
    val all = listOf(
        ThemeDef("Emerald", 160f, 0.90f),
        ThemeDef("Ocean", 205f, 0.85f),
        ThemeDef("Indigo", 235f, 0.70f),
        ThemeDef("Violet", 268f, 0.65f),
        ThemeDef("Rose", 338f, 0.70f),
        ThemeDef("Crimson", 355f, 0.75f),
        ThemeDef("Sunset", 20f, 0.85f),
        ThemeDef("Amber", 38f, 0.90f),
        ThemeDef("Lagoon", 184f, 0.85f),
        ThemeDef("Graphite", 215f, 0.10f)
    )

    fun scheme(id: Int, dark: Boolean): ColorScheme {
        if (id <= 0) return if (dark) EmeraldDark else EmeraldLight
        val t = all[id.coerceIn(0, all.size - 1)]
        val h = t.hue
        val s = t.sat
        val soft = if (s < 0.2f) 0.05f else 0.25f
        return if (!dark) {
            lightColorScheme(
                primary = hsl(h, s, 0.34f), onPrimary = Color.White,
                primaryContainer = hsl(h, s * 0.9f, 0.90f), onPrimaryContainer = hsl(h, s, 0.17f),
                secondary = hsl(h + 30f, s, 0.45f),
                background = hsl(h, soft, 0.965f), onBackground = hsl(h, 0.25f, 0.09f),
                surface = Color.White, onSurface = hsl(h, 0.25f, 0.09f),
                surfaceVariant = hsl(h, soft, 0.92f), onSurfaceVariant = hsl(h, 0.12f, 0.38f),
                outline = hsl(h, 0.12f, 0.74f), error = Color(0xFFDC2626)
            )
        } else {
            val deep = if (s < 0.2f) 0.06f else 0.30f
            darkColorScheme(
                primary = hsl(h, min(s, 0.9f) * 0.9f, 0.66f), onPrimary = hsl(h, s, 0.10f),
                primaryContainer = hsl(h, s * 0.7f, 0.20f), onPrimaryContainer = hsl(h, s * 0.8f, 0.85f),
                secondary = hsl(h + 30f, s, 0.65f),
                background = hsl(h, deep, 0.055f), onBackground = hsl(h, 0.15f, 0.92f),
                surface = hsl(h, deep, 0.09f), onSurface = hsl(h, 0.15f, 0.92f),
                surfaceVariant = hsl(h, deep, 0.15f), onSurfaceVariant = hsl(h, 0.14f, 0.68f),
                outline = hsl(h, 0.12f, 0.28f), error = Color(0xFFF87171)
            )
        }
    }

    fun hero(id: Int): List<Color> {
        if (id <= 0) return listOf(Color(0xFF059669), Color(0xFF0F766E), Color(0xFF155E75))
        val t = all[id.coerceIn(0, all.size - 1)]
        return listOf(
            hsl(t.hue, t.sat, 0.30f),
            hsl(t.hue + 14f, t.sat * 0.95f, 0.26f),
            hsl(t.hue + 38f, t.sat * 0.9f, 0.24f)
        )
    }
}

@Composable
private fun PreviewPanel(scheme: ColorScheme, hero: List<Color>, modifier: Modifier) {
    Column(
        modifier.height(70.dp).clip(RoundedCornerShape(14.dp)).background(scheme.background).padding(6.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(24.dp).clip(RoundedCornerShape(8.dp)).background(Brush.linearGradient(hero)))
        Spacer(Modifier.height(5.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.width(26.dp).height(11.dp).clip(RoundedCornerShape(4.dp)).background(scheme.primary))
            Box(Modifier.width(20.dp).height(11.dp).clip(RoundedCornerShape(4.dp)).background(scheme.primaryContainer))
            Box(Modifier.width(12.dp).height(11.dp).clip(RoundedCornerShape(4.dp)).background(scheme.surfaceVariant))
        }
    }
}

@Composable
private fun ThemeCard(id: Int, selected: Boolean, modifier: Modifier, onPick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val t = AppThemes.all[id]
    val hero = AppThemes.hero(id)
    Column(
        modifier.clip(RoundedCornerShape(22.dp)).background(cs.surface)
            .border(if (selected) 2.5.dp else 1.dp, if (selected) cs.primary else cs.outline.copy(alpha = 0.3f), RoundedCornerShape(22.dp))
            .bounceClick(true, onPick).padding(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PreviewPanel(AppThemes.scheme(id, false), hero, Modifier.weight(1f))
            PreviewPanel(AppThemes.scheme(id, true), hero, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t.name, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (selected) NIcon(R.drawable.ic_check, cs.primary, 18.dp)
        }
        Text("Light and dark", fontSize = 11.sp, color = cs.onSurfaceVariant)
    }
}

@Composable
fun ThemesScreen(settings: Settings, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SubHeader("Themes", onBack)
        AppCard {
            Text("Mode", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("Every theme has a light and a dark look", fontSize = 12.sp, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("System", "Light", "Dark").forEachIndexed { i, l -> FancyChip(l, settings.themeMode == i) { settings.setTheme(i) } }
            }
        }
        AppThemes.all.indices.toList().chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { id -> ThemeCard(id, settings.themeId == id, Modifier.weight(1f)) { settings.setThemeId(id) } }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(100.dp))
    }
}
