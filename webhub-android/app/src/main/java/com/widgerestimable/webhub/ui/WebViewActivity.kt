package com.widgerestimable.webhub.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.widgerestimable.webhub.R
import com.widgerestimable.webhub.data.WebAppEntry
import com.widgerestimable.webhub.data.WebAppRepository
import com.widgerestimable.webhub.session.WebAppSessionManager

class WebViewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_APP_ID = "extra_app_id"
    }

    private lateinit var entry: WebAppEntry
    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var spinner: ProgressBar
    private lateinit var errorView: View

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var pendingPermissionRequest: PermissionRequest? = null
    private var pendingGeoOrigin: String? = null
    private var pendingGeoCallback: GeolocationPermissions.Callback? = null

    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        filePathCallback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
        filePathCallback = null
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it }
        pendingPermissionRequest?.let { req ->
            if (granted) req.grant(req.resources) else req.deny()
        }
        pendingPermissionRequest = null

        pendingGeoCallback?.let { cb ->
            pendingGeoOrigin?.let { origin -> cb.invoke(origin, granted, false) }
        }
        pendingGeoCallback = null
        pendingGeoOrigin = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        val appId = intent.getStringExtra(EXTRA_APP_ID)
        val loaded = appId?.let { WebAppRepository.getInstance(this).getById(it) }
        if (loaded == null || loaded.url.isBlank()) {
            finish()
            return
        }
        entry = loaded

        webView = findViewById(R.id.webview)
        swipeRefresh = findViewById(R.id.swipe_refresh)
        spinner = findViewById(R.id.progress_spinner)
        errorView = findViewById(R.id.error_view)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.title = entry.name
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.inflateMenu(R.menu.webview_menu)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_fullscreen -> { toggleImmersive(); true }
                R.id.menu_share -> { shareUrl(); true }
                R.id.menu_open_browser -> { openInBrowser(entry.url); true }
                R.id.menu_clear_cache -> {
                    WebAppSessionManager.clearGlobalHttpCache(webView)
                    Toast.makeText(this, R.string.settings_clear_all_caches, Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }

        setupWebView()
        swipeRefresh.setOnRefreshListener { webView.reload() }

        findViewById<ImageButton>(R.id.btn_nav_back).setOnClickListener { if (webView.canGoBack()) webView.goBack() }
        findViewById<ImageButton>(R.id.btn_nav_forward).setOnClickListener { if (webView.canGoForward()) webView.goForward() }
        findViewById<ImageButton>(R.id.btn_nav_refresh).setOnClickListener { webView.reload() }
        findViewById<ImageButton>(R.id.btn_nav_home).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btn_nav_logout).setOnClickListener { confirmLogout() }

        findViewById<android.widget.Button>(R.id.btn_retry).setOnClickListener {
            errorView.visibility = View.GONE
            webView.reload()
        }
        findViewById<android.widget.Button>(R.id.btn_back_home_error).setOnClickListener { finish() }

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            loadEntry()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(false)
        settings.mediaPlaybackRequiresUserGesture = false
        settings.setGeolocationEnabled(true)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val targetHost = request.url.host
                val ownHost = entry.originHost()
                if (targetHost != null && ownHost != null && targetHost.equals(ownHost, ignoreCase = true)) {
                    return false // même origine : navigation normale dans WebHub
                }
                showExternalLinkChoice(request.url.toString())
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                spinner.visibility = View.VISIBLE
                errorView.visibility = View.GONE
            }

            override fun onPageFinished(view: WebView, url: String) {
                spinner.visibility = View.GONE
                swipeRefresh.isRefreshing = false
                updateNavButtons()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame) {
                    spinner.visibility = View.GONE
                    swipeRefresh.isRefreshing = false
                    errorView.visibility = View.VISIBLE
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                this@WebViewActivity.filePathCallback = filePathCallback
                val mimeType = fileChooserParams.acceptTypes?.firstOrNull()?.takeIf { it.isNotBlank() } ?: "*/*"
                fileChooserLauncher.launch(mimeType)
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                val androidPerms = request.resources.mapNotNull {
                    when (it) {
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> Manifest.permission.CAMERA
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> Manifest.permission.RECORD_AUDIO
                        else -> null
                    }
                }.toTypedArray()

                if (androidPerms.isEmpty()) {
                    request.deny()
                    return
                }
                val allGranted = androidPerms.all {
                    ContextCompat.checkSelfPermission(this@WebViewActivity, it) == PackageManager.PERMISSION_GRANTED
                }
                if (allGranted) {
                    request.grant(request.resources)
                } else {
                    pendingPermissionRequest = request
                    permissionLauncher.launch(androidPerms)
                }
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback
            ) {
                val perm = Manifest.permission.ACCESS_FINE_LOCATION
                if (ContextCompat.checkSelfPermission(this@WebViewActivity, perm) == PackageManager.PERMISSION_GRANTED) {
                    callback.invoke(origin, true, false)
                } else {
                    pendingGeoOrigin = origin
                    pendingGeoCallback = callback
                    permissionLauncher.launch(arrayOf(perm))
                }
            }
        }

        webView.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            if (url.startsWith("blob:")) {
                Toast.makeText(this, R.string.download_blob_unsupported, Toast.LENGTH_LONG).show()
                return@setDownloadListener
            }
            try {
                val request = DownloadManager.Request(Uri.parse(url))
                request.setMimeType(mimeType)
                request.addRequestHeader("cookie", CookieManager.getInstance().getCookie(url))
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                val fileName = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType)
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("WebHub", "Échec du téléchargement", e)
                Toast.makeText(this, R.string.download_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadEntry() {
        if (!isOnline()) {
            Toast.makeText(this, R.string.offline_notice, Toast.LENGTH_LONG).show()
        }
        webView.loadUrl(entry.url)
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun updateNavButtons() {
        findViewById<ImageButton>(R.id.btn_nav_back).alpha = if (webView.canGoBack()) 1f else 0.4f
        findViewById<ImageButton>(R.id.btn_nav_forward).alpha = if (webView.canGoForward()) 1f else 0.4f
    }

    private fun showExternalLinkChoice(url: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.external_link_title)
            .setMessage(getString(R.string.external_link_message, entry.name, url))
            .setNegativeButton(R.string.action_cancel, null)
            .setNeutralButton(R.string.external_link_open_browser) { _, _ -> openInBrowser(url) }
            .setPositiveButton(R.string.external_link_open_webhub) { _, _ -> webView.loadUrl(url) }
            .show()
    }

    private fun openInBrowser(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, R.string.download_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareUrl() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, entry.name)
            putExtra(Intent.EXTRA_TEXT, entry.url)
        }
        startActivity(Intent.createChooser(intent, entry.name))
    }

    @Suppress("DEPRECATION")
    private fun toggleImmersive() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val controller = window.insetsController ?: return
            val isVisible = window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.statusBars()) != false
            if (isVisible) {
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            }
        } else {
            // Repli pour Android < 11 (API < 30) : anciens indicateurs systemUiVisibility.
            val decorView = window.decorView
            val isImmersive = decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN != 0
            decorView.systemUiVisibility = if (isImmersive) {
                View.SYSTEM_UI_FLAG_VISIBLE
            } else {
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN
            }
        }
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle(R.string.logout_confirm_title)
            .setMessage(R.string.logout_confirm_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_logout) { _, _ ->
                WebAppSessionManager.logout(entry.url, webView) {
                    runOnUiThread {
                        Toast.makeText(this, getString(R.string.logout_done, entry.name), Toast.LENGTH_SHORT).show()
                        webView.reload()
                    }
                }
            }
            .show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }
}

