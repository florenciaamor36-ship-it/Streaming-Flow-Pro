package com.example

import android.app.Activity
import android.content.Intent
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
        CookieManager.getInstance().setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) view.settings.safeBrowsingEnabled = true
        // No addJavascriptInterface: page scripts cannot invoke native methods.
        view.webViewClient = object : WebViewClient() {
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
        uploadCallback?.onReceiveValue(null)
        uploadCallback = null
        webView?.apply { stopLoading(); webChromeClient = null; webViewClient = WebViewClient(); destroy() }
        webView = null
        super.onDestroy()
    }

    private companion object { const val REQUEST_UPLOAD = 4802 }
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
