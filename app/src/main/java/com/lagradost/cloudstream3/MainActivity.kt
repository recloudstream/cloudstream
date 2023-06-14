package com.lagradost.cloudstream3

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.IdRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
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
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.navigationrail.NavigationRailView
import com.google.android.material.snackbar.Snackbar
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import com.lagradost.cloudstream3.APIHolder.allProviders
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.APIHolder.getApiDubstatusSettings
import com.lagradost.cloudstream3.APIHolder.initAll
import com.lagradost.cloudstream3.APIHolder.updateHasTrailers
import com.lagradost.cloudstream3.AcraApplication.Companion.removeKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.CommonActivity.loadThemes
import com.lagradost.cloudstream3.CommonActivity.onColorSelectedEvent
import com.lagradost.cloudstream3.CommonActivity.onDialogDismissedEvent
import com.lagradost.cloudstream3.CommonActivity.onUserLeaveHint
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.CommonActivity.updateLocale
import com.lagradost.cloudstream3.mvvm.*
import com.lagradost.cloudstream3.network.initClient
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.plugins.PluginManager.loadAllOnlinePlugins
import com.lagradost.cloudstream3.plugins.PluginManager.loadSinglePlugin
import com.lagradost.cloudstream3.receivers.VideoDownloadRestartReceiver
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.OAuth2Apis
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.accountManagers
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.appString
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.appStringPlayer
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.appStringRepo
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.appStringResumeWatching
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.appStringSearch
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.inAppAuths
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_NAVIGATE_TO
import com.lagradost.cloudstream3.ui.home.HomeViewModel
import com.lagradost.cloudstream3.ui.player.BasicLink
import com.lagradost.cloudstream3.ui.player.GeneratorPlayer
import com.lagradost.cloudstream3.ui.player.LinkGenerator
import com.lagradost.cloudstream3.ui.result.ResultViewModel2
import com.lagradost.cloudstream3.ui.result.START_ACTION_RESUME_LATEST
import com.lagradost.cloudstream3.ui.result.setImage
import com.lagradost.cloudstream3.ui.result.setText
import com.lagradost.cloudstream3.ui.search.SearchFragment
import com.lagradost.cloudstream3.ui.search.SearchResultBuilder
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isEmulatorSettings
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTrueTvSettings
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTvSettings
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.updateTv
import com.lagradost.cloudstream3.ui.settings.SettingsGeneral
import com.lagradost.cloudstream3.ui.setup.HAS_DONE_SETUP_KEY
import com.lagradost.cloudstream3.ui.setup.SetupFragmentExtensions
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.html
import com.lagradost.cloudstream3.utils.AppUtils.isCastApiAvailable
import com.lagradost.cloudstream3.utils.AppUtils.isNetworkAvailable
import com.lagradost.cloudstream3.utils.AppUtils.loadCache
import com.lagradost.cloudstream3.utils.AppUtils.loadRepository
import com.lagradost.cloudstream3.utils.AppUtils.loadResult
import com.lagradost.cloudstream3.utils.AppUtils.loadSearchResult
import com.lagradost.cloudstream3.utils.AppUtils.setDefaultFocus
import com.lagradost.cloudstream3.utils.BackupUtils.setUpBackup
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.setKey
import com.lagradost.cloudstream3.utils.DataStoreHelper.migrateResumeWatching
import com.lagradost.cloudstream3.utils.InAppUpdater.Companion.runAutoUpdate
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.UIHelper.changeStatusBarState
import com.lagradost.cloudstream3.utils.UIHelper.checkWrite
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.getResourceColor
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.UIHelper.requestRW
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.ResponseParser
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.bottom_resultview_preview.*
import kotlinx.android.synthetic.main.fragment_result_swipe.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.Charset
import kotlin.reflect.KClass
import kotlin.system.exitProcess


//https://github.com/videolan/vlc-android/blob/3706c4be2da6800b3d26344fc04fab03ffa4b860/application/vlc-android/src/org/videolan/vlc/gui/video/VideoPlayerActivity.kt#L1898
//https://wiki.videolan.org/Android_Player_Intents/

//https://github.com/mpv-android/mpv-android/blob/0eb3cdc6f1632636b9c30d52ec50e4b017661980/app/src/main/java/is/xyz/mpv/MPVActivity.kt#L904
//https://mpv-android.github.io/mpv-android/intent.html

