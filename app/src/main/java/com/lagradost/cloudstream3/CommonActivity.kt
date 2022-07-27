package com.lagradost.cloudstream3

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import android.util.Log
import android.view.*
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.preference.PreferenceManager
import com.google.android.gms.cast.framework.CastSession
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.player.PlayerEventType
import com.lagradost.cloudstream3.utils.Event
import com.lagradost.cloudstream3.utils.UIHelper
import com.lagradost.cloudstream3.utils.UIHelper.hasPIPPermission
import com.lagradost.cloudstream3.utils.UIHelper.shouldShowPIPMode
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import org.schabi.newpipe.extractor.NewPipe
import java.util.*

object CommonActivity {
    fun Activity?.getCastSession(): CastSession? {
        return (this as MainActivity?)?.mSessionManager?.currentCastSession
    }

    var canEnterPipMode: Boolean = false
    var canShowPipMode: Boolean = false
    var isInPIPMode: Boolean = false

    val onColorSelectedEvent = Event<Pair<Int, Int>>()
    val onDialogDismissedEvent = Event<Int>()

    var playerEventListener: ((PlayerEventType) -> Unit)? = null
    var keyEventListener: ((Pair<KeyEvent?, Boolean>) -> Boolean)? = null


    var currentToast: Toast? = null

    fun showToast(act: Activity?, @StringRes message: Int, duration: Int) {
        if (act == null) return
        showToast(act, act.getString(message), duration)
    }

    const val TAG = "COMPACT"

    /** duration is Toast.LENGTH_SHORT if null*/
    fun showToast(act: Activity?, message: String?, duration: Int? = null) {
        if (act == null || message == null) {
            Log.w(TAG, "invalid showToast act = $act message = $message")
            return
        }
        Log.i(TAG, "showToast = $message")

        try {
            currentToast?.cancel()
        } catch (e: Exception) {
            logError(e)
        }
        try {
            val inflater =
                act.getSystemService(AppCompatActivity.LAYOUT_INFLATER_SERVICE) as LayoutInflater

            val layout: View = inflater.inflate(
                R.layout.toast,
                act.findViewById<View>(R.id.toast_layout_root) as ViewGroup?
            )

            val text = layout.findViewById(R.id.text) as TextView
            text.text = message.trim()

            val toast = Toast(act)
            toast.setGravity(Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM, 0, 5.toPx)
            toast.duration = duration ?: Toast.LENGTH_SHORT
            toast.view = layout
            //https://github.com/PureWriter/ToastCompat
            toast.show()
            currentToast = toast
        } catch (e: Exception) {
            logError(e)
        }
    }

    fun setLocale(context: Context?, languageCode: String?) {
        if (context == null || languageCode == null) return
        val locale = Locale(languageCode)
        val resources: Resources = context.resources
        val config = resources.configuration
        Locale.setDefault(locale)
        config.setLocale(locale)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            context.createConfigurationContext(config)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    fun Context.updateLocale() {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        val localeCode = settingsManager.getString(getString(R.string.locale_key), null)
        setLocale(this, localeCode)
    }

    fun init(act: Activity?) {
        if (act == null) return
        //https://stackoverflow.com/questions/52594181/how-to-know-if-user-has-disabled-picture-in-picture-feature-permission
        //https://developer.android.com/guide/topics/ui/picture-in-picture
        canShowPipMode =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && // OS SUPPORT
                    act.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) && // HAS FEATURE, MIGHT BE BLOCKED DUE TO POWER DRAIN
                    act.hasPIPPermission() // CHECK IF FEATURE IS ENABLED IN SETTINGS

        act.updateLocale()

        NewPipe.init(DownloaderTestImpl.getInstance())
    }

