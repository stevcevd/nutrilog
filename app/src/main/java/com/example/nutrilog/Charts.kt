package com.example.nutrilog

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.sin

/** Single progress ring with an animated sweep. */
@Composable
fun RingChart(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    stroke: Dp = 12.dp,
    track: Color = MaterialTheme.colorScheme.surfaceVariant,
    cap: StrokeCap = StrokeCap.Round,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val a = rememberAnimatedFloat(progress.coerceIn(0f, 1f), 1000)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val sz = Size(size.width - sw, size.height - sw)
            val tl = Offset(sw / 2, sw / 2)
            drawArc(color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = tl, size = sz, style = Stroke(sw))
            if (a > 0.002f) {
                drawArc(
                    color = color, startAngle = -90f, sweepAngle = 360f * a, useCenter = false,
                    topLeft = tl, size = sz, style = Stroke(sw, cap = cap)
                )
            }
        }
        content()
    }
}

/** Concentric "activity" rings, outermost first. */
@Composable
fun ActivityRings(
    progress: List<Float>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    stroke: Dp = 12.dp,
    gap: Dp = 5.dp,
    track: Color = MaterialTheme.colorScheme.surfaceVariant,
    cap: StrokeCap = StrokeCap.Round,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val anims = progress.map { rememberAnimatedFloat(it.coerceIn(0f, 1f), 1100) }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val g = gap.toPx()
            anims.forEachIndexed { i, a ->
                val inset = sw / 2 + i * (sw + g)
                val sz = Size(size.width - 2 * inset, size.height - 2 * inset)
                val tl = Offset(inset, inset)
                drawArc(color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = tl, size = sz, style = Stroke(sw))
                if (a > 0.002f) {
                    drawArc(
                        color = colors[i], startAngle = -90f, sweepAngle = 360f * a, useCenter = false,
                        topLeft = tl, size = sz, style = Stroke(sw, cap = cap)
                    )
                }
            }
        }
        content()
    }
}

@Composable
fun DonutChart(
    values: List<Float>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    stroke: Dp = 18.dp,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val a = rememberAnimatedFloat(1f, 1100)
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val sz = Size(size.width - sw, size.height - sw)
            val tl = Offset(sw / 2, sw / 2)
            val total = values.sum()
            if (total <= 0f) {
                drawArc(color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = tl, size = sz, style = Stroke(sw))
            } else {
                var start = -90f
                val multi = values.count { it > 0f } > 1
                values.forEachIndexed { i, v ->
                    val sweep = 360f * v / total * a
                    val shown = if (multi) max(sweep - 3f, 0.1f) else sweep
                    if (v > 0f) {
                        drawArc(
                            color = colors[i], startAngle = start + (if (multi) 1.5f else 0f), sweepAngle = shown,
                            useCenter = false, topLeft = tl, size = sz, style = Stroke(sw)
                        )
                    }
                    start += sweep
                }
            }
        }
        content()
    }
}

/** Animated water glass with a moving wave surface. */
@Composable
fun WaterGlass(level: Float, modifier: Modifier = Modifier) {
    val lvl = rememberAnimatedFloat(level.coerceIn(0f, 1f), 1200)
    val inf = rememberInfiniteTransition(label = "wave")
    val phase by inf.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )
    val outline = MaterialTheme.colorScheme.outline
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val corner = CornerRadius(w * 0.2f, w * 0.2f)
        val glass = Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, corner)) }
        clipPath(glass) {
            val top = h * (1f - lvl)
            val amp = if (lvl > 0.02f) h * 0.022f else 0f
            val wave = Path().apply {
                moveTo(0f, h)
                lineTo(0f, top)
                var x = 0f
                while (x <= w) {
                    lineTo(x, top + amp * sin(phase + x / w * 2f * Math.PI.toFloat() * 1.3f))
                    x += 4f
                }
                lineTo(w, h)
                close()
            }
            if (lvl > 0.005f) {
                drawPath(wave, Brush.verticalGradient(listOf(Brand.Water.copy(alpha = 0.8f), Color(0xFF0EA5E9)), startY = top, endY = h))
            }
        }
        drawRoundRect(color = outline, size = size, cornerRadius = corner, style = Stroke(3.dp.toPx()))
    }
}

@Composable
private fun ChartHeader(sel: Int, fullLabels: List<String>, values: List<Double>, unit: String, goal: Double?) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
        AnimatedContent(targetState = sel, modifier = Modifier.weight(1f), label = "chartHeader") { s ->
            if (s in values.indices) {
                Text("${fullLabels[s]}  ·  ${Format.r1(values[s])} $unit", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Text("Tap or drag to inspect", fontSize = 13.sp, color = cs.onSurfaceVariant)
            }
        }
        if (goal != null && goal > 0) {
            Text("Goal ${Format.num(goal)} $unit", fontSize = 12.sp, color = Brand.Carbs)
        }
    }
}

