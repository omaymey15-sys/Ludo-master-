package com.ludomasterpro.engine

import kotlin.random.Random

// ══════════════════════════════════════════════════════════════
//  LUDO MASTER PRO — Moteur de jeu complet et corrigé
//  Règles officielles :
//  • 6 = sortir un pion ET rejouer
//  • Atterrissage exact sur l'arrivée
//  • Cases sûres (rosettes) : pas de capture
//  • Capture = renvoi en base + rejouer
//  • Blocage : 2 pions même couleur = forteresse imprenable
// ══════════════════════════════════════════════════════════════

enum class PieceColor(val hex: String, val hexDark: String, val label: String) {
    RED   ("#E74C3C", "#C0392B", "Rouge"),
    BLUE  ("#2980B9", "#1A5276", "Bleu"),
    GREEN ("#27AE60", "#1E8449", "Vert"),
    YELLOW("#F39C12", "#D68910", "Jaune");

    val emoji get() = when(this) {
        RED->"🔴"; BLUE->"🔵"; GREEN->"🟢"; YELLOW->"🟡"
    }
}

enum class PlayerType { HUMAN, AI }
enum class AiLevel    { EASY, NORMAL, EXPERT }
enum class GamePhase  { MENU, PLAYING, FINISHED }

// ─── Plateau 15×15 ────────────────────────────────────────────
object Board {
    // 52 cases du chemin principal
    val PATH: List<Pair<Int,Int>> = listOf(
        14 to 6, 13 to 6, 12 to 6, 11 to 6, 10 to 6, 9 to 6,
        8  to 5, 8  to 4, 8  to 3, 8  to 2, 8  to 1, 8  to 0,
        7  to 0, 6  to 0,
        6  to 1, 6  to 2, 6  to 3, 6  to 4, 6  to 5,
        5  to 6, 4  to 6, 3  to 6, 2  to 6, 1  to 6, 0  to 6,
        0  to 7, 0  to 8,
        1  to 8, 2  to 8, 3  to 8, 4  to 8, 5  to 8,
        6  to 9, 6  to 10,6  to 11,6  to 12,6  to 13,6  to 14,
        7  to 14,8  to 14,
        8  to 13,8  to 12,8  to 11,8  to 10,8  to 9,
        9  to 8, 10 to 8, 11 to 8, 12 to 8, 13 to 8, 14 to 8,
        14 to 7  // 51 — boucle vers 0
    )

    val CORRIDORS = mapOf(
        PieceColor.RED    to listOf(13 to 7,12 to 7,11 to 7,10 to 7, 9 to 7, 8 to 7),
        PieceColor.BLUE   to listOf( 7 to 1, 7 to 2, 7 to 3, 7 to 4, 7 to 5, 7 to 6),
        PieceColor.GREEN  to listOf( 1 to 7, 2 to 7, 3 to 7, 4 to 7, 5 to 7, 6 to 7),
        PieceColor.YELLOW to listOf( 7 to 13,7 to 12,7 to 11,7 to 10, 7 to 9, 7 to 8),
    )
    val CENTER = 7 to 7

    val HOME_CELLS = mapOf(
        PieceColor.RED    to listOf(11 to 1,11 to 2,12 to 1,12 to 2),
        PieceColor.BLUE   to listOf( 1 to 11, 2 to 11, 1 to 12, 2 to 12),
        PieceColor.GREEN  to listOf(11 to 11,11 to 12,12 to 11,12 to 12),
        PieceColor.YELLOW to listOf( 1 to 1,  1 to 2,  2 to 1,  2 to 2),
    )

    // Case de départ sur la piste (index PATH)
    val START_IDX = mapOf(
        PieceColor.RED to 0, PieceColor.BLUE to 13,
        PieceColor.GREEN to 26, PieceColor.YELLOW to 39
    )
    // Dernière case avant couloir
    val ENTRY_IDX = mapOf(
        PieceColor.RED to 50, PieceColor.BLUE to 11,
        PieceColor.GREEN to 24, PieceColor.YELLOW to 37
    )
    // Index logique début couloir
    val CORR_START = mapOf(
        PieceColor.RED to 52, PieceColor.BLUE to 58,
        PieceColor.GREEN to 64, PieceColor.YELLOW to 70
    )
    // Index logique fin couloir (= centre - 1)
    val CORR_END = mapOf(
        PieceColor.RED to 57, PieceColor.BLUE to 63,
        PieceColor.GREEN to 69, PieceColor.YELLOW to 75
    )

    // Cases protégées
    val SAFE = setOf(0, 8, 13, 21, 26, 34, 39, 47)

    const val BASE     = -1
    const val ARRIVED  = 100
}

