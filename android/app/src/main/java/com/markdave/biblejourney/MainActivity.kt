package com.markdave.biblejourney

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.webkit.WebViewAssetLoader
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.UUID

class MainActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var root: FrameLayout
    private var barColor = Color.parseColor("#EDEEE9")

    // In-app NIV viewer (bible.com), shown on top of the app so a recording keeps running
    private var nivPanel: LinearLayout? = null
    private var nivWeb: WebView? = null
    private var nivTitle: TextView? = null
    private var nivMark: TextView? = null
    private var nivBack: TextView? = null
    private var nivBar: LinearLayout? = null
    private var nivProgress: ProgressBar? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingMedia: PermissionRequest? = null
    private val streams = HashMap<String, Pair<OutputStream, String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        root = FrameLayout(this)
        root.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = false
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.overScrollMode = View.OVER_SCROLL_NEVER

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.host == APP_HOST) return false
                openExternal(request.url.toString())
                return true
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread { handleMediaRequest(request) }
            }

            override fun onPermissionRequestCanceled(request: PermissionRequest) {
                if (pendingMedia == request) pendingMedia = null
            }

            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                val pick = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                return try {
                    @Suppress("DEPRECATION")
                    startActivityForResult(Intent.createChooser(pick, "Choose a backup file"), REQ_FILE)
                    true
                } catch (e: Exception) {
                    fileCallback = null
                    false
                }
            }
        }

        web.addJavascriptInterface(Bridge(), "AndroidApp")
        web.loadUrl("https://$APP_HOST/assets/index.html")

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            Reminder.isOn(this)
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFY)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQ_FILE) {
            val uri = if (resultCode == RESULT_OK) data?.data else null
            fileCallback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
            fileCallback = null
            return
        }
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun isLight(c: Int) = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000 > 140

    private fun buildNivPanel() {
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(4), 0)
        }
        fun barButton(text: String) = TextView(this).apply {
            this.text = text
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(14), 0, dp(14), 0)
            minWidth = dp(48)
            isClickable = true
            isFocusable = true
        }
        val back = barButton("‹ Back")
        val title = TextView(this).apply {
            textSize = 17f
            setTypeface(Typeface.SERIF, Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val mark = barButton("Mark as read")
        bar.addView(back, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(56)))
        bar.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(mark, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(56)))

        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
        }

        val w = WebView(this)
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                val host = u.host ?: ""
                val web = u.scheme == "https" || u.scheme == "http"
                if (web && (host.endsWith("bible.com") || host.endsWith("youversion.com"))) return false
                if (web) openExternal(u.toString())
                return true   // ignore "open in app" intent links
            }
        }
        w.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, p: Int) {
                progress.progress = p
                progress.visibility = if (p >= 100) View.GONE else View.VISIBLE
            }
        }

        panel.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
        panel.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)))
        panel.addView(w, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(panel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        back.setOnClickListener { hideNiv(false) }
        mark.setOnClickListener { hideNiv(true) }

        nivPanel = panel; nivWeb = w; nivTitle = title; nivMark = mark; nivBack = back; nivBar = bar; nivProgress = progress
    }

    private fun styleNiv() {
        val light = isLight(barColor)
        val ink = if (light) Color.parseColor("#1E2333") else Color.parseColor("#ECE8DD")
        val gilt = if (light) Color.parseColor("#A87A1F") else Color.parseColor("#D8B159")
        nivPanel?.setBackgroundColor(barColor)
        nivBar?.setBackgroundColor(barColor)
        nivTitle?.setTextColor(ink)
        nivBack?.setTextColor(gilt)
        nivMark?.setTextColor(gilt)
    }

    private fun showNiv(url: String, title: String, canMark: Boolean) {
        if (nivPanel == null) buildNivPanel()
        styleNiv()
        nivTitle?.text = title
        nivMark?.visibility = if (canMark) View.VISIBLE else View.INVISIBLE
        nivProgress?.progress = 0
        nivProgress?.visibility = View.VISIBLE
        nivWeb?.loadUrl(url)
        nivPanel?.visibility = View.VISIBLE
    }

    private fun hideNiv(marked: Boolean) {
        val p = nivPanel ?: return
        if (p.visibility != View.VISIBLE) return
        p.visibility = View.GONE
        nivWeb?.loadUrl("about:blank")
        web.evaluateJavascript(if (marked) "window.onNivMark && window.onNivMark()" else "window.onNivClosed && window.onNivClosed()", null)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (nivPanel?.visibility == View.VISIBLE) {
            val w = nivWeb
            if (w != null && w.canGoBack() && w.url?.contains("bible.com/bible/") != true) w.goBack() else hideNiv(false)
            return
        }
        web.evaluateJavascript("(window.onAndroidBack && window.onAndroidBack()) ? 'yes' : 'no'") { result ->
            if (result == null || !result.contains("yes")) {
                @Suppress("DEPRECATION")
                super.onBackPressed()
            }
        }
    }

    private fun openExternal(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, "No app can open that link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun granted(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun handleMediaRequest(request: PermissionRequest) {
        val need = ArrayList<String>()
        for (r in request.resources) {
            if (r == PermissionRequest.RESOURCE_AUDIO_CAPTURE && !granted(Manifest.permission.RECORD_AUDIO)) need.add(Manifest.permission.RECORD_AUDIO)
            if (r == PermissionRequest.RESOURCE_VIDEO_CAPTURE && !granted(Manifest.permission.CAMERA)) need.add(Manifest.permission.CAMERA)
        }
        if (need.isEmpty()) {
            grantMedia(request)
        } else {
            pendingMedia?.deny()
            pendingMedia = request
            requestPermissions(need.toTypedArray(), REQ_MEDIA)
        }
    }

    private fun grantMedia(request: PermissionRequest) {
        val ok = request.resources.filter {
            (it == PermissionRequest.RESOURCE_AUDIO_CAPTURE && granted(Manifest.permission.RECORD_AUDIO)) ||
                (it == PermissionRequest.RESOURCE_VIDEO_CAPTURE && granted(Manifest.permission.CAMERA))
        }
        if (ok.isNotEmpty() && ok.size == request.resources.size) request.grant(ok.toTypedArray()) else request.deny()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_MEDIA) {
            val req = pendingMedia ?: return
            pendingMedia = null
            grantMedia(req)
        }
    }

    /** Opens a new file in the phone's shared storage: videos in Movies, audio in Music, images in Pictures, the rest in Download. */
    private fun openShared(name: String, mime: String): Pair<OutputStream, String>? {
        if (Build.VERSION.SDK_INT >= 29) {
            val tries = ArrayList<Triple<Uri, String, String>>()
            when {
                mime.startsWith("video/") -> tries.add(Triple(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, Environment.DIRECTORY_MOVIES + "/Bible Journey", "Movies/Bible Journey"))
                mime.startsWith("audio/") -> tries.add(Triple(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, Environment.DIRECTORY_MUSIC + "/Bible Journey", "Music/Bible Journey"))
                mime.startsWith("image/") -> tries.add(Triple(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, Environment.DIRECTORY_PICTURES + "/Bible Journey", "Pictures/Bible Journey"))
            }
            tries.add(Triple(MediaStore.Downloads.EXTERNAL_CONTENT_URI, Environment.DIRECTORY_DOWNLOADS, "Downloads"))
            for ((collection, folder, label) in tries) {
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                        put(MediaStore.MediaColumns.MIME_TYPE, mime)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
                    }
                    val uri = contentResolver.insert(collection, values) ?: continue
                    val out = contentResolver.openOutputStream(uri) ?: continue
                    return out to label
                } catch (e: Exception) {
                }
            }
            return null
        }
        return try {
            val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
            dir.mkdirs()
            FileOutputStream(File(dir, name)) to "the app’s Download folder"
        } catch (e: Exception) {
            null
        }
    }

    private fun safeName(name: String) = name.replace(Regex("[^A-Za-z0-9._-]"), "_")

    inner class Bridge {
        @JavascriptInterface
        fun isApp(): Boolean = true

        @JavascriptInterface
        fun getVersion(): String = try {
            "Android app, version " + packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "Android app"
        }

        /** Saves a small file in one go. Returns where it went, or "" on failure. */
        @JavascriptInterface
        fun saveFile(base64: String, name: String, mime: String): String {
            return try {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                val (out, where) = openShared(safeName(name), mime) ?: return ""
                out.use { it.write(bytes) }
                where
            } catch (e: Exception) {
                ""
            }
        }

        /** Large files (recordings) arrive in pieces: fileStart, fileChunk..., fileFinish. */
        @JavascriptInterface
        fun fileStart(name: String, mime: String): String {
            val opened = openShared(safeName(name), mime) ?: return ""
            val id = UUID.randomUUID().toString()
            synchronized(streams) { streams[id] = opened }
            return id
        }

        @JavascriptInterface
        fun fileChunk(id: String, base64: String): Boolean {
            val s = synchronized(streams) { streams[id] } ?: return false
            return try {
                s.first.write(Base64.decode(base64, Base64.DEFAULT))
                true
            } catch (e: Exception) {
                false
            }
        }

        @JavascriptInterface
        fun fileFinish(id: String): String {
            val s = synchronized(streams) { streams.remove(id) } ?: return ""
            return try {
                s.first.close()
                s.second
            } catch (e: Exception) {
                ""
            }
        }

        @JavascriptInterface
        fun keepAwake(on: Boolean) {
            runOnUiThread {
                if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        @JavascriptInterface
        fun setReminder(hour: Int, minute: Int): Boolean {
            Reminder.schedule(this@MainActivity, hour, minute)
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                runOnUiThread { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFY) }
            }
            return true
        }

        @JavascriptInterface
        fun cancelReminder() {
            Reminder.cancel(this@MainActivity)
        }

        @JavascriptInterface
        fun shareText(text: String) {
            runOnUiThread {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                startActivity(Intent.createChooser(send, "Share Bible Journey"))
            }
        }

        @JavascriptInterface
        fun copyText(text: String) {
            runOnUiThread {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Bible Journey", text))
            }
        }

        @JavascriptInterface
        fun openUrl(url: String) {
            runOnUiThread { openExternal(url) }
        }

        @JavascriptInterface
        fun showNiv(url: String, title: String, canMark: Boolean) {
            if (!url.startsWith("https://www.bible.com/")) return
            runOnUiThread { this@MainActivity.showNiv(url, title, canMark) }
        }

        @JavascriptInterface
        fun setBarColor(hex: String) {
            runOnUiThread {
                try {
                    val c = Color.parseColor(hex.trim())
                    barColor = c
                    styleNiv()
                    window.statusBarColor = c
                    window.navigationBarColor = c
                    val light = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000 > 140
                    var flags = window.decorView.systemUiVisibility
                    @Suppress("DEPRECATION")
                    flags = if (light) flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                    else flags and (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR).inv()
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility = flags
                } catch (e: Exception) {
                }
            }
        }
    }

    companion object {
        const val APP_HOST = "appassets.androidplatform.net"
        private const val REQ_FILE = 41
        private const val REQ_NOTIFY = 42
        private const val REQ_MEDIA = 43
    }
}
