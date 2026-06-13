package com.ludomasterpro.ui.screens

// ══════════════════════════════════════════════════════════════
//  AUTH SCREEN — Inscription / Connexion
// ══════════════════════════════════════════════════════════════

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import com.ludomasterpro.ui.theme.LudoColors

@Composable
fun AuthScreen(
    onLogin:    (email: String, password: String) -> Unit,
    onRegister: (username: String, email: String, phone: String, password: String) -> Unit,
    isLoading:  Boolean = false,
    errorMsg:   String  = ""
) {
    var isLogin by remember { mutableStateOf(true) }
    var username by remember { mutableStateOf("") }
    var email    by remember { mutableStateOf("") }
    var phone    by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0D0D2E), Color(0xFF1A1A4A), Color(0xFF0D0D2E)))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth().padding(28.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Logo
            Text("🎲", fontSize = 56.sp)
            Text("LUDO MASTER PRO", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                 fontFamily = FontFamily.Monospace, color = LudoColors.Primary)
            Text(if (isLogin) "Connexion" else "Créer un compte",
                 fontSize = 14.sp, color = LudoColors.TextSub)

            Spacer(Modifier.height(8.dp))

            // Carte
            Surface(
                shape  = RoundedCornerShape(20.dp),
                color  = Color(0xFF13132A),
                border = BorderStroke(1.dp, Color(0xFF222244)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

                    if (!isLogin) {
                        LudoField("Pseudo", username, { username = it }, "😊 Votre pseudo")
                        LudoField("Téléphone (M-Pesa, Orange…)", phone, { phone = it },
                                  "+243812345678", KeyboardType.Phone)
                    }
                    LudoField("Email", email, { email = it }, "exemple@email.com", KeyboardType.Email)
                    LudoField("Mot de passe", password, { password = it }, "••••••••",
                              KeyboardType.Password, isPassword = true)

                    if (errorMsg.isNotEmpty()) {
                        Text(errorMsg, color = Color(0xFFE74C3C), fontSize = 12.sp,
                             fontFamily = FontFamily.Monospace)
                    }

                    Button(
                        onClick  = {
                            if (isLogin) onLogin(email, password)
                            else onRegister(username, email, phone, password)
                        },
                        enabled  = !isLoading,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = LudoColors.Primary,
                            contentColor   = Color(0xFF0D0D1A)
                        )
                    ) {
                        if (isLoading) CircularProgressIndicator(Modifier.size(20.dp),
                            color = Color(0xFF0D0D1A), strokeWidth = 2.dp)
                        else Text(if (isLogin) "Connexion" else "Créer le compte",
                                  fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }

            TextButton(onClick = { isLogin = !isLogin }) {
                Text(
                    if (isLogin) "Pas encore de compte ? S'inscrire"
                    else "Déjà un compte ? Se connecter",
                    color = LudoColors.Primary, fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun LudoField(
    label:       String,
    value:       String,
    onValue:     (String) -> Unit,
    placeholder: String     = "",
    keyType:     KeyboardType = KeyboardType.Text,
    isPassword:  Boolean    = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label.uppercase(), fontSize = 10.sp, color = LudoColors.TextSub,
             letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)
        OutlinedTextField(
            value         = value,
            onValueChange = onValue,
            placeholder   = { Text(placeholder, color = LudoColors.TextDim, fontSize = 13.sp) },
            singleLine    = true,
            visualTransformation = if (isPassword) PasswordVisualTransformation()
                                   else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyType),
            modifier      = Modifier.fillMaxWidth(),
            shape         = RoundedCornerShape(10.dp),
            colors        = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = LudoColors.Primary,
                unfocusedBorderColor = Color(0xFF333355),
                focusedTextColor     = Color.White,
                unfocusedTextColor   = Color.White,
                cursorColor          = LudoColors.Primary
            )
        )
    }
}

// ══════════════════════════════════════════════════════════════
//  WALLET SCREEN — Portefeuille, Dépôt, Retrait
// ══════════════════════════════════════════════════════════════

@Composable
fun WalletScreen(
    balance:      Double,
    transactions: List<WalletTx>,
    onDeposit:    (amount: Double, phone: String, op: String) -> Unit,
    onWithdraw:   (amount: Double, phone: String, op: String) -> Unit,
    onBack:       () -> Unit,
    isLoading:    Boolean = false,
    message:      String  = ""
) {
    var tab      by remember { mutableIntStateOf(0) }
    var amount   by remember { mutableStateOf("") }
    var phone    by remember { mutableStateOf("") }
    var operator by remember { mutableStateOf("mpesa") }

    val operators = listOf("mpesa" to "M-Pesa 🟩", "orange_money" to "Orange 🟧", "airtel_money" to "Airtel 🟥")
    val quickAmounts = listOf(500, 1000, 2000, 5000, 10000, 20000)

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0D0D2E), Color(0xFF1A1A4A))))
    ) {
        Column(Modifier.fillMaxSize()) {

            // ── Header ──────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Text("◀", color = Color.White, fontSize = 18.sp)
                }
                Text("💰 Mon Portefeuille", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                     color = Color.White)
            }

            // ── Solde ───────────────────────────────────────
            Surface(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF13132A),
                border = BorderStroke(1.dp, LudoColors.Primary.copy(alpha = 0.4f))
            ) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("SOLDE DISPONIBLE", fontSize = 11.sp, color = LudoColors.TextSub,
                         letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)
                    Text(formatCDF(balance), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold,
                         color = LudoColors.Primary, fontFamily = FontFamily.Monospace)
                    Text("Franc Congolais (CDF)", fontSize = 12.sp, color = LudoColors.TextSub)
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Onglets Dépôt / Retrait / Historique ────────
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("⬇️ Dépôt","⬆️ Retrait","📋 Historique").forEachIndexed { i, lbl ->
                    FilterChip(
                        selected = tab == i,
                        onClick  = { tab = i },
                        label    = { Text(lbl, fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = LudoColors.Primary.copy(alpha = 0.2f),
                            selectedLabelColor     = LudoColors.Primary
                        )
                    )
                }
            }

            // ── Contenu des onglets ──────────────────────────
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (tab == 0 || tab == 1) {
                    // Montants rapides
                    Text("Montant rapide", fontSize = 11.sp, color = LudoColors.TextSub,
                         letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        quickAmounts.forEach { q ->
                            FilterChip(
                                selected = amount == q.toString(),
                                onClick  = { amount = q.toString() },
                                label    = { Text("${q} CDF", fontSize = 11.sp) }
                            )
                        }
                    }

                    LudoField("Montant (CDF)", amount, { amount = it },
                              "Ex: 1000", KeyboardType.Number)
                    LudoField("Numéro Mobile Money", phone, { phone = it },
                              "+243812345678", KeyboardType.Phone)

                    // Opérateur
                    Text("Opérateur", fontSize = 11.sp, color = LudoColors.TextSub,
                         letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        operators.forEach { (code, label) ->
                            FilterChip(
                                selected = operator == code,
                                onClick  = { operator = code },
                                label    = { Text(label, fontSize = 11.sp) },
                                colors   = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = LudoColors.Primary.copy(alpha = 0.2f),
                                    selectedLabelColor     = LudoColors.Primary
                                )
                            )
                        }
                    }

                    if (message.isNotEmpty()) {
                        Surface(shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF1A3A1A), border = BorderStroke(1.dp, LudoColors.Green)) {
                            Text(message, Modifier.padding(12.dp), color = LudoColors.Green, fontSize = 13.sp)
                        }
                    }

                    Button(
                        onClick  = {
                            val amt = amount.toDoubleOrNull() ?: return@Button
                            if (tab == 0) onDeposit(amt, phone, operator)
                            else onWithdraw(amt, phone, operator)
                        },
                        enabled  = !isLoading && amount.isNotEmpty() && phone.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = if (tab == 0) LudoColors.Green else LudoColors.Accent,
                            contentColor   = Color.White
                        )
                    ) {
                        if (isLoading) CircularProgressIndicator(Modifier.size(20.dp),
                            color = Color.White, strokeWidth = 2.dp)
                        else Text(if (tab == 0) "⬇️  Déposer" else "⬆️  Retirer",
                                  fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    // Info
                    Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF0A0A1E)) {
                        Text(
                            "• Minimum dépôt : 500 CDF\n• Minimum retrait : 200 CDF\n" +
                            "• Frais plateforme : 10% sur les gains\n" +
                            "• Powered by WonyaPay (Agréé BCC)",
                            Modifier.padding(12.dp), color = LudoColors.TextDim, fontSize = 11.sp,
                            lineHeight = 18.sp, fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Historique
                if (tab == 2) {
                    if (transactions.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            Text("Aucune transaction", color = LudoColors.TextSub)
                        }
                    } else {
                        transactions.forEach { tx ->
                            WalletTxRow(tx)
                        }
                    }
                }
            }
        }
    }
}

