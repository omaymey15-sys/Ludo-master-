package com.ludomasterpro.ui.components

// ══════════════════════════════════════════════════════════════
//  BoardCanvas — Image board.jpg comme fond + pions superposés
//
//  Le plateau est l'image drawable/board.jpg (740×740px, 15×15 cases).
//  On superpose les pions (quille 3D) par-dessus en calculant
//  les positions en fractions (0.0–1.0) de la taille du composable.
//
//  Correspondance zones / couleurs (image analysée) :
//   VERT   → haut-gauche
//   JAUNE  → haut-droit
//   ROUGE  → bas-gauche
//   BLEU   → bas-droit
// ══════════════════════════════════════════════════════════════

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import com.ludomasterpro.R
import com.ludomasterpro.engine.*

// ─── Extensions couleur ───────────────────────────────────────
fun PieceColor.base(): Color = when (this) {
    PieceColor.RED    -> Color(0xFFE53935)
    PieceColor.BLUE   -> Color(0xFF1E88E5)
    PieceColor.GREEN  -> Color(0xFF43A047)
    PieceColor.YELLOW -> Color(0xFFFDD835)
}
fun PieceColor.dark(): Color = when (this) {
    PieceColor.RED    -> Color(0xFFB71C1C)
    PieceColor.BLUE   -> Color(0xFF0D47A1)
    PieceColor.GREEN  -> Color(0xFF1B5E20)
    PieceColor.YELLOW -> Color(0xFFF57F17)
}
fun PieceColor.light(): Color = when (this) {
    PieceColor.RED    -> Color(0xFFFF8A80)
    PieceColor.BLUE   -> Color(0xFF82B1FF)
    PieceColor.GREEN  -> Color(0xFFB9F6CA)
    PieceColor.YELLOW -> Color(0xFFFFFF8D)
}
fun PieceColor.zone(): Color = when (this) {
    PieceColor.RED    -> Color(0xFFEF5350)
    PieceColor.BLUE   -> Color(0xFF42A5F5)
    PieceColor.GREEN  -> Color(0xFF66BB6A)
    PieceColor.YELLOW -> Color(0xFFFFEE58)
}

// ═══════════════════════════════════════════════════════════════
//  Positions des pions en fractions (x, y) de la taille du board
//  Calculées depuis l'image 740×740px (case = 49.33px)
// ═══════════════════════════════════════════════════════════════

// ── Cases de la zone de base (pions non sortis) ───────────────
// 2×2 dans le rectangle blanc de chaque coin
private val HOME_FRACTIONS = mapOf(
    PieceColor.GREEN  to arrayOf(
        Offset(0.1133f, 0.1133f), Offset(0.2867f, 0.1133f),
        Offset(0.1133f, 0.2867f), Offset(0.2867f, 0.2867f),
    ),
    PieceColor.YELLOW to arrayOf(
        Offset(0.7133f, 0.1133f), Offset(0.8867f, 0.1133f),
        Offset(0.7133f, 0.2867f), Offset(0.8867f, 0.2867f),
    ),
    PieceColor.RED    to arrayOf(
        Offset(0.1133f, 0.7133f), Offset(0.2867f, 0.7133f),
        Offset(0.1133f, 0.8867f), Offset(0.2867f, 0.8867f),
    ),
    PieceColor.BLUE   to arrayOf(
        Offset(0.7133f, 0.7133f), Offset(0.8867f, 0.7133f),
        Offset(0.7133f, 0.8867f), Offset(0.8867f, 0.8867f),
    ),
)

// ── Cases du chemin principal (52 cases) ─────────────────────
// Calculées depuis Board.PATH (row, col) → fraction = (col+0.5)/15, (row+0.5)/15
private fun pathFraction(row: Int, col: Int) =
    Offset((col + 0.5f) / 15f, (row + 0.5f) / 15f)

// ── Cases des couloirs finaux ──────────────────────────────────
private fun corrFraction(row: Int, col: Int) =
    Offset((col + 0.5f) / 15f, (row + 0.5f) / 15f)

// ── Centre ────────────────────────────────────────────────────
private val CENTER_FRACTION = Offset(0.5f, 0.5f)

// ─── Calcule la position d'un pion sur le board ──────────────
private fun piecePosition(piece: Piece, boardPx: Float): Offset {
    return when {
        piece.atBase -> {
            val fracs = HOME_FRACTIONS[piece.color]!![piece.index]
            Offset(fracs.x * boardPx, fracs.y * boardPx)
        }
        piece.arrived -> Offset(boardPx * CENTER_FRACTION.x, boardPx * CENTER_FRACTION.y)
        piece.pos >= Board.CORR_START[piece.color]!! -> {
            val ci = piece.pos - Board.CORR_START[piece.color]!!
            val (r, c) = Board.CORRIDORS[piece.color]!![ci]
            val f = corrFraction(r, c)
            Offset(f.x * boardPx, f.y * boardPx)
        }
        else -> {
            val (r, c) = Board.PATH[piece.pos % 52]
            val f = pathFraction(r, c)
            Offset(f.x * boardPx, f.y * boardPx)
        }
    }
}

