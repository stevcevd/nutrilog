package com.example.nutrilog

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object AvatarParts {
    val bgs = listOf(
        Color(0xFF10B981) to Color(0xFF0D9488), Color(0xFF3B82F6) to Color(0xFF6366F1),
        Color(0xFFF59E0B) to Color(0xFFEF4444), Color(0xFFEC4899) to Color(0xFF8B5CF6),
        Color(0xFF38BDF8) to Color(0xFF0EA5E9), Color(0xFF84CC16) to Color(0xFF16A34A),
        Color(0xFF64748B) to Color(0xFF1E293B), Color(0xFFFB7185) to Color(0xFFF97316)
    )
    val skins = listOf(
        Color(0xFFFFDFC4), Color(0xFFF1C27D), Color(0xFFE0AC69),
        Color(0xFFC68642), Color(0xFF8D5524), Color(0xFF5C3A21)
    )
    val hairColors = listOf(
        Color(0xFF1F1B16), Color(0xFF4A312C), Color(0xFF8B5A2B), Color(0xFFD4A24C),
        Color(0xFFB7410E), Color(0xFFC9C9C9), Color(0xFF3B82F6), Color(0xFFEC4899)
    )
    val shirts = listOf(
        Color(0xFFFFFFFF), Color(0xFF111827), Color(0xFF10B981), Color(0xFF3B82F6),
        Color(0xFFF59E0B), Color(0xFFEF4444), Color(0xFF8B5CF6), Color(0xFF14B8A6)
    )
    const val HAIR = 7
    const val EYES = 5
    const val MOUTH = 5
    const val ACC = 5
}

/** The whole avatar is stored as one string, e.g. "v1;bg=2;skin=1;hair=3;hc=4;eyes=1;mouth=0;acc=2;shirt=5". */
data class AvatarSpec(
    val bg: Int = 0, val skin: Int = 1, val hair: Int = 1, val hc: Int = 0,
    val eyes: Int = 0, val mouth: Int = 0, val acc: Int = 0, val shirt: Int = 2
) {
    fun encode() = "v1;bg=$bg;skin=$skin;hair=$hair;hc=$hc;eyes=$eyes;mouth=$mouth;acc=$acc;shirt=$shirt"

    companion object {
        fun parse(s: String?): AvatarSpec {
            if (s.isNullOrBlank()) return AvatarSpec()
            val m = HashMap<String, Int>()
            s.split(';').forEach { part ->
                val kv = part.split('=')
                if (kv.size == 2) kv[1].toIntOrNull()?.let { m[kv[0]] = it }
            }
            fun g(k: String, max: Int, def: Int) = (m[k] ?: def).coerceIn(0, max - 1)
            return AvatarSpec(
                g("bg", AvatarParts.bgs.size, 0), g("skin", AvatarParts.skins.size, 1),
                g("hair", AvatarParts.HAIR, 1), g("hc", AvatarParts.hairColors.size, 0),
                g("eyes", AvatarParts.EYES, 0), g("mouth", AvatarParts.MOUTH, 0),
                g("acc", AvatarParts.ACC, 0), g("shirt", AvatarParts.shirts.size, 2)
            )
        }

        fun random(): AvatarSpec = AvatarSpec(
            Random.nextInt(AvatarParts.bgs.size), Random.nextInt(AvatarParts.skins.size),
            Random.nextInt(AvatarParts.HAIR), Random.nextInt(AvatarParts.hairColors.size),
            Random.nextInt(AvatarParts.EYES), Random.nextInt(AvatarParts.MOUTH),
            Random.nextInt(AvatarParts.ACC), Random.nextInt(AvatarParts.shirts.size)
        )
    }
}

