package com.lagradost.cloudstream3

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.IdRes
import androidx.annotation.MainThread
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.marginStart
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.Session
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.navigationrail.NavigationRailView
import com.google.android.material.snackbar.Snackbar
import com.google.common.collect.Comparators.min
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import com.lagradost.cloudstream3.APIHolder.allProviders
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.APIHolder.initAll
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.removeKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.CommonActivity.loadThemes
import com.lagradost.cloudstream3.CommonActivity.onColorSelectedEvent
import com.lagradost.cloudstream3.CommonActivity.onDialogDismissedEvent
import com.lagradost.cloudstream3.CommonActivity.onUserLeaveHint
import com.lagradost.cloudstream3.CommonActivity.screenHeight
import com.lagradost.cloudstream3.CommonActivity.setActivityInstance
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.CommonActivity.updateLocale
import com.lagradost.cloudstream3.CommonActivity.updateTheme
import com.lagradost.cloudstream3.databinding.ActivityMainBinding
import com.lagradost.cloudstream3.databinding.ActivityMainTvBinding
import com.lagradost.cloudstream3.databinding.BottomResultviewPreviewBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.mvvm.observeNullable
import com.lagradost.cloudstream3.network.initClient
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.plugins.PluginManager.loadAllOnlinePlugins
import com.lagradost.cloudstream3.plugins.PluginManager.loadSinglePlugin
import com.lagradost.cloudstream3.receivers.VideoDownloadRestartReceiver
import com.lagradost.cloudstream3.services.SubscriptionWorkManager
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.APP_STRING
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.APP_STRING_PLAYER
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.APP_STRING_REPO
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.APP_STRING_RESUME_WATCHING
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.APP_STRING_SEARCH
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.OAuth2Apis
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.accountManagers
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.inAppAuths
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.localListApi
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.SyncWatchType
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.account.AccountHelper.showAccountSelectLinear
import com.lagradost.cloudstream3.ui.account.AccountViewModel
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_NAVIGATE_TO
import com.lagradost.cloudstream3.ui.home.HomeViewModel
import com.lagradost.cloudstream3.ui.library.LibraryViewModel
import com.lagradost.cloudstream3.ui.player.BasicLink
import com.lagradost.cloudstream3.ui.player.GeneratorPlayer
import com.lagradost.cloudstream3.ui.player.LinkGenerator
import com.lagradost.cloudstream3.ui.result.LinearListLayout
import com.lagradost.cloudstream3.ui.result.ResultViewModel2
import com.lagradost.cloudstream3.ui.result.START_ACTION_RESUME_LATEST
import com.lagradost.cloudstream3.ui.result.SyncViewModel
import com.lagradost.cloudstream3.utils.setText
import com.lagradost.cloudstream3.utils.setTextHtml
import com.lagradost.cloudstream3.utils.txt
import com.lagradost.cloudstream3.ui.search.SearchFragment
import com.lagradost.cloudstream3.ui.search.SearchResultBuilder
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.ui.settings.Globals.updateTv
import com.lagradost.cloudstream3.ui.settings.SettingsGeneral
import com.lagradost.cloudstream3.ui.setup.HAS_DONE_SETUP_KEY
import com.lagradost.cloudstream3.ui.setup.SetupFragmentExtensions
import com.lagradost.cloudstream3.utils.ApkInstaller
import com.lagradost.cloudstream3.utils.AppContextUtils.getApiDubstatusSettings
import com.lagradost.cloudstream3.utils.AppContextUtils.html
import com.lagradost.cloudstream3.utils.AppContextUtils.isCastApiAvailable
import com.lagradost.cloudstream3.utils.AppContextUtils.isLtr
import com.lagradost.cloudstream3.utils.AppContextUtils.isNetworkAvailable
import com.lagradost.cloudstream3.utils.AppContextUtils.isRtl
import com.lagradost.cloudstream3.utils.AppContextUtils.loadCache
import com.lagradost.cloudstream3.utils.AppContextUtils.loadRepository
import com.lagradost.cloudstream3.utils.AppContextUtils.loadResult
import com.lagradost.cloudstream3.utils.AppContextUtils.loadSearchResult
import com.lagradost.cloudstream3.utils.AppContextUtils.setDefaultFocus
import com.lagradost.cloudstream3.utils.AppContextUtils.updateHasTrailers
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.attachBackPressedCallback
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.detachBackPressedCallback
import com.lagradost.cloudstream3.utils.BackupUtils.backup
import com.lagradost.cloudstream3.utils.BackupUtils.setUpBackup
import com.lagradost.cloudstream3.utils.BiometricAuthenticator.BiometricCallback
import com.lagradost.cloudstream3.utils.BiometricAuthenticator.biometricPrompt
import com.lagradost.cloudstream3.utils.BiometricAuthenticator.deviceHasPasswordPinLock
import com.lagradost.cloudstream3.utils.BiometricAuthenticator.isAuthEnabled
import com.lagradost.cloudstream3.utils.BiometricAuthenticator.promptInfo
import com.lagradost.cloudstream3.utils.BiometricAuthenticator.startBiometricAuthentication
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.setKey
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.accounts
import com.lagradost.cloudstream3.utils.DataStoreHelper.migrateResumeWatching
import com.lagradost.cloudstream3.utils.Event
import com.lagradost.cloudstream3.utils.InAppUpdater.Companion.runAutoUpdate
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.SnackbarHelper.showSnackbar
import com.lagradost.cloudstream3.utils.UIHelper.changeStatusBarState
import com.lagradost.cloudstream3.utils.UIHelper.checkWrite
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.getResourceColor
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.UIHelper.requestRW
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import com.lagradost.cloudstream3.utils.USER_PROVIDER_API
import com.lagradost.cloudstream3.utils.USER_SELECTED_HOMEPAGE_API
import com.lagradost.cloudstream3.actions.temp.fcast.FcastManager
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.safefile.SafeFile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.lang.ref.WeakReference
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.Charset
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity(), ColorPickerDialogListener, BiometricCallback {
    companion object {
        var activityResultLauncher: ActivityResultLauncher<Intent>? = null

        const val TAG = "MAINACT"
        const val ANIMATED_OUTLINE: Boolean = false
        var lastError: String? = null

        private const val FILE_DELETE_KEY = "FILES_TO_DELETE_KEY"

        /**
         * Transient files to delete on application exit.
         * Deletes files on onDestroy().
         */
        private var filesToDelete: Set<String>
            // This needs to be persistent because the application may exit without calling onDestroy.
            get() = getKey<Set<String>>(FILE_DELETE_KEY) ?: setOf()
            private set(value) = setKey(FILE_DELETE_KEY, value)

        /**
         * Add file to delete on Exit.
         */
        fun deleteFileOnExit(file: File) {
            filesToDelete = filesToDelete + file.path
        }

        /**
         * Setting this will automatically enter the query in the search
         * next time the search fragment is opened.
         * This variable will clear itself after one use. Null does nothing.
         *
         * This is a very bad solution but I was unable to find a better one.
         **/
        var nextSearchQuery: String? = null

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
         * Used by DataStoreHelper to fully reload home when switching accounts
         */
        val reloadHomeEvent = Event<Boolean>()

        /**
         * Used by DataStoreHelper to fully reload library when switching accounts
         */
        val reloadLibraryEvent = Event<Boolean>()

        /**
         * Used by DataStoreHelper to fully reload Navigation Rail header picture
         */
        val reloadAccountEvent = Event<Boolean>()

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
                    } else if (str.contains(APP_STRING)) {
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
                        if (str == "$APP_STRING:") {
                            PluginManager.hotReloadAllLocalPlugins(activity)
                        }
                    } else if (safeURI(str)?.scheme == APP_STRING_REPO) {
                        val url = str.replaceFirst(APP_STRING_REPO, "https")
                        loadRepository(url)
                        return true
                    } else if (safeURI(str)?.scheme == APP_STRING_SEARCH) {
                        val query = str.substringAfter("$APP_STRING_SEARCH://")
                        nextSearchQuery =
                            try {
                                URLDecoder.decode(query, "UTF-8")
                            } catch (t: Throwable) {
                                logError(t)
                                query
                            }
                        // Use both navigation views to support both layouts.
                        // It might be better to use the QuickSearch.
                        activity?.findViewById<BottomNavigationView>(R.id.nav_view)?.selectedItemId =
                            R.id.navigation_search
                        activity?.findViewById<NavigationRailView>(R.id.nav_rail_view)?.selectedItemId =
                            R.id.navigation_search
                    } else if (safeURI(str)?.scheme == APP_STRING_PLAYER) {
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
                    } else if (safeURI(str)?.scheme == APP_STRING_RESUME_WATCHING) {
                        val id =
                            str.substringAfter("$APP_STRING_RESUME_WATCHING://").toIntOrNull()
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
                            synchronized(apis) {
                                for (api in apis) {
                                    if (str.startsWith(api.mainUrl)) {
                                        loadResult(str, api.name, "")
                                        return true
                                    }
                                }
                            }
                        }
                    }
                }
                return false
            }
    }

    var lastPopup: SearchResponse? = null
    fun loadPopup(result: SearchResponse, load: Boolean = true) {
        lastPopup = result
        val syncName = syncViewModel.syncName(result.apiName)

        // based on apiName we decide on if it is a local list or not, this is because
        // we want to show a bit of extra UI to sync apis
        if (result is SyncAPI.LibraryItem && syncName != null) {
            isLocalList = false
            syncViewModel.setSync(syncName, result.syncId)
            syncViewModel.updateMetaAndUser()
        } else {
            isLocalList = true
            syncViewModel.clear()
        }

        if (load) {
            viewModel.load(
                this, result.url, result.apiName, false, if (getApiDubstatusSettings()
                        .contains(DubStatus.Dubbed)
                ) DubStatus.Dubbed else DubStatus.Subbed, null
            )
        } else {
            viewModel.loadSmall(result)
        }
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
        updateTheme(this) // Update if system theme

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navHostFragment.navController.currentDestination?.let { updateNavBar(it) }
    }

    private fun updateNavBar(destination: NavDestination) {
        this.hideKeyboard()

        // Fucks up anime info layout since that has its own layout
        binding?.castMiniControllerHolder?.isVisible =
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
            R.id.navigation_quick_search,
        ).contains(destination.id)

        binding?.navHostFragment?.apply {
            val params = layoutParams as ConstraintLayout.LayoutParams
            val push =
                if (!dontPush && isLayout(TV or EMULATOR)) resources.getDimensionPixelSize(R.dimen.navbar_width) else 0

            if (!this.isLtr()) {
                params.setMargins(
                    params.leftMargin,
                    params.topMargin,
                    push,
                    params.bottomMargin
                )
            } else {
                params.setMargins(
                    push,
                    params.topMargin,
                    params.rightMargin,
                    params.bottomMargin
                )
            }

            layoutParams = params
        }

        val landscape = when (resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                true
            }

            Configuration.ORIENTATION_PORTRAIT -> {
                isLayout(TV or EMULATOR)
            }

            else -> {
                false
            }
        }

        binding?.apply {
            navRailView.isVisible = isNavVisible && landscape
            navView.isVisible = isNavVisible && !landscape

            /**
             * We need to make sure if we return to a sub-fragment,
             * the correct navigation item is selected so that it does not
             * highlight the wrong one in UI.
             */
            when (destination.id) {
                in listOf(R.id.navigation_downloads, R.id.navigation_download_child) -> {
                    navRailView.menu.findItem(R.id.navigation_downloads).isChecked = true
                    navView.menu.findItem(R.id.navigation_downloads).isChecked = true
                }
                in listOf(
                    R.id.navigation_settings,
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
                    R.id.navigation_test_providers
                ) -> {
                    navRailView.menu.findItem(R.id.navigation_settings).isChecked = true
                    navView.menu.findItem(R.id.navigation_settings).isChecked = true
                }
            }
        }
    }

    //private var mCastSession: CastSession? = null
    var mSessionManager: SessionManager? = null
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
        setActivityInstance(this)
        try {
            if (isCastApiAvailable()) {
                mSessionManager?.addSessionManagerListener(mSessionManagerListener)
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
                mSessionManager?.removeSessionManagerListener(mSessionManagerListener)
                //mCastSession = null
            }
        } catch (e: Exception) {
            logError(e)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val response = CommonActivity.dispatchKeyEvent(this, event)
        if (response != null)
            return response
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

    @SuppressLint("ApplySharedPref") // commit since the op needs to be synchronous
    private fun showConfirmExitDialog(settingsManager: SharedPreferences) {
        val confirmBeforeExit = settingsManager.getInt(getString(R.string.confirm_exit_key), -1)
        when(confirmBeforeExit) {
            // AUTO - Confirm exit is shown only on TV or EMULATOR by default
            -1 -> if(isLayout(PHONE)) exitProcess(0)
            // DON'T SHOW
            1 -> exitProcess(0)
            // 0 -> SHOW
            else -> { /*NO-OP : Continue*/ }
        }

        val dialogView = layoutInflater.inflate(R.layout.confirm_exit_dialog, null)
        val dontShowAgainCheck: CheckBox = dialogView.findViewById(R.id.checkboxDontShowAgain)
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setView(dialogView)
            .setTitle(R.string.confirm_exit_dialog)
            .setNegativeButton(R.string.no) { _, _ -> /*NO-OP*/}
            .setPositiveButton(R.string.yes) { _, _ ->
                if(dontShowAgainCheck.isChecked) {
                    settingsManager.edit().putInt(getString(R.string.confirm_exit_key), 1).commit()
                }
                exitProcess(0)
            }

        builder.show().setDefaultFocus()
    }

    override fun onDestroy() {
        filesToDelete.forEach { path ->
            val result = File(path).deleteRecursively()
            if (result) {
                Log.d(TAG, "Deleted temporary file: $path")
            } else {
                Log.d(TAG, "Failed to delete temporary file: $path")
            }
        }
        filesToDelete = setOf()
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
                synchronized(allProviders) {
                    // Load cloned sites after plugins have been loaded since clones depend on plugins.
                    try {
                        getKey<Array<SettingsGeneral.CustomSite>>(USER_PROVIDER_API)?.let { list ->
                            list.forEach { custom ->
                                allProviders.firstOrNull { it.javaClass.simpleName == custom.parentJavaClass }
                                    ?.let {
                                        allProviders.add(it.javaClass.getDeclaredConstructor().newInstance().apply {
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
    }

    lateinit var viewModel: ResultViewModel2
    lateinit var syncViewModel: SyncViewModel
    private var libraryViewModel: LibraryViewModel? = null
    private var accountViewModel: AccountViewModel? = null

    /** kinda dirty, however it signals that we should use the watch status as sync or not*/
    var isLocalList: Boolean = false
    override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? {

        viewModel = ViewModelProvider(this)[ResultViewModel2::class.java]
        syncViewModel = ViewModelProvider(this)[SyncViewModel::class.java]

        return super.onCreateView(name, context, attrs)
    }

    private fun hidePreviewPopupDialog() {
        bottomPreviewPopup.dismissSafe(this)
        bottomPreviewPopup = null
        bottomPreviewBinding = null
    }

    private var bottomPreviewPopup: BottomSheetDialog? = null
    private var bottomPreviewBinding: BottomResultviewPreviewBinding? = null
    private fun showPreviewPopupDialog(): BottomResultviewPreviewBinding {
        val ret = (bottomPreviewBinding ?: run {
            val builder =
                BottomSheetDialog(this)
            val binding: BottomResultviewPreviewBinding =
                BottomResultviewPreviewBinding.inflate(builder.layoutInflater, null, false)
            bottomPreviewBinding = binding
            builder.setContentView(binding.root)
            builder.setOnDismissListener {
                bottomPreviewPopup = null
                bottomPreviewBinding = null
                viewModel.clear()
            }
            builder.setCanceledOnTouchOutside(true)
            builder.show()
            bottomPreviewPopup = builder
            binding
        })

        return ret
    }

    var binding: ActivityMainBinding? = null

    object TvFocus {
        data class FocusTarget(
            val width: Int,
            val height: Int,
            val x: Float,
            val y: Float,
        ) {
            companion object {
                fun lerp(a: FocusTarget, b: FocusTarget, lerp: Float): FocusTarget {
                    val ilerp = 1 - lerp
                    return FocusTarget(
                        width = (a.width * ilerp + b.width * lerp).toInt(),
                        height = (a.height * ilerp + b.height * lerp).toInt(),
                        x = a.x * ilerp + b.x * lerp,
                        y = a.y * ilerp + b.y * lerp
                    )
                }
            }
        }

        var last: FocusTarget = FocusTarget(0, 0, 0.0f, 0.0f)
        var current: FocusTarget = FocusTarget(0, 0, 0.0f, 0.0f)

        var focusOutline: WeakReference<View> = WeakReference(null)
        var lastFocus: WeakReference<View> = WeakReference(null)
        private val layoutListener: View.OnLayoutChangeListener =
            View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                // shitty fix for layouts
                lastFocus.get()?.apply {
                    updateFocusView(
                        this, same = true
                    )
                    postDelayed({
                        updateFocusView(
                            lastFocus.get(), same = false
                        )
                    }, 300)
                }
            }
        private val attachListener: View.OnAttachStateChangeListener =
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    updateFocusView(v)
                }

                override fun onViewDetachedFromWindow(v: View) {
                    // removes the focus view but not the listener as updateFocusView(null) will remove the listener
                    focusOutline.get()?.isVisible = false
                }
            }
        /*private val scrollListener = object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                current = current.copy(x = current.x + dx, y = current.y + dy)
                setTargetPosition(current)
            }
        }*/

        private fun setTargetPosition(target: FocusTarget) {
            focusOutline.get()?.apply {
                layoutParams = layoutParams?.apply {
                    width = target.width
                    height = target.height
                }

                translationX = target.x
                translationY = target.y
                bringToFront()
            }
        }

        private var animator: ValueAnimator? = null

        /** if this is enabled it will keep the focus unmoving
         *  during listview move */
        private const val NO_MOVE_LIST: Boolean = false

        /** If this is enabled then it will try to move the
         * listview focus to the left instead of center */
        private const val LEFTMOST_MOVE_LIST: Boolean = true

        private val reflectedScroll by lazy {
            try {
                RecyclerView::class.java.declaredMethods.firstOrNull {
                    it.name == "scrollStep"
                }?.also { it.isAccessible = true }
            } catch (t: Throwable) {
                null
            }
        }

        @MainThread
        fun updateFocusView(newFocus: View?, same: Boolean = false) {
            val focusOutline = focusOutline.get() ?: return
            val lastView = lastFocus.get()
            val exactlyTheSame = lastView == newFocus && newFocus != null
            if (!exactlyTheSame) {
                lastView?.removeOnLayoutChangeListener(layoutListener)
                lastView?.removeOnAttachStateChangeListener(attachListener)
                (lastView?.parent as? RecyclerView)?.apply {
                    removeOnLayoutChangeListener(layoutListener)
                    //removeOnScrollListener(scrollListener)
                }
            }

            val wasGone = focusOutline.isGone

            val visible =
                newFocus != null && newFocus.measuredHeight > 0 && newFocus.measuredWidth > 0 && newFocus.isShown && newFocus.tag != "tv_no_focus_tag"
            focusOutline.isVisible = visible

            if (newFocus != null) {
                lastFocus = WeakReference(newFocus)
                val parent = newFocus.parent
                var targetDx = 0
                if (parent is RecyclerView) {
                    val layoutManager = parent.layoutManager
                    if (layoutManager is LinearListLayout && layoutManager.orientation == LinearLayoutManager.HORIZONTAL) {
                        val dx =
                            LinearSnapHelper().calculateDistanceToFinalSnap(layoutManager, newFocus)
                                ?.get(0)

                        if (dx != null) {
                            val rdx = if (LEFTMOST_MOVE_LIST) {
                                // this makes the item the leftmost in ltr, instead of center
                                val diff =
                                    ((layoutManager.width - layoutManager.paddingStart - newFocus.measuredWidth) / 2) - newFocus.marginStart
                                dx + if (parent.isRtl()) {
                                    -diff
                                } else {
                                    diff
                                }
                            } else {
                                if (dx > 0) dx else 0
                            }

                            if (!NO_MOVE_LIST) {
                                parent.smoothScrollBy(rdx, 0)
                            } else {
                                val smoothScroll = reflectedScroll
                                if (smoothScroll == null) {
                                    parent.smoothScrollBy(rdx, 0)
                                } else {
                                    try {
                                        // this is very fucked but because it is a protected method to
                                        // be able to compute the scroll I use reflection, scroll, then
                                        // scroll back, then smooth scroll and set the no move
                                        val out = IntArray(2)
                                        smoothScroll.invoke(parent, rdx, 0, out)
                                        val scrolledX = out[0]
                                        if (abs(scrolledX) <= 0) { // newFocus.measuredWidth*2
                                            smoothScroll.invoke(parent, -rdx, 0, out)
                                            parent.smoothScrollBy(scrolledX, 0)
                                            if (NO_MOVE_LIST) targetDx = scrolledX
                                        }
                                    } catch (t: Throwable) {
                                        parent.smoothScrollBy(rdx, 0)
                                    }
                                }
                            }
                        }
                    }
                }

                val out = IntArray(2)
                newFocus.getLocationInWindow(out)
                val (screenX, screenY) = out
                var (x, y) = screenX.toFloat() to screenY.toFloat()
                val (currentX, currentY) = focusOutline.translationX to focusOutline.translationY

                if (!newFocus.isLtr()) {
                    x = x - focusOutline.rootView.width + newFocus.measuredWidth
                }
                x -= targetDx

                // out of bounds = 0,0
                if (screenX == 0 && screenY == 0) {
                    focusOutline.isVisible = false
                }
                if (!exactlyTheSame) {
                    (newFocus.parent as? RecyclerView)?.apply {
                        addOnLayoutChangeListener(layoutListener)
                        //addOnScrollListener(scrollListener)
                    }
                    newFocus.addOnLayoutChangeListener(layoutListener)
                    newFocus.addOnAttachStateChangeListener(attachListener)
                }
                val start = FocusTarget(
                    x = currentX,
                    y = currentY,
                    width = focusOutline.measuredWidth,
                    height = focusOutline.measuredHeight
                )
                val end = FocusTarget(
                    x = x,
                    y = y,
                    width = newFocus.measuredWidth,
                    height = newFocus.measuredHeight
                )

                // if they are the same within then snap, aka scrolling
                val deltaMinX = min(end.width / 2, 60.toPx)
                val deltaMinY = min(end.height / 2, 60.toPx)
                if (start.width == end.width && start.height == end.height && (start.x - end.x).absoluteValue < deltaMinX && (start.y - end.y).absoluteValue < deltaMinY) {
                    animator?.cancel()
                    last = start
                    current = end
                    setTargetPosition(end)
                    return
                }

                // if running then "reuse"
                if (animator?.isRunning == true) {
                    current = end
                    return
                } else {
                    animator?.cancel()
                }


                last = start
                current = end

                // if previously gone, then tp
                if (wasGone) {
                    setTargetPosition(current)
                    return
                }

                // animate between a and b
                animator = ValueAnimator.ofFloat(0.0f, 1.0f).apply {
                    startDelay = 0
                    duration = 200
                    addUpdateListener { animation ->
                        val animatedValue = animation.animatedValue as Float
                        val target = FocusTarget.lerp(last, current, minOf(animatedValue, 1.0f))
                        setTargetPosition(target)
                    }
                    start()
                }

                // post check
                if (!same) {
                    newFocus.postDelayed({
                        updateFocusView(lastFocus.get(), same = true)
                    }, 200)
                }

                /*

                the following is working, but somewhat bad code code

                if (!wasGone) {
                    (focusOutline.parent as? ViewGroup)?.let {
                        TransitionManager.endTransitions(it)
                        TransitionManager.beginDelayedTransition(
                            it,
                            TransitionSet().addTransition(ChangeBounds())
                                .addTransition(ChangeTransform())
                                .setDuration(100)
                        )
                    }
                }

                focusOutline.layoutParams = focusOutline.layoutParams?.apply {
                    width = newFocus.measuredWidth
                    height = newFocus.measuredHeight
                }
                focusOutline.translationX = x.toFloat()
                focusOutline.translationY = y.toFloat()*/
            }
        }
    }

    private fun centerView(view: View?) {
        if (view == null) return
        try {
            Log.v(TAG, "centerView: $view")
            val r = Rect(0, 0, 0, 0)
            view.getDrawingRect(r)
            val x = r.centerX()
            val y = r.centerY()
            val dx = r.width() / 2 //screenWidth / 2
            val dy = screenHeight / 2
            val r2 = Rect(x - dx, y - dy, x + dx, y + dy)
            view.requestRectangleOnScreen(r2, false)
            // TvFocus.current =TvFocus.current.copy(y=y.toFloat())
        } catch (_: Throwable) {
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        app.initClient(this)
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)

        val errorFile = filesDir.resolve("last_error")
        if (errorFile.exists() && errorFile.isFile) {
            lastError = errorFile.readText(Charset.defaultCharset())
            errorFile.delete()
        } else {
            lastError = null
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
                CastContext.getSharedInstance(this) {it.run()}.addOnSuccessListener { mSessionManager = it.sessionManager }
            }
        } catch (t: Throwable) {
            logError(t)
        }

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        updateTv()

        // backup when we update the app, I don't trust myself to not boot lock users, might want to make this a setting?
        normalSafeApiCall {
            val appVer = BuildConfig.VERSION_NAME
            val lastAppAutoBackup: String = getKey("VERSION_NAME") ?: ""
            if (appVer != lastAppAutoBackup) {
                setKey("VERSION_NAME", BuildConfig.VERSION_NAME)
                normalSafeApiCall {
                    backup(this)
                }
                normalSafeApiCall {
                    // Recompile oat on new version
                    PluginManager.deleteAllOatFiles(this)
                }
            }
        }

        // just in case, MAIN SHOULD *NEVER* BOOT LOOP CRASH
        binding = try {
            if (isLayout(TV or EMULATOR)) {
                val newLocalBinding = ActivityMainTvBinding.inflate(layoutInflater, null, false)
                setContentView(newLocalBinding.root)

                if (isLayout(TV) && ANIMATED_OUTLINE) {
                    TvFocus.focusOutline = WeakReference(newLocalBinding.focusOutline)
                    newLocalBinding.root.viewTreeObserver.addOnScrollChangedListener {
                        TvFocus.updateFocusView(TvFocus.lastFocus.get(), same = true)
                    }
                    newLocalBinding.root.viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
                        TvFocus.updateFocusView(newFocus)
                    }
                } else {
                    newLocalBinding.focusOutline.isVisible = false
                }

                if (isLayout(TV)) {
                    // Put here any button you don't want focusing it to center the view
                    val exceptionButtons = listOf(
                        R.id.home_preview_play_btt,
                        R.id.home_preview_info_btt,
                        R.id.home_preview_hidden_next_focus,
                        R.id.home_preview_hidden_prev_focus,
                        R.id.result_play_movie_button,
                        R.id.result_play_series_button,
                        R.id.result_resume_series_button,
                        R.id.result_play_trailer_button,
                        R.id.result_bookmark_Button,
                        R.id.result_favorite_Button,
                        R.id.result_subscribe_Button,
                        R.id.result_search_Button,
                        R.id.result_episodes_show_button,
                    )

                    newLocalBinding.root.viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
                        if (exceptionButtons.contains(newFocus?.id)) return@addOnGlobalFocusChangeListener
                        centerView(newFocus)
                    }
                }

                ActivityMainBinding.bind(newLocalBinding.root) // this may crash
            } else {
                val newLocalBinding = ActivityMainBinding.inflate(layoutInflater, null, false)
                setContentView(newLocalBinding.root)
                newLocalBinding
            }
        } catch (t: Throwable) {
            showToast(txt(R.string.unable_to_inflate, t.message ?: ""), Toast.LENGTH_LONG)
            null
        }

        changeStatusBarState(isLayout(EMULATOR))

        /** Biometric stuff for users without accounts **/
        val noAccounts = settingsManager.getBoolean(
            getString(R.string.skip_startup_account_select_key),
            false
        ) || accounts.count() <= 1

        if (isLayout(PHONE) && isAuthEnabled(this) && noAccounts) {
            if (deviceHasPasswordPinLock(this)) {
                startBiometricAuthentication(this, R.string.biometric_authentication_title, false)

                promptInfo?.let { prompt ->
                    biometricPrompt?.authenticate(prompt)
                }

                // hide background while authenticating, Sorry moms & dads 🙏
                binding?.navHostFragment?.isInvisible = true
            }
        }

        // Automatically enable jsdelivr if cant connect to raw.githubusercontent.com
        if (this.getKey<Boolean>(getString(R.string.jsdelivr_proxy_key)) == null && isNetworkAvailable()) {
            main {
                if (checkGithubConnectivity()) {
                    this.setKey(getString(R.string.jsdelivr_proxy_key), false)
                } else {
                    this.setKey(getString(R.string.jsdelivr_proxy_key), true)
                    showSnackbar(
                        this@MainActivity,
                        R.string.jsdelivr_enabled,
                        Snackbar.LENGTH_LONG,
                        R.string.revert
                    ) { setKey(getString(R.string.jsdelivr_proxy_key), false) }
                }
            }
        }

        ioSafe { SafeFile.check(this@MainActivity) }

        if (PluginManager.checkSafeModeFile()) {
            normalSafeApiCall {
                showToast(R.string.safe_mode_file, Toast.LENGTH_LONG)
            }
        } else if (lastError == null) {
            ioSafe {
                DataStoreHelper.currentHomePage?.let { homeApi ->
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

                    //Automatically download not existing plugins, using mode specified.
                    val autoDownloadPlugin = AutoDownloadMode.getEnum(
                        settingsManager.getInt(
                            getString(R.string.auto_download_plugins_key),
                            0
                        )
                    ) ?: AutoDownloadMode.Disable
                    if (autoDownloadPlugin != AutoDownloadMode.Disable) {
                        PluginManager.downloadNotExistingPluginsAndLoad(
                            this@MainActivity,
                            autoDownloadPlugin
                        )
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


        fun setUserData(status: Resource<SyncAPI.AbstractSyncStatus>?) {
            if (isLocalList) return
            bottomPreviewBinding?.apply {
                when (status) {
                    is Resource.Success -> {
                        resultviewPreviewBookmark.isEnabled = true
                        resultviewPreviewBookmark.setText(status.value.status.stringRes)
                        resultviewPreviewBookmark.setIconResource(status.value.status.iconRes)
                    }

                    is Resource.Failure -> {
                        resultviewPreviewBookmark.isEnabled = false
                        resultviewPreviewBookmark.setIconResource(R.drawable.ic_baseline_bookmark_border_24)
                        resultviewPreviewBookmark.text = status.errorString
                    }

                    else -> {
                        resultviewPreviewBookmark.isEnabled = false
                        resultviewPreviewBookmark.setIconResource(R.drawable.ic_baseline_bookmark_border_24)
                        resultviewPreviewBookmark.setText(R.string.loading)
                    }
                }
            }
        }

        fun setWatchStatus(state: WatchType?) {
            if (!isLocalList || state == null) return

            bottomPreviewBinding?.resultviewPreviewBookmark?.apply {
                setIconResource(state.iconRes)
                setText(state.stringRes)
            }
        }

        fun setSubscribeStatus(state: Boolean?) {
            bottomPreviewBinding?.resultviewPreviewSubscribe?.apply {
                if (state != null) {
                    val drawable = if (state) {
                        R.drawable.ic_baseline_notifications_active_24
                    } else {
                        R.drawable.baseline_notifications_none_24
                    }
                    setImageResource(drawable)
                }
                isVisible = state != null

                setOnClickListener {
                    viewModel.toggleSubscriptionStatus(context) { newStatus: Boolean? ->
                        if (newStatus == null) return@toggleSubscriptionStatus

                        val message = if (newStatus) {
                            // Kinda icky to have this here, but it works.
                            SubscriptionWorkManager.enqueuePeriodicWork(context)
                            R.string.subscription_new
                        } else {
                            R.string.subscription_deleted
                        }

                        val name = (viewModel.page.value as? Resource.Success)?.value?.title
                            ?: txt(R.string.no_data).asStringNull(context) ?: ""
                        showToast(txt(message, name), Toast.LENGTH_SHORT)
                    }
                }
            }
        }

        observe(viewModel.watchStatus, ::setWatchStatus)
        observe(syncViewModel.userData, ::setUserData)
        observeNullable(viewModel.subscribeStatus, ::setSubscribeStatus)

        observeNullable(viewModel.page) { resource ->
            if (resource == null) {
                hidePreviewPopupDialog()
                return@observeNullable
            }
            when (resource) {
                is Resource.Failure -> {
                    showToast(R.string.error)
                    viewModel.clear()
                    hidePreviewPopupDialog()
                }

                is Resource.Loading -> {
                    showPreviewPopupDialog().apply {
                        resultviewPreviewLoading.isVisible = true
                        resultviewPreviewResult.isVisible = false
                        resultviewPreviewLoadingShimmer.startShimmer()
                    }
                }

                is Resource.Success -> {
                    val d = resource.value
                    showPreviewPopupDialog().apply {
                        resultviewPreviewLoading.isVisible = false
                        resultviewPreviewResult.isVisible = true
                        resultviewPreviewLoadingShimmer.stopShimmer()

                        resultviewPreviewTitle.text = d.title

                        resultviewPreviewMetaType.setText(d.typeText)
                        resultviewPreviewMetaYear.setText(d.yearText)
                        resultviewPreviewMetaDuration.setText(d.durationText)
                        resultviewPreviewMetaRating.setText(d.ratingText)

                        resultviewPreviewDescription.setTextHtml(d.plotText)
                        resultviewPreviewPoster.loadImage(
                            d.posterImage ?: d.posterBackgroundImage
                        )

                        setUserData(syncViewModel.userData.value)
                        setWatchStatus(viewModel.watchStatus.value)
                        setSubscribeStatus(viewModel.subscribeStatus.value)

                        resultviewPreviewBookmark.setOnClickListener {
                            //viewModel.updateWatchStatus(WatchType.PLANTOWATCH)
                            if (isLocalList) {
                                val value = viewModel.watchStatus.value ?: WatchType.NONE

                                this@MainActivity.showBottomDialog(
                                    WatchType.entries.map { getString(it.stringRes) }.toList(),
                                    value.ordinal,
                                    this@MainActivity.getString(R.string.action_add_to_bookmarks),
                                    showApply = false,
                                    {}) {
                                    viewModel.updateWatchStatus(
                                        WatchType.entries[it],
                                        this@MainActivity
                                    )
                                }
                            } else {
                                val value =
                                    (syncViewModel.userData.value as? Resource.Success)?.value?.status
                                        ?: SyncWatchType.NONE

                                this@MainActivity.showBottomDialog(
                                    SyncWatchType.entries.map { getString(it.stringRes) }.toList(),
                                    value.ordinal,
                                    this@MainActivity.getString(R.string.action_add_to_bookmarks),
                                    showApply = false,
                                    {}) {
                                    syncViewModel.setStatus(SyncWatchType.entries[it].internalId)
                                    syncViewModel.publishUserData()
                                }
                            }
                        }

                        observeNullable(viewModel.favoriteStatus) observeFavoriteStatus@{ isFavorite ->
                            resultviewPreviewFavorite.isVisible = isFavorite != null
                            if (isFavorite == null) return@observeFavoriteStatus

                            val drawable = if (isFavorite) {
                                R.drawable.ic_baseline_favorite_24
                            } else {
                                R.drawable.ic_baseline_favorite_border_24
                            }

                            resultviewPreviewFavorite.setImageResource(drawable)
                        }

                        resultviewPreviewFavorite.setOnClickListener {
                            viewModel.toggleFavoriteStatus(this@MainActivity) { newStatus: Boolean? ->
                                if (newStatus == null) return@toggleFavoriteStatus

                                val message = if (newStatus) {
                                    R.string.favorite_added
                                } else {
                                    R.string.favorite_removed
                                }

                                val name = (viewModel.page.value as? Resource.Success)?.value?.title
                                    ?: txt(R.string.no_data).asStringNull(this@MainActivity) ?: ""
                                showToast(txt(message, name), Toast.LENGTH_SHORT)
                            }
                        }

                        if (isLayout(PHONE)) // dont want this clickable on tv layout
                            resultviewPreviewDescription.setOnClickListener { view ->
                                view.context?.let { ctx ->
                                    val builder: AlertDialog.Builder =
                                        AlertDialog.Builder(ctx, R.style.AlertDialogCustom)
                                    builder.setMessage(d.plotText.asString(ctx).html())
                                        .setTitle(d.plotHeaderText.asString(ctx))
                                        .show()
                                }
                            }

                        resultviewPreviewMoreInfo.setOnClickListener {
                            viewModel.clear()
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

            // we need to run this after we init all apis, otherwise currentSyncApi will fuck itself
            this@MainActivity.runOnUiThread {
                // Change library icon with logo of current api in sync
                libraryViewModel =
                    ViewModelProvider(this@MainActivity)[LibraryViewModel::class.java]
                libraryViewModel?.currentApiName?.observe(this@MainActivity) {
                    val syncAPI = libraryViewModel?.currentSyncApi
                    Log.i("SYNC_API", "${syncAPI?.name}, ${syncAPI?.idPrefix}")
                    val icon = if (syncAPI?.idPrefix == localListApi.idPrefix) {
                        R.drawable.library_icon_selector
                    } else {
                        syncAPI?.icon ?: R.drawable.library_icon_selector
                    }

                    binding?.apply {
                        navRailView.menu.findItem(R.id.navigation_library)?.setIcon(icon)
                        navView.menu.findItem(R.id.navigation_library)?.setIcon(icon)
                    }
                }
            }
        }

        SearchResultBuilder.updateCache(this)

        ioSafe {
            initAll()
            // No duplicates (which can happen by registerMainAPI)
            apis = synchronized(allProviders) {
                allProviders.distinctBy { it }
            }
        }

        //  val navView: BottomNavigationView = findViewById(R.id.nav_view)
        setUpBackup()

        CommonActivity.init(this)
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        navController.addOnDestinationChangedListener { _: NavController, navDestination: NavDestination, bundle: Bundle? ->
            // Intercept search and add a query
            updateNavBar(navDestination)
            if (navDestination.matchDestination(R.id.navigation_search) && !nextSearchQuery.isNullOrBlank()) {
                bundle?.apply {
                    this.putString(SearchFragment.SEARCH_QUERY, nextSearchQuery)
                }
            }

            if (navDestination.matchDestination(R.id.navigation_home)) {
                attachBackPressedCallback {
                    showConfirmExitDialog(settingsManager)
                    window?.navigationBarColor =
                        colorFromAttribute(R.attr.primaryGrayBackground)
                    updateLocale()
                }
            } else detachBackPressedCallback()
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

        val rippleColor = ColorStateList.valueOf(getResourceColor(R.attr.colorPrimary, 0.1f))

        binding?.navView?.apply {
            itemRippleColor = rippleColor
            itemActiveIndicatorColor = rippleColor
            setupWithNavController(navController)
            setOnItemSelectedListener { item ->
                onNavDestinationSelected(
                    item,
                    navController
                )
            }
        }

        binding?.navRailView?.apply {
            itemRippleColor = rippleColor
            itemActiveIndicatorColor = rippleColor
            setupWithNavController(navController)
            if (isLayout(TV or EMULATOR)) {
                background?.alpha = 200
            } else {
                background?.alpha = 255
            }

            setOnItemSelectedListener { item ->
                onNavDestinationSelected(
                    item,
                    navController
                )
            }

            fun noFocus(view: View) {
                view.tag = view.context.getString(R.string.tv_no_focus_tag)
                (view as? ViewGroup)?.let {
                    for (child in it.children) {
                        noFocus(child)
                    }
                }
            }
            //noFocus(this)

            val navProfileRoot = findViewById<LinearLayout>(R.id.nav_footer_root)

            if (isLayout(TV or EMULATOR)) {
                val navProfilePic = findViewById<ImageView>(R.id.nav_footer_profile_pic)
                val navProfileCard = findViewById<CardView>(R.id.nav_footer_profile_card)

                navProfileCard?.setOnClickListener {
                    showAccountSelectLinear()
                }

                val homeViewModel =
                    ViewModelProvider(this@MainActivity)[HomeViewModel::class.java]

                observe(homeViewModel.currentAccount) { currentAccount ->
                    if (currentAccount != null) {
                        navProfilePic?.loadImage(
                            currentAccount.image
                        )
                        navProfileRoot.isVisible = true
                    } else {
                        navProfileRoot.isGone = true
                    }
                }
            } else {
                navProfileRoot.isGone = true
            }
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


        if (!checkWrite()) {
            requestRW()
            if (checkWrite()) return
        }
        //CastButtonFactory.setUpMediaRouteButton(this, media_route_button)

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
            synchronized(allProviders) {
                for (api in allProviders) {
                    providersAndroidManifestString += "<data android:scheme=\"https\" android:host=\"${
                        api.mainUrl.removePrefix(
                            "https://"
                        )
                    }\" android:pathPrefix=\"/\"/>\n"
                }
            }
            println(providersAndroidManifestString)
        }

        handleAppIntent(intent)

        ioSafe {
            runAutoUpdate()
        }

        FcastManager().init(this, false)

        APIRepository.dubStatusActive = getApiDubstatusSettings()

        try {
            // this ensures that no unnecessary space is taken
            loadCache()
            File(filesDir, "exoplayer").deleteRecursively() // old cache
            deleteFileOnExit(File(cacheDir, "exoplayer"))   // current cache
        } catch (e: Exception) {
            logError(e)
        }
        println("Loaded everything")

        ioSafe {
            migrateResumeWatching()
        }

        getKey<String>(USER_SELECTED_HOMEPAGE_API)?.let { homepage ->
            DataStoreHelper.currentHomePage = homepage
            removeKey(USER_SELECTED_HOMEPAGE_API)
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
        }

//        Used to check current focus for TV
//        main {
//            while (true) {
//                delay(5000)
//                println("Current focus: $currentFocus")
//                showToast(this, currentFocus.toString(), Toast.LENGTH_LONG)
//            }
//        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    window?.navigationBarColor = colorFromAttribute(R.attr.primaryGrayBackground)
                    updateLocale()

                    // If we don't disable we end up in a loop with default behavior calling
                    // this callback as well, so we disable it, run default behavior,
                    // then re-enable this callback so it can be used for next back press.
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        )
    }

    /** Biometric stuff **/
    override fun onAuthenticationSuccess() {
        // make background (nav host fragment) visible again
        binding?.navHostFragment?.isInvisible = false
    }

    override fun onAuthenticationError() {
        finish()
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
