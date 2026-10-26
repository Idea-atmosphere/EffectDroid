/*
 * EffectDroid — Android shell for EffectCraft's official web build (the web → Android port).
 *
 * Serves the bundled static site (src/main/assets/web) through WebViewAssetLoader over
 * https://appassets.androidassets.net, which gives the page a secure context: WebGPU/WebGL2,
 * Web Workers, OPFS and Web Audio all behave as in production (see packaging/web/README.md
 * upstream — EffectCraft needs no COOP/COEP headers: no SharedArrayBuffer).
 */
package com.ideaatmosphere.effectdroid

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val assetLoader: WebViewAssetLoader by lazy {
        WebViewAssetLoader.Builder()
            .setDomain(ASSET_HOST)
            .addPathHandler("/", WebAssetsPathHandler(this))
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        configureWebView()

        // Back navigates the (single-page) app history, then leaves — legacy back is fine
        // because predictive back is not enabled for this app.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            // ?nosw: the service worker only matters for repeat visits on the web; the assets
            // are already on the device and OPFS/IndexedDB persistence does the rest.
            webView.loadUrl("$START_URL?nosw")
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            hide(WindowInsetsCompat.Type.displayCutout())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun configureWebView() {
        webView.setBackgroundColor(0xFF1B1C1F.toInt())
        webView.isHorizontalScrollBarEnabled = false
        webView.isVerticalScrollBarEnabled = false
        // Long-press must not pop WebView's "save image / select" menu over the egui canvas.
        webView.setOnLongClickListener { true }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            // The app plays audio previews without a tap on the play button itself.
            mediaPlaybackRequiresUserGesture = false
            // The bundled site loads only through the asset handler; deny raw file/content URLs.
            allowFileAccess = false
            allowContentAccess = false
            // Pinch-zoom belongs to egui on the canvas, not to WebView's built-in zoom.
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            // Ignore the system font scale for the DOM shell (egui has its own scaling).
            textZoom = 100
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
        }

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        // Chunked writer for the page's blob downloads (Save project, render outputs).
        webView.addJavascriptInterface(DownloadBridge(this), JS_INTERFACE)

        webView.webChromeClient = object : WebChromeClient() {
            // File ▸ Open / Import and every <input type="file"> the page creates.
            override fun onShowFileChooser(
                view: WebView?,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams,
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                val pick = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    // Only pass MIME types ("<type>/<subtype>" or "<type>/*"); extension-style
                    // accept values (.ecproj) are not understood by document pickers.
                    val mimes = params.acceptTypes
                        .orEmpty()
                        .filter { it.contains('/') }
                        .distinct()
                        .toTypedArray()
                    if (mimes.isNotEmpty()) {
                        type = mimes.first()
                        if (mimes.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, mimes)
                    }
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                startActivityForResult(
                    Intent.createChooser(pick, getString(R.string.file_chooser_title)),
                    FILE_CHOOSER_REQUEST,
                )
                return true
            }

            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                consoleMessage?.let {
                    Log.d(LOG_TAG, "${it.message()} (${it.sourceId()}:${it.lineNumber()})")
                }
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?,
            ): WebResourceResponse? = request?.let { assetLoader.shouldInterceptRequest(it.url) }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // Before any user action: hook blob downloads to the native bridge.
                view?.evaluateJavascript(DownloadBridge.INJECT_JS, null)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean {
                val url = request?.url ?: return true
                val scheme = url.scheme?.lowercase(Locale.ROOT)
                return when {
                    scheme == "https" || scheme == "http" -> {
                        if (url.host.equals(ASSET_HOST, ignoreCase = true)) {
                            false // same-site asset load: keep it in the WebView
                        } else {
                            openExternally(url) // Help / Discord / getartcraft.com links
                            true
                        }
                    }
                    scheme == "about" -> false
                    else -> true // blob:/data:/intent:/… never navigate the shell
                }
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?,
            ): Boolean {
                // target="_blank" links (eframe's `links` feature): capture the request and
                // hand the URL to the system browser instead of a hidden WebView.
                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                val sneaky = WebView(view?.context ?: return false)
                sneaky.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        v: WebView?,
                        r: WebResourceRequest?,
                    ): Boolean {
                        r?.url?.let(::openExternally)
                        return true
                    }
                }
                transport.webView = sneaky
                resultMsg.sendToTarget()
                return true
            }
        }
    }

    private fun openExternally(url: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.external_link_failed, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FILE_CHOOSER_REQUEST) {
            val callback = filePathCallback
            filePathCallback = null
            if (callback != null) {
                callback.onReceiveValue(decodedFileResult(resultCode, data))
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    /** Turn the picker result into the Uri[] the page expects (or null when cancelled). */
    private fun decodedFileResult(resultCode: Int, data: Intent?): Array<Uri>? {
        if (resultCode != Activity.RESULT_OK || data == null) return null
        val uris = mutableListOf<Uri>()
        val clip = data.clipData
        if (clip != null && clip.itemCount > 0) {
            for (i in 0 until clip.itemCount) {
                clip.getItemAt(i).uri?.let(uris::add)
            }
        }
        data.data?.let { if (it !in uris) uris.add(it) }
        if (uris.isEmpty()) return null
        // Keep read access alive while the page reads the streams.
        for (uri in uris) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (e: SecurityException) {
                // Not persistable: the temporary grant still covers this callback.
                Log.d(LOG_TAG, "persistable grant unavailable for $uri")
            }
        }
        return uris.toTypedArray()
    }

    companion object {
        private const val LOG_TAG = "EffectDroid"
        private const val ASSET_HOST = "appassets.androidassets.net"
        private const val JS_INTERFACE = "EffectDroid"
        private const val FILE_CHOOSER_REQUEST = 0x10EC

        /** The bundled EffectCraft site (built by `cargo xtask web` upstream). */
        private const val START_URL = "https://$ASSET_HOST/web/index.html"
    }
}