// ─── Data classes ─────────────────────────────────────────────
data class Piece(
    val id:    String,
    val color: PieceColor,
    val index: Int,
    val pos:   Int = Board.BASE
) {
    val atBase    get() = pos == Board.BASE
    val arrived   get() = pos == Board.ARRIVED

    fun cell(): Pair<Int,Int> = with(Board) {
        when {
            atBase   -> HOME_CELLS[color]!![index]
            arrived  -> CENTER
            pos >= CORR_START[color]!! -> CORRIDORS[color]!![pos - CORR_START[color]!!]
            else     -> PATH[pos % 52]
        }
    }
}

data class Player(
    val id:       String,
    val name:     String,
    val color:    PieceColor,
    val type:     PlayerType,
    val aiLevel:  AiLevel      = AiLevel.NORMAL,
    val pieces:   List<Piece>  = List(4) { i -> Piece("${color.name}_$i", color, i) },
    val score:    Int          = 0,
    val captures: Int          = 0,
    val suffered: Int          = 0,
    val rank:     Int?         = null,
    // Champs financiers
    val bet:      Double       = 0.0,
    val userId:   String       = ""
) {
    val won         get() = pieces.all { it.arrived }
    val doneCount   get() = pieces.count { it.arrived }
    val progressPct get() = (doneCount * 25)
}

data class HistoryEntry(
    val turn:   Int,
    val name:   String,
    val color:  PieceColor,
    val dice:   Int,
    val action: String
)

data class GameState(
    val phase:         GamePhase         = GamePhase.MENU,
    val players:       List<Player>      = emptyList(),
    val currentTurn:   Int               = 0,
    val dice:          Int               = 0,
    val totalTurns:    Int               = 0,
    val history:       List<HistoryEntry> = emptyList(),
    val ranking:       List<Player>      = emptyList(),
    val playableIds:   List<String>      = emptyList(),
    val waitChoice:    Boolean           = false,
    val animating:     Boolean           = false,
    val message:       String            = "",
    // Financier
    val prizePool:     Double            = 0.0,
    val competitionId: String            = ""
) {
    val current get() = players.getOrNull(currentTurn % players.size.coerceAtLeast(1))
}

// ─── Règles ───────────────────────────────────────────────────
object LudoRules {

    fun rollDice() = Random.nextInt(1, 7)

    /** Nouvelle position logique après un jet de dé. null = impossible. */
    fun newPos(piece: Piece, dice: Int): Int? = with(Board) {
        if (piece.atBase) return if (dice == 6) START_IDX[piece.color]!! else null

        val c   = piece.color
        val cs  = CORR_START[c]!!
        val ce  = CORR_END[c]!!
        val ent = ENTRY_IDX[c]!!
        val pos = piece.pos

        if (pos >= cs) {
            // Dans le couloir
            val np = pos + dice
            return when {
                np <  ce  -> np
                np == ce  -> np          // avant-dernière case
                np == ce + 1 -> ARRIVED  // entrée exacte centre
                else      -> null        // dépasse
            }
        }

        // Sur la piste principale
        val dist = ((ent - pos) + 52) % 52
        return if (dice > dist) {
            // Entre dans le couloir
            val rem = dice - dist - 1
            val np  = cs + rem
            when {
                np <= ce      -> np
                np == ce + 1  -> ARRIVED
                else          -> null
            }
        } else {
            (pos + dice) % 52
        }
    }

    /** Chemin case par case pour l'animation. */
    fun animPath(piece: Piece, dice: Int): List<Int> = with(Board) {
        if (piece.atBase) return listOf(START_IDX[piece.color]!!)

        val c   = piece.color
        val cs  = CORR_START[c]!!
        val ce  = CORR_END[c]!!
        val ent = ENTRY_IDX[c]!!
        val pos = piece.pos

        buildList {
            if (pos >= cs) {
                for (i in 1..dice) {
                    val np = pos + i
                    if (np <= ce) add(np) else { add(ARRIVED); break }
                }
            } else {
                val dist = ((ent - pos) + 52) % 52
                if (dice > dist) {
                    for (i in 1..dist)         add((pos + i) % 52)
                    for (j in 0..(dice-dist-1)) {
                        val np = cs + j
                        if (np <= ce) add(np) else { add(ARRIVED); break }
                    }
                } else {
                    for (i in 1..dice) add((pos + i) % 52)
                }
            }
        }
    }

    /** Vérifie si une case est une forteresse (2+ pions adverses) — imprenable. */
    fun isFortress(pos: Int, owner: PieceColor, players: List<Player>): Boolean {
        if (pos < 0 || pos == Board.ARRIVED || pos in Board.SAFE) return false
        return players.any { pl ->
            pl.color != owner && pl.pieces.count { it.pos == pos } >= 2
        }
    }

