package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.ProfileSaleEntity
import com.example.ui.MainViewModel
import com.example.ui.components.ExpirationStatusChip
import com.example.ui.components.PlatformBadge
import com.example.ui.dialogs.WhatsAppMessageDialog
import com.example.ui.theme.BrandError
import com.example.ui.theme.BrandSecondary
import com.example.ui.theme.BrandTertiary
import com.example.util.DateFormatter
import com.example.util.ExpirationStatus

@Composable
fun RemindersScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val allSales by viewModel.allSales.collectAsState()
    var selectedSaleForWhatsApp by remember { mutableStateOf<ProfileSaleEntity?>(null) }

    val expiringSales = remember(allSales) {
        allSales.filter { sale ->
            val status = DateFormatter.getExpirationStatus(sale.expirationDate)
            status == ExpirationStatus.EXPIRING_TODAY ||
                    status == ExpirationStatus.EXPIRING_SOON ||
                    status == ExpirationStatus.EXPIRED
        }.sortedBy { it.expirationDate }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Notificaciones y Vencimientos",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Control de cobro y avisos automáticos de renovación por WhatsApp",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Action card to test Android system push notifications
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sistema de Notificaciones Android",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Genera alertas en la barra del teléfono para perfiles por vencer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = { viewModel.triggerNotificationsForExpiringSales() }
                ) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Probar Alertas")
                }
            }
        }

        if (expiringSales.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.CheckCircleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = BrandSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "¡Excelente! No tienes perfiles vencidos ni próximos a vencer.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Todas tus ventas de perfiles se encuentran al día.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(expiringSales, key = { it.id }) { sale ->
                    val status = DateFormatter.getExpirationStatus(sale.expirationDate)
                    val cardBorderColor = when (status) {
                        ExpirationStatus.EXPIRED -> BrandError
                        ExpirationStatus.EXPIRING_TODAY -> BrandError
                        ExpirationStatus.EXPIRING_SOON -> BrandTertiary
                        else -> MaterialTheme.colorScheme.outline
                    }

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
                                PlatformBadge(sale.platformName)
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
                                    Text(
                                        text = "📱 Tel: ${sale.clientPhone}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = BrandSecondary
                                    )
                                }
                            }

                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { viewModel.renewSale(sale, 30) }) {
                                    Icon(Icons.Default.Autorenew, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Renovar +30 días")
                                }

                                Button(
                                    onClick = { selectedSaleForWhatsApp = sale },
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandSecondary)
                                ) {
                                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Aviso WhatsApp")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedSaleForWhatsApp?.let { sale ->
        WhatsAppMessageDialog(
            sale = sale,
            onDismiss = { selectedSaleForWhatsApp = null }
        )
    }
}