data class WalletTx(
    val type:   String,
    val amount: Double,
    val status: String,
    val desc:   String,
    val date:   String
)

@Composable
fun WalletTxRow(tx: WalletTx) {
    val isCredit = tx.type in listOf("deposit", "prize", "refund")
    val color    = if (isCredit) LudoColors.Green else LudoColors.Accent
    val icon     = when (tx.type) {
        "deposit"  -> "⬇️"; "withdraw" -> "⬆️"; "prize" -> "🏆"
        "bet"      -> "🎮"; "refund"   -> "↩️"; else -> "💸"
    }
    Surface(
        shape    = RoundedCornerShape(12.dp),
        color    = Color(0xFF13132A),
        border   = BorderStroke(1.dp, Color(0xFF222244)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 24.sp, modifier = Modifier.padding(end = 12.dp))
            Column(Modifier.weight(1f)) {
                Text(tx.desc, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
                Text(tx.date, fontSize = 11.sp, color = LudoColors.TextSub)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${if (isCredit) "+" else "−"}${formatCDF(tx.amount)}",
                     fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
                Text(tx.status, fontSize = 10.sp, color = when(tx.status) {
                    "success" -> LudoColors.Green; "pending" -> LudoColors.Primary
                    else      -> LudoColors.Accent
                })
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  LOBBY SCREEN — Compétitions disponibles
// ══════════════════════════════════════════════════════════════

data class CompetitionItem(
    val id:           String,
    val title:        String,
    val entryFee:     Double,
    val prizePool:    Double,
    val players:      Int,
    val maxPlayers:   Int,
    val status:       String,
    val distribution: List<Int>
)

@Composable
fun LobbyScreen(
    competitions: List<CompetitionItem>,
    balance:      Double,
    onJoin:       (CompetitionItem, String) -> Unit,
    onRefresh:    () -> Unit,
    onBack:       () -> Unit,
    isLoading:    Boolean = false
) {
    var selectedComp  by remember { mutableStateOf<CompetitionItem?>(null) }

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0D0D2E), Color(0xFF1A1A4A))))
    ) {
        Column(Modifier.fillMaxSize()) {

            // Header
            Row(Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Text("◀", color = Color.White, fontSize = 18.sp) }
                Column(Modifier.weight(1f)) {
                    Text("🏆 Tournois", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                         color = Color.White)
                    Text("Solde : ${formatCDF(balance)}", fontSize = 12.sp,
                         color = LudoColors.Primary, fontFamily = FontFamily.Monospace)
                }
                IconButton(onClick = onRefresh) { Text("🔄", fontSize = 20.sp) }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LudoColors.Primary)
                }
            } else {
                LazyCompList(competitions, balance) { comp ->
                    selectedComp = comp
                }
            }
        }

        // Dialogue couleur
        selectedComp?.let { comp ->
            ColorPickerDialog(
                comp      = comp,
                balance   = balance,
                onConfirm = { color ->
                    onJoin(comp, color)
                    selectedComp = null
                },
                onDismiss = { selectedComp = null }
            )
        }
    }
}

