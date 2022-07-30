package com.lagradost.cloudstream3

import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.android.gms.cast.framework.*
import com.google.android.material.navigationrail.NavigationRailView
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import com.lagradost.cloudstream3.APIHolder.allProviders
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.APIHolder.getApiDubstatusSettings
import com.lagradost.cloudstream3.APIHolder.initAll
import com.lagradost.cloudstream3.APIHolder.updateHasTrailers
import com.lagradost.cloudstream3.CommonActivity.loadThemes
import com.lagradost.cloudstream3.CommonActivity.onColorSelectedEvent
import com.lagradost.cloudstream3.CommonActivity.onDialogDismissedEvent
import com.lagradost.cloudstream3.CommonActivity.onUserLeaveHint
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.CommonActivity.updateLocale
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import com.lagradost.cloudstream3.network.initClient
import com.lagradost.cloudstream3.receivers.VideoDownloadRestartReceiver
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.OAuth2Apis
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.accountManagers
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.appString
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.inAppAuths
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_NAVIGATE_TO
import com.lagradost.cloudstream3.ui.result.ResultFragment
import com.lagradost.cloudstream3.ui.search.SearchResultBuilder
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isEmulatorSettings
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTvSettings
import com.lagradost.cloudstream3.ui.settings.SettingsGeneral
import com.lagradost.cloudstream3.ui.setup.HAS_DONE_SETUP_KEY
import com.lagradost.cloudstream3.utils.AppUtils.isCastApiAvailable
import com.lagradost.cloudstream3.utils.AppUtils.loadCache
import com.lagradost.cloudstream3.utils.AppUtils.loadResult
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.BackupUtils.setUpBackup
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.DataStore
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.removeKey
import com.lagradost.cloudstream3.utils.DataStore.setKey
import com.lagradost.cloudstream3.utils.DataStoreHelper.migrateResumeWatching
import com.lagradost.cloudstream3.utils.DataStoreHelper.setViewPos
import com.lagradost.cloudstream3.utils.IOnBackPressed
import com.lagradost.cloudstream3.utils.InAppUpdater.Companion.runAutoUpdate
import com.lagradost.cloudstream3.utils.UIHelper.changeStatusBarState
import com.lagradost.cloudstream3.utils.UIHelper.checkWrite
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.utils.UIHelper.getResourceColor
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.UIHelper.requestRW
import com.lagradost.cloudstream3.utils.USER_PROVIDER_API
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.ResponseParser
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_result_swipe.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import java.io.File
import kotlin.concurrent.thread
import kotlin.reflect.KClass


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

var app = Requests(responseParser = object : ResponseParser {
    val mapper: ObjectMapper = jacksonObjectMapper().configure(
        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
        false
    )

    override fun <T : Any> parse(text: String, kClass: KClass<T>): T {
        return mapper.readValue(text, kClass.java)
    }

    override fun <T : Any> parseSafe(text: String, kClass: KClass<T>): T? {
        return try {
            mapper.readValue(text, kClass.java)
        } catch (e: Exception) {
            null
        }
    }

    override fun writeValueAsString(obj: Any): String {
        return mapper.writeValueAsString(obj)
    }
}).apply {
    defaultHeaders = mapOf("user-agent" to USER_AGENT)
}

class MainActivity : AppCompatActivity(), ColorPickerDialogListener {
    companion object {
        const val TAG = "MAINACT"
    }

    override fun onColorSelected(dialogId: Int, color: Int) {
        onColorSelectedEvent.invoke(Pair(dialogId, color))
    }

