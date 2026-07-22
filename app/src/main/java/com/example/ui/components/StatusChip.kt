package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BrandError
import com.example.ui.theme.BrandSecondary
import com.example.ui.theme.BrandTertiary
import com.example.util.DateFormatter
import com.example.util.ExpirationStatus

@Composable
fun ExpirationStatusChip(
    expirationTimestamp: Long,
    modifier: Modifier = Modifier
) {
    val status = DateFormatter.getExpirationStatus(expirationTimestamp)
    val label = DateFormatter.getStatusLabel(expirationTimestamp)

    val (bgColor, textColor, icon) = when (status) {
        ExpirationStatus.EXPIRED -> Triple(
            BrandError.copy(alpha = 0.2f),
            BrandError,
            Icons.Default.Error
        )
        ExpirationStatus.EXPIRING_TODAY -> Triple(
            BrandError.copy(alpha = 0.25f),
            BrandError,
            Icons.Default.Warning
        )
        ExpirationStatus.EXPIRING_SOON -> Triple(
            BrandTertiary.copy(alpha = 0.2f),
            BrandTertiary,
            Icons.Default.Schedule
        )
        ExpirationStatus.ACTIVE -> Triple(
            BrandSecondary.copy(alpha = 0.15f),
            BrandSecondary,
            Icons.Default.CheckCircle
        )
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                color = textColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun PaymentStatusChip(
    paymentStatus: String,
    modifier: Modifier = Modifier
) {
    val isPaid = paymentStatus.equals("PAID", ignoreCase = true)
    val label = if (isPaid) "PAGADO" else "PENDIENTE"
    val color = if (isPaid) BrandSecondary else BrandTertiary

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
    }
}
