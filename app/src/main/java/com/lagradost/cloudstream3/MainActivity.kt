package com.lagradost.cloudstream3

import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.lagradost.cloudstream3.UIHelper.checkWrite
import com.lagradost.cloudstream3.UIHelper.getResourceColor
import com.lagradost.cloudstream3.UIHelper.hasPIPPermission
import com.lagradost.cloudstream3.UIHelper.isUsingMobileData
import com.lagradost.cloudstream3.UIHelper.requestRW
import com.lagradost.cloudstream3.UIHelper.shouldShowPIPMode
import com.lagradost.cloudstream3.receivers.VideoDownloadRestartReceiver
import com.lagradost.cloudstream3.ui.download.DownloadChildFragment
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.getKeys
import com.lagradost.cloudstream3.utils.DataStore.removeKey
import com.lagradost.cloudstream3.utils.DataStore.removeKeys
import com.lagradost.cloudstream3.utils.DataStoreHelper.setViewPos
import com.lagradost.cloudstream3.utils.VideoDownloadManager
import kotlinx.android.synthetic.main.fragment_result.*

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

class MainActivity : AppCompatActivity() {
    /*, ViewModelStoreOwner {
        private val appViewModelStore: ViewModelStore by lazy {
            ViewModelStore()
        }

        override fun getViewModelStore(): ViewModelStore {
            return appViewModelStore
        }*/
    companion object {
        var isInPlayer: Boolean = false
        var canShowPipMode: Boolean = false
        var isInPIPMode: Boolean = false
        lateinit var mainContext: MainActivity
        lateinit var navOptions: NavOptions

        //https://github.com/anggrayudi/SimpleStorage/blob/4eb6306efb6cdfae4e34f170c8b9d4e135b04d51/sample/src/main/java/com/anggrayudi/storage/sample/activity/MainActivity.kt#L624
        const val REQUEST_CODE_STORAGE_ACCESS = 1
        const val REQUEST_CODE_PICK_FOLDER = 2
        const val REQUEST_CODE_PICK_FILE = 3
        const val REQUEST_CODE_ASK_PERMISSIONS = 4
    }

    private fun enterPIPMode() {
        if (!shouldShowPIPMode(isInPlayer) || !canShowPipMode) return
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
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isInPlayer && canShowPipMode) {
            enterPIPMode()
        }
    }

    private fun AppCompatActivity.backPressed(): Boolean {
        val currentFragment = supportFragmentManager.fragments.last {
            it.isVisible
        }

        if (currentFragment is NavHostFragment) {
            val child = currentFragment.childFragmentManager.fragments.last {
                it.isVisible
            }
            if (child is DownloadChildFragment) {
                val navController = findNavController(R.id.nav_host_fragment)
                navController.navigate(R.id.navigation_downloads, Bundle(), navOptions)
                return true
            }
        }

        if (currentFragment != null && supportFragmentManager.fragments.size > 2) {
            //MainActivity.showNavbar()
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.enter_anim, R.anim.exit_anim, R.anim.pop_enter, R.anim.pop_exit)
                .remove(currentFragment)
                .commitAllowingStateLoss()
            return true
        }
        return false
    }

    override fun onBackPressed() {
        if (backPressed()) return
        super.onBackPressed()
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainContext = this

        setContentView(R.layout.activity_main)
        val navView: BottomNavigationView = findViewById(R.id.nav_view)

        //https://stackoverflow.com/questions/52594181/how-to-know-if-user-has-disabled-picture-in-picture-feature-permission
        //https://developer.android.com/guide/topics/ui/picture-in-picture
        canShowPipMode =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && // OS SUPPORT
                    packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) && // HAS FEATURE, MIGHT BE BLOCKED DUE TO POWER DRAIN
                    hasPIPPermission() // CHECK IF FEATURE IS ENABLED IN SETTINGS

        val navController = findNavController(R.id.nav_host_fragment)

        navOptions = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setEnterAnim(R.anim.nav_enter_anim)
            .setExitAnim(R.anim.nav_exit_anim)
            .setPopEnterAnim(R.anim.nav_pop_enter)
            .setPopExitAnim(R.anim.nav_pop_exit)
            .setPopUpTo(navController.graph.startDestination, false)
            .build()

        navView.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
               // R.id.navigation_home -> {
                   // navController.navigate(R.id.navigation_home, null, navOptions)
                //}
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
        }

        navView.itemRippleColor = ColorStateList.valueOf(getResourceColor(R.attr.colorPrimary, 0.1f))

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
    }
}