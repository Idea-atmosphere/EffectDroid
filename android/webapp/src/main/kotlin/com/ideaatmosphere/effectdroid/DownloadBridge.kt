/*
 * EffectDroid — saves EffectCraft's blob downloads (File ▸ Save, render-queue outputs) to the
 * device's storage.
 *
 * Upstream `apps/effectcraft-web/src/files.rs` offers every saved/rendered file as
 * `<a download href=blob:…>.click()`. Android's WebView does not handle blob: downloads, so
 * INJECT_JS (installed at document start by MainActivity) intercepts those clicks and streams
 * the bytes here in base64 chunks — bounded memory, one open output stream per file.
 */
package com.ideaatmosphere.effectdroid

import android.app.Activity
import android.content.ContentValues
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

class DownloadBridge(private val activity: Activity) {

    private class Pending(
        val name: String,
        val uri: Uri?,      // MediaStore row (API 29+), null for the app-specific path below
        val file: File?,    // legacy path (API ≤ 28)
        val out: OutputStream,
    )

    @Volatile
    private var pending: Pending? = null

    /** First call of the injected hook: JS number → Java long is exact below 2^53. */
    @JavascriptInterface
    @Synchronized
    fun begin(name: String, total: Long) {
        finishPending(publish = false)
        val safe = sanitize(name)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safe)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType(safe))
                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DOWNLOADS}/$SUBDIR",
                    )
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = activity.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values,
                ) ?: throw IOException("MediaStore insert failed")
                val out = activity.contentResolver.openOutputStream(uri)
                    ?: throw IOException("cannot open $uri")
                pending = Pending(safe, uri, null, out)
            } else {
                // No storage permission: app-specific external dir (Android/data/<pkg>/files).
                val base = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: activity.filesDir
                val dir = File(base, SUBDIR).apply { mkdirs() }
                val file = uniqueFile(dir, safe)
                pending = Pending(safe, null, file, FileOutputStream(file))
            }
            postToast(activity.getString(R.string.download_started, safe))
        } catch (e: Exception) {
            abandon()
            postToast(activity.getString(R.string.download_failed, safe, e.message ?: "error"))
        }
    }

    @JavascriptInterface
    @Synchronized
    fun chunk(base64: String) {
        val p = pending ?: return
        try {
            p.out.write(Base64.decode(base64, Base64.DEFAULT))
        } catch (e: Exception) {
            abandon()
            postToast(activity.getString(R.string.download_failed, p.name, e.message ?: "error"))
        }
    }

    @JavascriptInterface
    @Synchronized
    fun end() {
        finishPending(publish = true)
    }

    @JavascriptInterface
    @Synchronized
    fun cancel() {
        finishPending(publish = false)
    }

    /** Flush/close; publish (MediaStore IS_PENDING 0 / toast) when the file is complete. */
    private fun finishPending(publish: Boolean) {
        val p = pending ?: return
        pending = null
        try {
            p.out.flush()
            p.out.close()
            if (publish) {
                val uri = p.uri
                if (uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                    activity.contentResolver.update(uri, values, null, null)
                    postToast(activity.getString(R.string.download_done, p.name))
                } else {
                    val file = p.file
                    if (file != null) {
                        MediaScannerConnection.scanFile(
                            activity,
                            arrayOf(file.absolutePath),
                            arrayOf(mimeType(p.name)),
                            null,
                        )
                        postToast(activity.getString(R.string.download_done, p.name))
                    }
                }
            } else if (uriOrFileExists(p)) {
                // Partial file: drop it.
                p.uri?.let { runCatching { activity.contentResolver.delete(it, null, null) } }
                p.file?.let { runCatching { it.delete() } }
            }
        } catch (e: Exception) {
            postToast(activity.getString(R.string.download_failed, p.name, e.message ?: "error"))
        }
    }

    private fun abandon() {
        val p = pending ?: return
        pending = null
        runCatching { p.out.close() }
        p.uri?.let { runCatching { activity.contentResolver.delete(it, null, null) } }
        p.file?.let { runCatching { it.delete() } }
    }

    private fun uriOrFileExists(p: Pending): Boolean = p.uri != null || p.file?.exists() == true

    private fun postToast(message: String) {
        activity.runOnUiThread {
            Toast.makeText(activity.applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun sanitize(name: String): String {
        val cleaned = File(name).name
            .replace(Regex("[\\u0000-\\u001f]"), "_")
            .trim()
        return cleaned.ifEmpty { "download.bin" }
    }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (File(dir, "$stem ($i)$ext").exists()) i++
        return File(dir, "$stem ($i)$ext")
    }

    companion object {
        private const val SUBDIR = "EffectCraft"

        /**
         * Installed at document start (MainActivity.onPageStarted). Patches
         * HTMLAnchorElement.prototype.click so blob: downloads stream to the native bridge.
         * Deliberately free of `${…}` — it is a Kotlin string and `$` would interpolate.
         */
        val INJECT_JS: String = """
            (function () {
              if (window.__effdroidDownloadHook) { return; }
              window.__effdroidDownloadHook = true;
              var original = HTMLAnchorElement.prototype.click;
              function toBase64(u8) {
                var s = '', i, step = 0x8000;
                for (i = 0; i < u8.length; i += step) {
                  s += String.fromCharCode.apply(
                    null, u8.subarray(i, Math.min(i + step, u8.length)));
                }
                return btoa(s);
              }
              function saveBlob(name, url) {
                fetch(url).then(function (r) { return r.arrayBuffer(); })
                  .then(function (buf) {
                    var u8 = new Uint8Array(buf), i, step = 2 * 1024 * 1024;
                    if (!window.EffectDroid || !window.EffectDroid.begin) {
                      return;
                    }
                    window.EffectDroid.begin(name, u8.length);
                    try {
                      for (i = 0; i < u8.length; i += step) {
                        window.EffectDroid.chunk(
                          toBase64(u8.subarray(i, Math.min(i + step, u8.length))));
                      }
                      window.EffectDroid.end();
                    } catch (e) {
                      window.EffectDroid.cancel();
                    }
                  })
                  .catch(function () { /* the bridge never opened a file */ });
              }
              HTMLAnchorElement.prototype.click = function () {
                var href = this.getAttribute('href') || '';
                var name = this.getAttribute('download') || '';
                try {
                  if (href.indexOf('blob:') === 0 && name &&
                      window.EffectDroid && window.EffectDroid.begin) {
                    saveBlob(name, href);
                    return;
                  }
                } catch (e) { /* fall through to the original click */ }
                return original.call(this);
              };
            })();
        """.trimIndent()

        /** Extensions EffectCraft saves/renders (files.rs `mime()` + the export formats). */
        fun mimeType(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
            "ecproj", "ecprojx", "json" -> "application/json"
            "zip" -> "application/zip"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "exr" -> "image/x-exr"
            "mp4", "m4v" -> "video/mp4"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "wav" -> "audio/wav"
            "mp3" -> "audio/mpeg"
            "ogg", "opus" -> "audio/ogg"
            "aac" -> "audio/aac"
            "flac" -> "audio/flac"
            "txt", "log" -> "text/plain"
            "svgz" -> "image/svg+xml"
            else -> "application/octet-stream"
        }
    }
}
