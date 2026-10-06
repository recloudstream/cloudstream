package com.lagradost.cloudstream3.ui.account

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.CommonActivity.loadThemes
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.DataStoreHelper.Account
import com.lagradost.cloudstream3.utils.UIHelper.enableEdgeToEdgeCompat
import com.lagradost.cloudstream3.utils.UIHelper.openActivity
import com.lagradost.cloudstream3.utils.UIHelper.setNavigationBarColorCompat
import com.lagradost.cloudstream4.compose.CloudStreamThemeAppSettings

class AccountSelectActivity2: FragmentActivity() {
    companion object {
        var hasLoggedIn: Boolean = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadThemes(this)
        enableEdgeToEdgeCompat()
        setNavigationBarColorCompat(R.attr.primaryBlackBackground)
        CommonActivity.init(this)

        setContent {
            val defaultName = stringResource(R.string.default_account)
            val viewModel = viewModel<AccountViewModel2> {
                AccountViewModel2(
                    defaultAccount = Account(
                        keyIndex = 0,
                        name = defaultName,
                        defaultImageIndex = 0,
                    )
                )
            }
            val state by viewModel.state.collectAsState()
            if(state.loggedInWith != null) {
                navigateToMainActivity()
            }
            CloudStreamThemeAppSettings {
                AccountScreen.Content(state, viewModel::onAction)
            }
        }
    }

    @SuppressLint("UnsafeIntentLaunch")
    private fun navigateToMainActivity() {
        hasLoggedIn = true
        // We want to propagate any intent we get here to MainActivity since this is just an intermediary
        openActivity(MainActivity::class.java, baseIntent = intent)
        finish() // Finish the account selection activity
    }
}