package com.example.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.local.ProfileSaleEntity
import com.example.data.local.StreamingAccountEntity
import com.example.data.model.StreamingPlatforms
import com.example.ui.components.PlatformBadge
import com.example.util.DateFormatter
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditSaleDialog(
    existingSale: ProfileSaleEntity? = null,
    availableAccounts: List<StreamingAccountEntity>,
    onDismiss: () -> Unit,
    onSave: (ProfileSaleEntity) -> Unit
) {
    var saleType by remember { mutableStateOf(existingSale?.saleType ?: "PROFILE") } // "PROFILE" or "FULL_ACCOUNT"
    
    var selectedAccount by remember {
        mutableStateOf(availableAccounts.find { it.id == existingSale?.accountId } ?: availableAccounts.firstOrNull())
    }
    var accountDropdownExpanded by remember { mutableStateOf(false) }

    var platformName by remember {
        mutableStateOf(existingSale?.platformName ?: selectedAccount?.platformName ?: StreamingPlatforms.ALL.first().name)
    }
    var accountEmail by remember {
        mutableStateOf(existingSale?.accountEmail ?: selectedAccount?.accountEmail ?: "")
    }
    var accountPassword by remember {
        mutableStateOf(existingSale?.accountPassword ?: selectedAccount?.accountPassword ?: "")
    }

    var profileName by remember { mutableStateOf(existingSale?.profileName ?: if (saleType == "FULL_ACCOUNT") "Cuenta Completa" else "Perfil 1") }
    var profilePin by remember { mutableStateOf(existingSale?.profilePin ?: if (saleType == "FULL_ACCOUNT") "N/A" else "1234") }
    var clientName by remember { mutableStateOf(existingSale?.clientName ?: "") }
    var clientPhone by remember { mutableStateOf(existingSale?.clientPhone ?: "+549") }
    var salePriceStr by remember { mutableStateOf(existingSale?.salePrice?.toString() ?: "5.0") }
    var costPriceStr by remember { mutableStateOf(existingSale?.costPrice?.toString() ?: "2.5") }
    var durationDaysStr by remember { mutableStateOf("30") }
    var paymentStatus by remember { mutableStateOf(existingSale?.paymentStatus ?: "PAID") }
    var supplierName by remember { mutableStateOf(existingSale?.supplierName ?: selectedAccount?.supplierName ?: "") }
    var supplierPhone by remember { mutableStateOf(existingSale?.supplierPhone ?: selectedAccount?.supplierPhone ?: "") }
    var notes by remember { mutableStateOf(existingSale?.notes ?: "") }

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
                        text = if (existingSale == null) "Nueva Venta / Registro" else "Editar Venta / Registro",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Sale Type Toggle (Perfil Individual vs Cuenta Completa)
                Text("Tipo de Venta", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = saleType == "PROFILE",
                        onClick = {
                            saleType = "PROFILE"
                            if (profileName == "Cuenta Completa") profileName = "Perfil 1"
                            if (profilePin == "N/A") profilePin = "1234"
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("👤 Perfil Individual", style = MaterialTheme.typography.labelSmall)
                    }
                    SegmentedButton(
                        selected = saleType == "FULL_ACCOUNT",
                        onClick = {
                            saleType = "FULL_ACCOUNT"
                            profileName = "Cuenta Completa"
                            profilePin = "N/A"
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("📺 Cuenta Completa", style = MaterialTheme.typography.labelSmall)
                    }
                }

                // Select Main Account (Cuenta Madre)
                if (availableAccounts.isNotEmpty()) {
                    Text(
                        text = "Vincular a Cuenta de Inventario (Opcional)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box {
                        OutlinedTextField(
                            value = selectedAccount?.let { "${it.platformName} (${it.accountEmail})" } ?: "Seleccionar cuenta de origen",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { accountDropdownExpanded = true },
                            enabled = false,
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                        DropdownMenu(
                            expanded = accountDropdownExpanded,
                            onDismissRequest = { accountDropdownExpanded = false }
                        ) {
                            availableAccounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            PlatformBadge(acc.platformName)
                                            Text(acc.accountEmail, style = MaterialTheme.typography.bodySmall)
                                        }
                                    },
                                    onClick = {
                                        selectedAccount = acc
                                        platformName = acc.platformName
                                        accountEmail = acc.accountEmail
                                        accountPassword = acc.accountPassword
                                        supplierName = acc.supplierName
                                        supplierPhone = acc.supplierPhone
                                        costPriceStr = if (saleType == "FULL_ACCOUNT") acc.cost.toString() else (acc.cost / acc.maxProfiles).toString()
                                        accountDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Platform name and account email / password
                OutlinedTextField(
                    value = platformName,
                    onValueChange = { platformName = it },
                    label = { Text("Plataforma / Servicio (Ej: Netflix, Disney+)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

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
                    label = { Text("Contraseña de Acceso") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (saleType == "PROFILE") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = profileName,
                            onValueChange = { profileName = it },
                            label = { Text("Nombre Perfil (Ej: Perfil 1)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = profilePin,
                            onValueChange = { profilePin = it },
                            label = { Text("PIN Perfil") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }

                // Client Details
                OutlinedTextField(
                    value = clientName,
                    onValueChange = { clientName = it },
                    label = { Text("Nombre del Cliente") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = clientPhone,
                    onValueChange = { clientPhone = it },
                    label = { Text("WhatsApp Cliente (+54...)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Sale Price & Cost
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = salePriceStr,
                        onValueChange = { salePriceStr = it },
                        label = { Text("Precio Venta ($)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = costPriceStr,
                        onValueChange = { costPriceStr = it },
                        label = { Text("Costo Real ($)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                // Duration in days
                if (existingSale == null) {
                    OutlinedTextField(
                        value = durationDaysStr,
                        onValueChange = { durationDaysStr = it },
                        label = { Text("Duración del Servicio (Días, ej: 30)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // Payment Status
                Text("Estado de Pago", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = paymentStatus == "PAID",
                        onClick = { paymentStatus = "PAID" },
                        label = { Text("PAGADO") }
                    )
                    FilterChip(
                        selected = paymentStatus == "PENDING",
                        onClick = { paymentStatus = "PENDING" },
                        label = { Text("PENDIENTE") }
                    )
                }

                // Internal Supplier Details
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
                            text = "🕵️ Datos del Proveedor (Uso Interno - No visible para el cliente)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = supplierName,
                                onValueChange = { supplierName = it },
                                label = { Text("Proveedor") },
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

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas Internas") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (clientName.isBlank()) {
                                errorMessage = "Ingresa el nombre del cliente"
                                return@Button
                            }
                            val price = salePriceStr.toDoubleOrNull() ?: 0.0
                            val cost = costPriceStr.toDoubleOrNull() ?: 0.0
                            val days = durationDaysStr.toIntOrNull() ?: 30

                            val now = System.currentTimeMillis()
                            val expiration = existingSale?.expirationDate ?: DateFormatter.addDaysToCurrentTime(days)

                            val saleEntity = ProfileSaleEntity(
                                id = existingSale?.id ?: ("sale_" + UUID.randomUUID().toString()),
                                userId = existingSale?.userId ?: "",
                                accountId = selectedAccount?.id ?: existingSale?.accountId ?: "",
                                platformName = platformName.ifBlank { "Streaming" },
                                accountEmail = accountEmail,
                                accountPassword = accountPassword,
                                profileName = if (saleType == "FULL_ACCOUNT") "Cuenta Completa" else profileName,
                                profilePin = if (saleType == "FULL_ACCOUNT") "N/A" else profilePin,
                                clientName = clientName,
                                clientPhone = clientPhone,
                                salePrice = price,
                                costPrice = cost,
                                saleDate = existingSale?.saleDate ?: now,
                                expirationDate = expiration,
                                paymentStatus = paymentStatus,
                                renewalStatus = existingSale?.renewalStatus ?: "ACTIVE",
                                saleType = saleType,
                                supplierName = supplierName,
                                supplierPhone = supplierPhone,
                                notes = notes
                            )
                            onSave(saleEntity)
                        }
                    ) {
                        Text("Guardar Registros")
                    }
                }
            }
        }
    }
}
