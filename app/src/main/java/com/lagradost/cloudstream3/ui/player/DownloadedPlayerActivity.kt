package com.lagradost.cloudstream3.ui.player

import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.ExtractorUri
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.safefile.SafeFile

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

    private fun playLink(url: String) {
        this.navigate(
            R.id.global_to_navigation_player, GeneratorPlayer.newInstance(
                LinkGenerator(
                    listOf(
                        BasicLink(url)
                    )
                )
            )
        )
    }

    private fun playUri(uri: Uri) {
        val name = SafeFile.fromUri(this, uri)?.name()
        this.navigate(
            R.id.global_to_navigation_player, GeneratorPlayer.newInstance(
                DownloadFileGenerator(
                    listOf(
                        ExtractorUri(
                            uri = uri,
                            name = name ?: getString(R.string.downloaded_file),
                            // well not the same as a normal id, but we take it as users may want to
                            // play downloaded files and save the location
                            id = kotlin.runCatching { ContentUris.parseId(uri) }.getOrNull()?.hashCode()
                        )
                    )
                )
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(DTAG, "onCreate")

        CommonActivity.loadThemes(this)
        super.onCreate(savedInstanceState)
        CommonActivity.init(this)

        setContentView(R.layout.empty_layout)

        val data = intent.data

        if (intent?.action == Intent.ACTION_SEND) {
            val extraText = try { // I dont trust android
                intent.getStringExtra(Intent.EXTRA_TEXT)
            } catch (e: Exception) {
                null
            }
            val cd = intent.clipData
            val item = if (cd != null && cd.itemCount > 0) cd.getItemAt(0) else null
            val url = item?.text?.toString()

            // idk what I am doing, just hope any of these work
            if (item?.uri != null)
                playUri(item.uri)
            else if (url != null)
                playLink(url)
            else if (data != null)
                playUri(data)
            else if (extraText != null)
                playLink(extraText)
            else {
                finish()
                return
            }
        } else if (data?.scheme == "content") {
            playUri(data)
        } else {
            finish()
            return
        }
    }
}