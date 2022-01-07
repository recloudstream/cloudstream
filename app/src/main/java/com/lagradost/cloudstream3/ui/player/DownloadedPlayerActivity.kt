package com.lagradost.cloudstream3.ui.player

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.AppUtils.getUri
import com.lagradost.cloudstream3.utils.ExtractorUri
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import java.io.File

const val DTAG = "PlayerActivity"

class DownloadedPlayerActivity : AppCompatActivity() {
    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
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

    override fun onBackPressed() {
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(DTAG, "onCreate")

        CommonActivity.loadThemes(this)
        super.onCreate(savedInstanceState)
        CommonActivity.init(this)

        val data = intent.data
        if (data == null) {
            finish()
            return
        }
        val uri = getUri(intent.data)
        if (uri == null) {
            finish()
            return
        }
        val path = uri.path
        // Because it doesn't get the path when it's downloaded, I have no idea
        val realPath = if (File(
                intent.data?.path?.removePrefix("/file") ?: "NONE"
            ).exists()
        ) intent.data?.path?.removePrefix("/file") else path

        if (realPath == null) {
            finish()
            return
        }

        val name = try {
            File(realPath).name
        } catch (e: Exception) {
            "NULL"
        }

        val realUri = AppUtils.getVideoContentUri(this, realPath)
        val tryUri = realUri ?: uri

        setContentView(R.layout.empty_layout)
        Log.i(DTAG, "navigating")

        //TODO add relative path for subs
        this.navigate(
            R.id.global_to_navigation_player, GeneratorPlayer.newInstance(
                DownloadFileGenerator(listOf(ExtractorUri(uri = tryUri, name = name)))
            )
        )
    }
}