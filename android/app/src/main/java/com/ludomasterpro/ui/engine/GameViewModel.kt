package com.ludomasterpro.engine

import android.app.Application
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

val Context.dataStore: DataStore<Preferences> by preferencesDataStore("ludo_prefs")

data class PlayerConfig(
    val name:    String      = "Joueur",
    val type:    PlayerType  = PlayerType.HUMAN,
    val aiLevel: AiLevel     = AiLevel.NORMAL,
    val avatar:  String      = "😊",
    val userId:  String      = "",
    val bet:     Double      = 0.0
)

class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val _state      = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val _configs    = MutableStateFlow(defaultConfigs())
    val configs: StateFlow<List<PlayerConfig>> = _configs.asStateFlow()

    private val _nbPlayers  = MutableStateFlow(2)
    val nbPlayers: StateFlow<Int> = _nbPlayers.asStateFlow()

    private val _bestScores = MutableStateFlow<Map<String,Int>>(emptyMap())
    val bestScores: StateFlow<Map<String,Int>> = _bestScores.asStateFlow()

    private val _wallet     = MutableStateFlow(0.0)
    val wallet: StateFlow<Double> = _wallet.asStateFlow()

    // ── Init ──────────────────────────────────────────────────
    init { loadPrefs() }

    private fun defaultConfigs() = listOf(
        PlayerConfig("Rouge",  PlayerType.HUMAN,  AiLevel.NORMAL, "😊"),
        PlayerConfig("Bleu",   PlayerType.AI,     AiLevel.NORMAL, "🤖"),
        PlayerConfig("Vert",   PlayerType.AI,     AiLevel.NORMAL, "🤖"),
        PlayerConfig("Jaune",  PlayerType.AI,     AiLevel.EASY,   "🤖"),
    )

    private fun loadPrefs() {
        viewModelScope.launch {
            getApplication<Application>().dataStore.data.collect { p ->
                _bestScores.value = p.asMap()
                    .filter { it.key.name.startsWith("score_") }
                    .mapKeys { it.key.name.removePrefix("score_") }
                    .mapValues { it.value as Int }
                _wallet.value = (p[doublePreferencesKey("wallet")] ?: 0.0)
            }
        }
    }

    private fun saveScore(name: String, turns: Int) {
        viewModelScope.launch {
            val cur = _bestScores.value[name]
            if (cur == null || turns < cur) {
                getApplication<Application>().dataStore.edit {
                    it[intPreferencesKey("score_$name")] = turns
                }
            }
        }
    }

    fun saveWallet(amount: Double) {
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit {
                it[doublePreferencesKey("wallet")] = amount
            }
            _wallet.value = amount
        }
    }

    // ── Config ────────────────────────────────────────────────
    fun setNbPlayers(n: Int) { _nbPlayers.value = n }
    fun updateConfig(i: Int, cfg: PlayerConfig) {
        _configs.value = _configs.value.toMutableList().also { it[i] = cfg }
    }

    // ── Démarrer partie ───────────────────────────────────────
    fun startGame(prizePool: Double = 0.0, competitionId: String = "") {
        val colors = PieceColor.entries
        val players = (0 until _nbPlayers.value).map { i ->
            val c = _configs.value[i]
            Player(id = colors[i].name, name = c.name.ifBlank { colors[i].label },
                color = colors[i], type = c.type, aiLevel = c.aiLevel,
                bet = c.bet, userId = c.userId)
        }
        _state.value = GameState(phase = GamePhase.PLAYING, players = players,
            prizePool = prizePool, competitionId = competitionId)
        viewModelScope.launch { delay(500); nextTurn() }
    }

    // ── Tour suivant ──────────────────────────────────────────
    private fun nextTurn() {
        val s = _state.value; val p = s.current ?: return
        _state.value = s.copy(
            message = if (p.type == PlayerType.HUMAN) "À vous de jouer !" else "",
            waitChoice = false, playableIds = emptyList(), dice = 0
        )
        if (p.type == PlayerType.AI) {
            viewModelScope.launch { delay(600); aiRollAndPlay() }
        }
    }

    // ── Lancer de dé (résultat fourni par l'UI) ───────────────
    fun onDiceRolled(dice: Int) {
        val s = _state.value; val p = s.current ?: return
        val candidates = LudoRules.playable(p, dice, s.players)
        val hist = HistoryEntry(s.totalTurns, p.name, p.color, dice, "🎲 $dice")

        if (candidates.isEmpty()) {
            _state.value = s.copy(dice = dice, message = "Aucun coup possible.",
                history = (s.history + hist).takeLast(80),
                waitChoice = false, playableIds = emptyList())
            viewModelScope.launch { delay(1300); advanceTurn() }
            return
        }

        if (p.type == PlayerType.AI) {
            val chosen = LudoRules.aiPick(p, candidates, s.players, dice)
            _state.value = s.copy(dice = dice,
                history    = (s.history + hist).takeLast(80),
                playableIds = listOf(chosen.id),
                animating  = true)
            return
        }

        if (candidates.size == 1) {
            _state.value = s.copy(dice = dice,
                history    = (s.history + hist).takeLast(80),
                playableIds = listOf(candidates[0].id),
                animating  = true)
            return
        }

        _state.value = s.copy(dice = dice,
            history    = (s.history + hist).takeLast(80),
            playableIds = candidates.map { it.id },
            waitChoice  = true, message = "Choisissez un pion ↑")
    }

    // ── Sélection pion (humain) ───────────────────────────────
    fun selectPiece(id: String) {
        val s = _state.value
        if (!s.waitChoice || id !in s.playableIds) return
        _state.value = s.copy(animating = true, waitChoice = false)
    }

    // ── Fin animation → appliquer ─────────────────────────────
    fun applyMove(pieceId: String, finalPos: Int) {
        val s   = _state.value
        val new = LudoRules.applyMove(s, pieceId, finalPos)
        _state.value = new

        if (new.phase == GamePhase.FINISHED) {
            new.ranking.firstOrNull()?.let { saveScore(it.name, new.totalTurns) }
            return
        }

        val cur = new.current ?: return
        viewModelScope.launch {
            delay(350)
            val replay = new.message.contains("Rejoue")
            if (replay) {
                if (cur.type == PlayerType.AI) { delay(700); aiRollAndPlay() }
                else _state.value = new.copy(message = new.message)
            } else {
                advanceTurn()
            }
        }
    }

    private fun advanceTurn() {
        val s = _state.value
        var t = s.currentTurn + 1
        repeat(s.players.size) {
            if (s.players[t % s.players.size].won) t++ else return@repeat
        }
        _state.value = s.copy(currentTurn = t, dice = 0, message = "")
        nextTurn()
    }

    private fun aiRollAndPlay() {
        val dice = LudoRules.rollDice()
        // Simule le résultat du dé IA → l'UI animera et appellera applyMove
        _state.value = _state.value.copy(dice = dice)
        onDiceRolled(dice)
    }

    fun goToMenu()   { _state.value = GameState() }
    fun replayGame() { startGame() }
}
