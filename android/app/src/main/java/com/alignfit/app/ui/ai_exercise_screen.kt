package com.alignfit.app.ui

import android.content.Intent
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.alignfit.app.network.RetrofitClient

// ─── AI exercise evaluation (WebView) ─────────────────────────────────────────
// Hosts the backend's ${BASE_URL}demo page in a WebView so the on-device demo can
// upload an exercise video and view the ML movement-quality result. JavaScript is
// required (the demo page posts the video with fetch + renders the JSON), and an
// onShowFileChooser handler is required so the page's <input type="file"> can open
// the system video picker — without it the upload button does nothing.

@Composable
fun AiExerciseWebViewScreen(onBack: () -> Unit) {
    // The WebView instance, kept so the top bar / system back can navigate inside it.
    var webView by remember { mutableStateOf<WebView?>(null) }
    // Pending callback handed to us by onShowFileChooser until the picker returns.
    var pendingFileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = pendingFileCallback
        pendingFileCallback = null
        // Always answer the callback (empty array on cancel) or the WebView's file
        // input stays stuck and further picks are ignored.
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        callback?.onReceiveValue(uris ?: emptyArray())
    }

    fun handleBack() {
        val wv = webView
        if (wv != null && wv.canGoBack()) wv.goBack() else onBack()
    }

    BackHandler { handleBack() }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(title = "재활운동 평가", onBack = { handleBack() })
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    webViewClient = WebViewClient()
                    webChromeClient = object : WebChromeClient() {
                        override fun onShowFileChooser(
                            view: WebView?,
                            filePathCallback: ValueCallback<Array<Uri>>?,
                            fileChooserParams: FileChooserParams?
                        ): Boolean {
                            // Replace any stale callback, then launch the picker.
                            pendingFileCallback?.onReceiveValue(null)
                            pendingFileCallback = filePathCallback
                            val intent = fileChooserParams?.createIntent()
                                ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                    type = "video/*"
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                }
                            return try {
                                fileChooserLauncher.launch(intent)
                                true
                            } catch (e: Exception) {
                                pendingFileCallback = null
                                false
                            }
                        }
                    }
                    webView = this
                    loadUrl(RetrofitClient.BASE_URL + "demo")
                }
            }
        )
    }
}
