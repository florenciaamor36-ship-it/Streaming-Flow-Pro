package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.data.local.ProfileSaleEntity

object NotificationHelper {
    const val CHANNEL_ID = "streaming_expirations_channel"
    private const val CHANNEL_NAME = "Recordatorios de Vencimientos"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificaciones sobre perfiles de streaming por vencer o vencidos"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun showExpirationNotification(context: Context, sale: ProfileSaleEntity) {
        createNotificationChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val days = DateFormatter.getDaysRemaining(sale.expirationDate)
        val title = when {
            days < 0 -> "⚠️ Perfil Vencido: ${sale.platformName}"
            days == 0L -> "🚨 Perfil Vence Hoy: ${sale.platformName}"
            else -> "⏳ Próximo a Vencer: ${sale.platformName}"
        }

        val contentText = "${sale.clientName} (${sale.profileName}) - ${DateFormatter.getStatusLabel(sale.expirationDate)}"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val notificationId = (sale.id.hashCode() and 0x7FFFFFFF)
        manager.notify(notificationId, notification)
    }

    fun notifyAllExpiringSales(context: Context, sales: List<ProfileSaleEntity>) {
        val expiring = sales.filter {
            val days = DateFormatter.getDaysRemaining(it.expirationDate)
            days <= 3
        }
        expiring.forEach { sale ->
            showExpirationNotification(context, sale)
        }
    }
}
