package com.lagradost.cloudstream3.ui.player

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.safefile.SafeFile
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import com.lagradost.cloudstream3.ui.player.OfflinePlaybackHelper.playLink
import com.lagradost.cloudstream3.ui.player.OfflinePlaybackHelper.playUri

const val DTAG = "PlayerActivity"

class DownloadedPlayerActivity : AppCompatActivity() {
    private val dTAG = "DownloadedPlayerAct"

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        CommonActivity.dispatchKeyEvent(this, event)?.let {
            return it
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        CommonActivity.onKeyDown(this, keyCode, event)

        return super.onKeyDown(keyCode, event)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        CommonActivity.onUserLeaveHint(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CommonActivity.loadThemes(this)
        CommonActivity.init(this)
        setContentView(R.layout.empty_layout)
        Log.i(dTAG, "onCreate")

        val data = intent.data

        if (intent?.action == Intent.ACTION_SEND) {
            val extraText = normalSafeApiCall { // I dont trust android
                intent.getStringExtra(Intent.EXTRA_TEXT)
            }
            val cd = intent.clipData
            val item = if (cd != null && cd.itemCount > 0) cd.getItemAt(0) else null
            val url = item?.text?.toString()

            // idk what I am doing, just hope any of these work
            if (item?.uri != null)
                playUri(this, item.uri)
            else if (url != null)
                playLink(this, url)
            else if (data != null)
                playUri(this, data)
            else if (extraText != null)
                playLink(this, extraText)
            else {
                finish()
                return
            }
        } else if (data?.scheme == "content") {
            playUri(this, data)
        } else {
            finish()
            return
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finish()
                }
            }
        )
    }

    override fun onResume() {
        super.onResume()
        CommonActivity.setActivityInstance(this)
    }
}