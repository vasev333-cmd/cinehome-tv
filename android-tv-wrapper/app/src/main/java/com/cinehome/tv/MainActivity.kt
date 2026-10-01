package com.cinehome.tv

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.KeyEvent
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.FragmentActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MainActivity : FragmentActivity() {

    private lateinit var webView: WebView

    // Change this to your deployed URL (e.g. Vercel / GitHub Pages) so AI changes update live!
    private val appUrl = "https://your-cinehome-app.vercel.app" 

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set fullscreen flags for TV
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        webView = WebView(this)
        setContentView(webView)

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
            cacheMode = WebSettings.LOAD_NO_CACHE // Always fetch latest updates from AI!
        }

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.webViewClient = WebViewClient()

        // JavaScript Bridge: Exposes USB file scanning to Web UI
        webView.addJavascriptInterface(WebAppBridge(), "AndroidBridge")

        // Loads embedded CineHome app directly offline (no PC required!)
        webView.loadUrl("file:///android_asset/index.html")
    }

    inner class WebAppBridge {

        @JavascriptInterface
        fun getUsbMoviesJson(): String {
            val jsonArray = JSONArray()
            val storageDir = File("/storage")

            if (storageDir.exists() && storageDir.isDirectory) {
                storageDir.listFiles()?.forEach { volume ->
                    // Scan mounted external USB drives (excluding emulated internal storage)
                    if (volume.isDirectory && !volume.name.equals("emulated", ignoreCase = true)) {
                        scanFolderForVideos(volume, jsonArray)
                    }
                }
            }

            // Also check standard Movies directory on device
            val defaultMovies = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            if (defaultMovies != null && defaultMovies.exists()) {
                scanFolderForVideos(defaultMovies, jsonArray)
            }

            return jsonArray.toString()
        }

        private fun scanFolderForVideos(dir: File, result: JSONArray, depth: Int = 0) {
            if (depth > 3) return // Prevent deep recursion
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory && !file.name.startsWith(".")) {
                    scanFolderForVideos(file, result, depth + 1)
                } else if (file.isFile) {
                    val name = file.name.lowercase()
                    if (name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") || name.endsWith(".mov")) {
                        val item = JSONObject().apply {
                            put("id", file.absolutePath.hashCode().toString())
                            put("title", cleanMovieTitle(file.nameWithoutExtension))
                            put("fileName", file.name)
                            put("path", file.absolutePath)
                            put("sizeMb", (file.length() / (1024 * 1024)).toInt())
                        }
                        result.put(item)
                    }
                }
            }
        }

        private fun cleanMovieTitle(raw: String): String {
            return raw.replace(Regex("(?i)(\\.1080p|\\.2160p|\\.uhd|\\.remux|\\.bluray|\\.web-dl|\\.x264|\\.x265|\\.dvdrip).*"), "")
                      .replace(".", " ")
                      .trim()
        }

        @JavascriptInterface
        fun playNative(filePath: String) {
            val intent = Intent(this@MainActivity, PlayerActivity::class.java).apply {
                putExtra("VIDEO_PATH", filePath)
            }
            startActivity(intent)
        }
    }

    // Forward TV Remote keys directly to WebView
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> { webView.dispatchKeyEvent(event); return true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { webView.dispatchKeyEvent(event); return true }
                KeyEvent.KEYCODE_DPAD_LEFT -> { webView.dispatchKeyEvent(event); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { webView.dispatchKeyEvent(event); return true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { webView.dispatchKeyEvent(event); return true }
                KeyEvent.KEYCODE_BACK -> {
                    // Send escape/back to web app first
                    webView.evaluateJavascript("window.dispatchEvent(new KeyboardEvent('keydown', {'key': 'Escape'}));", null)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
