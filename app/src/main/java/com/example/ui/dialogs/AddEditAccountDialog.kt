package com.example.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.local.StreamingAccountEntity
import com.example.data.model.StreamingPlatforms
import com.example.util.DateFormatter
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditAccountDialog(
    existingAccount: StreamingAccountEntity? = null,
    onDismiss: () -> Unit,
    onSave: (StreamingAccountEntity) -> Unit
) {
    var selectedPlatform by remember {
        mutableStateOf(StreamingPlatforms.ALL.find { it.name.equals(existingAccount?.platformName, ignoreCase = true) } ?: StreamingPlatforms.NETFLIX)
    }
    var customPlatformName by remember { mutableStateOf(if (selectedPlatform == StreamingPlatforms.OTRO) existingAccount?.platformName ?: "" else "") }
    var accountEmail by remember { mutableStateOf(existingAccount?.accountEmail ?: "") }
    var accountPassword by remember { mutableStateOf(existingAccount?.accountPassword ?: "") }
    var maxProfilesStr by remember { mutableStateOf(existingAccount?.maxProfiles?.toString() ?: selectedPlatform.defaultMaxProfiles.toString()) }
    var costStr by remember { mutableStateOf(existingAccount?.cost?.toString() ?: "10.0") }
    var supplierName by remember { mutableStateOf(existingAccount?.supplierName ?: "") }
    var supplierPhone by remember { mutableStateOf(existingAccount?.supplierPhone ?: "+549") }
    var durationDaysStr by remember { mutableStateOf("30") }
    var notes by remember { mutableStateOf(existingAccount?.notes ?: "") }

    var platformDropdownExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (existingAccount == null) "Agregar Cuenta Madre" else "Editar Cuenta Madre",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Select Platform
                Text("Plataforma de Streaming", style = MaterialTheme.typography.labelMedium)
                Box {
                    OutlinedButton(
                        onClick = { platformDropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (selectedPlatform == StreamingPlatforms.OTRO && customPlatformName.isNotBlank()) customPlatformName else selectedPlatform.name)
                    }
                    DropdownMenu(
                        expanded = platformDropdownExpanded,
                        onDismissRequest = { platformDropdownExpanded = false }
                    ) {
                        StreamingPlatforms.ALL.forEach { platform ->
                            DropdownMenuItem(
                                text = { Text(platform.name) },
                                onClick = {
                                    selectedPlatform = platform
                                    maxProfilesStr = platform.defaultMaxProfiles.toString()
                                    platformDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                if (selectedPlatform == StreamingPlatforms.OTRO) {
                    OutlinedTextField(
                        value = customPlatformName,
                        onValueChange = { customPlatformName = it },
                        label = { Text("Nombre del Servicio / Combo Personalizado") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = accountEmail,
                    onValueChange = { accountEmail = it },
                    label = { Text("Correo / Usuario de Acceso") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = accountPassword,
                    onValueChange = { accountPassword = it },
                    label = { Text("Contraseña de la Cuenta Madre") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = maxProfilesStr,
                        onValueChange = { maxProfilesStr = it },
                        label = { Text("Cant. Perfiles") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = costStr,
                        onValueChange = { costStr = it },
                        label = { Text("Costo Total ($)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                // Supplier info (INTERNAL ONLY - NOT FOR CLIENT)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "🕵️ Datos del Proveedor (Uso Interno Privado)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = supplierName,
                                onValueChange = { supplierName = it },
                                label = { Text("Proveedor / Vendedor") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = supplierPhone,
                                onValueChange = { supplierPhone = it },
                                label = { Text("WhatsApp Proveedor") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                    }
                }

                if (existingAccount == null) {
                    OutlinedTextField(
                        value = durationDaysStr,
                        onValueChange = { durationDaysStr = it },
                        label = { Text("Vencimiento Cuenta (Días, ej: 30)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas / Observaciones") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (accountEmail.isBlank()) {
                                errorMessage = "Ingresa el correo de la cuenta"
                                return@Button
                            }
                            val finalPlatformName = if (selectedPlatform == StreamingPlatforms.OTRO && customPlatformName.isNotBlank()) customPlatformName else selectedPlatform.name
                            val maxProf = maxProfilesStr.toIntOrNull() ?: 5
                            val cost = costStr.toDoubleOrNull() ?: 10.0
                            val days = durationDaysStr.toIntOrNull() ?: 30

                            val now = System.currentTimeMillis()
                            val expiration = existingAccount?.expirationDate ?: DateFormatter.addDaysToCurrentTime(days)

                            val accountEntity = StreamingAccountEntity(
                                id = existingAccount?.id ?: ("acc_" + UUID.randomUUID().toString()),
                                userId = existingAccount?.userId ?: "",
                                platformName = finalPlatformName,
                                accountEmail = accountEmail,
                                accountPassword = accountPassword,
                                maxProfiles = maxProf,
                                cost = cost,
                                purchaseDate = existingAccount?.purchaseDate ?: now,
                                expirationDate = expiration,
                                supplierName = supplierName,
                                supplierPhone = supplierPhone,
                                notes = notes
                            )
                            onSave(accountEntity)
                        }
                    ) {
                        Text("Guardar Cuenta")
                    }
                }
            }
        }
    }
}
