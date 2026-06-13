package com.ludomasterpro.ui.screens

// ══════════════════════════════════════════════════════════════
//  GameScreen — Dé positionné dans le coin du joueur actif
//  (comme Ludo Master)
//  YELLOW → haut-gauche | BLUE  → haut-droit
//  RED    → bas-gauche  | GREEN → bas-droit
// ══════════════════════════════════════════════════════════════

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.ludomasterpro.engine.*
import com.ludomasterpro.ui.components.*

fun PieceColor.toCompose(): Color = base()

// ══════════════════════════════════════════════════════════════
//  GAME SCREEN
// ══════════════════════════════════════════════════════════════
@Composable
fun GameScreen(
    state:       GameState,
    onDiceRoll:  (Int) -> Unit,
    onPiece:     (String) -> Unit,
    onApplyMove: (String, Int) -> Unit,
    onQuit:      () -> Unit
) {
    val cfg     = LocalConfiguration.current
    val player  = state.current
    val pColor  = player?.color?.toCompose() ?: Color(0xFFFFD700)
    val canRoll = player?.type == PlayerType.HUMAN
               && !state.waitChoice
               && !state.animating

    // Déclenchement animation IA
    LaunchedEffect(state.animating, state.playableIds) {
        if (!state.animating || state.playableIds.isEmpty()) return@LaunchedEffect
        val id    = state.playableIds.first()
        val piece = state.players.flatMap { it.pieces }.find { it.id == id }
                    ?: return@LaunchedEffect
        val path  = LudoRules.animPath(piece, state.dice)
        if (path.isEmpty()) { onApplyMove(id, piece.pos); return@LaunchedEffect }
        kotlinx.coroutines.delay(path.size * 130L + 80L)
        onApplyMove(id, path.last())
    }

    val boardDp  = minOf(cfg.screenWidthDp, cfg.screenHeightDp - 170).dp
    val diceSz   = boardDp * 0.235f

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF0F0F0))
    ) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement   = Arrangement.SpaceBetween,
            horizontalAlignment   = Alignment.CenterHorizontally
        ) {

            // ── BARRE HAUTE ──────────────────────────────────
            GameTopBar(player, state.players, onQuit)

            // ── PLATEAU + DÉ SUPERPOSÉ ────────────────────────
            Box(
                Modifier
                    .size(boardDp)
                    .align(Alignment.CenterHorizontally)
            ) {
                // Plateau
                BoardCanvas(
                    players      = state.players,
                    playableIds  = when {
                        state.waitChoice                                    -> state.playableIds
                        state.playableIds.size == 1 && !state.animating    -> state.playableIds
                        else                                               -> emptyList()
                    },
                    boardSizeDp  = boardDp,
                    onPieceClick = onPiece
                )

                // Message flottant (capture, arrivée…)
                AnimatedVisibility(
                    visible  = state.message.isNotEmpty(),
                    modifier = Modifier.align(Alignment.Center),
                    enter    = fadeIn(tween(200)) + scaleIn(),
                    exit     = fadeOut(tween(300))
                ) {
                    Surface(
                        shape  = RoundedCornerShape(24.dp),
                        color  = Color.Black.copy(alpha = 0.72f),
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(
                            state.message,
                            modifier   = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                            color      = Color.White,
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign  = TextAlign.Center
                        )
                    }
                }

                // ── DÉ dans le coin du joueur actif ──────────
                // YELLOW=haut-gauche  BLUE=haut-droit
                // RED=bas-gauche      GREEN=bas-droit
                if (player != null) {
                    val diceAlign: Alignment = when (player.color) {
                        PieceColor.YELLOW -> Alignment.TopStart
                        PieceColor.BLUE   -> Alignment.TopEnd
                        PieceColor.RED    -> Alignment.BottomStart
                        PieceColor.GREEN  -> Alignment.BottomEnd
                        else              -> Alignment.TopStart
                    }
                    Box(
                        modifier         = Modifier
                            .align(diceAlign)
                            .padding(boardDp * 0.024f),
                        contentAlignment = Alignment.Center
                    ) {
                        DiceOverlay(
                            value       = state.dice,
                            canRoll     = canRoll,
                            playerColor = pColor,
                            size        = diceSz,
                            onResult    = onDiceRoll
                        )
                    }
                }
            }

            // ── BARRE BAS ────────────────────────────────────
            GameBottomBar(title = "Solo\nClassique", onQuit = onQuit)
        }
    }
}

// ─── Barre du haut ────────────────────────────────────────────
@Composable
fun GameTopBar(
    current:    Player?,
    allPlayers: List<Player>,
    onQuit:     () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        // Bouton retour
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF4CAF50))
                .clickable { onQuit() },
            Alignment.Center
        ) { Text("◀", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp) }

        // Mini-scores de tous les joueurs
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            allPlayers.forEach { pl ->
                val isActive = pl.id == current?.id
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isActive) pl.color.zone()
                            else Color(0xFFEEEEEE)
                        )
                        .border(
                            width  = if (isActive) 2.dp else 0.dp,
                            color  = pl.color.dark(),
                            shape  = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(pl.color.emoji, fontSize = 13.sp)
                        repeat(4) { i ->
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .background(
                                        if (i < pl.doneCount) pl.color.base()
                                        else Color(0xFFCCCCCC),
                                        CircleShape
                                    )
                            )
                        }
                    }
                }
            }
        }

        // Nom + type du joueur actif
        if (current != null) {
            Surface(
                shape  = RoundedCornerShape(10.dp),
                color  = current.color.zone(),
                border = BorderStroke(1.5.dp, current.color.dark())
            ) {
                Text(
                    text     = "${current.color.emoji} ${current.name.take(7)}",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color    = if (current.color == PieceColor.YELLOW)
                                   Color(0xFF5D4037) else Color.White
                )
            }
        }
    }
}

