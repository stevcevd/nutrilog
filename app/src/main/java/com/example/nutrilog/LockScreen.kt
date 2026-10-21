package com.example.nutrilog

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlin.math.roundToInt

fun biometricAvailable(ctx: Context): Boolean =
    BiometricManager.from(ctx).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

fun promptBiometric(activity: FragmentActivity, onSuccess: () -> Unit) {
    val prompt = BiometricPrompt(
        activity, ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onSuccess() }
        }
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock NutriLog")
        .setNegativeButtonText("Use PIN")
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        .build()
    prompt.authenticate(info)
}

/** Numeric keypad with animated dots. Calls [onComplete] when [length] digits were entered. */
@Composable
fun PinPad(
    length: Int,
    title: String,
    subtitle: String? = null,
    errorTick: Int = 0,
    resetKey: Any = Unit,
    bottomLeft: @Composable () -> Unit = {},
    onComplete: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var digits by remember(resetKey) { mutableStateOf("") }
    val shake = remember { Animatable(0f) }

    LaunchedEffect(errorTick) {
        if (errorTick > 0) {
            repeat(3) {
                shake.animateTo(14f, tween(45))
                shake.animateTo(-14f, tween(45))
            }
            shake.animateTo(0f, tween(45))
        }
    }
    LaunchedEffect(digits) {
        if (digits.length == length) {
            val d = digits
            onComplete(d)
            digits = ""
        }
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, fontSize = 13.sp, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(24.dp))
        Row(
            Modifier.offset { IntOffset(shake.value.roundToInt(), 0) },
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            for (i in 0 until length) {
                val filled = i < digits.length
                val sc by animateFloatAsState(if (filled) 1.15f else 1f, label = "dot")
                Box(
                    Modifier.size(16.dp).scale(sc).clip(CircleShape)
                        .background(if (filled) cs.primary else cs.outline.copy(alpha = 0.35f))
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "<"))
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                row.forEach { key ->
                    Box(Modifier.size(74.dp), contentAlignment = Alignment.Center) {
                        when (key) {
                            "" -> bottomLeft()
                            "<" -> Box(
                                Modifier.size(74.dp).clip(CircleShape).clickable { if (digits.isNotEmpty()) digits = digits.dropLast(1) },
                                contentAlignment = Alignment.Center
                            ) { NIcon(R.drawable.ic_backspace, cs.onSurfaceVariant, 26.dp) }
                            else -> Box(
                                Modifier.size(74.dp).clip(CircleShape).background(cs.surfaceVariant)
                                    .clickable { if (digits.length < length) digits += key },
                                contentAlignment = Alignment.Center
                            ) { Text(key, fontSize = 26.sp, fontWeight = FontWeight.Medium) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LockScreen(settings: Settings, onUnlocked: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val activity = ctx as? FragmentActivity
    var errorTick by remember { mutableIntStateOf(0) }
    val canBio = settings.lockMode == 2 && biometricAvailable(ctx)

    // Swallow back presses so the lock can't be bypassed.
    BackHandler(true) { }

    LaunchedEffect(Unit) {
        if (canBio && activity != null) {
            AppLock.skipNext = true
            promptBiometric(activity, onUnlocked)
        }
    }

    Box(Modifier.fillMaxSize().background(cs.background).pointerInput(Unit) { detectTapGestures { } }, contentAlignment = Alignment.Center) {
        Column(
            Modifier.statusBarsPadding().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            NIcon(R.drawable.ic_lock, cs.primary, 36.dp)
            Spacer(Modifier.height(16.dp))
            PinPad(
                length = settings.pinLength,
                title = "NutriLog is locked",
                subtitle = "Enter your PIN",
                errorTick = errorTick,
                bottomLeft = {
                    if (canBio && activity != null) {
                        Box(
                            Modifier.size(74.dp).clip(CircleShape).clickable {
                                AppLock.skipNext = true
                                promptBiometric(activity, onUnlocked)
                            },
                            contentAlignment = Alignment.Center
                        ) { NIcon(R.drawable.ic_fingerprint, cs.primary, 28.dp) }
                    }
                }
            ) { pin ->
                if (settings.checkPin(pin)) onUnlocked() else errorTick++
            }
        }
    }
}
