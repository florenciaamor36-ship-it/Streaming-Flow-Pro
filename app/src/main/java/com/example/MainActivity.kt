package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.ui.MainViewModel
import com.example.ui.screens.*
import com.example.ui.theme.ControlStreamingTheme
import com.example.util.DateFormatter
import com.example.util.ExpirationStatus

sealed class NavScreen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : NavScreen("dashboard", "Inicio", Icons.Default.Dashboard)
    object Sales : NavScreen("sales", "Ventas", Icons.Default.ReceiptLong)
    object Accounts : NavScreen("accounts", "Cuentas", Icons.Default.Tv)
    object Clients : NavScreen("clients", "Clientes", Icons.Default.People)
    object Reminders : NavScreen("reminders", "Alertas", Icons.Default.Notifications)
    object Profile : NavScreen("profile", "Mi Cuenta", Icons.Default.Person)
}

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ControlStreamingTheme {
                val userSession by mainViewModel.userSession.collectAsState()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Crossfade(
                        targetState = userSession != null,
                        label = "AuthCrossfade"
                    ) { isLoggedIn ->
                        if (isLoggedIn) {
                            MainAppContent(viewModel = mainViewModel)
                        } else {
                            AuthScreen(viewModel = mainViewModel)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(viewModel: MainViewModel) {
    var currentScreen by remember { mutableStateOf<NavScreen>(NavScreen.Dashboard) }

    val licenseStatus by viewModel.licenseStatus.collectAsState()
    val userSession by viewModel.userSession.collectAsState()
    val allSales by viewModel.allSales.collectAsState()
    val expiringCount = remember(allSales) {
        allSales.count {
            val status = DateFormatter.getExpirationStatus(it.expirationDate)
            status == ExpirationStatus.EXPIRING_TODAY ||
                    status == ExpirationStatus.EXPIRING_SOON ||
                    status == ExpirationStatus.EXPIRED
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                windowInsets = WindowInsets.navigationBars
            ) {
                val screens = listOf(
                    NavScreen.Dashboard,
                    NavScreen.Sales,
                    NavScreen.Accounts,
                    NavScreen.Clients,
                    NavScreen.Reminders,
                    NavScreen.Profile
                )

                screens.forEach { screen ->
                    val isSelected = currentScreen.route == screen.route

                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentScreen = screen },
                        icon = {
                            if (screen == NavScreen.Reminders && expiringCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge { Text("$expiringCount") }
                                    }
                                ) {
                                    Icon(screen.icon, contentDescription = screen.title)
                                }
                            } else {
                                Icon(screen.icon, contentDescription = screen.title)
                            }
                        },
                        label = { Text(screen.title) }
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                NavScreen.Dashboard -> DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToSales = { currentScreen = NavScreen.Sales },
                    onNavigateToReminders = { currentScreen = NavScreen.Reminders }
                )
                NavScreen.Sales -> SalesScreen(viewModel = viewModel)
                NavScreen.Accounts -> AccountsScreen(viewModel = viewModel)
                NavScreen.Clients -> ClientsScreen(viewModel = viewModel)
                NavScreen.Reminders -> RemindersScreen(viewModel = viewModel)
                NavScreen.Profile -> ProfileScreen(viewModel = viewModel)
            }

            // Subscription Lock Overlay when trial or paid license expires
            if (licenseStatus.isExpired && currentScreen != NavScreen.Profile) {
                var tokenInput by remember { mutableStateOf("") }
                var tokenError by remember { mutableStateOf<String?>(null) }
                val context = androidx.compose.ui.platform.LocalContext.current

                AlertDialog(
                    onDismissRequest = { /* Modal lock - require action or go to Profile */ },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(36.dp)
                        )
                    },
                    title = {
                        Text(
                            text = "Suscripción Vencida",
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Tu periodo de prueba de 3 días o suscripción ha finalizado. Para seguir administrando tus ventas y cuentas de streaming, activa tu suscripción ($5.000 ARS/mes).",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            OutlinedTextField(
                                value = tokenInput,
                                onValueChange = { tokenInput = it.uppercase() },
                                label = { Text("Ingresar Token de Activación") },
                                placeholder = { Text("Ej: SFP-30D-XXXX-YYYY") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            if (tokenError != null) {
                                Text(
                                    text = tokenError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val res = viewModel.redeemLicenseToken(tokenInput)
                                        res.onSuccess {
                                            tokenError = null
                                            tokenInput = ""
                                        }.onFailure { err ->
                                            tokenError = err.message ?: "Token inválido"
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Activar Token")
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    val userEmailStr = userSession?.email ?: "Usuario Local"
                                    val userIdStr = userSession?.userId ?: "N/A"
                                    val msg = """
                                        👋 *Hola! Quisiera activar mi suscripción de Stream Flow Pro.*
                                        
                                        📦 *Plan:* Mensual ($5.000 ARS / 30 Días)
                                        👤 *Usuario:* $userEmailStr
                                        🆔 *ID Cuenta:* $userIdStr
                                    """.trimIndent()
                                    com.example.util.WhatsAppHelper.openWhatsApp(context, com.example.util.LicenseManager.OWNER_WHATSAPP_NUMBER, msg)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Solicitar Token por WhatsApp")
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = { currentScreen = NavScreen.Profile }
                        ) {
                            Text("Ir a Mi Cuenta")
                        }
                    }
                )
            }
        }
    }
}
