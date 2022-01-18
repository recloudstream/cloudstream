package com.lagradost.cloudstream3

import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
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
import com.lagradost.cloudstream3.CommonActivity.backEvent
import com.lagradost.cloudstream3.CommonActivity.loadThemes
import com.lagradost.cloudstream3.CommonActivity.onColorSelectedEvent
import com.lagradost.cloudstream3.CommonActivity.onDialogDismissedEvent
import com.lagradost.cloudstream3.CommonActivity.onUserLeaveHint
import com.lagradost.cloudstream3.CommonActivity.updateLocale
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.network.Requests
import com.lagradost.cloudstream3.receivers.VideoDownloadRestartReceiver
import com.lagradost.cloudstream3.syncproviders.OAuth2API.Companion.OAuth2Apis
import com.lagradost.cloudstream3.syncproviders.OAuth2API.Companion.OAuth2accountApis
import com.lagradost.cloudstream3.syncproviders.OAuth2API.Companion.appString
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_NAVIGATE_TO
import com.lagradost.cloudstream3.ui.result.ResultFragment
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTvSettings
import com.lagradost.cloudstream3.utils.AppUtils.isCastApiAvailable
import com.lagradost.cloudstream3.utils.AppUtils.loadCache
import com.lagradost.cloudstream3.utils.AppUtils.loadResult
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.removeKey
import com.lagradost.cloudstream3.utils.DataStoreHelper.setViewPos
import com.lagradost.cloudstream3.utils.InAppUpdater.Companion.runAutoUpdate
import com.lagradost.cloudstream3.utils.UIHelper.checkWrite
import com.lagradost.cloudstream3.utils.UIHelper.getResourceColor
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.UIHelper.requestRW
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_result.*
import java.io.File
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

    private fun updateNavBar(destination: NavDestination) {
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

        val landscape = when (resources.configuration.orientation) {
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
        onUserLeaveHint(this)
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
                    data.getLongExtra(
                        VLC_EXTRA_POSITION_OUT,
                        -1
                    ) //Last position in media when player exited
                val dur: Long =
                    data.getLongExtra(
                        VLC_EXTRA_DURATION_OUT,
                        -1
                    ) //Last position in media when player exited
                val id = getKey<Int>(VLC_LAST_ID_KEY)
                println("SET KEY $id at $pos / $dur")
                if (dur > 0 && pos > 0) {
                    setViewPos(id, pos, dur)
                }
                removeKey(VLC_LAST_ID_KEY)
                ResultFragment.updateUI()
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
        loadCache()
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
        loadThemes(this)
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


        CommonActivity.init(this)

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
        loadCache()

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

        try {
            // this ensures that no unnecessary space is taken
            loadCache()
            File(filesDir, "exoplayer").deleteRecursively() // old cache
            File(cacheDir, "exoplayer").deleteOnExit()      // current cache
        } catch (e: Exception) {
            logError(e)
        }

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