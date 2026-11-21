package com.example.notifications

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.WebContainerActivity
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.example.util.NotificationHelper
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.R
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Periodic, privacy-preserving reminder check for the WebView app when its UI is closed. */
class ExpiryNotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(ENABLED, false)) return Result.success()
        val token = SessionTokenStore.read(applicationContext) ?: return Result.success()
        val accountId = SessionTokenStore.readAccountId(applicationContext)
        if (accountId.isNullOrBlank() || accountId != prefs.getString(OPT_IN_ACCOUNT_ID, null)) {
            disable(applicationContext)
            SessionTokenStore.clear(applicationContext)
            return Result.success()
        }
        return try {
            val request = Request.Builder()
                .url("https://stream-flow-api.tiendasshoficial.workers.dev/api/notifications/expirations")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code == 401) {
                    SessionTokenStore.clear(applicationContext)
                    disable(applicationContext)
                    return Result.success()
                }
                if (!response.isSuccessful) return Result.retry()
                val data = JSONObject(response.body?.string().orEmpty())
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
                val count = data.optInt("count", 0)
                val dayKey = data.optString("dayKey", "")
                if (count <= 0 || dayKey.isBlank() || prefs.getString(LAST_DAY, null) == dayKey) return Result.success()
                val earliest = data.optInt("earliestDays", 99)
                showNotification(count, earliest)
                prefs.edit().putString(LAST_DAY, dayKey).apply()
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun showNotification(count: Int, days: Int) {
        NotificationHelper.createNotificationChannel(applicationContext)
        val title = if (count == 1) "Un servicio está por vencer" else "$count servicios están por vencer"
        val body = when (days) {
            0 -> "Hay un servicio que vence hoy. Abrí Stream Flow Pro para ver el detalle."
            1 -> "Hay servicios que vencen mañana. Abrí Stream Flow Pro para ver el detalle."
            else -> "Hay servicios que vencen en los próximos 3 días. Abrí Stream Flow Pro para ver el detalle."
        }
        val openApp = PendingIntent.getActivity(
            applicationContext,
            7314,
            Intent(applicationContext, WebContainerActivity::class.java)
                .setData(android.net.Uri.parse("https://florenciaamor36-ship-it.github.io/stream/?tab=expirations"))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, NotificationHelper.CHANNEL_ID)
            .setContentIntent(openApp)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(EXPIRY_NOTIFICATION_ID, notification)
    }

    companion object {
        private const val PREFS = "streamflow_notification_session"
        private const val ENABLED = "expiry_notifications_enabled"
        private const val LAST_DAY = "last_expiry_notice_day"
        private const val OPT_IN_ACCOUNT_ID = "expiry_notification_account_id"
        private const val EXPIRY_NOTIFICATION_ID = 7314
        private const val WORK_NAME = "streamflow-expiry-notification-check"
        private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()

        fun isEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val currentAccount = SessionTokenStore.readAccountId(context)
            return prefs.getBoolean(ENABLED, false) && !currentAccount.isNullOrBlank() && prefs.getString(OPT_IN_ACCOUNT_ID, null) == currentAccount
        }

        fun enable(context: Context, accountId: String) {
            require(accountId.isNotBlank())
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(ENABLED, true)
                .putString(OPT_IN_ACCOUNT_ID, accountId)
                .apply()
            val request = androidx.work.PeriodicWorkRequestBuilder<ExpiryNotificationWorker>(6, TimeUnit.HOURS)
                .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
                .build()
            androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun disable(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(ENABLED, false).remove(OPT_IN_ACCOUNT_ID).apply()
            androidx.work.WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
