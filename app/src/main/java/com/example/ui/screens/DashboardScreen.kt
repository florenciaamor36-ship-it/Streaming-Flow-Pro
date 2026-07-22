package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ProfileSaleEntity
import com.example.ui.MainViewModel
import com.example.ui.components.ExpirationStatusChip
import com.example.ui.components.MetricCard
import com.example.ui.components.PaymentStatusChip
import com.example.ui.components.PlatformBadge
import com.example.ui.dialogs.AddEditAccountDialog
import com.example.ui.dialogs.AddEditSaleDialog
import com.example.ui.dialogs.WhatsAppMessageDialog
import com.example.ui.theme.BrandError
import com.example.ui.theme.BrandSecondary
import com.example.ui.theme.BrandTertiary
import com.example.util.DateFormatter
import com.example.util.ExpirationStatus

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToSales: () -> Unit,
    onNavigateToReminders: () -> Unit,
    modifier: Modifier = Modifier
) {
    val metrics by viewModel.metrics.collectAsState()
    val allSales by viewModel.allSales.collectAsState()
    val accounts by viewModel.accounts.collectAsState()

    var showAddSaleDialog by remember { mutableStateOf(false) }
    var showAddAccountDialog by remember { mutableStateOf(false) }
    var selectedSaleForWhatsApp by remember { mutableStateOf<ProfileSaleEntity?>(null) }

    val urgentExpiringSales = remember(allSales) {
        allSales.filter { sale ->
            val status = DateFormatter.getExpirationStatus(sale.expirationDate)
            status == ExpirationStatus.EXPIRING_TODAY || status == ExpirationStatus.EXPIRING_SOON || status == ExpirationStatus.EXPIRED
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome Title & User Bar
        item {
            Column {
                Text(
                    text = "Control de Ventas",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Resumen de perfiles y facturación de streaming",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Financial & Metrics Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MetricCard(
                        title = "Ingresos Totales",
                        value = "$${String.format("%.2f", metrics.totalSalesIncome)}",
                        icon = Icons.Default.AttachMoney,
                        iconTint = BrandSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Ganancia Neta",
                        value = "$${String.format("%.2f", metrics.netProfit)}",
                        icon = Icons.Default.TrendingUp,
                        iconTint = MaterialTheme.colorScheme.primary,
                        subtitle = "Costo: $${String.format("%.2f", metrics.totalCost)}",
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MetricCard(
                        title = "Perfiles Activos",
                        value = "${metrics.activeProfilesCount}",
                        icon = Icons.Default.CheckCircle,
                        iconTint = BrandSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Por Vencer / Vencidos",
                        value = "${metrics.expiringCount + metrics.expiredCount}",
                        icon = Icons.Default.Warning,
                        iconTint = if (metrics.expiredCount > 0) BrandError else BrandTertiary,
                        subtitle = "Alerta de renovación",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Quick Actions Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { showAddSaleDialog = true },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Vender Perfil", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = { showAddAccountDialog = true },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Tv, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cuenta Madre", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // Expiration Urgent Warning Banner
        if (urgentExpiringSales.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = BrandError.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToReminders() }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(BrandError.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = BrandError
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "⚠️ ${urgentExpiringSales.size} perfiles requieren atención",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = BrandError
                            )
                            Text(
                                text = "Toca para ver notificaciones y enviar recordatorios por WhatsApp.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = BrandError
                        )
                    }
                }
            }
        }

        // Recent Sales Title & View All
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Últimas Ventas y Perfiles",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onNavigateToSales) {
                    Text("Ver Todos")
                }
            }
        }

        // Sales List Items (Up to 5)
        if (allSales.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.TvOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No tienes ventas registradas.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(allSales.take(5)) { sale ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlatformBadge(sale.platformName)
                            ExpirationStatusChip(sale.expirationDate)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = sale.clientName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${sale.profileName} • PIN: ${sale.profilePin}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "$${sale.salePrice}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandSecondary
                                )
                                PaymentStatusChip(sale.paymentStatus)
                            }
                        }

                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Tel: ${sale.clientPhone}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            IconButton(
                                onClick = { selectedSaleForWhatsApp = sale },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Enviar WhatsApp",
                                    tint = BrandSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialogs
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

    if (showAddAccountDialog) {
        AddEditAccountDialog(
            onDismiss = { showAddAccountDialog = false },
            onSave = { acc ->
                viewModel.saveAccount(acc)
                showAddAccountDialog = false
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
