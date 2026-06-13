package com.ludomasterpro.engine

// ══════════════════════════════════════════════════════════════
//  AuthViewModel.kt — Gestion Auth + Wallet + Compétitions
//  Branché sur ApiService → Backend Render
// ══════════════════════════════════════════════════════════════

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ludomasterpro.Config
import com.ludomasterpro.network.ApiService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject

// ── États UI ─────────────────────────────────────────────────
data class AuthState(
    val isLoggedIn:  Boolean = false,
    val userId:      String  = "",
    val username:    String  = "",
    val role:        String  = "player",
    val isLoading:   Boolean = false,
    val error:       String  = ""
)

data class WalletState(
    val balance:      Double       = 0.0,
    val transactions: List<TxItem> = emptyList(),
    val isLoading:    Boolean      = false,
    val message:      String       = ""
)

data class TxItem(
    val type:   String,
    val amount: Double,
    val status: String,
    val desc:   String,
    val date:   String
)

data class CompItem(
    val id:           String,
    val title:        String,
    val entryFee:     Double,
    val prizePool:    Double,
    val players:      Int,
    val maxPlayers:   Int,
    val status:       String,
    val distribution: List<Int>
)

class AuthViewModel(app: Application) : AndroidViewModel(app) {

    private val _auth   = MutableStateFlow(AuthState())
    val auth: StateFlow<AuthState> = _auth.asStateFlow()

    private val _wallet = MutableStateFlow(WalletState())
    val wallet: StateFlow<WalletState> = _wallet.asStateFlow()

    private val _comps  = MutableStateFlow<List<CompItem>>(emptyList())
    val competitions: StateFlow<List<CompItem>> = _comps.asStateFlow()

    init { restoreSession() }

    // ── Restaurer session depuis DataStore ───────────────────
    private fun restoreSession() {
        viewModelScope.launch {
            getApplication<Application>().dataStore.data.collect { prefs ->
                val token    = prefs[stringPreferencesKey(Config.DS_TOKEN)]    ?: ""
                val userId   = prefs[stringPreferencesKey(Config.DS_USER_ID)]  ?: ""
                val balance  = prefs[doublePreferencesKey(Config.DS_WALLET)]   ?: 0.0
                if (token.isNotEmpty()) {
                    ApiService.setToken(token)
                    _auth.value  = _auth.value.copy(isLoggedIn = true, userId = userId)
                    _wallet.value = _wallet.value.copy(balance = balance)
                    refreshProfile()
                }
            }
        }
    }

