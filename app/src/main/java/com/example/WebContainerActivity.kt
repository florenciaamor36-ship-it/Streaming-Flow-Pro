package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.core.content.ContextCompat
import com.example.notifications.ExpiryNotificationWorker
import com.example.notifications.SessionTokenStore
import com.example.util.NotificationHelper
import org.json.JSONTokener
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import java.net.URI
import java.util.Locale

/** A thin, same-origin container. All authentication and application data remain in the web app. */
class WebContainerActivity : ComponentActivity() {
    private var webView: WebView? = null
    private var uploadCallback: ValueCallback<Array<Uri>>? = null
    private var baseOrigin: URI? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val captureSessionRunnable = object : Runnable {
        override fun run() {
            captureBackgroundSessionToken()
            mainHandler.postDelayed(this, SESSION_CAPTURE_INTERVAL_MS)
        }
    }
    private val notificationsPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            NotificationHelper.createNotificationChannel(this)
            ExpiryNotificationWorker.enable(this)
            dispatchNativeNotificationState(true, "Avisos de vencimientos activados en Android.")
        } else {
            dispatchNativeNotificationState(false, "No se concedió permiso para mostrar notificaciones.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val configured = BuildConfig.WEB_APP_URL.trim()
        val origin = parseHttpsOrigin(configured)
        if (origin == null) {
            showMessage(
                "La versión web no está configurada.\n\n" +
                    "Definí WEB_APP_URL con la URL HTTPS de la aplicación web y volvé a compilar. " +
                    "No se cargó ningún sitio."
            )
            return
        }
        baseOrigin = origin
        val view = WebView(this)
        webView = view
        view.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        view.settings.apply {
            javaScriptEnabled = true // Firebase web SDK and its UI require JavaScript.
            domStorageEnabled = true // Firebase web Auth persistence.
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = false
            allowContentAccess = true // Required for user-selected upload URIs.
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
        }
        installOriginRestrictedNotificationBridge(view)
        CookieManager.getInstance().setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) view.settings.safeBrowsingEnabled = true
        // The notification bridge exposes only toggles; it never returns credentials or session data.
        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView, url: String) {
                super.onPageFinished(v, url)
                if (isAppPage(url)) {
                    captureBackgroundSessionToken()
                    installWebNotificationFacade()
                    mainHandler.removeCallbacks(captureSessionRunnable)
                    mainHandler.postDelayed(captureSessionRunnable, SESSION_CAPTURE_INTERVAL_MS)
                }
            }

            override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean =
                routeNavigation(request.url.toString())

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(v: WebView, url: String): Boolean = routeNavigation(url)

            override fun onReceivedError(v: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) showLoadError()
            }
        }
        view.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@WebContainerActivity.uploadCallback?.onReceiveValue(null)
                this@WebContainerActivity.uploadCallback = filePathCallback ?: return false
                return try {
                    val intent = fileChooserParams?.createIntent()
                        ?: Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                        }
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, REQUEST_UPLOAD)
                    true
                } catch (_: Exception) {
                    this@WebContainerActivity.uploadCallback?.onReceiveValue(null)
                    this@WebContainerActivity.uploadCallback = null
                    false
                }
            }
        }
        setContentView(view)
        if (savedInstanceState == null) view.loadUrl(configured)
        else view.restoreState(savedInstanceState) ?: view.loadUrl(configured)
    }

    private fun routeNavigation(target: String): Boolean {
        val trusted = baseOrigin?.let { isAllowedWebAppUrl(target, it) } == true
        if (trusted) return false
        val uri = try { Uri.parse(target) } catch (_: Exception) { return true }
        if (uri.scheme.equals("https", true) || uri.scheme.equals("mailto", true) || uri.scheme.equals("tel", true)) {
            try { startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Exception) { /* Stay fail-closed. */ }
        }
        // Block http, file, content, javascript, and other non-web schemes.
        return true
    }

    private fun captureBackgroundSessionToken() {
        val view = webView ?: return
        if (baseOrigin?.let { isAllowedWebAppUrl(view.url.orEmpty(), it) } != true) return
        view.evaluateJavascript("(function(){try{return localStorage.getItem('stream_flow_session')}catch(e){return null}})()") { encoded ->
            val token = try { JSONTokener(encoded ?: "null").nextValue() as? String } catch (_: Exception) { null }
            if (token.isNullOrBlank()) {
                SessionTokenStore.clear(this)
            } else {
                try {
                    SessionTokenStore.save(this, token)
                } catch (_: Exception) { SessionTokenStore.clear(this) }
            }
        }
    }

    private fun requestExpiryNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            NotificationHelper.createNotificationChannel(this)
            ExpiryNotificationWorker.enable(this)
            dispatchNativeNotificationState(true, "Avisos de vencimientos activados en Android.")
        }
    }

    private fun dispatchNativeNotificationState(enabled: Boolean, message: String) {
        val safeMessage = org.json.JSONObject.quote(message)
        webView?.post { webView?.evaluateJavascript("window.dispatchEvent(new CustomEvent('streamflow:native-notification-state',{detail:{enabled:$enabled,message:$safeMessage}}))", null) }
    }

    private fun installOriginRestrictedNotificationBridge(view: WebView) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
        WebViewCompat.addWebMessageListener(
            view,
            "StreamFlowAndroidNative",
            setOf("https://florenciaamor36-ship-it.github.io"),
            object : WebViewCompat.WebMessageListener {
                override fun onPostMessage(webView: WebView, message: WebMessageCompat, sourceOrigin: Uri, isMainFrame: Boolean, replyProxy: JavaScriptReplyProxy) {
                    if (!isMainFrame || !isAppPage(webView.url.orEmpty()) || !sourceOrigin.host.equals(baseOrigin?.host, ignoreCase = true)) return
                    runOnUiThread {
                        when (message.data) {
                            "enable-expiry-notifications" -> requestExpiryNotifications()
                            "disable-expiry-notifications" -> {
                                ExpiryNotificationWorker.disable(this@WebContainerActivity)
                                SessionTokenStore.clear(this@WebContainerActivity)
                                dispatchNativeNotificationState(false, "Avisos de vencimientos desactivados en Android.")
                            }
                        }
                    }
                }
            }
        )
    }

    private fun installWebNotificationFacade() {
        val enabled = ExpiryNotificationWorker.isEnabled(this)
        val script = "(function(){window.StreamFlowAndroid={isExpiryNotificationsEnabled:function(){return localStorage.getItem('streamflow_android_expiry_notifications')==='true'},requestExpiryNotifications:function(){window.StreamFlowAndroidNative.postMessage('enable-expiry-notifications')},disableExpiryNotifications:function(){window.StreamFlowAndroidNative.postMessage('disable-expiry-notifications')}};localStorage.setItem('streamflow_android_expiry_notifications','$enabled');window.dispatchEvent(new CustomEvent('streamflow:native-ready'));})();"
        webView?.evaluateJavascript(script, null)
    }

    private fun isAppPage(url: String): Boolean = try {
        val configuredPath = URI(BuildConfig.WEB_APP_URL.trim()).path.orEmpty().ifBlank { "/" }
        val target = URI(url)
        baseOrigin?.let { isAllowedWebAppUrl(url, it) } == true && target.path.startsWith(configuredPath)
    } catch (_: Exception) { false }

    private fun showLoadError() {
        showMessage("No se pudo cargar la aplicación web. Revisá tu conexión e intentá de nuevo.")
    }

    private fun showMessage(message: String) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            gravity = android.view.Gravity.CENTER
        }
        val label = TextView(this).apply { text = message; textSize = 18f; gravity = android.view.Gravity.CENTER }
        layout.addView(label, LinearLayout.LayoutParams(-1, 0, 1f))
        val retry = Button(this).apply {
            text = "Reintentar"
            setOnClickListener {
                val url = BuildConfig.WEB_APP_URL.trim()
                if (parseHttpsOrigin(url) != null) {
                    baseOrigin = parseHttpsOrigin(url)
                    recreate()
                }
            }
        }
        layout.addView(retry, LinearLayout.LayoutParams(-1, -2))
        setContentView(layout)
    }

    override fun onResume() {
        super.onResume()
        if (webView != null) {
            captureBackgroundSessionToken()
            mainHandler.removeCallbacks(captureSessionRunnable)
            mainHandler.postDelayed(captureSessionRunnable, SESSION_CAPTURE_INTERVAL_MS)
        }
    }

    override fun onPause() {
        mainHandler.removeCallbacks(captureSessionRunnable)
        super.onPause()
    }

    @Deprecated("Required by WebChromeClient file chooser on older Android versions")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_UPLOAD) return
        val callback = uploadCallback ?: return
        uploadCallback = null
        val results = if (resultCode == Activity.RESULT_OK && data != null) {
            WebChromeClient.FileChooserParams.parseResult(resultCode, data)
        } else null
        callback.onReceiveValue(results)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        val view = webView
        if (view?.canGoBack() == true) view.goBack() else super.onBackPressed()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView?.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(captureSessionRunnable)
        uploadCallback?.onReceiveValue(null)
        uploadCallback = null
        webView?.apply { removeJavascriptInterface("StreamFlowAndroid"); stopLoading(); webChromeClient = null; webViewClient = WebViewClient(); destroy() }
        webView = null
        super.onDestroy()
    }

    private companion object { const val REQUEST_UPLOAD = 4802; const val SESSION_CAPTURE_INTERVAL_MS = 15_000L }
}

/** Restrict in-WebView navigation to the configured HTTPS origin (host and effective port). */
internal fun parseHttpsOrigin(url: String): URI? = try {
    val uri = URI(url)
    if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank() || uri.userInfo != null) null
    else URI("https", null, uri.host.lowercase(Locale.ROOT), if (uri.port == -1) 443 else uri.port, null, null, null)
} catch (_: Exception) { null }

internal fun isAllowedWebAppUrl(target: String, configuredOrigin: URI): Boolean = try {
    val uri = URI(target)
    uri.scheme.equals("https", ignoreCase = true) && uri.userInfo == null &&
        uri.host?.equals(configuredOrigin.host, ignoreCase = true) == true &&
        (if (uri.port == -1) 443 else uri.port) == configuredOrigin.port
} catch (_: Exception) { false }
