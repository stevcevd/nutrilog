package com.example.nutrilog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Animates from 0 to [target] the first time it appears, then follows changes. */
@Composable
fun rememberAnimatedFloat(target: Float, duration: Int = 1000): Float {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(target) { t = target }
    val v by animateFloatAsState(t, tween(duration, easing = FastOutSlowInEasing), label = "anim")
    return v
}

fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val cb by rememberUpdatedState(onClick)
    val haptic = LocalHapticFeedback.current
    val s by animateFloatAsState(
        if (pressed) 0.94f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "bounce"
    )
    this.scale(s).pointerInput(enabled) {
        detectTapGestures(
            onPress = {
                if (enabled) {
                    pressed = true
                    tryAwaitRelease()
                    pressed = false
                }
            },
            onTap = { if (enabled) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); cb() } }
        )
    }
}

@Composable
fun NIcon(res: Int, tint: Color = LocalContentColor.current, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Icon(painterResource(res), contentDescription = null, modifier = modifier.size(size), tint = tint)
}

@Composable
fun Reveal(index: Int = 0, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 70L)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(450)) + slideInVertically(tween(450)) { it / 8 }
    ) { content() }
}

@Composable
fun AppCard(modifier: Modifier = Modifier, padding: Dp = 18.dp, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp)) {
        if (subtitle != null) {
            Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier,
        fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun FancyButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: Int? = null,
    filled: Boolean = true,
    danger: Boolean = false,
    compact: Boolean = false,
    enabled: Boolean = true,
    accent: Color? = null,
    onAccent: Color? = null,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val base = when {
        danger -> Brand.Warn
        accent != null -> accent
        else -> cs.primary
    }
    val shape = RoundedCornerShape(if (compact) 14.dp else 18.dp)
    val fg = when {
        !enabled -> cs.onSurfaceVariant
        !filled -> base
        else -> onAccent ?: cs.onPrimary
    }
    val bgModifier = when {
        !enabled -> Modifier.background(cs.surfaceVariant)
        filled -> Modifier.background(Brush.linearGradient(listOf(base, lerp(base, Color.Black, 0.2f))))
        else -> Modifier.border(1.5.dp, base.copy(alpha = 0.5f), shape)
    }
    Row(
        modifier.bounceClick(enabled, onClick).clip(shape).then(bgModifier)
            .padding(horizontal = if (compact) 12.dp else 18.dp, vertical = if (compact) 8.dp else 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) NIcon(icon, fg, if (compact) 18.dp else 20.dp)
        if (icon != null && text.isNotEmpty()) Spacer(Modifier.width(6.dp))
        if (text.isNotEmpty()) {
            Text(text, color = fg, fontSize = if (compact) 13.sp else 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
fun FancyChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) cs.primary else cs.surfaceVariant, label = "chipbg")
    val fg by animateColorAsState(if (selected) cs.onPrimary else cs.onSurfaceVariant, label = "chipfg")
    Box(
        modifier.bounceClick(true, onClick).clip(RoundedCornerShape(50)).background(bg)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun CountUp(
    value: Double,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 24.sp,
    color: Color = Color.Unspecified,
    weight: FontWeight = FontWeight.Bold,
    suffix: String = ""
) {
    val v = rememberAnimatedFloat(value.toFloat(), 1000)
    Text(Format.int(v.toDouble()) + suffix, modifier, fontSize = fontSize, color = color, fontWeight = weight)
}

@Composable
fun NutrientBar(n: Nutrient, value: Double, goal: Double, modifier: Modifier = Modifier, colorOverride: Color? = null) {
    val cs = MaterialTheme.colorScheme
    val frac = if (goal > 0) (value / goal).toFloat() else 0f
    val a = rememberAnimatedFloat(frac.coerceIn(0f, 1f), 900)
    val over = goal > 0 && value > goal && n.key in Nutrients.limitKeys
    val c = colorOverride ?: (if (over) Brand.Warn else Brand.colorFor(n.key, cs.primary))
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(n.name, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            val g = if (goal > 0) " / ${Format.num(goal)}" else ""
            val pct = if (goal > 0) "  ${(value / goal * 100).toInt()}%" else ""
            Text("${Format.r1(value)}$g ${n.unit}$pct", fontSize = 12.sp, color = if (over) Brand.Warn else cs.onSurfaceVariant)
        }
        Spacer(Modifier.height(5.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(cs.surfaceVariant)) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(a).clip(RoundedCornerShape(4.dp))
                    .background(Brush.horizontalGradient(listOf(c.copy(alpha = 0.65f), c)))
            )
        }
    }
}

@Composable
fun FloatingNavBar(items: List<Pair<String, Int>>, selected: Int, onSelect: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = cs.surface,
            shadowElevation = 14.dp,
            border = BorderStroke(1.dp, cs.outline.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(8.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { i, (label, icon) ->
                    val sel = i == selected
                    val bg by animateColorAsState(if (sel) cs.primaryContainer else Color.Transparent, label = "navbg")
                    val fg by animateColorAsState(if (sel) cs.onPrimaryContainer else cs.onSurfaceVariant, label = "navfg")
                    Row(
                        Modifier.animateContentSize().clip(RoundedCornerShape(22.dp)).background(bg)
                            .clickable { onSelect(i) }
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NIcon(icon, fg)
                        if (sel) {
                            Spacer(Modifier.width(6.dp))
                            Text(label, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