    private fun saveSession(token: String, userId: String, balance: Double) {
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit {
                it[stringPreferencesKey(Config.DS_TOKEN)]   = token
                it[stringPreferencesKey(Config.DS_USER_ID)] = userId
                it[doublePreferencesKey(Config.DS_WALLET)]  = balance
            }
        }
    }

    private fun clearSession() {
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { it.clear() }
            ApiService.clearToken()
        }
    }

    // ── Connexion ────────────────────────────────────────────
    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _auth.value = _auth.value.copy(error = "Email et mot de passe requis")
            return
        }
        viewModelScope.launch {
            _auth.value = _auth.value.copy(isLoading = true, error = "")
            val res = ApiService.login(email.trim(), password)
            if (res.success && res.data != null) {
                _auth.value = AuthState(
                    isLoggedIn = true,
                    userId     = res.data.username,
                    username   = res.data.username,
                    role       = res.data.role
                )
                _wallet.value = WalletState(balance = res.data.balance)
                saveSession(res.data.token, res.data.username, res.data.balance)
            } else {
                _auth.value = _auth.value.copy(
                    isLoading = false,
                    error     = res.error ?: "Identifiants incorrects"
                )
            }
        }
    }

    // ── Inscription ──────────────────────────────────────────
    fun register(username: String, email: String, phone: String, password: String) {
        if (username.isBlank() || email.isBlank() || phone.isBlank() || password.isBlank()) {
            _auth.value = _auth.value.copy(error = "Tous les champs sont requis")
            return
        }
        viewModelScope.launch {
            _auth.value = _auth.value.copy(isLoading = true, error = "")
            val res = ApiService.register(username.trim(), email.trim(),
                                          phone.trim(), password)
            if (res.success && res.data != null) {
                _auth.value = AuthState(
                    isLoggedIn = true,
                    username   = res.data.username,
                    role       = "player"
                )
                saveSession(res.data.token, res.data.username, 0.0)
            } else {
                _auth.value = _auth.value.copy(
                    isLoading = false,
                    error     = res.error ?: "Erreur inscription"
                )
            }
        }
    }

    // ── Déconnexion ──────────────────────────────────────────
    fun logout() {
        clearSession()
        _auth.value   = AuthState()
        _wallet.value = WalletState()
    }

    // ── Profil ───────────────────────────────────────────────
    private fun refreshProfile() {
        viewModelScope.launch {
            val res = ApiService.getProfile()
            if (res.success && res.data != null) {
                val u = res.data
                _auth.value = _auth.value.copy(
                    username = u.optString("username", _auth.value.username),
                    role     = u.optString("role", "player")
                )
                val balance = u.optJSONObject("wallet")?.optDouble("balance", 0.0) ?: 0.0
                _wallet.value = _wallet.value.copy(balance = balance)
                saveSession(ApiService.getToken(), _auth.value.userId, balance)
            }
        }
    }

    // ── Dépôt ────────────────────────────────────────────────
    fun deposit(amount: Double, phone: String, operator: String) {
        if (amount < Config.MIN_DEPOSIT_CDF) {
            _wallet.value = _wallet.value.copy(message = "Minimum ${Config.MIN_DEPOSIT_CDF.toInt()} CDF")
            return
        }
        viewModelScope.launch {
            _wallet.value = _wallet.value.copy(isLoading = true, message = "")
            val res = ApiService.deposit(amount, phone, operator)
            if (res.success) {
                _wallet.value = _wallet.value.copy(
                    isLoading = false,
                    message   = "✅ Demande envoyée ! Confirmez sur votre téléphone."
                )
            } else {
                _wallet.value = _wallet.value.copy(
                    isLoading = false,
                    message   = "❌ ${res.error ?: "Erreur dépôt"}"
                )
            }
        }
    }

    // ── Retrait ──────────────────────────────────────────────
    fun withdraw(amount: Double, phone: String, operator: String) {
        if (amount > _wallet.value.balance) {
            _wallet.value = _wallet.value.copy(message = "Solde insuffisant")
            return
        }
        viewModelScope.launch {
            _wallet.value = _wallet.value.copy(isLoading = true, message = "")
            val res = ApiService.withdraw(amount, phone, operator)
            if (res.success) {
                val newBal = res.data?.optDouble("newBalance", _wallet.value.balance)
                             ?: _wallet.value.balance
                _wallet.value = _wallet.value.copy(
                    isLoading = false,
                    balance   = newBal,
                    message   = "✅ Retrait en cours. Vous recevrez l'argent sous peu."
                )
            } else {
                _wallet.value = _wallet.value.copy(
                    isLoading = false,
                    message   = "❌ ${res.error ?: "Erreur retrait"}"
                )
            }
        }
    }

    // ── Historique transactions ───────────────────────────────
    fun loadTransactions() {
        viewModelScope.launch {
            _wallet.value = _wallet.value.copy(isLoading = true)
            val res = ApiService.getTransactions()
            if (res.success && res.data != null) {
                val list = mutableListOf<TxItem>()
                val arr  = res.data.optJSONArray("transactions")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val tx = arr.getJSONObject(i)
                        list.add(TxItem(
                            type   = tx.optString("type"),
                            amount = tx.optDouble("amount", 0.0),
                            status = tx.optString("status"),
                            desc   = tx.optString("description"),
                            date   = tx.optString("createdAt").take(10)
                        ))
                    }
                }
                _wallet.value = _wallet.value.copy(
                    isLoading    = false,
                    transactions = list
                )
            } else {
                _wallet.value = _wallet.value.copy(isLoading = false)
            }
        }
    }

    // ── Compétitions ─────────────────────────────────────────
    fun loadCompetitions() {
        viewModelScope.launch {
            val res = ApiService.getCompetitions()
            if (res.success && res.data != null) {
                val list = mutableListOf<CompItem>()
                val arr  = res.data.optJSONArray("competitions")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val c   = arr.getJSONObject(i)
                        val pls = c.optJSONArray("players")?.length() ?: 0
                        val dist = mutableListOf<Int>()
                        val distArr = c.optJSONArray("prizeDistribution")
                        if (distArr != null)
                            for (j in 0 until distArr.length()) dist.add(distArr.getInt(j))
                        list.add(CompItem(
                            id           = c.optString("_id"),
                            title        = c.optString("title"),
                            entryFee     = c.optDouble("entryFee", 0.0),
                            prizePool    = c.optDouble("prizePool", 0.0),
                            players      = pls,
                            maxPlayers   = c.optInt("maxPlayers", 4),
                            status       = c.optString("status"),
                            distribution = dist
                        ))
                    }
                }
                _comps.value = list
            }
        }
    }

    // ── Rejoindre compétition ────────────────────────────────
    fun joinCompetition(
        compId:  String,
        color:   String,
        onOk:    (prizePool: Double) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val res = ApiService.joinCompetition(compId, color)
            if (res.success && res.data != null) {
                val prize   = res.data.optDouble("prizePool", 0.0)
                val newBal  = res.data.optDouble("newBalance", _wallet.value.balance)
                _wallet.value = _wallet.value.copy(balance = newBal)
                onOk(prize)
            } else {
                onError(res.error ?: "Impossible de rejoindre")
            }
        }
    }

    fun clearWalletMessage() {
        _wallet.value = _wallet.value.copy(message = "")
    }
}