    private fun Activity.enterPIPMode() {
        if (!shouldShowPIPMode(canEnterPipMode) || !canShowPipMode) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    enterPictureInPictureMode(PictureInPictureParams.Builder().build())
                } catch (e: Exception) {
                    enterPictureInPictureMode()
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    enterPictureInPictureMode()
                }
            }
        } catch (e: Exception) {
            logError(e)
        }
    }

    fun onUserLeaveHint(act: Activity?) {
        if (canEnterPipMode && canShowPipMode) {
            act?.enterPIPMode()
        }
    }

    fun loadThemes(act: Activity?) {
        if (act == null) return
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(act)

        val currentTheme =
            when (settingsManager.getString(act.getString(R.string.app_theme_key), "AmoledLight")) {
                "Black" -> R.style.AppTheme
                "Light" -> R.style.LightMode
                "Amoled" -> R.style.AmoledMode
                "AmoledLight" -> R.style.AmoledModeLight
                else -> R.style.AppTheme
            }

        val currentOverlayTheme =
            when (settingsManager.getString(act.getString(R.string.primary_color_key), "Normal")) {
                "Normal" -> R.style.OverlayPrimaryColorNormal
                "CarnationPink" -> R.style.OverlayPrimaryColorCarnationPink
                "DarkGreen" -> R.style.OverlayPrimaryColorDarkGreen
                "Maroon" -> R.style.OverlayPrimaryColorMaroon
                "NavyBlue" -> R.style.OverlayPrimaryColorNavyBlue
                "Grey" -> R.style.OverlayPrimaryColorGrey
                "White" -> R.style.OverlayPrimaryColorWhite
                "Brown" -> R.style.OverlayPrimaryColorBrown
                "Purple" -> R.style.OverlayPrimaryColorPurple
                "Green" -> R.style.OverlayPrimaryColorGreen
                "GreenApple" -> R.style.OverlayPrimaryColorGreenApple
                "Red" -> R.style.OverlayPrimaryColorRed
                "Banana" -> R.style.OverlayPrimaryColorBanana
                "Party" -> R.style.OverlayPrimaryColorParty
                "Pink" -> R.style.OverlayPrimaryColorPink
                else -> R.style.OverlayPrimaryColorNormal
            }
        act.theme.applyStyle(currentTheme, true)
        act.theme.applyStyle(currentOverlayTheme, true)

        act.theme.applyStyle(
            R.style.LoadedStyle,
            true
        ) // THEME IS SET BEFORE VIEW IS CREATED TO APPLY THE THEME TO THE MAIN VIEW
    }

    private fun getNextFocus(
        act: Activity?,
        view: View?,
        direction: FocusDirection,
        depth: Int = 0
    ): Int? {
        if (view == null || depth >= 10 || act == null) {
            return null
        }

        val nextId = when (direction) {
            FocusDirection.Left -> {
                view.nextFocusLeftId
            }
            FocusDirection.Up -> {
                view.nextFocusUpId
            }
            FocusDirection.Right -> {
                view.nextFocusRightId
            }
            FocusDirection.Down -> {
                view.nextFocusDownId
            }
        }

        return if (nextId != -1) {
            val next = act.findViewById<View?>(nextId)
            //println("NAME: ${next.accessibilityClassName} | ${next?.isShown}" )

            if (next?.isShown == false) {
                getNextFocus(act, next, direction, depth + 1)
            } else {
                if (depth == 0) {
                    null
                } else {
                    nextId
                }
            }
        } else {
            null
        }
    }

    enum class FocusDirection {
        Left,
        Right,
        Up,
        Down,
    }

    fun onKeyDown(act: Activity?, keyCode: Int, event: KeyEvent?) {
        //println("Keycode: $keyCode")
        //showToast(
        //    this,
        //    "Got Keycode $keyCode | ${KeyEvent.keyCodeToString(keyCode)} \n ${event?.action}",
        //    Toast.LENGTH_LONG
        //)

        // Tested keycodes on remote:
        // KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
        // KeyEvent.KEYCODE_MEDIA_REWIND
        // KeyEvent.KEYCODE_MENU
        // KeyEvent.KEYCODE_MEDIA_NEXT
        // KeyEvent.KEYCODE_MEDIA_PREVIOUS
        // KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE

        // 149 keycode_numpad 5
        when (keyCode) {
            KeyEvent.KEYCODE_FORWARD, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                PlayerEventType.SeekForward
            }
            KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                PlayerEventType.SeekBack
            }
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_BUTTON_R1 -> {
                PlayerEventType.NextEpisode
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_BUTTON_L1 -> {
                PlayerEventType.PrevEpisode
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                PlayerEventType.Pause
            }
            KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_BUTTON_START -> {
                PlayerEventType.Play
            }
            KeyEvent.KEYCODE_L, KeyEvent.KEYCODE_NUMPAD_7 -> {
                PlayerEventType.Lock
            }
            KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_MENU -> {
                PlayerEventType.ToggleHide
            }
            KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_VOLUME_MUTE -> {
                PlayerEventType.ToggleMute
            }
            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_NUMPAD_9 -> {
                PlayerEventType.ShowMirrors
            }
            // OpenSubtitles shortcut
            KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_NUMPAD_8 -> {
                PlayerEventType.SearchSubtitlesOnline
            }
            KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_NUMPAD_3 -> {
                PlayerEventType.ShowSpeed
            }
            KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_NUMPAD_0 -> {
                PlayerEventType.Resize
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_ENTER -> { // space is not captured due to navigation
                PlayerEventType.PlayPauseToggle
            }
            else -> null
        }?.let { playerEvent ->
            playerEventListener?.invoke(playerEvent)
        }

        //when (keyCode) {
        //    KeyEvent.KEYCODE_DPAD_CENTER -> {
        //        println("DPAD PRESSED")
        //    }
        //}
    }

    fun dispatchKeyEvent(act: Activity?, event: KeyEvent?): Boolean? {
        if (act == null) return null
        event?.keyCode?.let { keyCode ->
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (act.currentFocus != null) {
                        val next = when (keyCode) {
                            KeyEvent.KEYCODE_DPAD_LEFT -> getNextFocus(
                                act,
                                act.currentFocus,
                                FocusDirection.Left
                            )
                            KeyEvent.KEYCODE_DPAD_RIGHT -> getNextFocus(
                                act,
                                act.currentFocus,
                                FocusDirection.Right
                            )
                            KeyEvent.KEYCODE_DPAD_UP -> getNextFocus(
                                act,
                                act.currentFocus,
                                FocusDirection.Up
                            )
                            KeyEvent.KEYCODE_DPAD_DOWN -> getNextFocus(
                                act,
                                act.currentFocus,
                                FocusDirection.Down
                            )

                            else -> null
                        }

                        if (next != null && next != -1) {
                            val nextView = act.findViewById<View?>(next)
                            if (nextView != null) {
                                nextView.requestFocus()
                                keyEventListener?.invoke(Pair(event, true))
                                return true
                            }
                        }

                        when (keyCode) {
                            KeyEvent.KEYCODE_DPAD_CENTER -> {
                                if (act.currentFocus is SearchView || act.currentFocus is SearchView.SearchAutoComplete) {
                                    UIHelper.showInputMethod(act.currentFocus?.findFocus())
                                }
                            }
                        }
                    }
                    //println("Keycode: $keyCode")
                    //showToast(
                    //    this,
                    //    "Got Keycode $keyCode | ${KeyEvent.keyCodeToString(keyCode)} \n ${event?.action}",
                    //    Toast.LENGTH_LONG
                    //)
                }
            }
        }

        if (keyEventListener?.invoke(Pair(event, false)) == true) {
            return true
        }
        return null
    }
}