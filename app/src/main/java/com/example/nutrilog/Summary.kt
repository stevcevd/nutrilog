package com.example.nutrilog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The calorie + macro summary used on Home (hero = true, on the green card) and in the Diary.
 * style 0: one thick calorie ring with a roomy centre, macros as thick flat bars.
 * style 1: classic concentric rings, now sized so the centre number always fits.
 */
@Composable
fun CalorieSummary(
    style: Int,
    hero: Boolean,
    showRemaining: Boolean,
    kcal: Double, kcalGoal: Double,
    p: Double, pGoal: Double,
    c: Double, cGoal: Double,
    f: Double, fGoal: Double,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val textColor = if (hero) Color.White else cs.onSurface
    val subColor = if (hero) Color.White.copy(alpha = 0.75f) else cs.onSurfaceVariant
    val track = if (hero) Color.White.copy(alpha = 0.18f) else cs.surfaceVariant
    val ringColor = if (hero) Color.White else cs.primary
    val pc = if (hero) Color(0xFF93C5FD) else Brand.Protein
    val cc = if (hero) Color(0xFFFCD34D) else Brand.Carbs
    val fc = if (hero) Color(0xFFF9A8D4) else Brand.Fat
    fun frac(v: Double, g: Double) = if (g > 0) (v / g).toFloat() else 0f

    val centre: @Composable BoxScope.() -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CountUp(
                kcal,
                fontSize = if (style == 0) (if (hero) 30.sp else 26.sp) else (if (hero) 24.sp else 20.sp),
                color = textColor
            )
            Text(if (showRemaining) "kcal eaten" else "of ${Format.int(kcalGoal)}", fontSize = 11.sp, color = subColor)
        }
    }

    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (style == 0) {
            RingChart(
                frac(kcal, kcalGoal), ringColor,
                Modifier.size(if (hero) 142.dp else 118.dp),
                stroke = if (hero) 16.dp else 14.dp, track = track, cap = StrokeCap.Butt, content = centre
            )
        } else {
            ActivityRings(
                listOf(frac(kcal, kcalGoal), frac(p, pGoal), frac(c, cGoal), frac(f, fGoal)),
                listOf(ringColor, pc, cc, fc),
                Modifier.size(if (hero) 168.dp else 140.dp),
                stroke = if (hero) 9.dp else 7.dp, gap = if (hero) 3.dp else 2.5.dp,
                track = track, content = centre
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            if (showRemaining) {
                Text("Remaining", fontSize = 12.sp, color = subColor)
                Row(verticalAlignment = Alignment.Bottom) {
                    CountUp(maxOf(kcalGoal - kcal, 0.0), fontSize = 28.sp, color = textColor)
                    Text(" kcal", fontSize = 13.sp, color = subColor, modifier = Modifier.padding(bottom = 3.dp))
                }
                Spacer(Modifier.height(8.dp))
            }
            if (style == 0) {
                MacroBar("Protein", p, pGoal, pc, track, textColor, subColor)
                MacroBar("Carbs", c, cGoal, cc, track, textColor, subColor)
                MacroBar("Fat", f, fGoal, fc, track, textColor, subColor)
            } else {
                LegendRow("Protein", p, pGoal, pc, textColor, subColor)
                LegendRow("Carbs", c, cGoal, cc, textColor, subColor)
                LegendRow("Fat", f, fGoal, fc, textColor, subColor)
            }
        }
    }
}

@Composable
private fun MacroBar(name: String, value: Double, goal: Double, color: Color, track: Color, textColor: Color, subColor: Color) {
    val a = rememberAnimatedFloat(if (goal > 0) (value / goal).toFloat().coerceIn(0f, 1f) else 0f, 900)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = textColor)
            Text("${Format.r1(value)} / ${Format.num(goal)} g", fontSize = 12.sp, color = subColor, maxLines = 1)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(3.dp)).background(track)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(a).background(color))
        }
    }
}

@Composable
private fun LegendRow(name: String, value: Double, goal: Double, color: Color, textColor: Color, subColor: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(name, Modifier.weight(1f), fontSize = 13.sp, color = textColor)
        Text("${Format.int(value)} / ${Format.int(goal)} g", fontSize = 12.sp, color = subColor, maxLines = 1)
    }
}
