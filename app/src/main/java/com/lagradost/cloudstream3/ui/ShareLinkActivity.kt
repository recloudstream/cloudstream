package com.lagradost.cloudstream3.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.lagradost.cloudstream3.MainActivity.Companion.EXTRA_SHARED_URL
import com.lagradost.cloudstream3.MainActivity.Companion.EXTRA_SHARED_URL_HANDLED
import com.lagradost.cloudstream3.ui.account.AccountSelectActivity

class ShareLinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        forwardShareIntent(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        forwardShareIntent(intent)
        finish()
    }

    private fun forwardShareIntent(sourceIntent: Intent?) {
        val url = sourceIntent?.extractSharedUrl() ?: return
        val targetIntent = Intent(sourceIntent).apply {
            setClass(this@ShareLinkActivity, AccountSelectActivity::class.java)
            putExtra(EXTRA_SHARED_URL, url)
            putExtra(EXTRA_SHARED_URL_HANDLED, false)
        }
        startActivity(targetIntent)
    }

    private fun Intent.extractSharedUrl(): String? {
        val candidates = listOfNotNull(
            dataString,
            getStringExtra(Intent.EXTRA_TEXT),
            getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
            @Suppress("DEPRECATION")
            getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)?.toString(),
        )

        return candidates.firstNotNullOfOrNull { value ->
            value
                .lineSequence()
                .flatMap { it.splitToSequence(Regex("\\s+")) }
                .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
        }
    }
}
