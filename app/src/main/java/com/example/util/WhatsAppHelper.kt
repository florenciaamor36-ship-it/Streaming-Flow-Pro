package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.data.local.ProfileSaleEntity
import java.net.URLEncoder

enum class MessageType(val title: String) {
    SERVICE_DELIVERY("Entrega de Credenciales"),
    REMINDER_PAYMENT("Recordatorio de Cobro"),
    RENEWAL_CONFIRMATION("Confirmación de Renovación")
}

object WhatsAppHelper {

    fun buildClientTemplate(
        type: MessageType,
        sale: ProfileSaleEntity
    ): String {
        val days = DateFormatter.getDaysRemaining(sale.expirationDate)
        val expDateStr = DateFormatter.formatDate(sale.expirationDate)
        val isFullAccount = sale.saleType == "FULL_ACCOUNT"
        val tipoServicio = if (isFullAccount) "Cuenta Completa" else "Perfil Individual"

        return when (type) {
            MessageType.SERVICE_DELIVERY -> """
                👋 ¡Hola *${sale.clientName}*!
                
                Gracias por tu compra. Aquí tienes los datos de tu servicio:
                
                📺 *Plataforma:* ${sale.platformName} ($tipoServicio)
                📧 *Correo / Usuario:* ${sale.accountEmail}
                ${if (sale.accountPassword.isNotBlank()) "🔑 *Contraseña:* ${sale.accountPassword}\n" else ""}${if (!isFullAccount) "👤 *Perfil:* ${sale.profileName}\n🔢 *PIN:* ${sale.profilePin}\n" else ""}
                📅 *Vencimiento:* $expDateStr ($days días de servicio)
                💰 *Monto Pagado:* ${'$'}${sale.salePrice}
                
                ⚠️ *REGLAS DEL SERVICIO:*
                1. No modificar contraseña ni correo de la cuenta.
                2. Usar únicamente el perfil asignado.
                
                ¡Que disfrutes tu contenido! 🍿
            """.trimIndent()

            MessageType.REMINDER_PAYMENT -> {
                when {
                    days < 0 -> """
                        Hola *${sale.clientName}* 👋,
                        Tu servicio de *${sale.platformName}* (${if (isFullAccount) "Cuenta Completa" else sale.profileName}) venció el *${expDateStr}*.
                        
                        💰 *Monto a renovar:* ${'$'}${sale.salePrice}
                        
                        Para mantener activo tu acceso y conservar tus perfiles, por favor realiza tu pago de renovación. ¡Muchas gracias!
                    """.trimIndent()

                    days == 0L -> """
                        Hola *${sale.clientName}* 👋,
                        Te recordamos que tu servicio de *${sale.platformName}* (${if (isFullAccount) "Cuenta Completa" else sale.profileName}) *VENCE HOY* (${expDateStr}).
                        
                        💰 *Monto a abonar:* ${'$'}${sale.salePrice}
                        
                        Por favor envíanos tu comprobante para mantener la suscripción activa sin interrupciones.
                    """.trimIndent()

                    else -> """
                        Hola *${sale.clientName}* 👋,
                        Recordatorio: Tu servicio de *${sale.platformName}* (${if (isFullAccount) "Cuenta Completa" else sale.profileName}) vence en *$days días* (Fecha: $expDateStr).
                        
                        💰 *Valor de renovación:* ${'$'}${sale.salePrice}
                        
                        ¡Quedamos atentos para renovar tu suscripción!
                    """.trimIndent()
                }
            }

            MessageType.RENEWAL_CONFIRMATION -> """
                ✅ ¡Hola *${sale.clientName}*!
                
                Tu renovación de *${sale.platformName}* (${if (isFullAccount) "Cuenta Completa" else sale.profileName}) ha sido procesada con éxito.
                
                📅 *Nueva fecha de vencimiento:* $expDateStr
                💰 *Monto Abonado:* ${'$'}${sale.salePrice}
                
                ¡Muchas gracias por tu preferencia y renovar con nosotros! 🎉
            """.trimIndent()
        }
    }

    fun buildSupplierContactMessage(
        platformName: String,
        email: String,
        notes: String
    ): String {
        return """
            Hola! Consulta sobre el servicio de *$platformName*:
            Email / Cuenta: $email
            $notes
        """.trimIndent()
    }

    fun openWhatsApp(context: Context, phone: String, message: String) {
        val cleanPhone = phone.replace(Regex("[^0-9+]"), "")
        try {
            val encodedMessage = URLEncoder.encode(message, "UTF-8")
            val url = if (cleanPhone.isNotBlank()) {
                "https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedMessage"
            } else {
                "https://api.whatsapp.com/send?text=$encodedMessage"
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "No se pudo abrir WhatsApp: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

