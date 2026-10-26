package com.example.nutrilog

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

object Brand {
    val Teal = Color(0xFF0D9488)
    val Protein = Color(0xFF3B82F6)
    val Carbs = Color(0xFFF59E0B)
    val Fat = Color(0xFFEC4899)
    val Fiber = Color(0xFF84CC16)
    val Water = Color(0xFF38BDF8)
    val Warn = Color(0xFFEF4444)
    val Emerald = Color(0xFF10B981)

    fun colorFor(key: String, fallback: Color): Color = when (key) {
        "p" -> Protein
        "c" -> Carbs
        "f" -> Fat
        "fiber" -> Fiber
        else -> fallback
    }

    fun mealColor(meal: String): Color = when (meal) {
        "Breakfast" -> Carbs
        "Lunch" -> Emerald
        "Dinner" -> Protein
        else -> Fat
    }
}

val EmeraldLight = lightColorScheme(
    primary = Color(0xFF059669),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = Color(0xFF0EA5E9),
    background = Color(0xFFF4F8F6),
    onBackground = Color(0xFF0F1A16),
    surface = Color.White,
    onSurface = Color(0xFF0F1A16),
    surfaceVariant = Color(0xFFE5EEE9),
    onSurfaceVariant = Color(0xFF55675F),
    outline = Color(0xFFB4C4BB),
    error = Color(0xFFDC2626)
)

val EmeraldDark = darkColorScheme(
    primary = Color(0xFF34D399),
    onPrimary = Color(0xFF022C22),
    primaryContainer = Color(0xFF0B3B2E),
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = Color(0xFF38BDF8),
    background = Color(0xFF08100D),
    onBackground = Color(0xFFE6F2EC),
    surface = Color(0xFF111B17),
    onSurface = Color(0xFFE6F2EC),
    surfaceVariant = Color(0xFF1D2B26),
    onSurfaceVariant = Color(0xFF9DB3A9),
    outline = Color(0xFF3B4D45),
    error = Color(0xFFF87171)
)

@Composable
fun NutriTheme(dark: Boolean = isSystemInDarkTheme(), themeId: Int = 0, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalHero provides AppThemes.hero(themeId)) {
        MaterialTheme(colorScheme = AppThemes.scheme(themeId, dark), content = content)
    }
}