@Composable
fun Avatar(spec: AvatarSpec, diameter: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(diameter).clip(CircleShape)) {
        val u = this.size.width / 100f
        fun o(x: Float, y: Float) = Offset(x * u, y * u)
        fun s(w: Float, h: Float) = Size(w * u, h * u)

        val skin = AvatarParts.skins[spec.skin]
        val hair = AvatarParts.hairColors[spec.hc]
        val shirt = AvatarParts.shirts[spec.shirt]
        val ink = Color(0xFF1F2937)
        val (b1, b2) = AvatarParts.bgs[spec.bg]

        drawRect(Brush.linearGradient(listOf(b1, b2), start = Offset.Zero, end = Offset(this.size.width, this.size.height)))

        // hair behind the head
        if (spec.hair == 2) drawRoundRect(hair, o(21f, 28f), s(58f, 62f), CornerRadius(26f * u, 26f * u))
        if (spec.hair == 3) drawCircle(hair, 11f * u, o(50f, 17f))

        // body, neck, head
        drawOval(shirt, o(10f, 76f), s(80f, 56f))
        drawRect(lerp(skin, Color.Black, 0.12f), o(43f, 62f), s(14f, 20f))
        drawCircle(skin, 26f * u, o(50f, 46f))

        // front hair
        val cap = { drawArc(hair, 205f, 130f, false, topLeft = o(22f, 18f), size = s(56f, 56f)) }
        when (spec.hair) {
            1, 2, 3 -> cap()
            4 -> {
                val path = Path().apply {
                    moveTo(24f * u, 37f * u); lineTo(27f * u, 15f * u); lineTo(38f * u, 28f * u)
                    lineTo(46f * u, 8f * u); lineTo(54f * u, 28f * u); lineTo(64f * u, 12f * u)
                    lineTo(70f * u, 28f * u); lineTo(76f * u, 37f * u); close()
                }
                drawPath(path, hair)
            }
            5 -> {
                cap()
                for (i in 0..6) {
                    val a = Math.toRadians(200.0 + i * 23.0)
                    drawCircle(hair, 8f * u, o(50f + 27f * cos(a).toFloat(), 46f + 27f * sin(a).toFloat()))
                }
            }
            6 -> drawRoundRect(hair, o(43f, 7f), s(14f, 30f), CornerRadius(7f * u, 7f * u))
        }

        // eyes
        when (spec.eyes) {
            0 -> { drawCircle(ink, 2.6f * u, o(40f, 47f)); drawCircle(ink, 2.6f * u, o(60f, 47f)) }
            1 -> {
                drawCircle(Color.White, 5f * u, o(40f, 47f)); drawCircle(Color.White, 5f * u, o(60f, 47f))
                drawCircle(ink, 2.6f * u, o(40f, 47f)); drawCircle(ink, 2.6f * u, o(60f, 47f))
            }
            2 -> {
                val st = Stroke(2.2f * u, cap = StrokeCap.Round)
                drawArc(ink, 200f, 140f, false, topLeft = o(35f, 44f), size = s(10f, 10f), style = st)
                drawArc(ink, 200f, 140f, false, topLeft = o(55f, 44f), size = s(10f, 10f), style = st)
            }
            3 -> {
                drawCircle(ink, 2.6f * u, o(40f, 47f))
                drawLine(ink, o(55f, 47f), o(65f, 47f), strokeWidth = 2.4f * u, cap = StrokeCap.Round)
            }
            4 -> {
                drawLine(ink, o(35f, 47f), o(45f, 47f), strokeWidth = 2.4f * u, cap = StrokeCap.Round)
                drawLine(ink, o(55f, 47f), o(65f, 47f), strokeWidth = 2.4f * u, cap = StrokeCap.Round)
            }
        }

        // mouth
        val st = Stroke(2.4f * u, cap = StrokeCap.Round)
        when (spec.mouth) {
            0 -> drawArc(ink, 20f, 140f, false, topLeft = o(40f, 50f), size = s(20f, 14f), style = st)
            1 -> drawArc(Color(0xFF7F1D1D), 0f, 180f, true, topLeft = o(40f, 52f), size = s(20f, 16f))
            2 -> drawLine(ink, o(43f, 59f), o(57f, 59f), strokeWidth = 2.4f * u, cap = StrokeCap.Round)
            3 -> drawOval(ink, o(46f, 55f), s(8f, 10f))
            4 -> drawArc(ink, 20f, 100f, false, topLeft = o(44f, 52f), size = s(14f, 10f), style = st)
        }

        // accessories
        when (spec.acc) {
            1 -> {
                val g = Stroke(1.8f * u)
                drawCircle(ink, 6.5f * u, o(40f, 47f), style = g)
                drawCircle(ink, 6.5f * u, o(60f, 47f), style = g)
                drawLine(ink, o(46.5f, 47f), o(53.5f, 47f), strokeWidth = 1.8f * u)
            }
            2 -> {
                val g = Stroke(1.8f * u)
                drawRoundRect(ink, o(33f, 41f), s(14f, 11f), CornerRadius(3f * u, 3f * u), style = g)
                drawRoundRect(ink, o(53f, 41f), s(14f, 11f), CornerRadius(3f * u, 3f * u), style = g)
                drawLine(ink, o(47f, 46f), o(53f, 46f), strokeWidth = 1.8f * u)
            }
            3 -> drawArc(hair, 20f, 140f, false, topLeft = o(27f, 23f), size = s(46f, 46f), style = Stroke(7f * u))
            4 -> drawRoundRect(Color(0xFFEF4444), o(28f, 28f), s(44f, 6f), CornerRadius(3f * u, 3f * u))
        }
    }
}

