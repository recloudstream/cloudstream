package com.lagradost.cloudstream3

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.ui.result.ResultFragment
import com.lagradost.cloudstream3.utils.Event

object UIHelper {
    val Int.toPx: Int get() = (this * Resources.getSystem().displayMetrics.density).toInt()
    val Float.toPx: Float get() = (this * Resources.getSystem().displayMetrics.density)
    val Int.toDp: Int get() = (this / Resources.getSystem().displayMetrics.density).toInt()
    val Float.toDp: Float get() = (this / Resources.getSystem().displayMetrics.density)

    fun Activity.checkWrite(): Boolean {
        return (ContextCompat.checkSelfPermission(this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED)
    }

    fun Activity.requestRW() {
        ActivityCompat.requestPermissions(this,
            arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ),
            1337)
    }

    fun AppCompatActivity.loadResult(url: String, slug: String, apiName: String) {
        this.runOnUiThread {
            this.supportFragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.enter_anim, R.anim.exit_anim, R.anim.pop_enter, R.anim.pop_exit)
                .add(R.id.homeRoot, ResultFragment.newInstance(url, slug, apiName))
                .commit()
        }
    }

    fun Activity.getStatusBarHeight(): Int {
        var result = 0
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

    fun Activity.fixPaddingStatusbar(v: View) {
        v.setPadding(v.paddingLeft, v.paddingTop + getStatusBarHeight(), v.paddingRight, v.paddingBottom)
    }

    private fun Activity.getGridFormat(): String {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        return settingsManager.getString(getString(R.string.grid_format_key), "grid")!!
    }

    fun Activity.getGridFormatId(): Int {
        return when (getGridFormat()) {
            "list" -> R.layout.search_result_compact
            "compact_list" -> R.layout.search_result_super_compact
            else -> R.layout.search_result_grid
        }
    }

    fun Activity.getGridIsCompact(): Boolean {
        return getGridFormat() != "grid"
    }

    fun Activity.requestLocalAudioFocus(focusRequest: AudioFocusRequest?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.requestAudioFocus(focusRequest)
        } else {
            val audioManager: AudioManager =
                getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }

    private var _AudioFocusRequest: AudioFocusRequest? = null
    private var _OnAudioFocusChangeListener: AudioManager.OnAudioFocusChangeListener? = null
    var onAudioFocusEvent = Event<Boolean>()

    fun getAudioListener(): AudioManager.OnAudioFocusChangeListener? {
        if(_OnAudioFocusChangeListener != null) return _OnAudioFocusChangeListener
        _OnAudioFocusChangeListener = AudioManager.OnAudioFocusChangeListener {
            onAudioFocusEvent.invoke(
                when (it) {
                    AudioManager.AUDIOFOCUS_GAIN -> true
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE -> true
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> true
                    else -> false
                }
            )
        }
        return _OnAudioFocusChangeListener
    }

    fun getFocusRequest(): AudioFocusRequest? {
        if (_AudioFocusRequest != null) return _AudioFocusRequest
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).run {
                setAudioAttributes(AudioAttributes.Builder().run {
                    setUsage(AudioAttributes.USAGE_MEDIA)
                    setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    build()
                })
                setAcceptsDelayedFocusGain(true)
                getAudioListener()?.let {
                    setOnAudioFocusChangeListener(it)
                }
                build()
            }
        } else {
            null
        }
    }
}