@Composable
private fun AxisLabels(labels: List<String>) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        labels.forEach {
            Text(
                it, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 10.sp, maxLines = 1, softWrap = false,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AreaChart(
    values: List<Double>,
    shortLabels: List<String>,
    fullLabels: List<String>,
    goal: Double?,
    color: Color,
    unit: String,
    modifier: Modifier = Modifier
) {
    val anim = rememberAnimatedFloat(1f, 1300)
    var sel by remember(values) { mutableIntStateOf(-1) }
    val n = values.size
    val cs = MaterialTheme.colorScheme
    val grid = cs.outline.copy(alpha = 0.25f)
    val surface = cs.surface
    val maxV = (maxOf(values.maxOrNull() ?: 0.0, goal ?: 0.0).coerceAtLeast(1.0) * 1.18).toFloat()

    Column(modifier) {
        ChartHeader(sel, fullLabels, values, unit, goal)
        Canvas(
            Modifier.fillMaxWidth().height(190.dp).padding(top = 6.dp)
                .pointerInput(n) {
                    detectTapGestures { o -> if (n > 0) sel = (o.x / size.width * n).toInt().coerceIn(0, n - 1) }
                }
                .pointerInput(n) {
                    detectHorizontalDragGestures { change, _ ->
                        if (n > 0) sel = (change.position.x / size.width * n).toInt().coerceIn(0, n - 1)
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            for (i in 0..3) {
                val y = h * i / 3f
                drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
            }
            if (n == 0) return@Canvas
            val xAt = { i: Int -> (i + 0.5f) * w / n }
            val yAt = { v: Double -> h - (v.toFloat() / maxV) * h }

            if (goal != null && goal > 0) {
                drawLine(
                    Brand.Carbs, Offset(0f, yAt(goal)), Offset(w, yAt(goal)),
                    strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                )
            }

            val line = Path()
            val fill = Path()
            for (i in 0 until n) {
                val x = xAt(i)
                val y = yAt(values[i])
                if (i == 0) {
                    line.moveTo(x, y)
                    fill.moveTo(x, h)
                    fill.lineTo(x, y)
                } else {
                    val x0 = xAt(i - 1)
                    val y0 = yAt(values[i - 1])
                    val mx = (x0 + x) / 2
                    line.cubicTo(mx, y0, mx, y, x, y)
                    fill.cubicTo(mx, y0, mx, y, x, y)
                }
            }
            fill.lineTo(xAt(n - 1), h)
            fill.lineTo(xAt(0), h)
            fill.close()

            clipRect(right = w * anim) {
                drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.38f), color.copy(alpha = 0f)), startY = 0f, endY = h))
                drawPath(line, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            if (sel in 0 until n) {
                drawLine(color.copy(alpha = 0.35f), Offset(xAt(sel), 0f), Offset(xAt(sel), h), strokeWidth = 1.5.dp.toPx())
            }
            for (i in 0 until n) {
                val x = xAt(i)
                if (x > w * anim) continue
                if (n <= 14 || i == sel) {
                    val r = if (i == sel) 6.dp.toPx() else 4.dp.toPx()
                    drawCircle(surface, r + 2.dp.toPx(), Offset(x, yAt(values[i])))
                    drawCircle(color, r, Offset(x, yAt(values[i])))
                }
            }
        }
        AxisLabels(shortLabels)
    }
}

@Composable
fun BarChart(
    values: List<Double>,
    shortLabels: List<String>,
    fullLabels: List<String>,
    goal: Double?,
    color: Color,
    unit: String,
    modifier: Modifier = Modifier,
    highlightOver: Boolean = false
) {
    val anim = rememberAnimatedFloat(1f, 1000)
    var sel by remember(values) { mutableIntStateOf(-1) }
    val n = values.size
    val cs = MaterialTheme.colorScheme
    val grid = cs.outline.copy(alpha = 0.25f)
    val maxV = (maxOf(values.maxOrNull() ?: 0.0, goal ?: 0.0).coerceAtLeast(1.0) * 1.18).toFloat()

    Column(modifier) {
        ChartHeader(sel, fullLabels, values, unit, goal)
        Canvas(
            Modifier.fillMaxWidth().height(190.dp).padding(top = 6.dp)
                .pointerInput(n) {
                    detectTapGestures { o -> if (n > 0) sel = (o.x / size.width * n).toInt().coerceIn(0, n - 1) }
                }
                .pointerInput(n) {
                    detectHorizontalDragGestures { change, _ ->
                        if (n > 0) sel = (change.position.x / size.width * n).toInt().coerceIn(0, n - 1)
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            for (i in 0..3) {
                val y = h * i / 3f
                drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
            }
            if (n == 0) return@Canvas
            val slot = w / n
            val barW = slot * 0.6f
            values.forEachIndexed { i, v ->
                val barH = (v.toFloat() / maxV) * h * anim
                val x = i * slot + (slot - barW) / 2
                val over = highlightOver && goal != null && goal > 0 && v > goal * 1.1
                val base = if (over) Brand.Carbs else color
                val c = if (sel == -1 || sel == i) base else base.copy(alpha = 0.35f)
                if (barH > 0.5f) {
                    drawRoundRect(
                        brush = Brush.verticalGradient(listOf(c, c.copy(alpha = 0.6f)), startY = h - barH, endY = h),
                        topLeft = Offset(x, h - barH),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(barW / 3f, barW / 3f)
                    )
                }
            }
            if (goal != null && goal > 0) {
                val gy = h - (goal.toFloat() / maxV) * h
                drawLine(
                    Brand.Carbs, Offset(0f, gy), Offset(w, gy),
                    strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                )
            }
        }
        AxisLabels(shortLabels)
    }
}

/** Weekly average calories as bars (left scale) with average weight as a line (own scale). */
@Composable
fun WeightCalChart(
    kcal: List<Double?>,
    weight: List<Double?>,
    shortLabels: List<String>,
    kcalGoal: Double?,
    weightUnit: String,
    modifier: Modifier = Modifier
) {
    val anim = rememberAnimatedFloat(1f, 1200)
    var sel by remember(kcal, weight) { mutableIntStateOf(-1) }
    val n = kcal.size
    val cs = MaterialTheme.colorScheme
    val grid = cs.outline.copy(alpha = 0.25f)
    val surface = cs.surface
    val maxK = (maxOf(kcal.filterNotNull().maxOrNull() ?: 0.0, kcalGoal ?: 0.0).coerceAtLeast(1.0) * 1.2).toFloat()
    val ws = weight.filterNotNull()
    val wMin = ws.minOrNull() ?: 0.0
    val wMax = ws.maxOrNull() ?: 1.0
    val pad = maxOf((wMax - wMin) * 0.3, 0.5)
    val lo = (wMin - pad).toFloat()
    val hi = (wMax + pad).toFloat()

    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.CenterStart) {
            if (sel in 0 until n) {
                val k = kcal[sel]?.let { Format.int(it) + " kcal/day" } ?: "no food logged"
                val w = weight[sel]?.let { Format.r1(it) + " " + weightUnit } ?: "no weigh-in"
                Text("${shortLabels[sel]}  ·  $k  ·  $w", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Text("Tap or drag to inspect", fontSize = 13.sp, color = cs.onSurfaceVariant)
            }
        }
        Canvas(
            Modifier.fillMaxWidth().height(200.dp).padding(top = 6.dp)
                .pointerInput(n) {
                    detectTapGestures { o -> if (n > 0) sel = (o.x / size.width * n).toInt().coerceIn(0, n - 1) }
                }
                .pointerInput(n) {
                    detectHorizontalDragGestures { change, _ ->
                        if (n > 0) sel = (change.position.x / size.width * n).toInt().coerceIn(0, n - 1)
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            for (i in 0..3) {
                val y = h * i / 3f
                drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
            }
            if (n == 0) return@Canvas
            val slot = w / n
            val barW = slot * 0.52f
            kcal.forEachIndexed { i, v ->
                if (v != null) {
                    val bh = (v.toFloat() / maxK) * h * anim
                    val c = Brand.Emerald.copy(alpha = if (sel == -1 || sel == i) 0.55f else 0.22f)
                    drawRoundRect(
                        color = c, topLeft = Offset(i * slot + (slot - barW) / 2, h - bh),
                        size = Size(barW, bh), cornerRadius = CornerRadius(barW / 4f, barW / 4f)
                    )
                }
            }
            if (kcalGoal != null && kcalGoal > 0) {
                val gy = h - (kcalGoal.toFloat() / maxK) * h
                drawLine(Brand.Carbs, Offset(0f, gy), Offset(w, gy), strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
            }

            val xAt = { i: Int -> (i + 0.5f) * slot }
            val yAt = { v: Double -> h - ((v.toFloat() - lo) / (hi - lo)) * h }
            val line = Path()
            var pen = false
            weight.forEachIndexed { i, v ->
                if (v == null) {
                    pen = false
                } else {
                    if (!pen) { line.moveTo(xAt(i), yAt(v)); pen = true } else line.lineTo(xAt(i), yAt(v))
                }
            }
            clipRect(right = w * anim) {
                drawPath(line, Brand.Protein, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            weight.forEachIndexed { i, v ->
                if (v != null && xAt(i) <= w * anim) {
                    drawCircle(surface, 6.dp.toPx(), Offset(xAt(i), yAt(v)))
                    drawCircle(Brand.Protein, 4.dp.toPx(), Offset(xAt(i), yAt(v)))
                }
            }
        }
        AxisLabels(shortLabels)
    }
}
