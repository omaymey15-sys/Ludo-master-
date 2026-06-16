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

// ── Data classes ─────────────────────────────
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

// ── Service API ─────────────────────────────
object ApiService {

    private var authToken: String = ""

    fun setToken(token: String) { authToken = token }
    fun clearToken() { authToken = "" }
    fun getToken(): String = authToken
    fun isLoggedIn() = authToken.isNotEmpty()

    // ── LOGIN ─────────────────────────────
    suspend fun login(email: String, password: String): ApiResult<AuthResponse> =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("email", email)
                    put("password", password)
                }

                val resp = post("/auth/login", body)

                if (resp.optString("token").isNotEmpty()) {

                    val user = resp.optJSONObject("user")

                    val result = AuthResponse(
                        token = resp.optString("token"),
                        username = user?.optString("username", "") ?: "",
                        balance = user?.optDouble("balance", 0.0) ?: 0.0,
                        role = user?.optString("role", "player") ?: "player"
                    )

                    authToken = result.token

                    ApiResult(true, result)
                } else {
                    ApiResult(false, error = resp.optString("message", "Login failed"))
                }

            } catch (e: Exception) {
                ApiResult(false, error = e.message ?: "Network error")
            }
        }

    // ── REGISTER ──────────────────────────
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

                if (resp.optString("token").isNotEmpty()) {

                    val user = resp.optJSONObject("user")

                    val result = AuthResponse(
                        token = resp.optString("token"),
                        username = user?.optString("username", username) ?: username,
                        balance = user?.optDouble("balance", 0.0) ?: 0.0,
                        role = user?.optString("role", "player") ?: "player"
                    )

                    authToken = result.token

                    ApiResult(true, result)
                } else {
                    ApiResult(false, error = resp.optString("message", "Register failed"))
                }

            } catch (e: Exception) {
                ApiResult(false, error = e.message ?: "Network error")
            }
        }

    // ── PROFILE ───────────────────────────
    suspend fun getProfile(): ApiResult<JSONObject> =
        withContext(Dispatchers.IO) {
            try {
                ApiResult(true, get("/auth/me"))
            } catch (e: Exception) {
                ApiResult(false, error = e.message)
            }
        }

    // ── HTTP GET ───────────────────────────
    private fun get(path: String): JSONObject {
        val conn = (URL(Config.API_BASE + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            if (authToken.isNotEmpty()) {
                setRequestProperty("Authorization", "Bearer $authToken")
            }
        }

        return readResponse(conn)
    }

    // ── HTTP POST ──────────────────────────
    private fun post(path: String, body: JSONObject): JSONObject {
        val conn = (URL(Config.API_BASE + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            if (authToken.isNotEmpty()) {
                setRequestProperty("Authorization", "Bearer $authToken")
            }
        }

        OutputStreamWriter(conn.outputStream).use {
            it.write(body.toString())
            it.flush()
        }

        return readResponse(conn)
    }

    // ── SAFE JSON PARSER (IMPORTANT FIX) ──
    private fun readResponse(conn: HttpURLConnection): JSONObject {
        val stream = try {
            if (conn.responseCode in 200..299)
                conn.inputStream
            else
                conn.errorStream
        } catch (e: Exception) {
            conn.errorStream
        }

        val raw = BufferedReader(InputStreamReader(stream ?: conn.errorStream))
            .use { it.readText() }

        return try {
            JSONObject(raw)
        } catch (e: Exception) {

            // 🔥 IMPORTANT: évite crash si backend renvoie texte
            JSONObject().apply {
                put("success", false)
                put("message", raw)
            }
        }
    }
}
