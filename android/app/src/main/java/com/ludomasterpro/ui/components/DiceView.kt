package com.ludomasterpro.ui.components

// ══════════════════════════════════════════════════════════════
//  DiceOverlay — Dé carré arrondi, couleur du joueur actif
//  Positionné dans le coin de la zone maison du joueur
// ══════════════════════════════════════════════════════════════

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

val DICE_FACES = listOf("⚀","⚁","⚂","⚃","⚄","⚅")

@Composable
fun DiceOverlay(
    value:       Int,
    canRoll:     Boolean,
    playerColor: Color,
    size:        Dp = 80.dp,
    onResult:    (Int) -> Unit
) {
    val haptic  = LocalHapticFeedback.current
    val scope   = rememberCoroutineScope()
    var display by remember { mutableIntStateOf(value.coerceIn(1, 6)) }
    var rolling by remember { mutableStateOf(false) }

    // Animation lancer
    val scaleAnim by animateFloatAsState(
        targetValue   = if (rolling) 1.12f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy),
        label = "ds"
    )
    val rotAnim by animateFloatAsState(
        targetValue   = if (rolling) 22f else 0f,
        animationSpec = tween(80),
        label = "dr"
    )

    // Pulsation légère quand c'est le tour du joueur
    val pulseAnim by rememberInfiniteTransition(label = "dp").animateFloat(
        initialValue  = 0.94f,
        targetValue   = 1.06f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "dpa"
    )

    // Garde le dernier résultat affiché
    LaunchedEffect(value) {
        if (value > 0) display = value
    }

    fun roll() {
        if (!canRoll || rolling) return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        rolling = true
        scope.launch {
            repeat(13) { i ->
                display = Random.nextInt(1, 7)
                delay(45 + i * 7L)
            }
            val result = Random.nextInt(1, 7)
            display = result
            rolling = false
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onResult(result)
        }
    }

    val combinedScale = if (canRoll && !rolling) pulseAnim * scaleAnim else scaleAnim

    Box(
        modifier = Modifier
            .size(size)
            .scale(combinedScale)
            .rotate(rotAnim)
            .clip(RoundedCornerShape(size * 0.22f))
            .background(
                brush = if (canRoll)
                    Brush.radialGradient(
                        listOf(
                            playerColor.copy(alpha = 0.95f),
                            playerColor,
                            darkenColor(playerColor, 0.72f)
                        )
                    )
                else
                    Brush.radialGradient(
                        listOf(Color(0xFF777777), Color(0xFF444444))
                    )
            )
            .border(
                width = (size.value * 0.05f).dp,
                brush = if (canRoll)
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.60f), playerColor.copy(alpha = 0.25f))
                    )
                else
                    Brush.linearGradient(
                        listOf(Color.Gray, Color.DarkGray)
                    ),
                shape = RoundedCornerShape(size * 0.22f)
            )
            .clickable(enabled = canRoll && !rolling) { roll() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Face du dé
            Text(
                text     = DICE_FACES[(display - 1).coerceIn(0, 5)],
                fontSize = (size.value * 0.48f).sp,
                color    = Color.White
            )
            // Valeur numérique sous le dé
            if (display > 0 && !rolling) {
                Text(
                    text       = "$display",
                    fontSize   = (size.value * 0.18f).sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White.copy(alpha = 0.80f)
                )
            }
        }
    }
}

private fun darkenColor(color: Color, factor: Float): Color =
    Color(color.red * factor, color.green * factor, color.blue * factor, color.alpha)
