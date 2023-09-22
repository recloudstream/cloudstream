package com.lagradost.cloudstream3

import android.Manifest
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.View.NO_ID
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.preference.PreferenceManager
import com.google.android.gms.cast.framework.CastSession
import com.google.android.material.chip.ChipGroup
import com.google.android.material.navigationrail.NavigationRailView
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.removeKey
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.player.PlayerEventType
import com.lagradost.cloudstream3.ui.result.ResultFragment
import com.lagradost.cloudstream3.ui.result.UiText
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.updateTv
import com.lagradost.cloudstream3.utils.AppUtils.isRtl
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.Event
import com.lagradost.cloudstream3.utils.UIHelper
import com.lagradost.cloudstream3.utils.UIHelper.hasPIPPermission
import com.lagradost.cloudstream3.utils.UIHelper.shouldShowPIPMode
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import org.schabi.newpipe.extractor.NewPipe
import java.lang.ref.WeakReference
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

enum class FocusDirection {
    Start,
    End,
    Up,
    Down,
}

object CommonActivity {

    private var _activity: WeakReference<Activity>? = null
    var activity
        get() = _activity?.get()
        private set(value) {
            _activity = WeakReference(value)
        }

    @MainThread
    fun Activity?.getCastSession(): CastSession? {
        return (this as MainActivity?)?.mSessionManager?.currentCastSession
    }

    val displayMetrics: DisplayMetrics = Resources.getSystem().displayMetrics

    // screenWidth and screenHeight does always
    // refer to the screen while in landscape mode
    val screenWidth: Int
        get() {
            return max(displayMetrics.widthPixels, displayMetrics.heightPixels)
        }
    val screenHeight: Int
        get() {
            return min(displayMetrics.widthPixels, displayMetrics.heightPixels)
        }


    var canEnterPipMode: Boolean = false
    var canShowPipMode: Boolean = false
    var isInPIPMode: Boolean = false

    val onColorSelectedEvent = Event<Pair<Int, Int>>()
    val onDialogDismissedEvent = Event<Int>()

    var playerEventListener: ((PlayerEventType) -> Unit)? = null
    var keyEventListener: ((Pair<KeyEvent?, Boolean>) -> Boolean)? = null


    var currentToast: Toast? = null

    fun showToast(@StringRes message: Int, duration: Int? = null) {
        val act = activity ?: return
        act.runOnUiThread {
            showToast(act, act.getString(message), duration)
        }
    }

    fun showToast(message: String?, duration: Int? = null) {
        val act = activity ?: return
        act.runOnUiThread {
            showToast(act, message, duration)
        }
    }

    fun showToast(message: UiText?, duration: Int? = null) {
        val act = activity ?: return
        if (message == null) return
        act.runOnUiThread {
            showToast(act, message.asString(act), duration)
        }
    }


    @MainThread
    fun showToast(act: Activity?, text: UiText, duration: Int) {
        if (act == null) return
        text.asStringNull(act)?.let {
            showToast(act, it, duration)
        }
    }

    /** duration is Toast.LENGTH_SHORT if null*/
    @MainThread
    fun showToast(act: Activity?, @StringRes message: Int, duration: Int? = null) {
        if (act == null) return
        showToast(act, act.getString(message), duration)
    }

    const val TAG = "COMPACT"

    /** duration is Toast.LENGTH_SHORT if null*/
    @MainThread
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

    /**
     * Not all languages can be fetched from locale with a code.
     * This map allows sidestepping the default Locale(languageCode)
     * when setting the app language.
     **/
    val appLanguageExceptions = hashMapOf(
        "zh-rTW" to Locale.TRADITIONAL_CHINESE
    )

