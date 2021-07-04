package com.lagradost.cloudstream3

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.anggrayudi.storage.SimpleStorage
import com.google.android.gms.cast.ApplicationMetadata
import com.google.android.gms.cast.Cast
import com.google.android.gms.cast.LaunchOptions
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.lagradost.cloudstream3.UIHelper.checkWrite
import com.lagradost.cloudstream3.UIHelper.hasPIPPermission
import com.lagradost.cloudstream3.UIHelper.requestRW
import com.lagradost.cloudstream3.UIHelper.shouldShowPIPMode
import kotlinx.android.synthetic.main.fragment_result.*


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

        //https://github.com/anggrayudi/SimpleStorage/blob/4eb6306efb6cdfae4e34f170c8b9d4e135b04d51/sample/src/main/java/com/anggrayudi/storage/sample/activity/MainActivity.kt#L624
        const val REQUEST_CODE_STORAGE_ACCESS = 1
        const val REQUEST_CODE_PICK_FOLDER = 2
        const val REQUEST_CODE_PICK_FILE = 3
        const val REQUEST_CODE_ASK_PERMISSIONS = 4
    }

    private lateinit var storage: SimpleStorage

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


    private fun setupSimpleStorage() {
        storage = SimpleStorage(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Mandatory for Activity, but not for Fragment
        storage.onActivityResult(requestCode, resultCode, data)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        storage.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        storage.onRestoreInstanceState(savedInstanceState)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainContext = this
        setupSimpleStorage()
        storage.requestStorageAccess(REQUEST_CODE_STORAGE_ACCESS)

        setContentView(R.layout.activity_main)
        val navView: BottomNavigationView = findViewById(R.id.nav_view)

        //https://stackoverflow.com/questions/52594181/how-to-know-if-user-has-disabled-picture-in-picture-feature-permission
        //https://developer.android.com/guide/topics/ui/picture-in-picture
        canShowPipMode =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && // OS SUPPORT
                    packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) && // HAS FEATURE, MIGHT BE BLOCKED DUE TO POWER DRAIN
                    hasPIPPermission() // CHECK IF FEATURE IS ENABLED IN SETTINGS

        val navController = findNavController(R.id.nav_host_fragment)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_search, R.id.navigation_notifications
            )
        )
        //setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        if (!checkWrite()) {
            requestRW()
            if (checkWrite()) return
        }
        CastButtonFactory.setUpMediaRouteButton(this, media_route_button)

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