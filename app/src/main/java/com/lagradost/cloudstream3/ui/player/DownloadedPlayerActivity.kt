package com.lagradost.cloudstream3.ui.player

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.ui.player.OfflinePlaybackHelper.playLink
import com.lagradost.cloudstream3.ui.player.OfflinePlaybackHelper.playUri
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.attachBackPressedCallback
import com.lagradost.cloudstream3.utils.UIHelper.enableEdgeToEdgeCompat

class DownloadedPlayerActivity : AppCompatActivity() {
    companion object {
        const val TAG = "DownloadedPlayerActivity"
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        CommonActivity.dispatchKeyEvent(this, event) ?: super.dispatchKeyEvent(event)

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        CommonActivity.onKeyDown(this, keyCode, event) ?: super.onKeyDown(keyCode, event)

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        CommonActivity.onUserLeaveHint(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Ignore same intent so the player doesnt totally
        // reload if you are playing the same thing.
        if (isSameIntent(intent)) return
        setIntent(intent)
        Log.i(TAG, "onNewIntent")
        handleIntent(intent)
    }

    private fun isSameIntent(newIntent: Intent): Boolean {
        val old = intent ?: return false
        // Compare URIs first
        val oldUri = old.data ?: old.clipData?.getItemAt(0)?.uri
        val newUri = newIntent.data ?: newIntent.clipData?.getItemAt(0)?.uri
        if (oldUri != null && oldUri == newUri) return true
        // Fall back to comparing EXTRA_TEXT links
        val oldText = safe { old.getStringExtra(Intent.EXTRA_TEXT) }
        val newText = safe { newIntent.getStringExtra(Intent.EXTRA_TEXT) }
        return oldText != null && oldText == newText
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CommonActivity.loadThemes(this)
        CommonActivity.init(this)
        enableEdgeToEdgeCompat()
        setContentView(R.layout.empty_layout)
        Log.i(TAG, "onCreate")

        // When savedInstanceState != null the system saved state before killing the
        // backgrounded process the NavController restores the correct player fragment
        // automatically. Do not replay the intent as it would push a duplicate player.
        if (savedInstanceState == null) {
            handleIntent(intent)
        }

        // Use moveTaskToBack instead of finish() so there is always exactly one task
        // entry in recents, always reflecting the current file.
        //
        // finish() destroys the Activity but may leave the task in recents. Each new file
        // open can create a new task entry, so recents accumulates stale entries for old
        // files. The user then taps a stale entry and gets the wrong file.
        //
        // moveTaskToBack keeps the Activity alive in the background. There is only ever
        // one task entry in recents. New files opened from the file manager arrive via
        // onNewIntent on the live instance, updating the player immediately. The single
        // recents entry always reflects the current state, ensuring we load the
        // correct file.
        attachBackPressedCallback("DownloadedPlayerActivity") { moveTaskToBack(true) }
    }

    private fun handleIntent(intent: Intent) {
        val data = intent.data
        if (OfflinePlaybackHelper.playIntent(activity = this, intent = intent)) {
            return
        }

        if (
            intent.action == Intent.ACTION_SEND ||
            intent.action == Intent.ACTION_OPEN_DOCUMENT ||
            intent.action == Intent.ACTION_VIEW
        ) {
            val extraText = safe { intent.getStringExtra(Intent.EXTRA_TEXT) }
            val cd = intent.clipData
            val item = if (cd != null && cd.itemCount > 0) cd.getItemAt(0) else null
            val url = item?.text?.toString()
            when {
                item?.uri != null -> playUri(this, item.uri)
                url != null -> playLink(this, url)
                data != null -> playUri(this, data)
                extraText != null -> playLink(this, extraText)
                else -> finishAndRemoveTask()
            }
        } else if (data?.scheme == "content") {
            playUri(this, data)
        } else finishAndRemoveTask()
    }

    override fun onResume() {
        super.onResume()
        CommonActivity.setActivityInstance(this)
    }
}
