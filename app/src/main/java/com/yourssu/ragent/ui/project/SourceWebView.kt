package com.yourssu.ragent.ui.project

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.net.Uri
import android.util.Log
import android.util.Base64
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import java.io.File
import java.io.FileOutputStream
import org.json.JSONObject
import org.json.JSONTokener

private object SourceWebViewCache {
    private val views = mutableMapOf<String, WebView>()

    fun getOrCreate(key: String, factory: () -> WebView): WebView =
        views.getOrPut(key, factory)
}

private const val SOURCE_WEBVIEW_TAG = "SourceWebView"
private const val BRIDGE_ASSET = "source_webview.js"

data class SourceSelectionRequest(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val key: String
        get() = "${left}_${top}_${right}_${bottom}"
}

data class SourceSelectionResult(
    val selectedText: String?,
    val sourceType: String,
    val sourceUrl: String,
    val canonicalUrl: String?,
    val canonicalUrls: List<String>,
    val pageId: String?,
    val blockId: String?,
    val blockIds: List<String>,
    val filePath: String?,
    val startLine: Int?,
    val endLine: Int?,
    val headingPath: List<String>,
    val capturedImage: AiAttachment? = null
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SourceWebView(
    url: String,
    emptyMessage: String,
    onExit: () -> Unit,
    visible: Boolean = true,
    applyNotionScrollFix: Boolean = false,
    darkTheme: Boolean = false,
    stateKey: String = url,
    selectionRequest: SourceSelectionRequest? = null,
    onSelectionResolved: (SourceSelectionResult) -> Unit = {},
    onSelectionImageCaptured: (AiAttachment) -> Unit = {}
) {
    if (url.isBlank()) {
        Text(
            emptyMessage,
            modifier = Modifier
                .alpha(if (visible) 1f else 0f)
                .zIndex(if (visible) 1f else 0f)
        )
        return
    }

    var webView by remember { mutableStateOf<WebView?>(null) }
    var requestedUrl by remember { mutableStateOf<String?>(null) }
    var handledSelectionKey by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = visible) {
        if (webView?.canGoBack() == true) webView?.goBack()
    }

    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .alpha(if (visible) 1f else 0f)
            .zIndex(if (visible) 1f else 0f),
        factory = { context ->
            SourceWebViewCache.getOrCreate(stateKey) {
                WebView(context.applicationContext)
            }.apply {
                webView = this
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                updateForceDark(darkTheme)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, pageUrl: String?) {
                        super.onPageFinished(view, pageUrl)
                        view?.installSourceBridge()
                        view?.applyPageStyles(applyNotionScrollFix, darkTheme)
                    }
                }
                requestedUrl = url
                if (this.url == null) loadUrl(url)
            }
        },
        update = { view ->
            webView = view
            view.installSourceBridge()
            view.updateForceDark(darkTheme)
            view.applyPageStyles(applyNotionScrollFix, darkTheme)

            if (requestedUrl != url) {
                requestedUrl = url
                view.loadUrl(url)
            }

            selectionRequest?.let { request ->
                if (handledSelectionKey != request.key) {
                    handledSelectionKey = request.key
                    view.resolveSourceSelection(request, url, onSelectionResolved, onSelectionImageCaptured)
                }
            }
        }
    )
}

private fun WebView.installSourceBridge() {
    val script = context.assets
        .open(BRIDGE_ASSET)
        .bufferedReader()
        .use { it.readText() }
    evaluateJavascript(script, null)
}

private fun WebView.updateForceDark(darkTheme: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        settings.forceDark = if (darkTheme) {
            WebSettings.FORCE_DARK_ON
        } else {
            WebSettings.FORCE_DARK_OFF
        }
    }
}

private fun WebView.applyPageStyles(applyNotionScrollFix: Boolean, darkTheme: Boolean) {
    if (applyNotionScrollFix) {
        injectNotionScrollFix(darkTheme)
    } else {
        injectDarkTheme(darkTheme)
    }
}

