package com.lagradost.cloudstream3

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.view.*
import android.view.KeyEvent.ACTION_DOWN
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.navigation.NavDestination
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.google.android.gms.cast.framework.*
import com.google.android.material.navigationrail.NavigationRailView
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.APIHolder.getApiDubstatusSettings
import com.lagradost.cloudstream3.APIHolder.restrictedApis
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.network.Requests
import com.lagradost.cloudstream3.receivers.VideoDownloadRestartReceiver
import com.lagradost.cloudstream3.syncproviders.OAuth2API.Companion.OAuth2Apis
import com.lagradost.cloudstream3.syncproviders.OAuth2API.Companion.OAuth2accountApis
import com.lagradost.cloudstream3.syncproviders.OAuth2API.Companion.appString
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_NAVIGATE_TO
import com.lagradost.cloudstream3.ui.player.PlayerEventType
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTvSettings
import com.lagradost.cloudstream3.utils.AppUtils.isCastApiAvailable
import com.lagradost.cloudstream3.utils.AppUtils.loadResult
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.removeKey
import com.lagradost.cloudstream3.utils.DataStoreHelper.setViewPos
import com.lagradost.cloudstream3.utils.Event
import com.lagradost.cloudstream3.utils.InAppUpdater.Companion.runAutoUpdate
import com.lagradost.cloudstream3.utils.UIHelper.checkWrite
import com.lagradost.cloudstream3.utils.UIHelper.getResourceColor
import com.lagradost.cloudstream3.utils.UIHelper.hasPIPPermission
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.UIHelper.requestRW
import com.lagradost.cloudstream3.utils.UIHelper.shouldShowPIPMode
import com.lagradost.cloudstream3.utils.UIHelper.showInputMethod
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_result.*
import java.util.*
import kotlin.concurrent.thread


const val VLC_PACKAGE = "org.videolan.vlc"
const val VLC_INTENT_ACTION_RESULT = "org.videolan.vlc.player.result"
val VLC_COMPONENT: ComponentName =
    ComponentName(VLC_PACKAGE, "org.videolan.vlc.gui.video.VideoPlayerActivity")
const val VLC_REQUEST_CODE = 42

const val VLC_FROM_START = -1
const val VLC_FROM_PROGRESS = -2
const val VLC_EXTRA_POSITION_OUT = "extra_position"
const val VLC_EXTRA_DURATION_OUT = "extra_duration"
const val VLC_LAST_ID_KEY = "vlc_last_open_id"
// Short name for requests client to make it nicer to use
var app = Requests()


class MainActivity : AppCompatActivity(), ColorPickerDialogListener {
    override fun onColorSelected(dialogId: Int, color: Int) {
        onColorSelectedEvent.invoke(Pair(dialogId, color))
    }

    override fun onDialogDismissed(dialogId: Int) {
        onDialogDismissedEvent.invoke(dialogId)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateLocale() // android fucks me by chaining lang when rotating the phone
        findNavController(R.id.nav_host_fragment).currentDestination?.let { updateNavBar(it) }
    }

    private fun updateNavBar(destination : NavDestination) {
        this.hideKeyboard()

        // Fucks up anime info layout since that has its own layout
        cast_mini_controller_holder?.isVisible =
            !listOf(R.id.navigation_results, R.id.navigation_player).contains(destination.id)

        val isNavVisible = listOf(
            R.id.navigation_home,
            R.id.navigation_search,
            R.id.navigation_downloads,
            R.id.navigation_settings,
            R.id.navigation_download_child
        ).contains(destination.id)

        val landscape = when(resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                true
            }
            Configuration.ORIENTATION_PORTRAIT -> {
                false
            }
            else -> {
                false
            }
        }

