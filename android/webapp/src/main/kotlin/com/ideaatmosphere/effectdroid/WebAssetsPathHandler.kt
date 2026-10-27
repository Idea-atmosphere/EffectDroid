/*
 * EffectDroid — serves the bundled EffectCraft site from `assets/web/`.
 *
 * A custom WebViewAssetLoader.PathHandler (instead of the stock AssetsPathHandler) so `.wasm`
 * is always sent as `application/wasm`: WebView refuses WebAssembly streaming compilation under
 * any other type, and not every device's MimeTypeMap knows the extension (upstream
 * packaging/web/README.md).
 *
 * `handle` receives the path AFTER the loader's domain+prefix match — "web/index.html" for
 * https://appassets.androidassets.net/web/index.html (no scheme, host or leading slash).
 */
package com.ideaatmosphere.effectdroid

import android.content.Context
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import java.io.FileNotFoundException
import java.io.InputStream

class WebAssetsPathHandler(private val context: Context) : WebViewAssetLoader.PathHandler {

    override fun handle(path: String): WebResourceResponse? {
        var p = path.removePrefix("/")
        if (p.endsWith("/")) p = p.removeSuffix("/")
        if (p.contains("..")) return notFound()
        // "" and "web" are entry points; everything else maps under assets/web/.
        val assetPath = if (p.isEmpty() || p == ASSET_ROOT) {
            "$ASSET_ROOT/index.html"
        } else {
            "$ASSET_ROOT/$p"
        }

        val stream: InputStream = try {
            context.assets.open(assetPath)
        } catch (e: FileNotFoundException) {
            // Documented "not handled" 404 (null mime/encoding/data): the fake asset host must
            // not fall through to the network.
            return notFound()
        }
        val mime = mimeFor(assetPath)
        val charset = if (
            mime.startsWith("text/") || mime.endsWith("javascript") || mime.endsWith("json")
        ) {
            "UTF-8"
        } else {
            null
        }
        return WebResourceResponse(mime, charset, stream)
    }

    /** Web files live in `assets/web/` so `https://<host>/web/…` maps to them. */
    private fun notFound(): WebResourceResponse = WebResourceResponse(null, null, null)

    companion object {
        private const val ASSET_ROOT = "web"

        fun mimeFor(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
            "html", "htm" -> "text/html"
            "js", "mjs" -> "text/javascript"
            "wasm" -> "application/wasm"
            "css" -> "text/css"
            "json" -> "application/json"
            "webmanifest" -> "application/manifest+json"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "ico" -> "image/x-icon"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "txt", "md" -> "text/plain"
            else -> "application/octet-stream"
        }
    }
}
