/*
 * EffectDroid — serves the bundled EffectCraft site from `assets/web/`.
 *
 * A custom PathHandler (instead of the stock AssetsPathHandler) so `.wasm` is always sent as
 * `application/wasm`: WebView refuses WebAssembly streaming compilation under any other type,
 * and not every device's MimeTypeMap knows the extension (upstream packaging/web/README.md).
 */
package com.ideaatmosphere.effectdroid

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import java.io.FileNotFoundException
import java.io.InputStream

class WebAssetsPathHandler(private val context: Context) : WebViewAssetLoader.PathHandler {

    override fun shouldInterceptRequest(url: Uri): WebResourceResponse? {
        var path = (url.path ?: return null).trimStart('/')
        if (path.endsWith("/")) path = path.removeSuffix("/")
        if (path.contains("..") || path.contains("\\")) return null
        // "/" and "/web" are entry points; everything else maps under assets/web/.
        val assetPath = if (path.isEmpty() || path == ASSET_ROOT) {
            "$ASSET_ROOT/index.html"
        } else {
            "$ASSET_ROOT/$path"
        }

        val stream: InputStream = try {
            context.assets.open(assetPath)
        } catch (e: FileNotFoundException) {
            // Directories and unknown files: no response, WebView shows its own error.
            return null
        }
        val mime = mimeFor(path)
        val charset = if (mime.startsWith("text/") || mime.endsWith("javascript") || mime.endsWith("json")) "UTF-8" else null
        return WebResourceResponse(mime, charset, stream)
    }

    companion object {
        /** Web files live in `assets/web/` so `https://<host>/web/…` maps to them. */
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
