package com.ludomasterpro.ui.screens

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import com.ludomasterpro.engine.*
import com.ludomasterpro.ui.components.*
import com.ludomasterpro.ui.theme.LudoColors

@Composable
fun MenuScreen(
    nbPlayers:   Int,
    configs:     List<PlayerConfig>,
    bestScores:  Map<String, Int>,
    balance:     Double,
    isLoggedIn:  Boolean,
    onNbChange:  (Int) -> Unit,
    onConfig:    (Int, PlayerConfig) -> Unit,
    onStartSolo: () -> Unit,
    onLobby:     () -> Unit,
    onWallet:    () -> Unit,
    onLogin:     () -> Unit
) {
    // Animation titre
    val titleScale by rememberInfiniteTransition(label = "t").animateFloat(
        initialValue  = 0.97f,
        targetValue   = 1.03f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ts"
    )
    val titleColor by rememberInfiniteTransition(label = "c").animateColor(
        initialValue  = LudoColors.Primary,
        targetValue   = Color(0xFFFF9500),
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "tc"
    )

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF0D0D2E), Color(0xFF1A1A4A), Color(0xFF0D0D2E)))
        )
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // ── Logo ──────────────────────────────────────────
            Text("🎲", fontSize = 60.sp, modifier = Modifier.scale(titleScale))
            Text("LUDO MASTER PRO", fontSize = 24.sp,
                 fontWeight = FontWeight.ExtraBold,
                 fontFamily = FontFamily.Monospace, color = titleColor)
            Text("Le Ludo de compétition", fontSize = 12.sp, color = LudoColors.TextSub)

            // ── Solde / Connexion ─────────────────────────────
            if (isLoggedIn) {
                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF0A0A20),
                        border = BorderStroke(1.dp, LudoColors.Primary.copy(alpha = 0.4f))) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text("💰 Mon solde", fontSize = 13.sp, color = LudoColors.TextSub)
                        Text(formatCDF(balance), fontSize = 16.sp,
                             fontWeight = FontWeight.ExtraBold,
                             fontFamily = FontFamily.Monospace, color = LudoColors.Primary)
                        TextButton(onClick = onWallet) { Text("Gérer", color = LudoColors.Primary) }
                    }
                }
            } else {
                Button(onClick = onLogin, Modifier.fillMaxWidth().height(48.dp),
                       shape = RoundedCornerShape(12.dp),
                       colors = ButtonDefaults.buttonColors(
                           containerColor = Color(0xFF1A1A4A),
                           contentColor   = LudoColors.Primary)) {
                    Text("🔑 Se connecter pour les tournois", fontSize = 13.sp)
                }
            }

            // ── Modes de jeu ──────────────────────────────────
            Text("MODE DE JEU", fontSize = 10.sp, color = LudoColors.TextSub,
                 letterSpacing = 2.sp, fontFamily = FontFamily.Monospace)

            // Solo
            ModeCard(
                icon        = "🎮",
                title       = "Solo Classique",
                subtitle    = "Jouez contre des IA",
                color       = LudoColors.Green,
                onClick     = onStartSolo
            )

            // Tournoi (si connecté)
            ModeCard(
                icon        = "🏆",
                title       = "Tournois",
                subtitle    = if (isLoggedIn) "Gagnez de l'argent réel"
                              else "Connexion requise",
                color       = LudoColors.Primary,
                onClick     = if (isLoggedIn) onLobby else onLogin,
                isPremium   = true
            )

            // ── Config Solo ───────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(16.dp),
                color    = Color(0xFF13132A),
                border   = BorderStroke(1.dp, LudoColors.Border)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("CONFIGURATION SOLO", fontSize = 10.sp,
                         color = LudoColors.TextSub, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)

                    // Nb joueurs
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(2, 3, 4).forEach { n ->
                            FilterChip(
                                selected = nbPlayers == n,
                                onClick  = { onNbChange(n) },
                                label    = { Text("$n joueurs") },
                                colors   = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = LudoColors.Primary.copy(alpha = 0.2f),
                                    selectedLabelColor     = LudoColors.Primary
                                )
                            )
                        }
                    }

                    // Config par joueur
                    PieceColor.entries.take(nbPlayers).forEachIndexed { i, color ->
                        val cfg = configs.getOrNull(i) ?: return@forEachIndexed
                        PlayerQuickRow(i, color, cfg, onConfig)
                    }
                }
            }

            // ── Records ───────────────────────────────────────
            if (bestScores.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(14.dp),
                    color    = Color(0xFF0A0A1E),
                    border   = BorderStroke(1.dp, LudoColors.Border)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("🏅 RECORDS", fontSize = 10.sp, color = LudoColors.TextSub,
                             letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)
                        bestScores.entries.sortedBy { it.value }.take(3).forEach { (n, t) ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("🥇 $n", fontSize = 12.sp, color = Color.White)
                                Text("$t tours", fontSize = 12.sp, color = LudoColors.Primary,
                                     fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun ModeCard(icon: String, title: String, subtitle: String,
             color: Color, onClick: () -> Unit, isPremium: Boolean = false) {
    Surface(
        onClick  = onClick,
        shape    = RoundedCornerShape(16.dp),
        color    = color.copy(alpha = 0.12f),
        border   = BorderStroke(1.5.dp, color.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(icon, fontSize = 32.sp)
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                     color = Color.White)
                Text(subtitle, fontSize = 12.sp, color = LudoColors.TextSub)
            }
            if (isPremium) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = color.copy(alpha = 0.25f)
                ) {
                    Text("💵", modifier = Modifier.padding(6.dp), fontSize = 18.sp)
                }
            }
            Text("▶", fontSize = 18.sp, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PlayerQuickRow(index: Int, color: PieceColor, cfg: PlayerConfig,
                   onConfig: (Int, PlayerConfig) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("${color.emoji} ${color.label}", fontSize = 13.sp,
             fontWeight = FontWeight.Bold, color = color.toCompose(),
             modifier = Modifier.width(80.dp))

        // Toggle IA / Humain
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (cfg.type == PlayerType.HUMAN) "Humain" else "IA",
                 fontSize = 11.sp, color = LudoColors.TextSub)
            Switch(
                checked         = cfg.type == PlayerType.AI,
                onCheckedChange = { onConfig(index, cfg.copy(type = if (it) PlayerType.AI else PlayerType.HUMAN)) },
                modifier        = Modifier.scale(0.75f),
                colors          = SwitchDefaults.colors(
                    checkedThumbColor   = color.toCompose(),
                    checkedTrackColor   = color.toCompose().copy(alpha = 0.4f),
                    uncheckedThumbColor = LudoColors.TextDim
                )
            )
        }

        if (cfg.type == PlayerType.AI) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(AiLevel.EASY to "😊", AiLevel.NORMAL to "🤖", AiLevel.EXPERT to "🧠")
                    .forEach { (lvl, ico) ->
                        Box(
                            modifier = Modifier.size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (cfg.aiLevel == lvl) color.toCompose().copy(alpha = 0.25f)
                                            else Color(0xFF0A0A1E))
                                .border(1.dp,
                                        if (cfg.aiLevel == lvl) color.toCompose() else LudoColors.Border,
                                        RoundedCornerShape(6.dp))
                                .clickable { onConfig(index, cfg.copy(aiLevel = lvl)) },
                            contentAlignment = Alignment.Center
                        ) { Text(ico, fontSize = 14.sp) }
                    }
            }
        }
    }
}
