package com.yourssu.ragent.ui.project

import android.annotation.SuppressLint
import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView

private object SourceWebViewCache {
    private val views = mutableMapOf<String, WebView>()
    fun getOrCreate(key: String, factory: () -> WebView): WebView = views.getOrPut(key, factory)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SourceWebView(
    url: String,
    emptyMessage: String,
    onExit: () -> Unit,
    visible: Boolean = true,
    applyNotionScrollFix: Boolean = false,
    darkTheme: Boolean = false,
    stateKey: String = url
) {
    if (url.isBlank()) {
        Text(emptyMessage, modifier = Modifier.alpha(if (visible) 1f else 0f).zIndex(if (visible) 1f else 0f))
        return
    }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var requestedUrl by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = visible) {
        if (webView?.canGoBack() == true) webView?.goBack()
    }
    AndroidView(
        modifier = Modifier.fillMaxSize().alpha(if (visible) 1f else 0f).zIndex(if (visible) 1f else 0f),
        factory = { context ->
            SourceWebViewCache.getOrCreate(stateKey) { WebView(context.applicationContext) }.apply {
                webView = this
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    settings.forceDark = if (darkTheme) WebSettings.FORCE_DARK_ON else WebSettings.FORCE_DARK_OFF
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, pageUrl: String?) {
                        super.onPageFinished(view, pageUrl)
                        if (applyNotionScrollFix) view?.injectNotionScrollFix(darkTheme)
                        else view?.injectDarkTheme(darkTheme)
                    }
                }
                requestedUrl = url
                if (this.url == null) loadUrl(url)
            }
        },
        update = { webView ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                webView.settings.forceDark = if (darkTheme) WebSettings.FORCE_DARK_ON else WebSettings.FORCE_DARK_OFF
            }
            if (applyNotionScrollFix) webView.injectNotionScrollFix(darkTheme)
            else webView.injectDarkTheme(darkTheme)
            if (requestedUrl != url) {
                requestedUrl = url
                webView.loadUrl(url)
            }
        }
    )
}

private fun WebView.injectNotionScrollFix(darkTheme: Boolean) {
    evaluateJavascript(
        """
        (function() {
          var style = document.getElementById('ragent-notion-scroll-fix');
          if (!style) {
            style = document.createElement('style');
            style.id = 'ragent-notion-scroll-fix';
            document.head.appendChild(style);
          }
          style.textContent = 'html, body, #notion-app, #notion-app > div, .notion-frame, .notion-scroller { overflow-y: auto !important; height: auto !important; } :root { color-scheme: ${if (darkTheme) "dark" else "light"}; } ${if (darkTheme) "html, body, #notion-app, #notion-app > div { background: #191919 !important; color: #e6e6e6 !important; } #notion-app * { border-color: #3a3a3a !important; }" else "html, body, #notion-app, #notion-app > div { background: #ffffff !important; color: #191919 !important; }"}';
        })();
        """.trimIndent(),
        null
    )
}

private fun WebView.injectDarkTheme(darkTheme: Boolean) {
    evaluateJavascript(
        """
        (function() {
          var style = document.getElementById('ragent-dark-theme');
          if (!style) {
            style = document.createElement('style');
            style.id = 'ragent-dark-theme';
            document.head.appendChild(style);
          }
          style.textContent = '${if (darkTheme) "html, body { background: #0d1117 !important; color: #e6edf3 !important; } body * { border-color: #30363d !important; } a { color: #58a6ff !important; }" else "html, body { background: #ffffff !important; color: #24292f !important; }"}';
        })();
        """.trimIndent(),
        null
    )
}