// https://www.webvideocaster.com/integrations

//https://github.com/jellyfin/jellyfin-android/blob/6cbf0edf84a3da82347c8d59b5d5590749da81a9/app/src/main/java/org/jellyfin/mobile/bridge/ExternalPlayer.kt#L225

const val VLC_PACKAGE = "org.videolan.vlc"
const val MPV_PACKAGE = "is.xyz.mpv"
const val WEB_VIDEO_CAST_PACKAGE = "com.instantbits.cast.webvideo"

val VLC_COMPONENT = ComponentName(VLC_PACKAGE, "$VLC_PACKAGE.gui.video.VideoPlayerActivity")
val MPV_COMPONENT = ComponentName(MPV_PACKAGE, "$MPV_PACKAGE.MPVActivity")

//TODO REFACTOR AF
open class ResultResume(
    val packageString: String,
    val action: String = Intent.ACTION_VIEW,
    val position: String? = null,
    val duration: String? = null,
    var launcher: ActivityResultLauncher<Intent>? = null,
) {
    val defaultTime = -1L

    val lastId get() = "${packageString}_last_open_id"
    suspend fun launch(id: Int?, callback: suspend Intent.() -> Unit) {
        val intent = Intent(action)

        if (id != null)
            setKey(lastId, id)
        else
            removeKey(lastId)

        intent.setPackage(packageString)
        callback.invoke(intent)
        launcher?.launch(intent)
    }

    open fun getPosition(intent: Intent?): Long {
        return defaultTime
    }

    open fun getDuration(intent: Intent?): Long {
        return defaultTime
    }
}

val VLC = object : ResultResume(
    VLC_PACKAGE,
    // Android 13 intent restrictions fucks up specifically launching the VLC player
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        "org.videolan.vlc.player.result"
    } else {
        Intent.ACTION_VIEW
    },
    "extra_position",
    "extra_duration",
) {
    override fun getPosition(intent: Intent?): Long {
        return intent?.getLongExtra(this.position, defaultTime) ?: defaultTime
    }

    override fun getDuration(intent: Intent?): Long {
        return intent?.getLongExtra(this.duration, defaultTime) ?: defaultTime
    }
}

val MPV = object : ResultResume(
    MPV_PACKAGE,
    //"is.xyz.mpv.MPVActivity.result", // resume not working :pensive:
    position = "position",
    duration = "duration",
) {
    override fun getPosition(intent: Intent?): Long {
        return intent?.getIntExtra(this.position, defaultTime.toInt())?.toLong() ?: defaultTime
    }

    override fun getDuration(intent: Intent?): Long {
        return intent?.getIntExtra(this.duration, defaultTime.toInt())?.toLong() ?: defaultTime
    }
}

val WEB_VIDEO = ResultResume(WEB_VIDEO_CAST_PACKAGE)