    override fun onDialogDismissed(dialogId: Int) {
        onDialogDismissedEvent.invoke(dialogId)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateLocale() // android fucks me by chaining lang when rotating the phone

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navHostFragment.navController.currentDestination?.let { updateNavBar(it) }
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
            R.id.navigation_download_child,
            R.id.navigation_subtitles,
            R.id.navigation_chrome_subtitles,
            R.id.navigation_settings_player,
            R.id.navigation_settings_updates,
            R.id.navigation_settings_ui,
            R.id.navigation_settings_account,
            R.id.navigation_settings_lang,
            R.id.navigation_settings_general,
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

    private fun backPressed() {
        this.window?.navigationBarColor =
            this.colorFromAttribute(R.attr.primaryGrayBackground)
        this.updateLocale()
        super.onBackPressed()
        this.updateLocale()
    }

    override fun onBackPressed() {
        ((supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment?)?.childFragmentManager?.primaryNavigationFragment as? IOnBackPressed)?.onBackPressed()
            ?.let { runNormal ->
                if (runNormal) backPressed()
            } ?: run {
            backPressed()
        }
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
                        ioSafe {
                            Log.i(TAG, "handleAppIntent $str")
                            val isSuccessful = api.handleRedirect(str)

                            if (isSuccessful) {
                                Log.i(TAG, "authenticated ${api.name}")
                            } else {
                                Log.i(TAG, "failed to authenticate ${api.name}")
                            }

                            this.runOnUiThread {
                                try {
                                    showToast(
                                        this,
                                        getString(if (isSuccessful) R.string.authenticated_user else R.string.authenticated_user_fail).format(
                                            api.name
                                        )
                                    )
                                } catch (e: Exception) {
                                    logError(e) // format might fail
                                }
                            }
                        }
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

    private fun NavDestination.matchDestination(@IdRes destId: Int): Boolean =
        hierarchy.any { it.id == destId }

    private fun onNavDestinationSelected(item: MenuItem, navController: NavController): Boolean {
        val builder = NavOptions.Builder().setLaunchSingleTop(true).setRestoreState(true)
            .setEnterAnim(R.anim.enter_anim)
            .setExitAnim(R.anim.exit_anim)
            .setPopEnterAnim(R.anim.pop_enter)
            .setPopExitAnim(R.anim.pop_exit)
        if (item.order and Menu.CATEGORY_SECONDARY == 0) {
            builder.setPopUpTo(
                navController.graph.findStartDestination().id,
                inclusive = false,
                saveState = true
            )
        }
        val options = builder.build()
        return try {
            navController.navigate(item.itemId, null, options)
            navController.currentDestination?.matchDestination(item.itemId) == true
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    fun test() {
        /*thread {
            val youtubeLink = "https://www.youtube.com/watch?v=Zxem9rqJ5S0"

            val url = YoutubeStreamLinkHandlerFactory.getInstance().fromUrl(youtubeLink)
            println("ID:::: ${url.id}")
            NewPipe.init(DownloaderTestImpl.getInstance())
            val service = ServiceList.YouTube
            val s = object : YoutubeStreamExtractor(
                service,
                url
            ) {

            }
            s.fetchPage()
            val streams = s.videoStreams
            println("STREAMS: ${streams.map { "url = "+ it.url + " extra= " + it.height + "|" + it.isVideoOnly + "\n" }}")
        }*/
        /*
        runBlocking {

            val query = """
            query {
                searchShows(search: "spider", limit: 10) {
                    id
                    name
                    originalName
                }
            }
            """
            val data =
                mapOf(
                    "query" to query,
                    //"variables" to
                    //        mapOf(
                    //            "name" to name,
                     //       ).toJson()
                )
            val txt = app.post(
                "http://api.anime-skip.com/graphql",
                headers = mapOf(
                    "X-Client-ID" to "",
                    "Content-Type" to "application/json",
                    "Accept" to "application/json",
                ),
                json = data
            )
            println("TEXT: $txt")
        }*/
        /*runBlocking {
            //https://test.api.anime-skip.com/graphiql
            val txt = app.get(
                "https://api.anime-skip.com/status",
                headers = mapOf("X-Client-ID" to "")
            )
            println("TEXT: $txt")
        }*/
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // init accounts
        for (api in accountManagers) {
            api.init()
        }

        ioSafe {
            inAppAuths.apmap { api ->
                try {
                    api.initialize()
                } catch (e: Exception) {
                    logError(e)
                }
            }
        }

        SearchResultBuilder.updateCache(this)

        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        val downloadFromGithub = try {
            settingsManager.getBoolean(getString(R.string.killswitch_key), true)
        } catch (e: Exception) {
            logError(e)
            false
        }

        // must give benenes to get beta providers
        val hasBenene = try {
            val count = settingsManager.getInt(getString(R.string.benene_count), 0)
            count > 30
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }

        try {
            getKey<Array<SettingsGeneral.CustomSite>>(USER_PROVIDER_API)?.let { list ->
                list.forEach { custom ->
                    allProviders.firstOrNull { it.javaClass.simpleName == custom.parentJavaClass }
                        ?.let {
                            allProviders.add(it.javaClass.newInstance().apply {
                                name = custom.name
                                lang = custom.lang
                                mainUrl = custom.url.trimEnd('/')
                                canBeOverridden = false
                            })
                        }
                }
            }
            apis = allProviders
            APIHolder.apiMap = null
        } catch (e: Exception) {
            logError(e)
        }

        // this pulls the latest data so ppl don't have to update to simply change provider url
        if (downloadFromGithub) {
            try {
                runBlocking {
                    withContext(Dispatchers.IO) {
                        try {
                            val cacheStr: String? = getKey(PROVIDER_STATUS_KEY)
                            val cache: HashMap<String, ProvidersInfoJson>? =
                                cacheStr?.let { tryParseJson(cacheStr) }
                            if (cache != null) {
                                // if cache is found then spin up a new request, but dont wait
                                main {
                                    try {
                                        val txt = app.get(PROVIDER_STATUS_URL).text
                                        val newCache =
                                            tryParseJson<HashMap<String, ProvidersInfoJson>>(txt)
                                        setKey(PROVIDER_STATUS_KEY, txt)
                                        MainAPI.overrideData = newCache // update all new providers

                                        initAll()
                                        for (api in apis) { // update current providers
                                            newCache?.get(api.javaClass.simpleName)
                                                ?.let { data ->
                                                    api.overrideWithNewData(data)
                                                }
                                        }
                                    } catch (e: Exception) {
                                        logError(e)
                                    }
                                }
                                cache
                            } else {
                                // if it is the first time the user has used the app then wait for a request to update all providers
                                val txt = app.get(PROVIDER_STATUS_URL).text
                                setKey(PROVIDER_STATUS_KEY, txt)
                                val newCache = tryParseJson<HashMap<String, ProvidersInfoJson>>(txt)
                                newCache
                            }?.let { providersJsonMap ->
                                MainAPI.overrideData = providersJsonMap
                                initAll()
                                val acceptableProviders =
                                    providersJsonMap.filter { it.value.status == PROVIDER_STATUS_OK || it.value.status == PROVIDER_STATUS_SLOW }
                                        .map { it.key }.toSet()

                                val restrictedApis =
                                    if (hasBenene) providersJsonMap.filter { it.value.status == PROVIDER_STATUS_BETA_ONLY }
                                        .map { it.key }.toSet() else emptySet()

                                apis = allProviders.filter { api ->
                                    val name = api.javaClass.simpleName
                                    // if the provider does not exist in the json file, then it is shown by default
                                    !providersJsonMap.containsKey(name) || acceptableProviders.contains(
                                        name
                                    ) || restrictedApis.contains(name)
                                }
                            }
                        } catch (e: Exception) {
                            logError(e)
                        }
                    }
                }
            } catch (e: Exception) {
                initAll()
                apis = allProviders
                e.printStackTrace()
                logError(e)
            }
        } else {
            initAll()
            apis = allProviders
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

        changeStatusBarState(isEmulatorSettings())

        //  val navView: BottomNavigationView = findViewById(R.id.nav_view)
        setUpBackup()

        CommonActivity.init(this)
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        //val navController = findNavController(R.id.nav_host_fragment)

        /*navOptions = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setEnterAnim(R.anim.nav_enter_anim)
            .setExitAnim(R.anim.nav_exit_anim)
            .setPopEnterAnim(R.anim.nav_pop_enter)
            .setPopExitAnim(R.anim.nav_pop_exit)
            .setPopUpTo(navController.graph.startDestination, false)
            .build()*/
        nav_view?.setupWithNavController(navController)
        val nav_rail = findViewById<NavigationRailView?>(R.id.nav_rail_view)
        nav_rail?.setupWithNavController(navController)

        nav_rail?.setOnItemSelectedListener { item ->
            onNavDestinationSelected(
                item,
                navController
            )
        }
        nav_view?.setOnItemSelectedListener { item ->
            onNavDestinationSelected(
                item,
                navController
            )
        }
        navController.addOnDestinationChangedListener { _, destination, _ ->
            updateNavBar(destination)
        }

        loadCache()
        test()
        updateHasTrailers()
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
        nav_rail?.itemRippleColor = rippleColor
        nav_rail?.itemActiveIndicatorColor = rippleColor
        nav_view?.itemActiveIndicatorColor = rippleColor

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

        if (BuildConfig.DEBUG) {
            var providersAndroidManifestString = "Current androidmanifest should be:\n"
            for (api in allProviders) {
                providersAndroidManifestString += "<data android:scheme=\"https\" android:host=\"${
                    api.mainUrl.removePrefix(
                        "https://"
                    )
                }\" android:pathPrefix=\"/\"/>\n"
            }

            println(providersAndroidManifestString)
        }

        handleAppIntent(intent)

        thread {
            runAutoUpdate()
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
        println("Loaded everything")

        ioSafe {
            migrateResumeWatching()
        }

        try {
            if (getKey(HAS_DONE_SETUP_KEY, false) != true) {
                navController.navigate(R.id.navigation_setup_language)
            }
        } catch (e: Exception) {
            logError(e)
        } finally {
            setKey(HAS_DONE_SETUP_KEY, true)
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
