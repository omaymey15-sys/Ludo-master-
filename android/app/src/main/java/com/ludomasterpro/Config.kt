package com.ludomasterpro

/**
 * ══════════════════════════════════════════════════════════════
 *  Config.kt — URLs et constantes globales de l'application
 *
 *  🔧 MODIFIER ICI UNIQUEMENT pour changer l'environnement
 * ══════════════════════════════════════════════════════════════
 */
object Config {

    // ── Environnement actif ────────────────────────────────
    //  Passer à PROD avant de builder l'APK final
    private val ENV = Env.PROD

    enum class Env { DEV, PROD }

    // ── URL de base de l'API ───────────────────────────────
    val API_BASE: String get() = when (ENV) {
        // 🖥️  Développement local
        //   • Émulateur Android  → 10.0.2.2
        //   • Téléphone (Wi-Fi) → IP de ton PC ex: 192.168.1.X
        Env.DEV  -> "http://10.0.2.2:3000/api"

        // 🚀 Production Render.com
        //   Remplacer par ton URL Render exacte
        Env.PROD -> "https://ludo-master-apii.onrender.com/api"
    }

    // ── URL WebSocket (Socket.IO) ──────────────────────────
    val SOCKET_BASE: String get() = when (ENV) {
        Env.DEV  -> "http://10.0.2.2:3000"
        Env.PROD -> "https://ludo-master-apii.onrender.com"
    }

    // ── Constantes de l'application ───────────────────────
    const val APP_NAME        = "Ludo Master Pro"
    const val APP_VERSION     = "1.0.0"
    const val MIN_DEPOSIT_CDF = 500.0
    const val MIN_BET_CDF     = 200.0

    // ── Timeouts réseau (millisecondes) ───────────────────
    const val CONNECT_TIMEOUT = 30_000L
    const val READ_TIMEOUT    = 30_000L

    // ── DataStore keys ────────────────────────────────────
    const val DS_TOKEN   = "auth_token"
    const val DS_USER_ID = "user_id"
    const val DS_WALLET  = "wallet_balance"
}
