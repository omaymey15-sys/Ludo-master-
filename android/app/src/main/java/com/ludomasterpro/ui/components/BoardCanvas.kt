package com.ludomasterpro.ui.components

// ══════════════════════════════════════════════════════════════
//  BoardCanvas — Style Ludo Master (corrigé)
//  • Pions quille 3D (tête + corps évasé + reflets)
//  • Cellules blanches avec bordure grise
//  • Zones maison colorées + rectangle blanc intérieur
//  • Cases sûres : étoile ★
//  • Couloirs finaux colorés
//  • Centre : 4 triangles colorés
//  • Flèches directionnelles ∨ ∧ > <
// ══════════════════════════════════════════════════════════════

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
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

// ─── Structures de données (file-level, pas locales) ─────────
private data class TriDef(
    val color: Color,
    val p1:    Offset,
    val p2:    Offset
)
private data class ArrowDef(
    val row:   Int,
    val col:   Int,
    val char:  String,
    val color: Color
)

// ─── Composable BoardCanvas ───────────────────────────────────
@Composable
fun BoardCanvas(
    players:      List<Player>,
    playableIds:  List<String>,
    boardSizeDp:  Dp = 340.dp,
    onPieceClick: (String) -> Unit
) {
    val tm     = rememberTextMeasurer()
    val pieces = players.flatMap { it.pieces }

    val pulse by rememberInfiniteTransition(label = "pu").animateFloat(
        initialValue  = 0.87f,
        targetValue   = 1.16f,
        animationSpec = infiniteRepeatable(
            tween(480, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val glow by rememberInfiniteTransition(label = "gl").animateFloat(
        initialValue  = 0.25f,
        targetValue   = 0.85f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "glow"
    )

    Canvas(
        modifier = Modifier
            .size(boardSizeDp)
            .pointerInput(playableIds) {
                detectTapGestures { tap ->
                    val cell = size.width / 15f
                    for (p in pieces) {
                        if (p.id !in playableIds) continue
                        val (r, c) = p.cell()
                        val (dx, dy) = pawnOff(p, cell)
                        val sc = if (p.id in playableIds) pulse else 1f
                        val dist = (tap - Offset(
                            c * cell + cell / 2f + dx,
                            r * cell + cell / 2f + dy
                        )).getDistance()
                        if (dist <= cell * 0.42f * sc + 12f) {
                            onPieceClick(p.id)
                            return@detectTapGestures
                        }
                    }
                }
            }
    ) {
        val W    = size.width
        val cell = W / 15f
        fun cx(c: Int) = c * cell + cell / 2f
        fun cy(r: Int) = r * cell + cell / 2f

        // ══ 1. FOND BLANC ═════════════════════════════════════
        drawRect(color = Color.White, size = size)

        // ══ 2. ZONES MAISON (6×6, 4 coins) ═══════════════════
        // YELLOW=haut-gauche  BLUE=haut-droit
        // RED=bas-gauche      GREEN=bas-droit
        val homeCorners = listOf(
            PieceColor.YELLOW to Offset(0f,        0f),
            PieceColor.BLUE   to Offset(9f * cell, 0f),
            PieceColor.RED    to Offset(0f,         9f * cell),
            PieceColor.GREEN  to Offset(9f * cell,  9f * cell),
        )
        for ((col, org) in homeCorners) {
            val s = 6f * cell
            // Fond coloré arrondi
            drawRoundRect(
                color        = col.zone(),
                topLeft      = org,
                size         = Size(s, s),
                cornerRadius = CornerRadius(cell * 0.22f)
            )
            // Liseré sombre
            drawRoundRect(
                color        = col.dark().copy(alpha = 0.30f),
                topLeft      = org,
                size         = Size(s, s),
                cornerRadius = CornerRadius(cell * 0.22f),
                style        = Stroke(width = cell * 0.06f)
            )
            // Rectangle blanc intérieur
            val mg  = cell * 0.44f
            val iSz = s - mg * 2f
            drawRoundRect(
                color        = Color.White.copy(alpha = 0.93f),
                topLeft      = org + Offset(mg, mg),
                size         = Size(iSz, iSz),
                cornerRadius = CornerRadius(cell * 0.18f)
            )
        }

        // ══ 3. CASES DU CHEMIN PRINCIPAL ══════════════════════
        val pad = 0.6f
        for ((idx, rc) in Board.PATH.withIndex()) {
            val (r, c) = rc
            val x = c * cell
            val y = r * cell

            // Couleur de départ = case colorée
            var bg = Color.White
            for ((col, si) in Board.START_IDX) {
                if (idx == si) bg = col.base()
            }

            // Fond de la case
            drawRect(
                color   = bg,
                topLeft = Offset(x + pad, y + pad),
                size    = Size(cell - pad * 2f, cell - pad * 2f)
            )
            // Bordure grise
            drawRect(
                color   = Color(0xFFCCCCCC),
                topLeft = Offset(x + pad, y + pad),
                size    = Size(cell - pad * 2f, cell - pad * 2f),
                style   = Stroke(width = 0.8f)
            )
            // Étoile sur cases sûres
            if (idx in Board.SAFE) {
                val starCol = if (bg != Color.White) Color.White else Color(0xFFBBBBBB)
                val sm = tm.measure(
                    AnnotatedString("★"),
                    style = TextStyle(
                        fontSize   = (cell * 0.46f).sp,
                        color      = starCol,
                        fontWeight = FontWeight.Normal
                    )
                )
                drawText(
                    sm,
                    topLeft = Offset(
                        cx(c) - sm.size.width / 2f,
                        cy(r) - sm.size.height / 2f
                    )
                )
            }
        }

        // ══ 4. COULOIRS FINAUX ═════════════════════════════════
        for ((col, cells) in Board.CORRIDORS) {
            cells.forEachIndexed { ci, (r, c) ->
                val x     = c * cell
                val y     = r * cell
                val alpha = (0.65f + ci * 0.06f).coerceAtMost(1f)
                drawRect(
                    color   = col.base().copy(alpha = alpha),
                    topLeft = Offset(x + pad, y + pad),
                    size    = Size(cell - pad * 2f, cell - pad * 2f)
                )
                drawRect(
                    color   = Color(0xFFCCCCCC),
                    topLeft = Offset(x + pad, y + pad),
                    size    = Size(cell - pad * 2f, cell - pad * 2f),
                    style   = Stroke(width = 0.5f)
                )
            }
        }

        // ══ 5. CENTRE — 4 TRIANGLES ═══════════════════════════
        val midX = cx(Board.CENTER.second)
        val midY = cy(Board.CENTER.first)
        val mid  = Offset(midX, midY)
        val hs   = cell * 0.5f

        val triangles = listOf(
            TriDef(PieceColor.GREEN.base(),  Offset(mid.x - hs, mid.y - hs), Offset(mid.x + hs, mid.y - hs)),
            TriDef(PieceColor.BLUE.base(),   Offset(mid.x - hs, mid.y - hs), Offset(mid.x - hs, mid.y + hs)),
            TriDef(PieceColor.RED.base(),    Offset(mid.x - hs, mid.y + hs), Offset(mid.x + hs, mid.y + hs)),
            TriDef(PieceColor.YELLOW.base(), Offset(mid.x + hs, mid.y - hs), Offset(mid.x + hs, mid.y + hs)),
        )
        triangles.forEach { t ->
            drawPath(
                path  = Path().apply {
                    moveTo(mid.x, mid.y)
                    lineTo(t.p1.x, t.p1.y)
                    lineTo(t.p2.x, t.p2.y)
                    close()
                },
                color = t.color
            )
        }
        // Cercle blanc + étoile au centre
        drawCircle(color = Color.White, radius = cell * 0.38f, center = mid)
        val smC = tm.measure(
            AnnotatedString("★"),
            style = TextStyle(
                fontSize   = (cell * 0.32f).sp,
                color      = Color(0xFFDDDDDD),
                fontWeight = FontWeight.Bold
            )
        )
        drawText(smC, topLeft = Offset(mid.x - smC.size.width / 2f,
                                        mid.y - smC.size.height / 2f))

        // ══ 6. FLÈCHES DIRECTIONNELLES ═════════════════════════
        val arrows = listOf(
            ArrowDef(0,  7,  "∨", PieceColor.GREEN.base()),
            ArrowDef(14, 7,  "∧", PieceColor.RED.base()),
            ArrowDef(7,  0,  ">", PieceColor.YELLOW.base()),
            ArrowDef(7,  14, "<", PieceColor.BLUE.base()),
        )
        arrows.forEach { a ->
            val am = tm.measure(
                AnnotatedString(a.char),
                style = TextStyle(
                    fontSize   = (cell * 0.55f).sp,
                    color      = a.color,
                    fontWeight = FontWeight.ExtraBold
                )
            )
            drawText(am, topLeft = Offset(
                cx(a.col) - am.size.width / 2f,
                cy(a.row) - am.size.height / 2f
            ))
        }

        // ══ 7. PIONS — quille 3D ═══════════════════════════════
        for (piece in pieces) {
            val (r, c) = piece.cell()
            val (dx, dy) = pawnOff(piece, cell)
            val isPlay = piece.id in playableIds
            val scale  = if (isPlay) pulse else 1f
            drawPawn(
                center = Offset(cx(c) + dx, cy(r) + dy),
                height = cell * 0.78f * scale,
                base   = piece.color.base(),
                dark   = piece.color.dark(),
                light  = piece.color.light(),
                play   = isPlay,
                glow   = glow
            )
        }
    }
}

// ─── Offset du pion dans sa case ─────────────────────────────
private fun pawnOff(piece: Piece, cell: Float): Pair<Float, Float> {
    val f = if (piece.atBase) 0.21f else 0.17f
    val offsets = arrayOf(
        Pair(-f, -f), Pair(f, -f),
        Pair(-f,  f), Pair(f,  f)
    )
    val (a, b) = offsets[piece.index]
    return Pair(a * cell, b * cell)
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
        color   = dark.copy(alpha = 0.26f),
        topLeft = Offset(center.x - h * 0.22f, center.y + h * 0.37f),
        size    = Size(h * 0.44f, h * 0.09f)
    )

    // Halo doré si pion jouable
    if (play) {
        drawCircle(
            color  = Color(0xFFFFD700).copy(alpha = glow * 0.50f),
            radius = headR + h * 0.14f,
            center = Offset(center.x, headY)
        )
    }

    // Corps — quille/bowling pin
    val bTop = headY + headR * 0.62f
    val bBot = center.y + h * 0.43f
    val bW   = h * 0.24f

    val bodyPath = Path().apply {
        moveTo(center.x - bW * 0.42f, bTop)
        cubicTo(
            center.x - bW * 1.08f, bTop + (bBot - bTop) * 0.32f,
            center.x - bW * 0.82f, bBot - (bBot - bTop) * 0.12f,
            center.x, bBot
        )
        cubicTo(
            center.x + bW * 0.82f, bBot - (bBot - bTop) * 0.12f,
            center.x + bW * 1.08f, bTop + (bBot - bTop) * 0.32f,
            center.x + bW * 0.42f, bTop
        )
        close()
    }

    drawPath(
        path  = bodyPath,
        brush = Brush.linearGradient(
            colors = listOf(light.copy(alpha = 0.95f), base, dark),
            start  = Offset(center.x - bW, bTop),
            end    = Offset(center.x + bW, bBot)
        )
    )
    drawPath(path = bodyPath, color = dark, style = Stroke(width = 1.3f))

    // Tête — dégradé radial 3D
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
        style  = Stroke(width = 1.3f)
    )

    // Reflet tête (haut-gauche)
    drawOval(
        color   = Color.White.copy(alpha = 0.72f),
        topLeft = Offset(center.x - headR * 0.62f, headY - headR * 0.68f),
        size    = Size(headR * 0.76f, headR * 0.52f)
    )
    // Reflet corps
    drawOval(
        color   = Color.White.copy(alpha = 0.20f),
        topLeft = Offset(center.x - bW * 0.52f, bTop + (bBot - bTop) * 0.12f),
        size    = Size(bW * 0.88f, (bBot - bTop) * 0.38f)
    )

    // Anneau doré pion jouable
    if (play) {
        drawCircle(
            color  = Color(0xFFFFD700),
            radius = headR + 2.5f,
            center = Offset(center.x, headY),
            style  = Stroke(width = 2.5f)
        )
    }
}
