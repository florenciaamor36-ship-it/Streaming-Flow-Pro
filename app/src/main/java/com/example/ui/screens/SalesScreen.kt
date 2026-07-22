package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ProfileSaleEntity
import com.example.ui.MainViewModel
import com.example.ui.SalesFilter
import com.example.ui.components.ExpirationStatusChip
import com.example.ui.components.PaymentStatusChip
import com.example.ui.components.PlatformBadge
import com.example.ui.dialogs.AddEditSaleDialog
import com.example.ui.dialogs.WhatsAppMessageDialog
import com.example.ui.theme.BrandSecondary
import com.example.ui.theme.BrandTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val sales by viewModel.filteredSales.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val currentFilter by viewModel.salesFilter.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var showAddSaleDialog by remember { mutableStateOf(false) }
    var editingSale by remember { mutableStateOf<ProfileSaleEntity?>(null) }
    var selectedSaleForWhatsApp by remember { mutableStateOf<ProfileSaleEntity?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSaleDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nueva Venta")
            }
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Control de Ventas de Perfiles",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Buscar cliente, teléfono o perfil...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = null)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Filter Chips Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = currentFilter == SalesFilter.ALL,
                        onClick = { viewModel.setSalesFilter(SalesFilter.ALL) },
                        label = { Text("Todos") }
                    )
                }
                item {
                    FilterChip(
                        selected = currentFilter == SalesFilter.EXPIRING_SOON,
                        onClick = { viewModel.setSalesFilter(SalesFilter.EXPIRING_SOON) },
                        label = { Text("⏳ Por Vencer") }
                    )
                }
                item {
                    FilterChip(
                        selected = currentFilter == SalesFilter.EXPIRED,
                        onClick = { viewModel.setSalesFilter(SalesFilter.EXPIRED) },
                        label = { Text("🚨 Vencidos") }
                    )
                }
                item {
                    FilterChip(
                        selected = currentFilter == SalesFilter.PENDING_PAYMENT,
                        onClick = { viewModel.setSalesFilter(SalesFilter.PENDING_PAYMENT) },
                        label = { Text("💰 Pend. Pago") }
                    )
                }
                item {
                    FilterChip(
                        selected = currentFilter == SalesFilter.ACTIVE,
                        onClick = { viewModel.setSalesFilter(SalesFilter.ACTIVE) },
                        label = { Text("✅ Activos") }
                    )
                }
            }

            // Sales List
            if (sales.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No se encontraron ventas con este filtro.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(sales, key = { it.id }) { sale ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        PlatformBadge(sale.platformName)
                                        AssistChip(
                                            onClick = {},
                                            label = {
                                                Text(
                                                    text = if (sale.saleType == "FULL_ACCOUNT") "📺 Cuenta Completa" else "👤 Perfil",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            },
                                            modifier = Modifier.height(24.dp)
                                        )
                                    }
                                    ExpirationStatusChip(sale.expirationDate)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = sale.clientName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (sale.saleType == "FULL_ACCOUNT") {
                                            Text(
                                                text = "Cuenta Completa • Usuario: ${sale.accountEmail}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (sale.accountPassword.isNotBlank()) {
                                                Text(
                                                    text = "Pass: ${sale.accountPassword}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = "${sale.profileName} • PIN: ${sale.profilePin}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "Cuenta: ${sale.accountEmail}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "$${sale.salePrice}",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = BrandSecondary
                                        )
                                        PaymentStatusChip(sale.paymentStatus)
                                    }
                                }

                                // Internal Supplier tag if phone exists
                                if (sale.supplierName.isNotBlank() || sale.supplierPhone.isNotBlank()) {
                                    val context = androidx.compose.ui.platform.LocalContext.current
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "🕵️ Prov: ${sale.supplierName.ifBlank { "N/A" }} (${sale.supplierPhone.ifBlank { "Sin WhatsApp" }})",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (sale.supplierPhone.isNotBlank()) {
                                                TextButton(
                                                    onClick = {
                                                        com.example.util.WhatsAppHelper.openWhatsApp(
                                                            context,
                                                            sale.supplierPhone,
                                                            com.example.util.WhatsAppHelper.buildSupplierContactMessage(
                                                                sale.platformName,
                                                                sale.accountEmail,
                                                                sale.notes
                                                            )
                                                        )
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                                ) {
                                                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    Text("Chat Proveedor", fontSize = 10.sp)
                                                }
                                            }
                                        }
                                    }
                                }

                                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                                // Quick Action Bar
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Toggle payment
                                    TextButton(
                                        onClick = { viewModel.togglePaymentStatus(sale) },
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (sale.paymentStatus == "PAID") Icons.Default.MoneyOff else Icons.Default.AttachMoney,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (sale.paymentStatus == "PAID") "Marcar Pendiente" else "Marcar Pagado",
                                            fontSize = 12.sp
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        // Renew +30 days
                                        IconButton(
                                            onClick = { viewModel.renewSale(sale, 30) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Autorenew,
                                                contentDescription = "Renovar +30 días",
                                                tint = BrandTertiary
                                            )
                                        }

                                        // WhatsApp Send
                                        IconButton(
                                            onClick = { selectedSaleForWhatsApp = sale },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Send,
                                                contentDescription = "WhatsApp",
                                                tint = BrandSecondary
                                            )
                                        }

                                        // Edit
                                        IconButton(
                                            onClick = { editingSale = sale },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Editar",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        // Delete
                                        IconButton(
                                            onClick = { viewModel.deleteSale(sale.id) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Eliminar",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddSaleDialog) {
        AddEditSaleDialog(
            availableAccounts = accounts,
            onDismiss = { showAddSaleDialog = false },
            onSave = { sale ->
                viewModel.saveSale(sale)
                showAddSaleDialog = false
            }
        )
    }

    editingSale?.let { sale ->
        AddEditSaleDialog(
            existingSale = sale,
            availableAccounts = accounts,
            onDismiss = { editingSale = null },
            onSave = { updated ->
                viewModel.saveSale(updated)
                editingSale = null
            }
        )
    }

    selectedSaleForWhatsApp?.let { sale ->
        WhatsAppMessageDialog(
            sale = sale,
            onDismiss = { selectedSaleForWhatsApp = null }
        )
    }
}
