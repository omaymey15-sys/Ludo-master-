package com.ludomasterpro

/**
 * ══════════════════════════════════════════════════════════════
 *  Config.kt — PRODUCTION ONLY
 * ══════════════════════════════════════════════════════════════
 */
object Config {

    // ── API BASE (PRODUCTION UNIQUEMENT) ────────────────
    val API_BASE: String =
        "https://ludo-master-apii.onrender.com/api"

    // ── SOCKET BASE (PRODUCTION UNIQUEMENT) ─────────────
    val SOCKET_BASE: String =
        "https://ludo-master-apii.onrender.com"

    // ── APP INFO ─────────────────────────────────────────
    const val APP_NAME        = "Ludo Master Pro"
    const val APP_VERSION     = "1.0.0"

    // ── LIMITES ─────────────────────────────────────────
    const val MIN_DEPOSIT_CDF = 500.0
    const val MIN_BET_CDF     = 200.0

    // ── TIMEOUTS ────────────────────────────────────────
    const val CONNECT_TIMEOUT = 30_000L
    const val READ_TIMEOUT    = 30_000L

    // ── DATASTORE KEYS ──────────────────────────────────
    const val DS_TOKEN   = "auth_token"
    const val DS_USER_ID = "user_id"
    const val DS_WALLET  = "wallet_balance"
}