    fun setLocale(context: Context?, languageCode: String?) {
        if (context == null || languageCode == null) return
        val locale = appLanguageExceptions[languageCode] ?: Locale(languageCode)
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

    fun init(act: ComponentActivity?) {
        if (act == null) return
        activity = act
        //https://stackoverflow.com/questions/52594181/how-to-know-if-user-has-disabled-picture-in-picture-feature-permission
        //https://developer.android.com/guide/topics/ui/picture-in-picture
        canShowPipMode =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && // OS SUPPORT
                    act.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) && // HAS FEATURE, MIGHT BE BLOCKED DUE TO POWER DRAIN
                    act.hasPIPPermission() // CHECK IF FEATURE IS ENABLED IN SETTINGS

        act.updateLocale()
        act.updateTv()
        NewPipe.init(DownloaderTestImpl.getInstance())

        for (resumeApp in resumeApps) {
            resumeApp.launcher =
                act.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                    val resultCode = result.resultCode
                    val data = result.data
                    if (resultCode == AppCompatActivity.RESULT_OK && data != null && resumeApp.position != null && resumeApp.duration != null) {
                        val pos = resumeApp.getPosition(data)
                        val dur = resumeApp.getDuration(data)
                        if (dur > 0L && pos > 0L)
                            DataStoreHelper.setViewPos(getKey(resumeApp.lastId), pos, dur)
                        removeKey(resumeApp.lastId)
                        ResultFragment.updateUI()
                    }
                }
        }

