package com.lagradost.cloudstream3

import android.os.Bundle
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.lagradost.cloudstream3.UIHelper.checkWrite
import com.lagradost.cloudstream3.UIHelper.requestRW

class MainActivity : AppCompatActivity() {/*, ViewModelStoreOwner {
    private val appViewModelStore: ViewModelStore by lazy {
        ViewModelStore()
    }

    override fun getViewModelStore(): ViewModelStore {
        return appViewModelStore
    }*/

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val navView: BottomNavigationView = findViewById(R.id.nav_view)

        val navController = findNavController(R.id.nav_host_fragment)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(setOf(
            R.id.navigation_home, R.id.navigation_search, R.id.navigation_notifications))
        //setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        if (!checkWrite()) {
            requestRW()
            if (checkWrite()) return
        }
    }
}