@Composable
private fun OptionRow(title: String, count: Int, selected: Int, onSelect: (Int) -> Unit, item: @Composable (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Text(title, Modifier.padding(top = 14.dp, bottom = 6.dp), fontSize = 13.sp, color = cs.onSurfaceVariant)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (i in 0 until count) {
            val sel = i == selected
            Box(
                Modifier.size(54.dp).clip(CircleShape)
                    .border(if (sel) 3.dp else 1.dp, if (sel) cs.primary else cs.outline.copy(alpha = 0.4f), CircleShape)
                    .clickable { onSelect(i) }.padding(4.dp),
                contentAlignment = Alignment.Center
            ) { item(i) }
        }
    }
}

@Composable
fun AvatarEditor(spec: AvatarSpec, onChange: (AvatarSpec) -> Unit) {
    val cs = MaterialTheme.colorScheme
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(spec, 104.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Your avatar", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("Pick a template for each part", fontSize = 12.sp, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                FancyButton("Randomize", icon = R.drawable.ic_bolt, compact = true, filled = false) { onChange(AvatarSpec.random()) }
            }
        }
        OptionRow("Background", AvatarParts.bgs.size, spec.bg, { onChange(spec.copy(bg = it)) }) { i ->
            val (a, b) = AvatarParts.bgs[i]
            Box(Modifier.fillMaxSize().clip(CircleShape).background(Brush.linearGradient(listOf(a, b))))
        }
        OptionRow("Skin", AvatarParts.skins.size, spec.skin, { onChange(spec.copy(skin = it)) }) { i ->
            Box(Modifier.fillMaxSize().clip(CircleShape).background(AvatarParts.skins[i]))
        }
        OptionRow("Hair style", AvatarParts.HAIR, spec.hair, { onChange(spec.copy(hair = it)) }) { i -> Avatar(spec.copy(hair = i), 44.dp) }
        OptionRow("Hair color", AvatarParts.hairColors.size, spec.hc, { onChange(spec.copy(hc = it)) }) { i ->
            Box(Modifier.fillMaxSize().clip(CircleShape).background(AvatarParts.hairColors[i]))
        }
        OptionRow("Eyes", AvatarParts.EYES, spec.eyes, { onChange(spec.copy(eyes = it)) }) { i -> Avatar(spec.copy(eyes = i), 44.dp) }
        OptionRow("Mouth", AvatarParts.MOUTH, spec.mouth, { onChange(spec.copy(mouth = it)) }) { i -> Avatar(spec.copy(mouth = i), 44.dp) }
        OptionRow("Extras", AvatarParts.ACC, spec.acc, { onChange(spec.copy(acc = it)) }) { i -> Avatar(spec.copy(acc = i), 44.dp) }
        OptionRow("Shirt", AvatarParts.shirts.size, spec.shirt, { onChange(spec.copy(shirt = it)) }) { i ->
            Box(Modifier.fillMaxSize().clip(CircleShape).background(AvatarParts.shirts[i]))
        }
    }
}