// ─── Composable principal ─────────────────────────────────────
@Composable
fun BoardCanvas(
    players:      List<Player>,
    playableIds:  List<String>,
    boardSizeDp:  Dp = 340.dp,
    onPieceClick: (String) -> Unit
) {
    val pieces = players.flatMap { it.pieces }

    val pulse by rememberInfiniteTransition(label = "pu").animateFloat(
        initialValue  = 0.85f,
        targetValue   = 1.18f,
        animationSpec = infiniteRepeatable(
            tween(480, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val glow by rememberInfiniteTransition(label = "gl").animateFloat(
        initialValue  = 0.25f,
        targetValue   = 0.80f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "glow"
    )

    Box(modifier = Modifier.size(boardSizeDp)) {

        // ── 1. IMAGE DU PLATEAU ───────────────────────────────
        Image(
            painter     = painterResource(id = R.drawable.board),
            contentDescription = "Plateau Ludo",
            contentScale= ContentScale.FillBounds,
            modifier    = Modifier.size(boardSizeDp)
        )

        // ── 2. PIONS SUPERPOSÉS (Canvas transparent) ──────────
        Canvas(
            modifier = Modifier
                .size(boardSizeDp)
                .pointerInput(playableIds) {
                    detectTapGestures { tap ->
                        val boardPx = size.width.toFloat()
                        for (p in pieces) {
                            if (p.id !in playableIds) continue
                            val pos = piecePosition(p, boardPx)
                            val sc  = if (p.id in playableIds) pulse else 1f
                            val rad = boardPx / 15f * 0.36f * sc
                            if ((tap - pos).getDistance() <= rad + 14f) {
                                onPieceClick(p.id)
                                return@detectTapGestures
                            }
                        }
                    }
                }
        ) {
            val boardPx = size.width
            val pawnR   = boardPx / 15f * 0.30f  // rayon de base du pion

            for (piece in pieces) {
                val pos    = piecePosition(piece, boardPx)
                val isPlay = piece.id in playableIds
                val scale  = if (isPlay) pulse else 1f

                drawPawn(
                    center = pos,
                    height = pawnR * 2.4f * scale,
                    base   = piece.color.base(),
                    dark   = piece.color.dark(),
                    light  = piece.color.light(),
                    play   = isPlay,
                    glow   = glow
                )
            }
        }
    }
}

// ─── Dessin d'un pion quille 3D ──────────────────────────────
private fun DrawScope.drawPawn(
    center: Offset,
    height: Float,
    base:   Color,
    dark:   Color,
    light:  Color,
    play:   Boolean,
    glow:   Float
) {
    val h     = height
    val headR = h * 0.24f
    val headY = center.y - h * 0.24f

    // Ombre portée
    drawOval(
        color   = dark.copy(alpha = 0.30f),
        topLeft = Offset(center.x - h * 0.22f, center.y + h * 0.35f),
        size    = Size(h * 0.44f, h * 0.10f)
    )

    // Halo doré pion jouable
    if (play) {
        drawCircle(
            color  = Color(0xFFFFD700).copy(alpha = glow * 0.55f),
            radius = headR + h * 0.14f,
            center = Offset(center.x, headY)
        )
    }

    // Corps — quille
    val bTop = headY + headR * 0.60f
    val bBot = center.y + h * 0.44f
    val bW   = h * 0.24f

    val bodyPath = Path().apply {
        moveTo(center.x - bW * 0.42f, bTop)
        cubicTo(
            center.x - bW * 1.08f, bTop + (bBot - bTop) * 0.32f,
            center.x - bW * 0.80f, bBot - (bBot - bTop) * 0.12f,
            center.x, bBot
        )
        cubicTo(
            center.x + bW * 0.80f, bBot - (bBot - bTop) * 0.12f,
            center.x + bW * 1.08f, bTop + (bBot - bTop) * 0.32f,
            center.x + bW * 0.42f, bTop
        )
        close()
    }

    drawPath(
        bodyPath,
        brush = Brush.linearGradient(
            colors = listOf(light.copy(alpha = 0.95f), base, dark),
            start  = Offset(center.x - bW, bTop),
            end    = Offset(center.x + bW, bBot)
        )
    )
    drawPath(bodyPath, color = dark, style = Stroke(width = 1.4f))

    // Tête
    drawCircle(
        brush = Brush.radialGradient(
            colors  = listOf(light, base, dark),
            center  = Offset(center.x - headR * 0.22f, headY - headR * 0.22f),
            radius  = headR * 1.5f
        ),
        radius = headR,
        center = Offset(center.x, headY)
    )
    drawCircle(
        color  = dark,
        radius = headR,
        center = Offset(center.x, headY),
        style  = Stroke(width = 1.4f)
    )

    // Reflet tête
    drawOval(
        color   = Color.White.copy(alpha = 0.70f),
        topLeft = Offset(center.x - headR * 0.60f, headY - headR * 0.68f),
        size    = Size(headR * 0.75f, headR * 0.50f)
    )
    // Reflet corps
    drawOval(
        color   = Color.White.copy(alpha = 0.18f),
        topLeft = Offset(center.x - bW * 0.50f, bTop + (bBot - bTop) * 0.12f),
        size    = Size(bW * 0.85f, (bBot - bTop) * 0.36f)
    )

    // Anneau doré
    if (play) {
        drawCircle(
            color  = Color(0xFFFFD700),
            radius = headR + 2.8f,
            center = Offset(center.x, headY),
            style  = Stroke(width = 2.8f)
        )
    }
}