// ─── Barre de navigation verte (bas) ─────────────────────────
@Composable
fun GameBottomBar(title: String, onQuit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        NavGreenBtn("◀", onQuit)

        Text(
            title,
            fontSize   = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color      = Color(0xFF888888),
            textAlign  = TextAlign.Center,
            lineHeight = 18.sp
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NavGreenBtn("🏃") {}
            NavGreenBtn("⚙️") {}
        }
    }
}

@Composable
fun NavGreenBtn(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(50.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF66BB6A), Color(0xFF388E3C))
                )
            )
            .clickable { onClick() },
        Alignment.Center
    ) {
        Text(label, fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

// ══════════════════════════════════════════════════════════════
//  PODIUM SCREEN
// ══════════════════════════════════════════════════════════════
@Composable
fun PodiumScreen(
    players:    List<Player>,
    totalTurns: Int,
    bestScores: Map<String, Int>,
    prizePool:  Double,
    onReplay:   () -> Unit,
    onMenu:     () -> Unit
) {
    val sorted  = players.sortedBy { it.rank ?: 99 }
    val winner  = sorted.firstOrNull()
    val isRec   = winner != null &&
                  (bestScores[winner.name]?.let { totalTurns <= it } ?: true)
    val hasPrize = prizePool > 0.0

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            Text("🏆", fontSize = 60.sp)
            Text(
                "FIN DE PARTIE",
                fontSize   = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color      = Color(0xFF333333)
            )
            Text("$totalTurns tours joués", fontSize = 13.sp, color = Color(0xFF888888))

            if (isRec) {
                Surface(
                    shape  = RoundedCornerShape(20.dp),
                    color  = Color(0xFFFFF9C4),
                    border = BorderStroke(1.dp, Color(0xFFFFD700))
                ) {
                    Text(
                        text       = "🏅 NOUVEAU RECORD !",
                        modifier   = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        fontWeight = FontWeight.ExtraBold,
                        color      = Color(0xFFFF8F00)
                    )
                }
            }

            if (hasPrize) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(14.dp),
                    color    = Color(0xFFFFFDE7),
                    border   = BorderStroke(1.5.dp, Color(0xFFFFD700))
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "💰 PRIZE POOL",
                            fontSize     = 11.sp,
                            color        = Color(0xFF888888),
                            letterSpacing= 1.sp
                        )
                        Text(
                            formatCDF(prizePool),
                            fontSize   = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color      = Color(0xFFFF8F00)
                        )
                    }
                }
            }

            // ── Podium animé ──────────────────────────────────
            val podOrder = listOfNotNull(
                sorted.getOrNull(1), sorted.getOrNull(0), sorted.getOrNull(2)
            )
            val podHeights = listOf(95.dp, 135.dp, 65.dp)
            val medals     = listOf("🥈", "🥇", "🥉")
            val barColors  = listOf(
                Color(0xFFC0C0C0), Color(0xFFFFD700), Color(0xFFCD7F32)
            )

            Row(
                Modifier.fillMaxWidth().height(210.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment     = Alignment.Bottom
            ) {
                podOrder.forEachIndexed { vi, pl ->
                    val h by animateDpAsState(
                        podHeights[vi],
                        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
                        label = "h$vi"
                    )
                    Column(
                        Modifier.width(88.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(pl.color.emoji, fontSize = 22.sp)
                        Text(
                            pl.name.take(8),
                            fontWeight = FontWeight.Bold,
                            fontSize   = 11.sp,
                            color      = pl.color.toCompose(),
                            maxLines   = 1
                        )
                        Text(medals[vi], fontSize = 20.sp)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(h)
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(barColors[vi].copy(alpha = 0.85f)),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Text(
                                "#${pl.rank}",
                                Modifier.padding(top = 6.dp),
                                fontWeight = FontWeight.Bold,
                                fontSize   = 14.sp,
                                color      = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            // ── Statistiques ──────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(14.dp),
                color    = Color.White,
                border   = BorderStroke(1.dp, Color(0xFFEEEEEE))
            ) {
                Column(
                    Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "📊 STATISTIQUES",
                        fontSize     = 11.sp,
                        color        = Color(0xFF888888),
                        letterSpacing= 1.sp,
                        fontWeight   = FontWeight.Bold
                    )
                    Row(Modifier.fillMaxWidth()) {
                        listOf("Joueur", "✅", "✕", "☠").forEach { header ->
                            Text(
                                header,
                                Modifier.weight(1f),
                                fontSize  = 10.sp,
                                color     = Color(0xFFAAAAAA),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    HorizontalDivider(color = Color(0xFFEEEEEE))
                    sorted.forEach { pl ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${pl.color.emoji} ${pl.name.take(7)}",
                                Modifier.weight(1f),
                                fontSize = 10.sp,
                                color    = pl.color.toCompose()
                            )
                            listOf(
                                "${pl.doneCount}/4",
                                "${pl.captures}",
                                "${pl.suffered}"
                            ).forEach { v ->
                                Text(
                                    v,
                                    Modifier.weight(1f),
                                    fontSize  = 10.sp,
                                    color     = Color(0xFF333333),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // ── Boutons ───────────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onReplay,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50),
                        contentColor   = Color.White
                    )
                ) {
                    Text("🔄  Rejouer", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onMenu,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape    = RoundedCornerShape(12.dp),
                    border   = BorderStroke(1.dp, Color(0xFFCCCCCC)),
                    colors   = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF333333)
                    )
                ) {
                    Text("🏠  Menu")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