@Composable
fun LazyCompList(
    competitions: List<CompetitionItem>,
    balance:      Double,
    onSelect:     (CompetitionItem) -> Unit
) {
    if (competitions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🎲", fontSize = 48.sp)
                Text("Aucun tournoi disponible", color = LudoColors.TextSub, fontSize = 14.sp)
                Text("Revenez plus tard !", color = LudoColors.TextDim, fontSize = 12.sp)
            }
        }
    } else {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            competitions.forEach { comp ->
                CompetitionCard(comp, balance, onSelect)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
fun CompetitionCard(
    comp:     CompetitionItem,
    balance:  Double,
    onSelect: (CompetitionItem) -> Unit
) {
    val canAfford = balance >= comp.entryFee
    val isFull    = comp.players >= comp.maxPlayers

    Surface(
        shape    = RoundedCornerShape(16.dp),
        color    = Color(0xFF13132A),
        border   = BorderStroke(
            1.5.dp,
            if (canAfford && !isFull) LudoColors.Primary.copy(alpha = 0.5f) else Color(0xFF333355)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(comp.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(buildString {
                        comp.distribution.forEachIndexed { i, pct ->
                            append(listOf("🥇","🥈","🥉").getOrElse(i){"${i+1}."})
                            append(" $pct%  ")
                        }
                    }, fontSize = 11.sp, color = LudoColors.TextSub)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatCDF(comp.prizePool), fontSize = 16.sp,
                         fontWeight = FontWeight.ExtraBold, color = LudoColors.Primary)
                    Text("Prize Pool", fontSize = 10.sp, color = LudoColors.TextSub)
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Color(0xFF222244))
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("Mise", fontSize = 10.sp, color = LudoColors.TextSub)
                    Text(formatCDF(comp.entryFee), fontSize = 13.sp,
                         fontWeight = FontWeight.Bold,
                         color = if (canAfford) LudoColors.Green else LudoColors.Accent)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Joueurs", fontSize = 10.sp, color = LudoColors.TextSub)
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(comp.maxPlayers) { i ->
                            Box(Modifier.size(10.dp).background(
                                if (i < comp.players) LudoColors.Primary else Color(0xFF333355),
                                androidx.compose.foundation.shape.CircleShape
                            ))
                        }
                    }
                    Text("${comp.players}/${comp.maxPlayers}", fontSize = 11.sp,
                         color = LudoColors.TextSub)
                }
                Button(
                    onClick  = { onSelect(comp) },
                    enabled  = canAfford && !isFull,
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = LudoColors.Primary,
                        contentColor   = Color(0xFF0D0D1A),
                        disabledContainerColor = Color(0xFF333355)
                    )
                ) {
                    Text(when {
                        isFull      -> "Complet"
                        !canAfford  -> "Solde insuffisant"
                        else        -> "Rejoindre"
                    }, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ColorPickerDialog(
    comp:      CompetitionItem,
    balance:   Double,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = listOf(
        "RED" to ("🔴" to Color(0xFFE74C3C)),
        "BLUE" to ("🔵" to Color(0xFF2980B9)),
        "GREEN" to ("🟢" to Color(0xFF27AE60)),
        "YELLOW" to ("🟡" to Color(0xFFF39C12)),
    )
    var selected by remember { mutableStateOf("RED") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Color(0xFF13132A),
        tonalElevation   = 0.dp,
        title = { Text("Choisissez votre couleur", fontWeight = FontWeight.Bold, color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Compétition : ${comp.title}", fontSize = 13.sp, color = LudoColors.TextSub)
                Text("Mise : ${formatCDF(comp.entryFee)}  •  Solde : ${formatCDF(balance)}",
                     fontSize = 12.sp, color = LudoColors.Primary, fontFamily = FontFamily.Monospace)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    colors.forEach { (code, pair) ->
                        val (emoji, color) = pair
                        Box(
                            Modifier.size(56.dp)
                                .background(
                                    if (selected == code) color.copy(alpha = 0.25f) else Color(0xFF0A0A1E),
                                    RoundedCornerShape(12.dp)
                                )
                                .border(
                                    2.dp,
                                    if (selected == code) color else Color(0xFF333355),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { selected = code },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji, fontSize = 26.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selected) },
                   colors = ButtonDefaults.buttonColors(containerColor = LudoColors.Primary,
                                                         contentColor = Color(0xFF0D0D1A))) {
                Text("✅ Confirmer", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler", color = LudoColors.TextSub) }
        }
    )
}

// ── Helpers ──────────────────────────────────────────────────
fun formatCDF(amount: Double): String {
    val formatted = "%,.0f".format(amount).replace(",", " ")
    return "$formatted CDF"
}
