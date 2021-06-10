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
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
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
            viewModelStore.clear()
            this.supportFragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.enter_anim, R.anim.exit_anim, R.anim.pop_enter, R.anim.pop_exit)
                .add(R.id.homeRoot, ResultFragment.newInstance(url, slug, apiName))
                .commit()
        }
    }

    fun Context.getStatusBarHeight(): Int {
        var result = 0
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

    fun Context.getNavigationBarHeight(): Int {
        var result = 0
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
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
        if (_OnAudioFocusChangeListener != null) return _OnAudioFocusChangeListener
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

    fun Context.isCastApiAvailable(): Boolean {
        val isCastApiAvailable =
            GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(applicationContext) == ConnectionResult.SUCCESS
        try {
            applicationContext?.let { CastContext.getSharedInstance(it) }
        } catch (e: Exception) {
            println(e)
            // track non-fatal
            return false
        }
        return isCastApiAvailable
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

    fun Activity.hideSystemUI() {
        // Enables regular immersive mode.
        // For "lean back" mode, remove SYSTEM_UI_FLAG_IMMERSIVE.
        // Or for "sticky immersive," replace it with SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                // Set the content to appear under the system bars so that the
                // content doesn't resize when the system bars hide and show.
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                // Hide the nav bar and status bar
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                //  or View.SYSTEM_UI_FLAG_LOW_PROFILE
                )
        // window.addFlags(View.KEEP_SCREEN_ON)
    }
    fun FragmentActivity.popCurrentPage() {
        val currentFragment = supportFragmentManager.fragments.lastOrNull {
            it.isVisible
        } ?: return

        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.enter_anim,
                R.anim.exit_anim,
                R.anim.pop_enter,
                R.anim.pop_exit
            )
            .remove(currentFragment)
            .commitAllowingStateLoss()
    }
    /*
    fun FragmentActivity.popCurrentPage(isInPlayer: Boolean, isInExpandedView: Boolean, isInResults: Boolean) {
        val currentFragment = supportFragmentManager.fragments.lastOrNull {
            it.isVisible
        }
            ?: //this.onBackPressed()
            return

/*
        if (tvActivity == null) {
            requestedOrientation = if (settingsManager?.getBoolean("force_landscape", false) == true) {
                ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }*/

        // No fucked animations leaving the player :)
        when {
            isInPlayer -> {
                supportFragmentManager.beginTransaction()
                    //.setCustomAnimations(R.anim.enter, R.anim.exit, R.anim.pop_enter, R.anim.pop_exit)
                    .remove(currentFragment)
                    .commitAllowingStateLoss()
            }
            isInExpandedView && !isInResults -> {
                supportFragmentManager.beginTransaction()
                    .setCustomAnimations(
                        R.anim.enter_anim,//R.anim.enter_from_right,
                        R.anim.exit_anim,//R.anim.exit_to_right,
                        R.anim.pop_enter,
                        R.anim.pop_exit
                    )
                    .remove(currentFragment)
                    .commitAllowingStateLoss()
            }
            else -> {
                supportFragmentManager.beginTransaction()
                    .setCustomAnimations(R.anim.enter_anim, R.anim.exit_anim, R.anim.pop_enter, R.anim.pop_exit)
                    .remove(currentFragment)
                    .commitAllowingStateLoss()
            }
        }
    }*/


    fun Activity.changeStatusBarState(hide: Boolean): Int {
        return if (hide) {
            window?.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
            0
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            this.getStatusBarHeight()
        }
    }

    // Shows the system bars by removing all the flags
    // except for the ones that make the content appear under the system bars.
    fun Activity.showSystemUI() {
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                ) // or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        // window.clearFlags(View.KEEP_SCREEN_ON)
    }
}