        nav_view?.isVisible = isNavVisible && !landscape
        nav_rail_view?.isVisible = isNavVisible && landscape
    }

    //private var mCastSession: CastSession? = null
    lateinit var mSessionManager: SessionManager
    private val mSessionManagerListener: SessionManagerListener<Session> by lazy { SessionManagerListenerImpl() }

    private inner class SessionManagerListenerImpl : SessionManagerListener<Session> {
        override fun onSessionStarting(session: Session) {
        }

        override fun onSessionStarted(session: Session, sessionId: String) {
            invalidateOptionsMenu()
        }

        override fun onSessionStartFailed(session: Session, i: Int) {
        }

        override fun onSessionEnding(session: Session) {
        }

        override fun onSessionResumed(session: Session, wasSuspended: Boolean) {
            invalidateOptionsMenu()
        }

        override fun onSessionResumeFailed(session: Session, i: Int) {
        }

        override fun onSessionSuspended(session: Session, i: Int) {
        }

        override fun onSessionEnded(session: Session, error: Int) {
        }

        override fun onSessionResuming(session: Session, s: String) {
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            if (isCastApiAvailable()) {
                //mCastSession = mSessionManager.currentCastSession
                mSessionManager.addSessionManagerListener(mSessionManagerListener)
            }
        } catch (e: Exception) {
            logError(e)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            if (isCastApiAvailable()) {
                mSessionManager.removeSessionManagerListener(mSessionManagerListener)
                //mCastSession = null
            }
        } catch (e: Exception) {
            logError(e)
        }
    }

    enum class FocusDirection {
        Left,
        Right,
        Up,
        Down,
    }

    private fun getNextFocus(view: View?, direction: FocusDirection, depth: Int = 0): Int? {
        if (view == null || depth >= 10) {
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
            val next = findViewById<View?>(nextId)
            //println("NAME: ${next.accessibilityClassName} | ${next?.isShown}" )

            if (next?.isShown == false) {
                getNextFocus(next, direction, depth + 1)
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

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        event?.keyCode?.let { keyCode ->
            when (event.action) {
                ACTION_DOWN -> {
                    if (currentFocus != null) {
                        val next = when (keyCode) {
                            KeyEvent.KEYCODE_DPAD_LEFT -> getNextFocus(currentFocus, FocusDirection.Left)
                            KeyEvent.KEYCODE_DPAD_RIGHT -> getNextFocus(currentFocus, FocusDirection.Right)
                            KeyEvent.KEYCODE_DPAD_UP -> getNextFocus(currentFocus, FocusDirection.Up)
                            KeyEvent.KEYCODE_DPAD_DOWN -> getNextFocus(currentFocus, FocusDirection.Down)

                            else -> null
                        }

                        if (next != null && next != -1) {
                            val nextView = findViewById<View?>(next)
                            if(nextView != null) {
                                nextView.requestFocus()
                                return true
                            }
                        }

                        when (keyCode) {

                            KeyEvent.KEYCODE_DPAD_CENTER -> {
                                println("DPAD PRESSED $currentFocus")
                                if (currentFocus is SearchView || currentFocus is SearchView.SearchAutoComplete) {
                                    println("current PRESSED")
                                    showInputMethod(currentFocus?.findFocus())
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

        if (keyEventListener?.invoke(event) == true) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
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

        return super.onKeyDown(keyCode, event)
    }

    companion object {
        fun Activity?.getCastSession(): CastSession? {
            return (this as MainActivity?)?.mSessionManager?.currentCastSession
        }

        var canEnterPipMode: Boolean = false
        var canShowPipMode: Boolean = false
        var isInPIPMode: Boolean = false

        val backEvent = Event<Boolean>()
        val onColorSelectedEvent = Event<Pair<Int, Int>>()
        val onDialogDismissedEvent = Event<Int>()

        var playerEventListener: ((PlayerEventType) -> Unit)? = null
        var keyEventListener: ((KeyEvent?) -> Boolean)? = null


        var currentToast: Toast? = null

        fun showToast(act: Activity?, @StringRes message: Int, duration: Int) {
            if (act == null) return
            showToast(act, act.getString(message), duration)
        }

        fun showToast(act: Activity?, message: String?, duration: Int? = null) {
            if (act == null || message == null) return
            try {
                currentToast?.cancel()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                val inflater = act.getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater

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
                toast.show()
                currentToast = toast
            } catch (e: Exception) {

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
    }

    private fun enterPIPMode() {
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

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (canEnterPipMode && canShowPipMode) {
            enterPIPMode()
        }
    }

    override fun onBackPressed() {
        this.updateLocale()
        backEvent.invoke(true)
        super.onBackPressed()
        this.updateLocale()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (VLC_REQUEST_CODE == requestCode) {
            if (resultCode == RESULT_OK && data != null) {
                val pos: Long =
                    data.getLongExtra(VLC_EXTRA_POSITION_OUT, -1) //Last position in media when player exited
                val dur: Long =
                    data.getLongExtra(VLC_EXTRA_DURATION_OUT, -1) //Last position in media when player exited
                val id = getKey<Int>(VLC_LAST_ID_KEY)
                println("SET KEY $id at $pos / $dur")
                if (dur > 0 && pos > 0) {
                    setViewPos(id, pos, dur)
                }
                removeKey(VLC_LAST_ID_KEY)
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        val broadcastIntent = Intent()
        broadcastIntent.action = "restart_service"
        broadcastIntent.setClass(this, VideoDownloadRestartReceiver::class.java)
        this.sendBroadcast(broadcastIntent)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent?) {
        handleAppIntent(intent)
        super.onNewIntent(intent)
    }

    private fun handleAppIntent(intent: Intent?) {
        if (intent == null) return
        val str = intent.dataString
        if (str != null) {
            if (str.contains(appString)) {
                for (api in OAuth2Apis) {
                    if (str.contains("/${api.redirectUrl}")) {
                        api.handleRedirect(str)
                    }
                }
            } else {
                if (str.startsWith(DOWNLOAD_NAVIGATE_TO)) {
                    this.navigate(R.id.navigation_downloads)
                } else {
                    for (api in apis) {
                        if (str.startsWith(api.mainUrl)) {
                            loadResult(str, api.name)
                            break
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // init accounts
        for (api in OAuth2accountApis) {
            api.init()
        }

        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)

        val currentTheme = when (settingsManager.getString(getString(R.string.app_theme_key), "AmoledLight")) {
            "Black" -> R.style.AppTheme
            "Light" -> R.style.LightMode
            "Amoled" -> R.style.AmoledMode
            "AmoledLight" -> R.style.AmoledModeLight
            else -> R.style.AppTheme
        }

        val currentOverlayTheme = when (settingsManager.getString(getString(R.string.primary_color_key), "Normal")) {
            "Normal" -> R.style.OverlayPrimaryColorNormal
            "Blue" -> R.style.OverlayPrimaryColorBlue
            "Purple" -> R.style.OverlayPrimaryColorPurple
            "Green" -> R.style.OverlayPrimaryColorGreen
            "GreenApple" -> R.style.OverlayPrimaryColorGreenApple
            "Red" -> R.style.OverlayPrimaryColorRed
            "Banana" -> R.style.OverlayPrimaryColorBanana
            "Party" -> R.style.OverlayPrimaryColorParty
            else -> R.style.OverlayPrimaryColorNormal
        }

        theme.applyStyle(currentTheme, true)
        theme.applyStyle(currentOverlayTheme, true)

        theme.applyStyle(
            R.style.LoadedStyle,
            true
        ) // THEME IS SET BEFORE VIEW IS CREATED TO APPLY THE THEME TO THE MAIN VIEW

        updateLocale()
        app.initClient(this)
        super.onCreate(savedInstanceState)
        try {
            if (isCastApiAvailable()) {
                mSessionManager = CastContext.getSharedInstance(this).sessionManager
            }
        } catch (e: Exception) {
            logError(e)
        }

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)

        if (isTvSettings()) {
            setContentView(R.layout.activity_main_tv)
        } else {
            setContentView(R.layout.activity_main)
        }

        //  val navView: BottomNavigationView = findViewById(R.id.nav_view)

        //https://stackoverflow.com/questions/52594181/how-to-know-if-user-has-disabled-picture-in-picture-feature-permission
        //https://developer.android.com/guide/topics/ui/picture-in-picture
        canShowPipMode =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && // OS SUPPORT
                    packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) && // HAS FEATURE, MIGHT BE BLOCKED DUE TO POWER DRAIN
                    hasPIPPermission() // CHECK IF FEATURE IS ENABLED IN SETTINGS

        val navController = findNavController(R.id.nav_host_fragment)

        /*navOptions = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setEnterAnim(R.anim.nav_enter_anim)
            .setExitAnim(R.anim.nav_exit_anim)
            .setPopEnterAnim(R.anim.nav_pop_enter)
            .setPopExitAnim(R.anim.nav_pop_exit)
            .setPopUpTo(navController.graph.startDestination, false)
            .build()*/
        nav_view?.setupWithNavController(navController)
        val navRail = findViewById<NavigationRailView?>(R.id.nav_rail_view)
        navRail?.setupWithNavController(navController)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            updateNavBar(destination)
        }

        /*nav_view.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    navController.navigate(R.id.navigation_home, null, navOptions)
                }
                R.id.navigation_search -> {
                    navController.navigate(R.id.navigation_search, null, navOptions)
                }
                R.id.navigation_downloads -> {
                    navController.navigate(R.id.navigation_downloads, null, navOptions)
                }
                R.id.navigation_settings -> {
                    navController.navigate(R.id.navigation_settings, null, navOptions)
                }
            }
            true
        }*/

        val rippleColor = ColorStateList.valueOf(getResourceColor(R.attr.colorPrimary, 0.1f))
        nav_view?.itemRippleColor = rippleColor
        navRail?.itemRippleColor = rippleColor

        if (!checkWrite()) {
            requestRW()
            if (checkWrite()) return
        }
        CastButtonFactory.setUpMediaRouteButton(this, media_route_button)

        // THIS IS CURRENTLY REMOVED BECAUSE HIGHER VERS OF ANDROID NEEDS A NOTIFICATION
        //if (!VideoDownloadManager.isMyServiceRunning(this, VideoDownloadKeepAliveService::class.java)) {
        //    val mYourService = VideoDownloadKeepAliveService()
        //    val mServiceIntent = Intent(this, mYourService::class.java).putExtra(START_VALUE_KEY, RESTART_ALL_DOWNLOADS_AND_QUEUE)
        //    this.startService(mServiceIntent)
        //}
//settingsManager.getBoolean("disable_automatic_data_downloads", true) &&

        // TODO RETURN TO TRUE
        /*
        if (isUsingMobileData()) {
            Toast.makeText(this, "Downloads not resumed on mobile data", Toast.LENGTH_LONG).show()
        } else {
            val keys = getKeys(VideoDownloadManager.KEY_RESUME_PACKAGES)
            val resumePkg = keys.mapNotNull { k -> getKey<VideoDownloadManager.DownloadResumePackage>(k) }

            // To remove a bug where this is permanent
            removeKeys(VideoDownloadManager.KEY_RESUME_PACKAGES)

            for (pkg in resumePkg) { // ADD ALL CURRENT DOWNLOADS
                VideoDownloadManager.downloadFromResume(this, pkg, false)
            }

            // ADD QUEUE
            // array needed because List gets cast exception to linkedList for some unknown reason
            val resumeQueue =
                getKey<Array<VideoDownloadManager.DownloadQueueResumePackage>>(VideoDownloadManager.KEY_RESUME_QUEUE_PACKAGES)

            resumeQueue?.sortedBy { it.index }?.forEach {
                VideoDownloadManager.downloadFromResume(this, it.pkg)
            }
        }*/


        /*
        val castContext = CastContext.getSharedInstance(applicationContext)
         fun buildMediaQueueItem(video: String): MediaQueueItem {
           // val movieMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_PHOTO)
            //movieMetadata.putString(MediaMetadata.KEY_TITLE, "CloudStream")
            val mediaInfo = MediaInfo.Builder(Uri.parse(video).toString())
                .setStreamType(MediaInfo.STREAM_TYPE_NONE)
                .setContentType(MimeTypes.IMAGE_JPEG)
               // .setMetadata(movieMetadata).build()
                .build()
            return MediaQueueItem.Builder(mediaInfo).build()
        }*/
        /*
        castContext.addCastStateListener { state ->
            if (state == CastState.CONNECTED) {
                println("TESTING")
                val isCasting = castContext?.sessionManager?.currentCastSession?.remoteMediaClient?.currentItem != null
                if(!isCasting) {
                    val castPlayer = CastPlayer(castContext)
                    println("LOAD ITEM")

                    castPlayer.loadItem(buildMediaQueueItem("https://cdn.discordapp.com/attachments/551382684560261121/730169809408622702/ChromecastLogo6.png"),0)
                }
            }
        }*/
        /*thread {
            createISO()
        }*/

        var providersString = "Current providers are:\n"
        var providersAndroidManifestString = "Current androidmanifest should be:\n"
        for (api in apis) {
            providersString += "+ ${api.mainUrl}\n"
            providersAndroidManifestString += "<data android:scheme=\"https\" android:host=\"${
                api.mainUrl.removePrefix(
                    "https://"
                )
            }\" android:pathPrefix=\"/\"/>\n"
        }

        for (api in restrictedApis) {
            providersString += "+ ${api.mainUrl}\n"
            providersAndroidManifestString += "<data android:scheme=\"https\" android:host=\"${
                api.mainUrl.removePrefix(
                    "https://"
                )
            }\" android:pathPrefix=\"/\"/>\n"
        }
        println(providersString)


        println(providersAndroidManifestString)

        handleAppIntent(intent)

        thread {
            runAutoUpdate()
        }

        // must give benenes to get beta providers
        try {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
            val count = settingsManager.getInt(getString(R.string.benene_count), 0)
            if (count > 30 && restrictedApis.size > 0 && !apis.contains(restrictedApis.first()))
                apis.addAll(restrictedApis)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        APIRepository.dubStatusActive = getApiDubstatusSettings()


/*
        val relativePath = (Environment.DIRECTORY_DOWNLOADS) + File.separatorChar
        val displayName = "output.dex" //""output.dex"
        val file =  getExternalFilesDir(null)?.absolutePath + File.separatorChar + displayName//"${Environment.getExternalStorageDirectory()}${File.separatorChar}$relativePath$displayName"
        println(file)

        val realFile = File(file)
        println("REAALFILE: ${realFile.exists()} at ${realFile.length()}"  )
        val src = ExtensionManager.getSourceFromDex(this, "com.example.testdex2.TestClassToDex", File(file))
        val output = src?.doMath()
        println("MASTER OUTPUT = $output")*/
    }
}