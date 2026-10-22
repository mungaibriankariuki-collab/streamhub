package com.streamhub.app

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.streamhub.app.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.siteList.layoutManager = GridLayoutManager(this, 2)
        binding.siteList.adapter = SiteAdapter(Sites.ALL) { site -> openUrl(site.url) }
        binding.siteList.isNestedScrollingEnabled = false

        binding.goButton.setOnClickListener { openTypedUrl() }
        binding.urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                openTypedUrl()
                true
            } else {
                false
            }
        }

        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_update_engine -> {
                    updateDownloader()
                    true
                }
                R.id.action_about -> {
                    showAbout()
                    true
                }
                else -> false
            }
        }

        handleIntent(intent)
        warmUpDownloader()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun openTypedUrl() {
        val text = binding.urlInput.text?.toString().orEmpty()
        if (text.isBlank()) return
        openUrl(normalizeUrl(text))
        binding.urlInput.setText("")
    }

    private fun openUrl(url: String) {
        startActivity(
            Intent(this, BrowserActivity::class.java)
                .putExtra(BrowserActivity.EXTRA_URL, url)
        )
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
                val link = Regex("""https?://\S+""").find(text)?.value ?: text
                openUrl(normalizeUrl(link))
            }
            Intent.ACTION_VIEW -> {
                val link = intent.dataString ?: return
                if (link.startsWith("http://", true) || link.startsWith("https://", true)) {
                    openUrl(link)
                }
            }
        }
    }

    private fun warmUpDownloader() {
        lifecycleScope.launch {
            Downloader.ensureReady(applicationContext)
            try {
                Downloader.update(applicationContext)
            } catch (_: Exception) {
                // Offline or a temporary failure; the bundled engine still works.
            }
        }
    }

    private fun updateDownloader() {
        Toast.makeText(this, R.string.update_checking, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val message = try {
                Downloader.update(applicationContext)
            } catch (e: Exception) {
                getString(R.string.update_failed, e.message ?: "unknown error")
            }
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun showAbout() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.app_name)
            .setMessage(R.string.about_text)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
