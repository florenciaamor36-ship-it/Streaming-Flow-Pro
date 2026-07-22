package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.util.LicenseManager
import com.example.util.WhatsAppHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val session by viewModel.userSession.collectAsState()
    val metrics by viewModel.metrics.collectAsState()
    val clients by viewModel.clients.collectAsState()
    val licenseStatus by viewModel.licenseStatus.collectAsState()

    var tokenInput by remember { mutableStateOf("") }
    var tokenMessage by remember { mutableStateOf<String?>(null) }
    var isTokenSuccess by remember { mutableStateOf(false) }

    // User Subscription Plan Selection
    var userSelectedPlanDays by remember { mutableIntStateOf(30) }

    // Easter Egg Hidden Admin State (10 Taps Trigger)
    var titleTapCount by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var showAdminDialog by remember { mutableStateOf(false) }
    var isAdminUnlocked by remember { mutableStateOf(false) }
    var adminPasswordInput by remember { mutableStateOf("") }
    var selectedDurationDays by remember { mutableIntStateOf(30) }
    var generatedToken by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // Title with 10 Taps Secret Trigger
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    val now = System.currentTimeMillis()
                    if (now - lastTapTime > 3000) {
                        titleTapCount = 1
                    } else {
                        titleTapCount++
                    }
                    lastTapTime = now

                    if (titleTapCount >= 10) {
                        titleTapCount = 0
                        showAdminDialog = true
                    }
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Mi Sesión y Suscripción",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                modifier = Modifier.size(20.dp)
            )
        }

        // License & Subscription Status Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (licenseStatus.isExpired) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (licenseStatus.isExpired) Icons.Default.Lock else Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = if (licenseStatus.isExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Estado de Licencia",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                text = if (licenseStatus.isTrial) "PRUEBA 3 DÍAS"
                                else if (licenseStatus.isExpired) "VENCIDA"
                                else "PRO ACTIVA",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                }

                Text(
                    text = licenseStatus.statusText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = "Vencimiento: ${licenseStatus.expirationDateStr}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                // Plan Selector for Purchasing Subscription
                Text(
                    text = "Selecciona tu Plan de Suscripción:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Plan 30 Days
                    FilterChip(
                        selected = userSelectedPlanDays == 30,
                        onClick = { userSelectedPlanDays = 30 },
                        label = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("30 Días (Mensual)", fontWeight = FontWeight.SemiBold)
                                Text("$5.000 ARS", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Plan 90 Days
                    FilterChip(
                        selected = userSelectedPlanDays == 90,
                        onClick = { userSelectedPlanDays = 90 },
                        label = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("90 Días (Trimestral)", fontWeight = FontWeight.SemiBold)
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(" -10% OFF ", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Text("$13.500 ARS", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Plan 365 Days
                    FilterChip(
                        selected = userSelectedPlanDays == 365,
                        onClick = { userSelectedPlanDays = 365 },
                        label = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("365 Días (Anual)", fontWeight = FontWeight.SemiBold)
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.tertiaryContainer
                                    ) {
                                        Text(" -20% OFF ", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                                    }
                                }
                                Text("$48.000 ARS", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Direct WhatsApp Button to Contact Admin/Owner (2235590910)
                val selectedPlanInfo = when (userSelectedPlanDays) {
                    90 -> "Plan Trimestral (90 Días - $13.500 ARS)"
                    365 -> "Plan Anual (365 Días - $48.000 ARS)"
                    else -> "Plan Mensual (30 Días - $5.000 ARS)"
                }

                Button(
                    onClick = {
                        val userEmailStr = session?.email ?: "Usuario Local"
                        val userIdStr = session?.userId ?: "N/A"
                        val message = """
                            👋 *Hola! Quisiera solicitar / renovar mi suscripción de Stream Flow Pro.*
                            
                            📦 *Plan Seleccionado:* $selectedPlanInfo
                            👤 *Usuario/Email:* $userEmailStr
                            🆔 *ID Cuenta:* $userIdStr
                            📅 *Estado Licencia Actual:* ${licenseStatus.statusText}
                        """.trimIndent()

                        WhatsAppHelper.openWhatsApp(context, LicenseManager.OWNER_WHATSAPP_NUMBER, message)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pedir $selectedPlanInfo por WhatsApp", fontWeight = FontWeight.Bold)
                }

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                // Redeem Token Section
                Text(
                    text = "Canjear Token de Activación",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it.uppercase() },
                        placeholder = { Text("Ej: SFP-30D-K89A-7F22") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            val res = viewModel.redeemLicenseToken(tokenInput)
                            res.onSuccess { days ->
                                isTokenSuccess = true
                                tokenMessage = "¡Licencia activada con éxito por $days días!"
                                tokenInput = ""
                            }.onFailure { err ->
                                isTokenSuccess = false
                                tokenMessage = err.message ?: "Error al validar token"
                            }
                        }
                    ) {
                        Text("Activar")
                    }
                }

                if (tokenMessage != null) {
                    Text(
                        text = tokenMessage!!,
                        color = if (isTokenSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // User Avatar & Partition Info Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Text(
                    text = session?.email ?: "Usuario Invitado",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Partición ID: ${session?.userId?.take(16)}...",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${metrics.totalAccountsCount}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Cuentas Madre",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${metrics.activeProfilesCount + metrics.expiringCount + metrics.expiredCount}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Perfiles Vendidos",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${clients.size}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Clientes",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Button(
            onClick = { viewModel.logout() },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Logout, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Cerrar Sesión / Cambiar de Usuario", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Hidden Secret Admin Token Generator Dialog (Triggered by 10 Taps)
    if (showAdminDialog) {
        AlertDialog(
            onDismissRequest = {
                showAdminDialog = false
                isAdminUnlocked = false
                adminPasswordInput = ""
                generatedToken = null
            },
            title = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(if (isAdminUnlocked) "Consola Admin: Generar Token" else "Acceso de Administrador")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!isAdminUnlocked) {
                        Text(
                            text = "Ingresa la clave de Administrador para acceder a la emisión de tokens:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedTextField(
                            value = adminPasswordInput,
                            onValueChange = { adminPasswordInput = it },
                            label = { Text("Clave Admin") },
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                if (adminPasswordInput == "Seleccionar1") {
                                    isAdminUnlocked = true
                                } else {
                                    Toast.makeText(context, "Clave incorrecta", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Ingresar")
                        }
                    } else {
                        Text(
                            text = "Genera un token criptográfico anti-fraude válido para enviar a tus clientes:",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Text("Selecciona la duración:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = selectedDurationDays == 30,
                                onClick = { selectedDurationDays = 30 },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                            ) {
                                Text("30 Días")
                            }
                            SegmentedButton(
                                selected = selectedDurationDays == 90,
                                onClick = { selectedDurationDays = 90 },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                            ) {
                                Text("90 Días")
                            }
                            SegmentedButton(
                                selected = selectedDurationDays == 365,
                                onClick = { selectedDurationDays = 365 },
                                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                            ) {
                                Text("1 Año")
                            }
                        }

                        Button(
                            onClick = {
                                generatedToken = LicenseManager.generateToken(selectedDurationDays)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Crear Token ($selectedDurationDays Días)")
                        }

                        if (generatedToken != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("🔑 Token Generado:", style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        text = generatedToken!!,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val clip = ClipData.newPlainText("Token Stream Flow", generatedToken)
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(context, "Token copiado al portapapeles", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Copiar")
                                        }

                                        Button(
                                            onClick = {
                                                val msg = "Hola! Tu código de activación mensual para Stream Flow Pro es: *$generatedToken* (Válido por $selectedDurationDays días). Ingrésalo en tu app en la sección 'Mi Sesión' para activar."
                                                WhatsAppHelper.openWhatsApp(context, "", msg)
                                            }
                                        ) {
                                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Enviar")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAdminDialog = false
                        isAdminUnlocked = false
                        adminPasswordInput = ""
                        generatedToken = null
                    }
                ) {
                    Text("Cerrar")
                }
            }
        )
    }
}