private fun WebView.resolveSourceSelection(
    request: SourceSelectionRequest,
    sourceUrl: String,
    onResolved: (SourceSelectionResult) -> Unit,
    onImageCaptured: (AiAttachment) -> Unit
) {
    val sourceType = if (sourceUrl.contains("notion", ignoreCase = true)) {
        "notion"
    } else {
        "github"
    }
    val rootLocation = IntArray(2).also { rootView.getLocationOnScreen(it) }
    val webViewLocation = IntArray(2).also { getLocationOnScreen(it) }
    val localRequest = request.copy(
        left = request.left + rootLocation[0] - webViewLocation[0],
        top = request.top + rootLocation[1] - webViewLocation[1],
        right = request.right + rootLocation[0] - webViewLocation[0],
        bottom = request.bottom + rootLocation[1] - webViewLocation[1]
    )

    Log.d(SOURCE_WEBVIEW_TAG, "selection request source=$sourceType root=$request local=$localRequest")

    val rect = JSONObject().apply {
        put("left", localRequest.left)
        put("top", localRequest.top)
        put("right", localRequest.right)
        put("bottom", localRequest.bottom)
    }
    val config = JSONObject().apply {
        put("sourceType", sourceType)
        put("rect", rect)
    }

    evaluateJavascript("window.ragentResolveSelection($config)") { value ->
        Log.d(SOURCE_WEBVIEW_TAG, "selection JS raw=${value.take(600)}")
        runCatching {
            val decoded = JSONTokener(value).nextValue() as? String
                ?: error("Selection result was not a JSON string")
            val json = JSONObject(decoded)
            SourceSelectionResult(
                selectedText = json.optString("selectedText").takeIf { it.isNotBlank() },
                sourceType = json.optString("sourceType"),
                sourceUrl = json.optString("sourceUrl", sourceUrl),
                canonicalUrl = json.optString("canonicalUrl").takeIf { it.isNotBlank() },
                canonicalUrls = buildList {
                    val urls = json.optJSONArray("canonicalUrls") ?: return@buildList
                    for (index in 0 until urls.length()) {
                        urls.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                    }
                },
                pageId = json.optString("pageId").takeIf { it.isNotBlank() },
                blockId = json.optString("blockId").takeIf { it.isNotBlank() },
                blockIds = buildList {
                    val ids = json.optJSONArray("blockIds") ?: return@buildList
                    for (index in 0 until ids.length()) {
                        ids.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                    }
                },
                filePath = json.optString("filePath").takeIf { it.isNotBlank() },
                startLine = json.optInt("startLine").takeIf { it > 0 },
                endLine = json.optInt("endLine").takeIf { it > 0 },
                headingPath = buildList {
                    val headings = json.optJSONArray("headingPath") ?: return@buildList
                    for (index in 0 until headings.length()) {
                        add(headings.optString(index))
                    }
                },
                capturedImage = captureSelectionImage(request)
            )
        }.onSuccess { result ->
            Log.d(
                SOURCE_WEBVIEW_TAG,
                "selection resolved source=${result.sourceType} " +
                    "blockIds=${result.blockIds} file=${result.filePath} " +
                    "lines=${result.startLine}-${result.endLine} " +
                    "canonicalUrl=${result.canonicalUrl} " +
                    "textPreview=${result.selectedText?.replace("\n", " ")?.take(160)}"
            )
            result.capturedImage?.let(onImageCaptured)
            onResolved(result)
        }.onFailure { error ->
            Log.e(SOURCE_WEBVIEW_TAG, "failed to parse selection result: $value", error)
        }
    }
}

private fun WebView.captureSelectionImage(request: SourceSelectionRequest): AiAttachment? {
    if (width <= 0 || height <= 0) return null
    val location = IntArray(2).also { getLocationOnScreen(it) }
    val rootLocation = IntArray(2).also { rootView.getLocationOnScreen(it) }
    val left = (request.left + rootLocation[0] - location[0]).coerceIn(0f, (width - 1).coerceAtLeast(0).toFloat())
    val top = (request.top + rootLocation[1] - location[1]).coerceIn(0f, (height - 1).coerceAtLeast(0).toFloat())
    val right = (request.right + rootLocation[0] - location[0]).coerceIn(left + 1f, width.toFloat())
    val bottom = (request.bottom + rootLocation[1] - location[1]).coerceIn(top + 1f, height.toFloat())
    val full = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    // capturePicture renders the document from its origin. Translate it by the
    // current scroll offset so the crop coordinates refer to the visible page.
    val canvas = Canvas(full)
    canvas.translate(-scrollX.toFloat(), -scrollY.toFloat())
    capturePicture().draw(canvas)
    val crop = Bitmap.createBitmap(full, left.toInt(), top.toInt(), (right - left).toInt(), (bottom - top).toInt())
    full.recycle()
    val file = File(context.cacheDir, "ai_selection_${System.currentTimeMillis()}.jpg")
    FileOutputStream(file).use { crop.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    crop.recycle()
    return AiAttachment(
        uri = Uri.fromFile(file).toString(),
        mimeType = "image/jpeg",
        displayName = file.name,
        sizeBytes = file.length(),
        dataBase64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
    )
}

private fun WebView.injectNotionScrollFix(darkTheme: Boolean) {
    evaluateJavascript("window.ragentInjectNotionScrollFix($darkTheme);", null)
}

private fun WebView.injectDarkTheme(darkTheme: Boolean) {
    evaluateJavascript("window.ragentInjectDarkTheme($darkTheme);", null)
}
