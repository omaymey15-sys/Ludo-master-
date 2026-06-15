package com.ludomasterpro.network

// ══════════════════════════════════════════════════════════════
//  ApiService.kt — Client HTTP vers le backend Render
//  Utilise HttpURLConnection (pas de dépendance externe)
// ══════════════════════════════════════════════════════════════

import com.ludomasterpro.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

// ── Data classes réponses ─────────────────────────────────────
data class AuthResponse(
    val token:    String,
    val username: String,
    val balance:  Double,
    val role:     String
)

data class ApiResult<T>(
    val success: Boolean,
    val data:    T?      = null,
    val error:   String? = null
)

// ── Service principal ─────────────────────────────────────────
object ApiService {

    private var authToken: String = ""

    fun setToken(token: String) { authToken = token }
    fun clearToken()             { authToken = "" }
    fun getToken(): String       = authToken
    fun isLoggedIn() = authToken.isNotEmpty()

    // ── Connexion ────────────────────────────────────────────
    suspend fun login(email: String, password: String): ApiResult<AuthResponse> =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("email", email); put("password", password)
                }
                val resp = post("/auth/login", body)
                if (resp.has("token")) {
                    authToken = resp.getString("token")
                    ApiResult(
                        success = true,
                        data    = AuthResponse(
                            token    = authToken,
                            username = resp.optString("user").let {
                                resp.optJSONObject("user")?.optString("username") ?: ""
                            },
                            balance  = resp.optJSONObject("user")?.optDouble("balance", 0.0) ?: 0.0,
                            role     = resp.optJSONObject("user")?.optString("role", "player") ?: "player"
                        )
                    )
                } else {
                    ApiResult(false, error = resp.optString("message", "Erreur connexion"))
                }
            } catch (e: Exception) {
                ApiResult(false, error = e.message ?: "Erreur réseau")
            }
        }

    // ── Inscription ──────────────────────────────────────────
    suspend fun register(
        username: String, email: String, phone: String, password: String
    ): ApiResult<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("username", username); put("email", email)
                put("phone", phone);       put("password", password)
            }
            val resp = post("/auth/register", body)
            if (resp.has("token")) {
                authToken = resp.getString("token")
                ApiResult(true, AuthResponse(
                    token    = authToken,
                    username = username,
                    balance  = 0.0,
                    role     = "player"
                ))
            } else {
                ApiResult(false, error = resp.optString("message"))
            }
        } catch (e: Exception) {
            ApiResult(false, error = e.message)
        }
    }

    // ── Profil utilisateur ───────────────────────────────────
    suspend fun getProfile(): ApiResult<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val resp = get("/auth/me")
            ApiResult(true, resp)
        } catch (e: Exception) {
            ApiResult(false, error = e.message)
        }
    }

    // ── Dépôt ────────────────────────────────────────────────
    suspend fun deposit(amount: Double, phone: String, operator: String): ApiResult<JSONObject> =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("amount", amount); put("phone", phone); put("operator", operator)
                }
                val resp = post("/payment/deposit", body)
                if (resp.has("txId"))
                    ApiResult(true, resp)
                else
                    ApiResult(false, error = resp.optString("message"))
            } catch (e: Exception) {
                ApiResult(false, error = e.message)
            }
        }

    // ── Retrait ──────────────────────────────────────────────
    suspend fun withdraw(amount: Double, phone: String, operator: String): ApiResult<JSONObject> =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("amount", amount); put("phone", phone); put("operator", operator)
                }
                val resp = post("/payment/withdraw", body)
                if (resp.has("txId") || resp.has("newBalance"))
                    ApiResult(true, resp)
                else
                    ApiResult(false, error = resp.optString("message"))
            } catch (e: Exception) {
                ApiResult(false, error = e.message)
            }
        }

    // ── Historique transactions ───────────────────────────────
    suspend fun getTransactions(page: Int = 1): ApiResult<JSONObject> =
        withContext(Dispatchers.IO) {
            try { ApiResult(true, get("/payment/history?page=$page&limit=20")) }
            catch (e: Exception) { ApiResult(false, error = e.message) }
        }

    // ── Compétitions ouvertes ────────────────────────────────
    suspend fun getCompetitions(): ApiResult<JSONObject> =
        withContext(Dispatchers.IO) {
            try { ApiResult(true, get("/competitions?status=open")) }
            catch (e: Exception) { ApiResult(false, error = e.message) }
        }

    // ── Rejoindre une compétition ────────────────────────────
    suspend fun joinCompetition(compId: String, color: String): ApiResult<JSONObject> =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply { put("color", color) }
                val resp = post("/competitions/$compId/join", body)
                if (resp.has("prizePool") || resp.has("message"))
                    ApiResult(true, resp)
                else
                    ApiResult(false, error = resp.optString("message"))
            } catch (e: Exception) {
                ApiResult(false, error = e.message)
            }
        }

    // ══════════════════════════════════════════════════════════
    //  Helpers HTTP bas niveau
    // ══════════════════════════════════════════════════════════
    private fun get(path: String): JSONObject {
        val url  = URL("${Config.API_BASE}$path")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = Config.CONNECT_TIMEOUT.toInt()
            readTimeout    = Config.READ_TIMEOUT.toInt()
            setRequestProperty("Content-Type",   "application/json")
            setRequestProperty("Accept",          "application/json")
            if (authToken.isNotEmpty())
                setRequestProperty("Authorization", "Bearer $authToken")
        }
        return readResponse(conn)
    }

    private fun post(path: String, body: JSONObject): JSONObject {
        val url  = URL("${Config.API_BASE}$path")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = Config.CONNECT_TIMEOUT.toInt()
            readTimeout    = Config.READ_TIMEOUT.toInt()
            doOutput = true
            setRequestProperty("Content-Type",   "application/json")
            setRequestProperty("Accept",          "application/json")
            if (authToken.isNotEmpty())
                setRequestProperty("Authorization", "Bearer $authToken")
        }
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use {
            it.write(body.toString())
            it.flush()
        }
        return readResponse(conn)
    }

    private fun readResponse(conn: HttpURLConnection): JSONObject {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val raw = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
            .use { it.readText() }
        return if (raw.isBlank()) JSONObject() else JSONObject(raw)
    }
}
