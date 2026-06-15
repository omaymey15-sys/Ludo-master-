package com.ludomasterpro.network

import com.ludomasterpro.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

// ── DATA ───────────────────────────────────────────────
data class AuthResponse(
    val token: String,
    val username: String,
    val balance: Double,
    val role: String
)

data class ApiResult<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null
)

// ── SERVICE ────────────────────────────────────────────
object ApiService {

    private var authToken: String = ""

    fun setToken(token: String) { authToken = token }
    fun clearToken() { authToken = "" }
    fun getToken(): String = authToken
    fun isLoggedIn() = authToken.isNotEmpty()

    // ─────────────────────────────────────────────
    // LOGIN
    // ─────────────────────────────────────────────
    suspend fun login(email: String, password: String): ApiResult<AuthResponse> =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("email", email)
                    put("password", password)
                }

                val resp = post("/auth/login", body)

                if (resp.has("token")) {
                    authToken = resp.getString("token")

                    val user = resp.optJSONObject("user")

                    ApiResult(
                        success = true,
                        data = AuthResponse(
                            token = authToken,
                            username = user?.optString("username") ?: "",
                            balance = user?.optJSONObject("wallet")
                                ?.optDouble("balance", 0.0) ?: 0.0,
                            role = user?.optString("role", "player") ?: "player"
                        )
                    )
                } else {
                    ApiResult(false, error = resp.optString("message", "Erreur login"))
                }
            } catch (e: Exception) {
                ApiResult(false, error = e.message ?: "Erreur réseau")
            }
        }

    // ─────────────────────────────────────────────
    // REGISTER
    // ─────────────────────────────────────────────
    suspend fun register(
        username: String,
        email: String,
        phone: String,
        password: String
    ): ApiResult<AuthResponse> =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("username", username)
                    put("email", email)
                    put("phone", phone)
                    put("password", password)
                }

                val resp = post("/auth/register", body)

                if (resp.has("token")) {
                    authToken = resp.getString("token")

                    ApiResult(
                        true,
                        AuthResponse(
                            token = authToken,
                            username = username,
                            balance = 0.0,
                            role = "player"
                        )
                    )
                } else {
                    ApiResult(false, error = resp.optString("message"))
                }
            } catch (e: Exception) {
                ApiResult(false, error = e.message)
            }
        }

    // ─────────────────────────────────────────────
    // PROFILE
    // ─────────────────────────────────────────────
    suspend fun getProfile(): ApiResult<JSONObject> =
        withContext(Dispatchers.IO) {
            try {
                ApiResult(true, get("/auth/me"))
            } catch (e: Exception) {
                ApiResult(false, error = e.message)
            }
        }

    // ─────────────────────────────────────────────
    // DEPOSIT
    // ─────────────────────────────────────────────
    suspend fun deposit(amount: Double, phone: String, operator: String): ApiResult<JSONObject> =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("amount", amount)
                    put("phone", phone)
                    put("operator", operator)
                }

                val resp = post("/payment/deposit", body)

                if (resp.has("txId")) ApiResult(true, resp)
                else ApiResult(false, error = resp.optString("message"))
            } catch (e: Exception) {
                ApiResult(false, error = e.message)
            }
        }

    // ─────────────────────────────────────────────
    // WITHDRAW
    // ─────────────────────────────────────────────
    suspend fun withdraw(amount: Double, phone: String, operator: String): ApiResult<JSONObject> =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("amount", amount)
                    put("phone", phone)
                    put("operator", operator)
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

    // ─────────────────────────────────────────────
    // COMPETITIONS
    // ─────────────────────────────────────────────
    suspend fun getCompetitions(): ApiResult<JSONObject> =
        withContext(Dispatchers.IO) {
            try {
                ApiResult(true, get("/competitions?status=open"))
            } catch (e: Exception) {
                ApiResult(false, error = e.message)
            }
        }

    suspend fun joinCompetition(compId: String, color: String): ApiResult<JSONObject> =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("color", color)
                }

                val resp = post("/competitions/$compId/join", body)

                ApiResult(true, resp)
            } catch (e: Exception) {
                ApiResult(false, error = e.message)
            }
        }

    // ─────────────────────────────────────────────
    // HTTP GET
    // ─────────────────────────────────────────────
    private fun get(path: String): JSONObject {
        val conn = (URL(Config.API_BASE + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = Config.CONNECT_TIMEOUT.toInt()
            readTimeout = Config.READ_TIMEOUT.toInt()
            setRequestProperty("Accept", "application/json")

            if (authToken.isNotEmpty()) {
                setRequestProperty("Authorization", "Bearer $authToken")
            }
        }

        return read(conn)
    }

    // ─────────────────────────────────────────────
    // HTTP POST
    // ─────────────────────────────────────────────
    private fun post(path: String, body: JSONObject): JSONObject {
        val conn = (URL(Config.API_BASE + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = Config.CONNECT_TIMEOUT.toInt()
            readTimeout = Config.READ_TIMEOUT.toInt()
            doOutput = true

            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")

            if (authToken.isNotEmpty()) {
                setRequestProperty("Authorization", "Bearer $authToken")
            }
        }

        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use {
            it.write(body.toString())
        }

        return read(conn)
    }

    // ─────────────────────────────────────────────
    // SAFE RESPONSE
    // ─────────────────────────────────────────────
    private fun read(conn: HttpURLConnection): JSONObject {
        val code = conn.responseCode

        val stream = conn.inputStream ?: conn.errorStream

        val text = BufferedReader(InputStreamReader(stream ?: return JSONObject()))
            .use { it.readText() }

        if (code !in 200..299) {
            return JSONObject().apply {
                put("success", false)
                put("message", text)
                put("status", code)
            }
        }

        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }
}