        // Ask for notification permissions on Android 13
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                act,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            val requestPermissionLauncher = act.registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted: Boolean ->
                Log.d(TAG, "Notification permission: $isGranted")
            }
            requestPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
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
                "Monet" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    R.style.MonetMode else R.style.AppTheme

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
                "Monet" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    R.style.OverlayPrimaryColorMonet else R.style.OverlayPrimaryColorNormal

                "Monet2" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    R.style.OverlayPrimaryColorMonetTwo else R.style.OverlayPrimaryColorNormal

                else -> R.style.OverlayPrimaryColorNormal
            }
        act.theme.applyStyle(currentTheme, true)
        act.theme.applyStyle(currentOverlayTheme, true)

        act.theme.applyStyle(
            R.style.LoadedStyle,
            true
        ) // THEME IS SET BEFORE VIEW IS CREATED TO APPLY THE THEME TO THE MAIN VIEW
    }

    /** because we want closes find, aka when multiple have the same id, we go to parent
    until the correct one is found */
    private fun localLook(from: View, id: Int): View? {
        if (id == NO_ID) return null
        var currentLook: View = from
        // limit to 15 look depth
        for (i in 0..15) {
            currentLook.findViewById<View?>(id)?.let { return it }
            currentLook = (currentLook.parent as? View) ?: break
        }
        return null
    }
    /*var currentLook: View = view
    while (true) {
        val tmpNext = currentLook.findViewById<View?>(nextId)
        if (tmpNext != null) {
            next = tmpNext
            break
        }
        currentLook = currentLook.parent as? View ?: break
    }*/

    private fun View.hasContent() : Boolean {
        return isShown && when(this) {
            //is RecyclerView -> this.childCount > 0
            is ViewGroup -> this.childCount > 0
            else -> true
        }
    }

    /** skips the initial stage of searching for an id using the view, see getNextFocus for specification */
    fun continueGetNextFocus(
        root: Any?,
        view: View,
        direction: FocusDirection,
        nextId: Int,
        depth: Int = 0
    ): View? {
        if (nextId == NO_ID) return null

        // do an initial search for the view, in case the localLook is too deep we can use this as
        // an early break and backup view
        var next =
            when (root) {
                is Activity -> root.findViewById(nextId)
                is View -> root.rootView.findViewById<View?>(nextId)
                else -> null
            } ?: return null

        next = localLook(view, nextId) ?: next
        val shown = next.hasContent()

        // if cant focus but visible then break and let android decide
        // the exception if is the view is a parent and has children that wants focus
        val hasChildrenThatWantsFocus = (next as? ViewGroup)?.let { parent ->
            parent.descendantFocusability == ViewGroup.FOCUS_AFTER_DESCENDANTS && parent.childCount > 0
        } ?: false
        if (!next.isFocusable && shown && !hasChildrenThatWantsFocus) return null

        // if not shown then continue because we will "skip" over views to get to a replacement
        if (!shown) {
            // we don't want a while true loop, so we let android decide if we find a recursive view
            if (next == view) return null
            return getNextFocus(root, next, direction, depth + 1)
        }

        (when (next) {
            is ChipGroup -> {
                next.children.firstOrNull { it.isFocusable && it.isShown }
            }

            is NavigationRailView -> {
                next.findViewById(next.selectedItemId) ?: next.findViewById(R.id.navigation_home)
            }

            else -> null
        })?.let {
            return it
        }

        // nothing wrong with the view found, return it
        return next
    }

    /** recursively looks for a next focus up to a depth of 10,
     * this is used to override the normal shit focus system
     * because this application has a lot of invisible views that messes with some tv devices*/
    fun getNextFocus(
        root: Any?,
        view: View?,
        direction: FocusDirection,
        depth: Int = 0
    ): View? {
        // if input is invalid let android decide + depth test to not crash if loop is found
        if (view == null || depth >= 10 || root == null) {
            return null
        }

        var nextId = when (direction) {
            FocusDirection.Start -> {
                if (view.isRtl())
                    view.nextFocusRightId
                else
                    view.nextFocusLeftId
            }

            FocusDirection.Up -> {
                view.nextFocusUpId
            }

            FocusDirection.End -> {
                if (view.isRtl())
                    view.nextFocusLeftId
                else
                    view.nextFocusRightId
            }

            FocusDirection.Down -> {
                view.nextFocusDownId
            }
        }

        if (nextId == NO_ID) {
            // if not specified then use forward id
            nextId = view.nextFocusForwardId
            // if view is still not found to next focus then return and let android decide
            if (nextId == NO_ID)
                return null
        }
        return continueGetNextFocus(root, view, direction, nextId, depth)
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

            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_N -> {
                PlayerEventType.NextEpisode
            }

            KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_B -> {
                PlayerEventType.PrevEpisode
            }

            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                PlayerEventType.Pause
            }

            KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_BUTTON_START -> {
                PlayerEventType.Play
            }

            KeyEvent.KEYCODE_L, KeyEvent.KEYCODE_NUMPAD_7, KeyEvent.KEYCODE_7 -> {
                PlayerEventType.Lock
            }

            KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_MENU -> {
                PlayerEventType.ToggleHide
            }

            KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_VOLUME_MUTE -> {
                PlayerEventType.ToggleMute
            }

            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_NUMPAD_9, KeyEvent.KEYCODE_9 -> {
                PlayerEventType.ShowMirrors
            }
            // OpenSubtitles shortcut
            KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_NUMPAD_8, KeyEvent.KEYCODE_8 -> {
                PlayerEventType.SearchSubtitlesOnline
            }

            KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_NUMPAD_3, KeyEvent.KEYCODE_3 -> {
                PlayerEventType.ShowSpeed
            }

            KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_NUMPAD_0, KeyEvent.KEYCODE_0 -> {
                PlayerEventType.Resize
            }

            KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_NUMPAD_4, KeyEvent.KEYCODE_4 -> {
                PlayerEventType.SkipOp
            }

            KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_NUMPAD_5, KeyEvent.KEYCODE_5 -> {
                PlayerEventType.SkipCurrentChapter
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

    /** overrides focus and custom key events */
    fun dispatchKeyEvent(act: Activity?, event: KeyEvent?): Boolean? {
        if (act == null) return null
        val currentFocus = act.currentFocus

        event?.keyCode?.let { keyCode ->
            if (currentFocus == null || event.action != KeyEvent.ACTION_DOWN) return@let
            val nextView = when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> getNextFocus(
                    act,
                    currentFocus,
                    FocusDirection.Start
                )

                KeyEvent.KEYCODE_DPAD_RIGHT -> getNextFocus(
                    act,
                    currentFocus,
                    FocusDirection.End
                )

                KeyEvent.KEYCODE_DPAD_UP -> getNextFocus(
                    act,
                    currentFocus,
                    FocusDirection.Up
                )

                KeyEvent.KEYCODE_DPAD_DOWN -> getNextFocus(
                    act,
                    currentFocus,
                    FocusDirection.Down
                )

                else -> null
            }
            // println("NEXT FOCUS : $nextView")
            if (nextView != null) {
                nextView.requestFocus()
                keyEventListener?.invoke(Pair(event, true))
                return true
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER &&
                (act.currentFocus is SearchView || act.currentFocus is SearchView.SearchAutoComplete)
            ) {
                UIHelper.showInputMethod(act.currentFocus?.findFocus())
            }

            //println("Keycode: $keyCode")
            //showToast(
            //    this,
            //    "Got Keycode $keyCode | ${KeyEvent.keyCodeToString(keyCode)} \n ${event?.action}",
            //    Toast.LENGTH_LONG
            //)

        }

        // if someone else want to override the focus then don't handle the event as it is already
        // consumed. used in video player
        if (keyEventListener?.invoke(Pair(event, false)) == true) {
            return true
        }
        return null
    }
}
