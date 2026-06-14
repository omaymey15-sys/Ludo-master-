package com.ludomasterpro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import com.ludomasterpro.engine.*
import com.ludomasterpro.ui.screens.*
import com.ludomasterpro.ui.theme.LudoMasterTheme

class MainActivity : ComponentActivity() {

    private val gameVm: GameViewModel by viewModels()
    private val authVm: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            LudoMasterTheme {
                LudoApp(
                    gameVm = gameVm,
                    authVm = authVm
                )
            }
        }
    }
}

enum class Screen {
    MENU,
    AUTH,
    WALLET,
    LOBBY,
    GAME,
    PODIUM
}

@Composable
fun LudoApp(
    gameVm: GameViewModel,
    authVm: AuthViewModel
) {

    val gameState by gameVm.state.collectAsState()
    val configs by gameVm.configs.collectAsState()
    val nbPlayers by gameVm.nbPlayers.collectAsState()
    val bestScores by gameVm.bestScores.collectAsState()

    val authState by authVm.auth.collectAsState()
    val walletState by authVm.wallet.collectAsState()
    val competitions by authVm.competitions.collectAsState()

    var screen by remember { mutableStateOf(Screen.MENU) }
    var showQuit by remember { mutableStateOf(false) }

    LaunchedEffect(gameState.phase) {
        if (
            gameState.phase == GamePhase.FINISHED &&
            screen == Screen.GAME
        ) {
            screen = Screen.PODIUM
        }
    }

    LaunchedEffect(authState.isLoggedIn) {
        if (
            authState.isLoggedIn &&
            screen == Screen.AUTH
        ) {
            authVm.loadTransactions()
            screen = Screen.MENU
        }
    }

    when (screen) {

        Screen.AUTH -> {
            AuthScreen(
                onLogin = { email, pwd ->
                    authVm.login(email, pwd)
                },
                onRegister = { user, email, phone, pwd ->
                    authVm.register(
                        user,
                        email,
                        phone,
                        pwd
                    )
                },
                isLoading = authState.isLoading,
                errorMsg = authState.error
            )
        }

        Screen.WALLET -> {

            LaunchedEffect(Unit) {
                authVm.loadTransactions()
            }

            WalletScreen(
                balance = walletState.balance,
                transactions = walletState.transactions.map {
                    WalletTx(
                        type = it.type,
                        amount = it.amount,
                        status = it.status,
                        desc = it.desc,
                        date = it.date
                    )
                },
                onDeposit = { amt, phone, op ->
                    authVm.deposit(amt, phone, op)
                },
                onWithdraw = { amt, phone, op ->
                    authVm.withdraw(amt, phone, op)
                },
                onBack = {
                    authVm.clearWalletMessage()
                    screen = Screen.MENU
                },
                isLoading = walletState.isLoading,
                message = walletState.message
            )
        }

        Screen.LOBBY -> {

            LaunchedEffect(Unit) {
                authVm.loadCompetitions()
            }

            LobbyScreen(
                competitions = competitions.map {
                    CompetitionItem(
                        id = it.id,
                        title = it.title,
                        entryFee = it.entryFee,
                        prizePool = it.prizePool,
                        players = it.players,
                        maxPlayers = it.maxPlayers,
                        status = it.status,
                        distribution = it.distribution
                    )
                },
                balance = walletState.balance,
                onJoin = { comp, color ->
                    authVm.joinCompetition(
                        compId = comp.id,
                        color = color,
                        onOk = { prize ->
                            gameVm.startGame(
                                prizePool = prize,
                                competitionId = comp.id
                            )
                            screen = Screen.GAME
                        },
                        onError = {}
                    )
                },
                onRefresh = {
                    authVm.loadCompetitions()
                },
                onBack = {
                    screen = Screen.MENU
                }
            )
        }

        Screen.GAME -> {

            GameScreen(
                state = gameState,
                onDiceRoll = gameVm::onDiceRolled,
                onPiece = gameVm::selectPiece,
                onApplyMove = gameVm::applyMove,
                onQuit = {
                    showQuit = true
                }
            )

            if (showQuit) {
                AlertDialog(
                    onDismissRequest = {
                        showQuit = false
                    },
                    title = {
                        Text("Quitter la partie ?")
                    },
                    text = {
                        Text("La partie en cours sera perdue.")
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showQuit = false
                                gameVm.goToMenu()
                                screen = Screen.MENU
                            }
                        ) {
                            Text("Quitter")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showQuit = false
                            }
                        ) {
                            Text("Continuer")
                        }
                    }
                )
            }
        }

        Screen.PODIUM -> {
            PodiumScreen(
                players = gameState.players,
                totalTurns = gameState.totalTurns,
                bestScores = bestScores,
                prizePool = gameState.prizePool,
                onReplay = {
                    gameVm.replayGame()
                    screen = Screen.GAME
                },
                onMenu = {
                    gameVm.goToMenu()
                    screen = Screen.MENU
                }
            )
        }

        Screen.MENU -> {

            LaunchedEffect(authState.isLoggedIn) {
                if (authState.isLoggedIn) {
                    authVm.loadTransactions()
                }
            }

            MenuScreen(
                nbPlayers = nbPlayers,
                configs = configs,
                bestScores = bestScores,
                balance = walletState.balance,
                isLoggedIn = authState.isLoggedIn,
                onNbChange = gameVm::setNbPlayers,
                onConfig = gameVm::updateConfig,
                onStartSolo = {
                    gameVm.startGame()
                    screen = Screen.GAME
                },
                onLobby = {
                    screen = Screen.LOBBY
                },
                onWallet = {
                    screen = Screen.WALLET
                },
                onLogin = {
                    screen = Screen.AUTH
                }
            )
        }
    }
}