    /** Pions qu'un joueur peut bouger avec ce dé (bloqué par les forteresses adverses). */
    fun playable(player: Player, dice: Int, all: List<Player> = emptyList()): List<Piece> =
        player.pieces.filter { p ->
            if (p.arrived) return@filter false
            val np = if (p.atBase) { if (dice == 6) Board.START_IDX[p.color]!! else null } else newPos(p, dice)
            np != null && !isFortress(np, player.color, all)
        }

    /**
     * Applique le mouvement : retourne le GameState mis à jour.
     * Gère : capture, forteresse, victoire, rejouer sur 6/capture.
     */
    fun applyMove(state: GameState, pieceId: String, finalPos: Int): GameState {
        val pi = state.currentTurn % state.players.size
        var captureHappened = false
        var capturedColor: PieceColor? = null

        val players = state.players.mapIndexed { idx, pl ->
            if (idx == pi) {
                val pieces = pl.pieces.map { p -> if (p.id == pieceId) p.copy(pos = finalPos) else p }
                pl.copy(pieces = pieces, score = pieces.count { it.arrived })
            } else {
                // Capture éventuelle
                val isSafe = finalPos < 0
                    || finalPos == Board.ARRIVED
                    || finalPos in Board.SAFE
                    || finalPos >= Board.CORR_START[pl.color]!!
                if (isSafe) return@mapIndexed pl

                val pieces = pl.pieces.map { p ->
                    if (p.pos == finalPos && pl.pieces.count { it.pos == finalPos } == 1) {
                        captureHappened = true
                        capturedColor = pl.color
                        p.copy(pos = Board.BASE)
                    } else p
                }
                if (captureHappened && capturedColor == pl.color)
                    pl.copy(pieces = pieces, suffered = pl.suffered + 1)
                else pl
            }
        }.toMutableList()

        if (captureHappened) {
            val ap = players[pi]
            players[pi] = ap.copy(captures = ap.captures + 1)
        }

        // Victoire ?
        var ranking = state.ranking.toMutableList()
        var phase   = state.phase
        val updated = players[pi]
        if (updated.won && updated.rank == null) {
            players[pi] = updated.copy(rank = ranking.size + 1)
            ranking.add(players[pi])
            if (ranking.size >= state.players.size) phase = GamePhase.FINISHED
        }

        // Rejouer si : dé=6 OU capture
        val replay = (state.dice == 6 || captureHappened) && phase != GamePhase.FINISHED
        val nextTurn = if (replay) state.currentTurn else state.currentTurn + 1

        val msgs = buildList {
            if (captureHappened) add("💥 Capture ${capturedColor?.emoji} !")
            if (finalPos == Board.ARRIVED) add("🏆 Pion arrivé !")
            if (replay && phase != GamePhase.FINISHED)
                add(if (state.dice == 6) "🎲 6 → Rejoue !" else "💥 Capture → Rejoue !")
        }

        return state.copy(
            players    = players,
            currentTurn= nextTurn,
            ranking    = ranking,
            phase      = phase,
            animating  = false,
            waitChoice = false,
            playableIds= emptyList(),
            totalTurns = if (replay) state.totalTurns else state.totalTurns + 1,
            message    = msgs.joinToString(" "),
            history    = (state.history + HistoryEntry(
                state.totalTurns, updated.name, updated.color,
                state.dice, msgs.firstOrNull() ?: "→ ${finalPos}"
            )).takeLast(80)
        )
    }

    // ─── IA ─────────────────────────────────────────────────
    fun aiPick(player: Player, candidates: List<Piece>, all: List<Player>, dice: Int): Piece {
        if (player.aiLevel == AiLevel.EASY || candidates.size == 1) return candidates.random()

        return candidates.maxByOrNull { p ->
            var s = 0
            if (p.atBase) { s = 12; return@maxByOrNull s }
            val np = newPos(p, dice) ?: return@maxByOrNull -9999
            if (np == Board.ARRIVED) return@maxByOrNull 10000

            s += if (np >= Board.CORR_START[p.color]!!) np + 300 else np

            for (other in all) {
                if (other.color == player.color) continue
                for (op in other.pieces) {
                    // Bonus capture
                    if (op.pos == np && np !in Board.SAFE) s += 600
                    // Expert : évite d'être à portée
                    if (player.aiLevel == AiLevel.EXPERT && !op.atBase && !op.arrived) {
                        val gap = ((np - op.pos) + 52) % 52
                        if (gap in 1..6 && np !in Board.SAFE) s -= 80
                    }
                }
            }
            s
        } ?: candidates.first()
    }
}