val resumeApps = arrayOf(
    VLC, MPV, WEB_VIDEO
)

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

        /**
         * Setting this will automatically enter the query in the search
         * next time the search fragment is opened.
         * This variable will clear itself after one use. Null does nothing.
         *
         * This is a very bad solution but I was unable to find a better one.
         **/
        private var nextSearchQuery: String? = null

        /**
         * Fires every time a new batch of plugins have been loaded, no guarantee about how often this is run and on which thread
         * Boolean signifies if stuff should be force reloaded (true if force reload, false if reload when necessary).
         *
         * The force reloading are used for plugin development to instantly reload the page on deployWithAdb
         * */
        val afterPluginsLoadedEvent = Event<Boolean>()
        val mainPluginsLoadedEvent =
            Event<Boolean>() // homepage api, used to speed up time to load for homepage
        val afterRepositoryLoadedEvent = Event<Boolean>()

        // kinda shitty solution, but cant com main->home otherwise for popups
        val bookmarksUpdatedEvent = Event<Boolean>()


        /**
         * @return true if the str has launched an app task (be it successful or not)
         * @param isWebview does not handle providers and opening download page if true. Can still add repos and login.
         * */
        fun handleAppIntentUrl(
            activity: FragmentActivity?,
            str: String?,
            isWebview: Boolean
        ): Boolean =
            with(activity) {
                // TODO MUCH BETTER HANDLING

                // Invalid URIs can crash
                fun safeURI(uri: String) = normalSafeApiCall { URI(uri) }

                if (str != null && this != null) {
                    if (str.startsWith("https://cs.repo")) {
                        val realUrl = "https://" + str.substringAfter("?")
                        println("Repository url: $realUrl")
                        loadRepository(realUrl)
                        return true
                    } else if (str.contains(appString)) {
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

                                    this@with.runOnUiThread {
                                        try {
                                            showToast(
                                                this@with,
                                                getString(if (isSuccessful) R.string.authenticated_user else R.string.authenticated_user_fail).format(
                                                    api.name
                                                )
                                            )
                                        } catch (e: Exception) {
                                            logError(e) // format might fail
                                        }
                                    }
                                }
                                return true
                            }
                        }
                        // This specific intent is used for the gradle deployWithAdb
                        // https://github.com/recloudstream/gradle/blob/master/src/main/kotlin/com/lagradost/cloudstream3/gradle/tasks/DeployWithAdbTask.kt#L46
                        if (str == "$appString:") {
                            PluginManager.hotReloadAllLocalPlugins(activity)
                        }
                    } else if (safeURI(str)?.scheme == appStringRepo) {
                        val url = str.replaceFirst(appStringRepo, "https")
                        loadRepository(url)
                        return true
                    } else if (safeURI(str)?.scheme == appStringSearch) {
                        nextSearchQuery =
                            URLDecoder.decode(str.substringAfter("$appStringSearch://"), "UTF-8")

                        // Use both navigation views to support both layouts.
                        // It might be better to use the QuickSearch.
                        nav_view?.selectedItemId = R.id.navigation_search
                        nav_rail_view?.selectedItemId = R.id.navigation_search
                    } else if (safeURI(str)?.scheme == appStringPlayer) {
                        val uri = Uri.parse(str)
                        val name = uri.getQueryParameter("name")
                        val url = URLDecoder.decode(uri.authority, "UTF-8")

                        navigate(
                            R.id.global_to_navigation_player,
                            GeneratorPlayer.newInstance(
                                LinkGenerator(
                                    listOf(BasicLink(url, name)),
                                    extract = true,
                                )
                            )
                        )
                    } else if (safeURI(str)?.scheme == appStringResumeWatching) {
                        val id =
                            str.substringAfter("$appStringResumeWatching://").toIntOrNull()
                                ?: return false
                        ioSafe {
                            val resumeWatchingCard =
                                HomeViewModel.getResumeWatching()?.firstOrNull { it.id == id }
                                    ?: return@ioSafe
                            activity.loadSearchResult(
                                resumeWatchingCard,
                                START_ACTION_RESUME_LATEST
                            )
                        }
                    } else if (!isWebview) {
                        if (str.startsWith(DOWNLOAD_NAVIGATE_TO)) {
                            this.navigate(R.id.navigation_downloads)
                            return true
                        } else {
                            for (api in apis) {
                                if (str.startsWith(api.mainUrl)) {
                                    loadResult(str, api.name)
                                    return true
                                }
                            }
                        }
                    }
                }
                return false
            }
    }

    var lastPopup: SearchResponse? = null
    fun loadPopup(result: SearchResponse) {
        lastPopup = result
        viewModel.load(
            this, result.url, result.apiName, false, if (getApiDubstatusSettings()
                    .contains(DubStatus.Dubbed)
            ) DubStatus.Dubbed else DubStatus.Subbed, null
        )
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
            !listOf(
                R.id.navigation_results_phone,
                R.id.navigation_results_tv,
                R.id.navigation_player
            ).contains(destination.id)

        val isNavVisible = listOf(
            R.id.navigation_home,
            R.id.navigation_search,
            R.id.navigation_library,
            R.id.navigation_downloads,
            R.id.navigation_settings,
            R.id.navigation_download_child,
            R.id.navigation_subtitles,
            R.id.navigation_chrome_subtitles,
            R.id.navigation_settings_player,
            R.id.navigation_settings_updates,
            R.id.navigation_settings_ui,
            R.id.navigation_settings_account,
            R.id.navigation_settings_providers,
            R.id.navigation_settings_general,
            R.id.navigation_settings_extensions,
            R.id.navigation_settings_plugins,
            R.id.navigation_test_providers,
        ).contains(destination.id)


        val dontPush = listOf(
            R.id.navigation_home,
            R.id.navigation_search,
            R.id.navigation_results_phone,
            R.id.navigation_results_tv,
            R.id.navigation_player,
        ).contains(destination.id)

        nav_host_fragment?.apply {
            val params = layoutParams as ConstraintLayout.LayoutParams

            params.setMargins(
                if (!dontPush && isTvSettings()) resources.getDimensionPixelSize(R.dimen.navbar_width) else 0,
                params.topMargin,
                params.rightMargin,
                params.bottomMargin
            )
            layoutParams = params
        }

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

        // Hide library on TV since it is not supported yet :(
        val isTrueTv = isTrueTvSettings()
        nav_view?.menu?.findItem(R.id.navigation_library)?.isVisible = !isTrueTv
        nav_rail_view?.menu?.findItem(R.id.navigation_library)?.isVisible = !isTrueTv
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
        afterPluginsLoadedEvent += ::onAllPluginsLoaded
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

        // Start any delayed updates
        if (ApkInstaller.delayedInstaller?.startInstallation() == true) {
            Toast.makeText(this, R.string.update_started, Toast.LENGTH_LONG).show()
        }
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

    private fun showConfirmExitDialog() {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.confirm_exit_dialog)
        builder.apply {
            // Forceful exit since back button can actually go back to setup
            setPositiveButton(R.string.yes) { _, _ -> exitProcess(0) }
            setNegativeButton(R.string.no) { _, _ -> }
        }
        builder.show().setDefaultFocus()
    }

    private fun backPressed() {
        this.window?.navigationBarColor =
            this.colorFromAttribute(R.attr.primaryGrayBackground)
        this.updateLocale()
        this.updateLocale()

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
        val navController = navHostFragment?.navController
        val isAtHome =
            navController?.currentDestination?.matchDestination(R.id.navigation_home) == true

        if (isAtHome && isTrueTvSettings()) {
            showConfirmExitDialog()
        } else {
            super.onBackPressed()
        }
    }

    override fun onBackPressed() {
        ((supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment?)?.childFragmentManager?.primaryNavigationFragment as? IOnBackPressed)?.onBackPressed()
            ?.let { runNormal ->
                if (runNormal) backPressed()
            } ?: run {
            backPressed()
        }
    }

    override fun onDestroy() {
        val broadcastIntent = Intent()
        broadcastIntent.action = "restart_service"
        broadcastIntent.setClass(this, VideoDownloadRestartReceiver::class.java)
        this.sendBroadcast(broadcastIntent)
        afterPluginsLoadedEvent -= ::onAllPluginsLoaded
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
        handleAppIntentUrl(this, str, false)
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

    private val pluginsLock = Mutex()
    private fun onAllPluginsLoaded(success: Boolean = false) {
        ioSafe {
            pluginsLock.withLock {
                // Load cloned sites after plugins have been loaded since clones depend on plugins.
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
                    // it.hashCode() is not enough to make sure they are distinct
                    apis =
                        allProviders.distinctBy { it.lang + it.name + it.mainUrl + it.javaClass.name }
                    APIHolder.apiMap = null
                } catch (e: Exception) {
                    logError(e)
                }
            }
        }
    }

    lateinit var viewModel: ResultViewModel2

    override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? {
        viewModel =
            ViewModelProvider(this)[ResultViewModel2::class.java]

        return super.onCreateView(name, context, attrs)
    }

    private fun hidePreviewPopupDialog() {
        viewModel.clear()
        bottomPreviewPopup.dismissSafe(this)
    }

    var bottomPreviewPopup: BottomSheetDialog? = null
    private fun showPreviewPopupDialog(): BottomSheetDialog {
        val ret = (bottomPreviewPopup ?: run {
            val builder =
                BottomSheetDialog(this)
            builder.setContentView(R.layout.bottom_resultview_preview)
            builder.setOnDismissListener {
                bottomPreviewPopup = null
                viewModel.clear()
            }
            builder.setCanceledOnTouchOutside(true)
            builder.show()
            builder
        })
        bottomPreviewPopup = ret
        return ret
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        app.initClient(this)
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)

        val errorFile = filesDir.resolve("last_error")
        var lastError: String? = null
        if (errorFile.exists() && errorFile.isFile) {
            lastError = errorFile.readText(Charset.defaultCharset())
            errorFile.delete()
        }

        val settingsForProvider = SettingsJson()
        settingsForProvider.enableAdult =
            settingsManager.getBoolean(getString(R.string.enable_nsfw_on_providers_key), false)

        MainAPI.settingsForProvider = settingsForProvider

        loadThemes(this)
        updateLocale()
        super.onCreate(savedInstanceState)
        try {
            if (isCastApiAvailable()) {
                mSessionManager = CastContext.getSharedInstance(this).sessionManager
            }
        } catch (e: Exception) {
            logError(e)
        }

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        updateTv()
        if (isTvSettings()) {
            setContentView(R.layout.activity_main_tv)
        } else {
            setContentView(R.layout.activity_main)
        }

        changeStatusBarState(isEmulatorSettings())

        // Automatically enable jsdelivr if cant connect to raw.githubusercontent.com
        if (this.getKey<Boolean>(getString(R.string.jsdelivr_proxy_key)) == null && isNetworkAvailable()) {
            main {
                if (checkGithubConnectivity()) {
                    this.setKey(getString(R.string.jsdelivr_proxy_key), false)
                } else {
                    this.setKey(getString(R.string.jsdelivr_proxy_key), true)
                    val parentView: View = findViewById(android.R.id.content)
                    Snackbar.make(parentView, R.string.jsdelivr_enabled, Snackbar.LENGTH_LONG)
                        .let { snackbar ->
                            snackbar.setAction(R.string.revert) {
                                setKey(getString(R.string.jsdelivr_proxy_key), false)
                            }
                            snackbar.setBackgroundTint(colorFromAttribute(R.attr.primaryGrayBackground))
                            snackbar.setTextColor(colorFromAttribute(R.attr.textColor))
                            snackbar.setActionTextColor(colorFromAttribute(R.attr.colorPrimary))
                            snackbar.show()
                        }
                }

            }
        }


        if (PluginManager.checkSafeModeFile()) {
            normalSafeApiCall {
                showToast(this, R.string.safe_mode_file, Toast.LENGTH_LONG)
            }
        } else if (lastError == null) {
            ioSafe {
                getKey<String>(USER_SELECTED_HOMEPAGE_API)?.let { homeApi ->
                    mainPluginsLoadedEvent.invoke(loadSinglePlugin(this@MainActivity, homeApi))
                } ?: run {
                    mainPluginsLoadedEvent.invoke(false)
                }

                ioSafe {
                    if (settingsManager.getBoolean(
                            getString(R.string.auto_update_plugins_key),
                            true
                        )
                    ) {
                        PluginManager.updateAllOnlinePluginsAndLoadThem(this@MainActivity)
                    } else {
                        loadAllOnlinePlugins(this@MainActivity)
                    }

                    //Automatically download not existing plugins
                    if (settingsManager.getBoolean(
                            getString(R.string.auto_download_plugins_key),
                            false
                        )
                    ) {
                        PluginManager.downloadNotExistingPluginsAndLoad(this@MainActivity)
                    }
                }

                ioSafe {
                    PluginManager.loadAllLocalPlugins(this@MainActivity, false)
                }
            }
        } else {
            val builder: AlertDialog.Builder = AlertDialog.Builder(this)
            builder.setTitle(R.string.safe_mode_title)
            builder.setMessage(R.string.safe_mode_description)
            builder.apply {
                setPositiveButton(R.string.safe_mode_crash_info) { _, _ ->
                    val tbBuilder: AlertDialog.Builder = AlertDialog.Builder(context)
                    tbBuilder.setTitle(R.string.safe_mode_title)
                    tbBuilder.setMessage(lastError)
                    tbBuilder.show()
                }

                setNegativeButton("Ok") { _, _ -> }
            }
            builder.show().setDefaultFocus()
        }

        observeNullable(viewModel.page) { resource ->
            if (resource == null) {
                bottomPreviewPopup.dismissSafe(this)
                return@observeNullable
            }
            when (resource) {
                is Resource.Failure -> {
                    showToast(this, R.string.error)
                    hidePreviewPopupDialog()
                }
                is Resource.Loading -> {
                    showPreviewPopupDialog().apply {
                        resultview_preview_loading?.isVisible = true
                        resultview_preview_result?.isVisible = false
                        resultview_preview_loading_shimmer?.startShimmer()
                    }
                }
                is Resource.Success -> {
                    val d = resource.value
                    showPreviewPopupDialog().apply {
                        resultview_preview_loading?.isVisible = false
                        resultview_preview_result?.isVisible = true
                        resultview_preview_loading_shimmer?.stopShimmer()

                        resultview_preview_title?.text = d.title

                        resultview_preview_meta_type.setText(d.typeText)
                        resultview_preview_meta_year.setText(d.yearText)
                        resultview_preview_meta_duration.setText(d.durationText)
                        resultview_preview_meta_rating.setText(d.ratingText)

                        resultview_preview_description?.setText(d.plotText)
                        resultview_preview_poster?.setImage(
                            d.posterImage ?: d.posterBackgroundImage
                        )

                        resultview_preview_poster?.setOnClickListener {
                            //viewModel.updateWatchStatus(WatchType.PLANTOWATCH)
                            val value = viewModel.watchStatus.value ?: WatchType.NONE

                            this@MainActivity.showBottomDialog(
                                WatchType.values().map { getString(it.stringRes) }.toList(),
                                value.ordinal,
                                this@MainActivity.getString(R.string.action_add_to_bookmarks),
                                showApply = false,
                                {}) {
                                viewModel.updateWatchStatus(WatchType.values()[it])
                                bookmarksUpdatedEvent(true)
                            }
                        }

                        if (!isTvSettings()) // dont want this clickable on tv layout
                            resultview_preview_description?.setOnClickListener { view ->
                                view.context?.let { ctx ->
                                    val builder: AlertDialog.Builder =
                                        AlertDialog.Builder(ctx, R.style.AlertDialogCustom)
                                    builder.setMessage(d.plotText.asString(ctx).html())
                                        .setTitle(d.plotHeaderText.asString(ctx))
                                        .show()
                                }
                            }

                        resultview_preview_more_info?.setOnClickListener {
                            hidePreviewPopupDialog()
                            lastPopup?.let {
                                loadSearchResult(it)
                            }
                        }
                    }
                }
            }
        }

