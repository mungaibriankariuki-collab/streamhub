package com.streamhub.app

import android.Manifest
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebChromeClient.FileChooserParams
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.streamhub.app.databinding.ActivityBrowserBinding
import kotlinx.coroutines.launch
import java.util.UUID

class BrowserActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "extra_url"

        private const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

        private const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }

    private lateinit var binding: ActivityBrowserBinding
    private lateinit var webView: WebView

    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var popupWebView: WebView? = null
    private var desktopMode = false

    private var downloadBusy = false
    private var downloadCancelled = false
    private var downloadProcessId: String? = null

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = fileChooserCallback ?: return@registerForActivityResult
            fileChooserCallback = null
            if (result.resultCode != RESULT_OK) {
                callback.onReceiveValue(null)
                return@registerForActivityResult
            }
            val data = result.data
            val uris = when {
                data?.clipData != null -> {
                    val clip = data.clipData!!
                    Array(clip.itemCount) { clip.getItemAt(it).uri }
                }
                data?.data != null -> arrayOf(data.data!!)
                else -> emptyArray()
            }
            if (uris.isEmpty()) callback.onReceiveValue(null) else callback.onReceiveValue(uris)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        webView = binding.webView
        setupWebView()
        setupToolbar()
        setupBackNavigation()
        requestLegacyStoragePermission()

        val startUrl = intent.getStringExtra(EXTRA_URL) ?: "https://m.youtube.com/"
        webView.loadUrl(startUrl)
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = MOBILE_UA
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.setBackgroundColor(android.graphics.Color.WHITE)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean = handleUrl(request.url)

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                handleUrl(Uri.parse(url))

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                updateNavigation(url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                updateNavigation(url)
                binding.progress.visibility = View.GONE
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    toast(getString(R.string.page_error, error?.description?.toString().orEmpty()))
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progress.progress = newProgress
                binding.progress.visibility =
                    if (newProgress in 1..99) View.VISIBLE else View.GONE
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                val popup = popupWebView ?: WebView(this@BrowserActivity).also { created ->
                    created.settings.javaScriptEnabled = true
                    created.settings.domStorageEnabled = true
                    created.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean {
                            openPopupUrl(request.url)
                            return true
                        }

                        @Suppress("DEPRECATION")
                        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                            openPopupUrl(Uri.parse(url))
                            return true
                        }
                    }
                    popupWebView = created
                }
                (resultMsg?.obj as? WebView.WebViewTransport)?.webView = popup
                resultMsg?.sendToTarget()
                return true
            }

            override fun onShowCustomView(
                view: View,
                callback: WebChromeClient.CustomViewCallback
            ) {
                if (fullscreenView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                (view.parent as? ViewGroup)?.removeView(view)
                fullscreenView = view
                fullscreenCallback = callback
                binding.fullscreenContainer.addView(
                    view,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                binding.fullscreenContainer.visibility = View.VISIBLE
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                hideSystemBars()
            }

            override fun onHideCustomView() {
                exitFullscreen()
            }

            override fun onShowFileChooser(
                view: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback
                val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT)
                if (intent.type == null) intent.type = "*/*"
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                return try {
                    fileChooserLauncher.launch(intent)
                    true
                } catch (e: Exception) {
                    fileChooserCallback = null
                    false
                }
            }
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            if (url.startsWith("blob:") || url.startsWith("data:")) {
                toast(getString(R.string.download_unsupported))
                return@setDownloadListener
            }
            if (url.contains(".m3u8", ignoreCase = true)) {
                toast(getString(R.string.download_unsupported))
                return@setDownloadListener
            }
            downloadDirect(url, userAgent, contentDisposition, mimeType)
        }
    }

    private fun setupToolbar() {
        binding.backButton.setOnClickListener {
            if (webView.canGoBack()) webView.goBack()
        }
        binding.forwardButton.setOnClickListener {
            if (webView.canGoForward()) webView.goForward()
        }
        binding.reloadButton.setOnClickListener { webView.reload() }
        binding.downloadButton.setOnClickListener { showDownloadOptions() }
        binding.menuButton.setOnClickListener { showBrowserMenu() }

        binding.urlInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.urlInput.selectAll()
        }
        binding.urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                val url = normalizeUrl(binding.urlInput.text?.toString().orEmpty())
                binding.urlInput.clearFocus()
                hideKeyboard()
                webView.requestFocus()
                webView.loadUrl(url)
                true
            } else {
                false
            }
        }

        updateNavigation(null)
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    fullscreenView != null -> exitFullscreen()
                    webView.canGoBack() -> webView.goBack()
                    else -> finish()
                }
            }
        })
    }

    private fun showBrowserMenu() {
        PopupMenu(this, binding.menuButton).apply {
            menuInflater.inflate(R.menu.browser_menu, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_home -> {
                        finish()
                        true
                    }
                    R.id.action_desktop -> {
                        toggleDesktopMode()
                        true
                    }
                    R.id.action_external -> {
                        openExternally()
                        true
                    }
                    R.id.action_update_engine -> {
                        updateEngine()
                        true
                    }
                    R.id.action_clear_data -> {
                        confirmClearData()
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    private fun updateNavigation(url: String?) {
        if (!binding.urlInput.hasFocus() && !url.isNullOrBlank()) {
            binding.urlInput.setText(url)
        }
        val canGoBack = webView.canGoBack()
        val canGoForward = webView.canGoForward()
        binding.backButton.isEnabled = canGoBack
        binding.backButton.alpha = if (canGoBack) 1f else 0.35f
        binding.forwardButton.visibility = if (canGoForward) View.VISIBLE else View.GONE
        binding.forwardButton.isEnabled = canGoForward
        binding.forwardButton.alpha = if (canGoForward) 1f else 0.35f
    }

    private fun handleUrl(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme == "http" || scheme == "https") return false
        if (scheme == "intent") {
            try {
                startActivity(Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME))
            } catch (e: Exception) {
                val fallback = uri.getQueryParameter("browser_fallback_url")
                    ?: uri.getQueryParameter("S.browser_fallback_url")
                if (fallback != null) webView.loadUrl(fallback)
            }
            return true
        }
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    private fun openPopupUrl(uri: Uri) {
        if (uri.scheme == "http" || uri.scheme == "https") {
            webView.loadUrl(uri.toString())
        } else {
            handleUrl(uri)
        }
    }

    private fun showDownloadOptions() {
        val url = webView.url
        if (url.isNullOrBlank()) {
            toast(getString(R.string.open_page_first))
            return
        }
        if (downloadBusy) {
            toast(getString(R.string.already_downloading))
            return
        }

        val options = arrayOf(
            getString(R.string.download_video),
            getString(R.string.download_audio),
            getString(R.string.download_direct)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.download_page)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startDownload(url, audioOnly = false)
                    1 -> startDownload(url, audioOnly = true)
                    2 -> downloadDirect(url, null, null, null)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun startDownload(url: String, audioOnly: Boolean) {
        if (downloadBusy) {
            toast(getString(R.string.already_downloading))
            return
        }

        downloadBusy = true
        downloadCancelled = false
        val id = UUID.randomUUID().toString()
        downloadProcessId = id

        binding.downloadBar.visibility = View.VISIBLE
        binding.downloadProgress.visibility = View.VISIBLE
        binding.downloadProgress.isIndeterminate = true
        binding.downloadProgress.progress = 0
        binding.downloadCancel.visibility = View.VISIBLE
        binding.downloadCancel.isEnabled = true
        binding.downloadClose.visibility = View.GONE
        binding.downloadStatus.text = getString(
            if (audioOnly) R.string.download_starting_audio
            else R.string.download_starting_video
        )

        binding.downloadCancel.setOnClickListener {
            if (!downloadBusy) return@setOnClickListener
            downloadCancelled = true
            binding.downloadCancel.isEnabled = false
            binding.downloadStatus.text = getString(R.string.download_cancelling)
            downloadProcessId?.let { Downloader.cancel(it) }
        }
        binding.downloadClose.setOnClickListener {
            binding.downloadBar.visibility = View.GONE
        }

        lifecycleScope.launch {
            var failure: Exception? = null
            val names = try {
                Downloader.download(this@BrowserActivity, url, audioOnly, id) { pct, eta, line ->
                    runOnUiThread {
                        if (!downloadBusy) return@runOnUiThread
                        if (pct >= 0f) {
                            val percent = pct.toInt().coerceIn(0, 100)
                            binding.downloadProgress.isIndeterminate = false
                            binding.downloadProgress.progress = percent
                            binding.downloadStatus.text = buildString {
                                append(if (audioOnly) "Song " else "Video ")
                                append(percent)
                                append("%")
                                if (eta > 0) append(" · about ${eta}s left")
                            }
                        } else if (line.isNotBlank()) {
                            binding.downloadStatus.text = line.take(160)
                        }
                    }
                }
            } catch (e: Exception) {
                failure = e
                null
            }

            downloadBusy = false
            downloadProcessId = null
            binding.downloadCancel.visibility = View.GONE
            binding.downloadCancel.isEnabled = true
            binding.downloadClose.visibility = View.VISIBLE

            if (downloadCancelled) {
                binding.downloadProgress.visibility = View.GONE
                binding.downloadStatus.text = getString(R.string.download_cancelled)
                return@launch
            }

            if (names != null) {
                binding.downloadProgress.isIndeterminate = false
                binding.downloadProgress.progress = 100
                val summary = names.joinToString(", ")
                binding.downloadStatus.text = getString(R.string.download_saved, summary)
                toast(getString(R.string.download_saved, summary))
                binding.downloadBar.postDelayed({
                    if (!downloadBusy) binding.downloadBar.visibility = View.GONE
                }, 8000)
            } else {
                binding.downloadProgress.visibility = View.GONE
                val message = failure?.message
                val detail = message
                    ?.lineSequence()
                    ?.lastOrNull { it.contains("ERROR") }
                    ?: message
                    ?: "unknown error"
                binding.downloadStatus.text = getString(R.string.download_failed, detail.take(300))
                binding.downloadBar.postDelayed({
                    if (!downloadBusy) binding.downloadBar.visibility = View.GONE
                }, 12000)
            }
        }
    }

    private fun downloadDirect(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ) {
        val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(name)
            setMimeType(mimeType)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                Downloader.FOLDER + "/" + name
            )
            if (!userAgent.isNullOrBlank()) addRequestHeader("User-Agent", userAgent)
            CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
        }
        try {
            (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            toast(getString(R.string.direct_started, name))
        } catch (e: Exception) {
            toast(getString(R.string.direct_failed, e.message ?: "unknown error"))
        }
    }

    private fun toggleDesktopMode() {
        desktopMode = !desktopMode
        webView.settings.userAgentString = if (desktopMode) DESKTOP_UA else MOBILE_UA
        webView.reload()
        toast(getString(if (desktopMode) R.string.desktop_on else R.string.desktop_off))
    }

    private fun openExternally() {
        val url = webView.url ?: return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            toast(getString(R.string.no_app))
        }
    }

    private fun updateEngine() {
        toast(getString(R.string.engine_updating))
        lifecycleScope.launch {
            val message = try {
                Downloader.update(applicationContext)
            } catch (e: Exception) {
                getString(R.string.update_failed, e.message ?: "unknown error")
            }
            toast(message)
        }
    }

    private fun confirmClearData() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.menu_clear_data)
            .setMessage(R.string.clear_data_confirm)
            .setPositiveButton(R.string.clear) { _, _ ->
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                WebStorage.getInstance().deleteAllData()
                webView.clearCache(true)
                webView.clearHistory()
                toast(getString(R.string.cleared))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun exitFullscreen() {
        val view = fullscreenView ?: return
        binding.fullscreenContainer.removeView(view)
        binding.fullscreenContainer.visibility = View.GONE
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        showSystemBars()
        fullscreenCallback?.onCustomViewHidden()
        fullscreenView = null
        fullscreenCallback = null
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun showSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
    }

    private fun requestLegacyStoragePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                10
            )
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.urlInput.windowToken, 0)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        downloadProcessId?.let { Downloader.cancel(it) }
        popupWebView?.destroy()
        popupWebView = null
        webView.destroy()
        super.onDestroy()
    }
}