//        ioSafe {
//            val plugins =
//                RepositoryParser.getRepoPlugins("https://raw.githubusercontent.com/recloudstream/TestPlugin/master/repo.json")
//                    ?: emptyList()
//            plugins.map {
//                println("Load plugin: ${it.name} ${it.url}")
//                RepositoryParser.loadSiteTemp(applicationContext, it.url, it.name)
//            }
//        }

        // init accounts
        ioSafe {
            for (api in accountManagers) {
                api.init()
            }

            inAppAuths.amap { api ->
                try {
                    api.initialize()
                } catch (e: Exception) {
                    logError(e)
                }
            }
        }

        SearchResultBuilder.updateCache(this)

        ioSafe {
            initAll()
            // No duplicates (which can happen by registerMainAPI)
            apis = allProviders.distinctBy { it }
        }

        //  val navView: BottomNavigationView = findViewById(R.id.nav_view)
        setUpBackup()

        CommonActivity.init(this)
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        navController.addOnDestinationChangedListener { _: NavController, navDestination: NavDestination, bundle: Bundle? ->
            // Intercept search and add a query
            if (navDestination.matchDestination(R.id.navigation_search) && !nextSearchQuery.isNullOrBlank()) {
                bundle?.apply {
                    this.putString(SearchFragment.SEARCH_QUERY, nextSearchQuery)
                    nextSearchQuery = null
                }
            }
        }

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
        if (isTvSettings()) {
            nav_rail?.background?.alpha = 200
        } else {
            nav_rail?.background?.alpha = 255

        }
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

        ioSafe {
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
                // If no plugins bring up extensions screen
            } else if (PluginManager.getPluginsOnline().isEmpty()
                && PluginManager.getPluginsLocal().isEmpty()
//                && PREBUILT_REPOSITORIES.isNotEmpty()
            ) {
                navController.navigate(
                    R.id.navigation_setup_extensions,
                    SetupFragmentExtensions.newInstance(false)
                )
            }
        } catch (e: Exception) {
            logError(e)
        } finally {
            setKey(HAS_DONE_SETUP_KEY, true)
        }

//        Used to check current focus for TV
//        main {
//            while (true) {
//                delay(5000)
//                println("Current focus: $currentFocus")
//                showToast(this, currentFocus.toString(), Toast.LENGTH_LONG)
//            }
//        }

    }

    suspend fun checkGithubConnectivity(): Boolean {
        return try {
            app.get(
                "https://raw.githubusercontent.com/recloudstream/.github/master/connectivitycheck",
                timeout = 5
            ).text.trim() == "ok"
        } catch (t: Throwable) {
            false
        }
    }
}
