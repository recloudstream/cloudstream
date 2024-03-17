package com.lagradost.cloudstream3.ui.account

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.CommonActivity.loadThemes
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.ActivityAccountSelectBinding
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.AutofitRecyclerView
import com.lagradost.cloudstream3.ui.account.AccountAdapter.Companion.VIEW_TYPE_EDIT_ACCOUNT
import com.lagradost.cloudstream3.ui.account.AccountAdapter.Companion.VIEW_TYPE_SELECT_ACCOUNT
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.BiometricAuthenticator
import com.lagradost.cloudstream3.utils.BiometricAuthenticator.deviceHasPasswordPinLock
import com.lagradost.cloudstream3.utils.BiometricAuthenticator.startBiometricAuthentication
import com.lagradost.cloudstream3.utils.DataStoreHelper.accounts
import com.lagradost.cloudstream3.utils.DataStoreHelper.selectedKeyIndex
import com.lagradost.cloudstream3.utils.DataStoreHelper.setAccount
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute

class AccountSelectActivity : AppCompatActivity(), BiometricAuthenticator.BiometricAuthCallback {

    lateinit var viewModel: AccountViewModel

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadThemes(this)

        window.navigationBarColor = colorFromAttribute(R.attr.primaryBlackBackground)

        // Are we editing and coming from MainActivity?
        val isEditingFromMainActivity = intent.getBooleanExtra(
            "isEditingFromMainActivity",
            false
        )

        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        val authEnabled = settingsManager.getBoolean(getString(R.string.biometric_key), false)
        val skipStartup = settingsManager.getBoolean(getString(R.string.skip_startup_account_select_key), false
        ) || accounts.count() <= 1

        viewModel = ViewModelProvider(this)[AccountViewModel::class.java]

        fun askBiometricAuth() {

            if (isLayout(PHONE) && authEnabled) {
                if (deviceHasPasswordPinLock(this)) {
                    startBiometricAuthentication(
                        this,
                        R.string.biometric_authentication_title,
                        false
                    )

                    BiometricAuthenticator.promptInfo?.let { promt ->
                        BiometricAuthenticator.biometricPrompt?.authenticate(promt)
                    }
                }
            }
        }

        observe(viewModel.isAllowedLogin) { isAllowedLogin ->
            if (isAllowedLogin) {
                // We are allowed to continue to MainActivity
                navigateToMainActivity()
            }
        }

        // Don't show account selection if there is only
        // one account that exists
        if (!isEditingFromMainActivity && skipStartup) {
            val currentAccount = accounts.firstOrNull { it.keyIndex == selectedKeyIndex }
            if (currentAccount?.lockPin != null) {
                CommonActivity.init(this)
                viewModel.handleAccountSelect(currentAccount, this, true)
            } else {
                if (accounts.count() > 1) {
                    showToast(this, getString(
                        R.string.logged_account,
                        currentAccount?.name
                    ))
                }

                navigateToMainActivity()
            }

            return
        }

        CommonActivity.init(this)

        val binding = ActivityAccountSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val recyclerView: AutofitRecyclerView = binding.accountRecyclerView

        observe(viewModel.accounts) { liveAccounts ->
            val adapter = AccountAdapter(
                liveAccounts,
                // Handle the selected account
                accountSelectCallback = {
                    viewModel.handleAccountSelect(it, this)
                },
                accountCreateCallback = { viewModel.handleAccountUpdate(it, this) },
                accountEditCallback = {
                    viewModel.handleAccountUpdate(it, this)

                    // We came from MainActivity, return there
                    // and switch to the edited account
                    if (isEditingFromMainActivity) {
                        setAccount(it)
                        navigateToMainActivity()
                    }
                },
                accountDeleteCallback = { viewModel.handleAccountDelete(it,this) }
            )

            recyclerView.adapter = adapter

            if (isLayout(TV or EMULATOR)) {
                binding.editAccountButton.setBackgroundResource(
                    R.drawable.player_button_tv_attr_no_bg
                )
            }

            observe(viewModel.selectedKeyIndex) { selectedKeyIndex ->
                // Scroll to current account (which is focused by default)
                val layoutManager = recyclerView.layoutManager as GridLayoutManager
                layoutManager.scrollToPositionWithOffset(selectedKeyIndex, 0)
            }

            observe(viewModel.isEditing) { isEditing ->
                if (isEditing) {
                    binding.editAccountButton.setImageResource(R.drawable.ic_baseline_close_24)
                    binding.title.setText(R.string.manage_accounts)
                    adapter.viewType = VIEW_TYPE_EDIT_ACCOUNT
                } else {
                    binding.editAccountButton.setImageResource(R.drawable.ic_baseline_edit_24)
                    binding.title.setText(R.string.select_an_account)
                    adapter.viewType = VIEW_TYPE_SELECT_ACCOUNT
                }

                adapter.notifyDataSetChanged()
            }

            if (isEditingFromMainActivity) {
                viewModel.setIsEditing(true)
            }

            binding.editAccountButton.setOnClickListener {
                // We came from MainActivity, return there
                // and resume its state
                if (isEditingFromMainActivity) {
                    navigateToMainActivity()
                    return@setOnClickListener
                }

                viewModel.toggleIsEditing()
            }

            if (isLayout(TV or EMULATOR)) {
                recyclerView.spanCount = if (liveAccounts.count() + 1 <= 6) {
                    liveAccounts.count() + 1
                } else 6
            }
        }

        askBiometricAuth()
    }

    private fun navigateToMainActivity() {
        val mainIntent = Intent(this, MainActivity::class.java)
        startActivity(mainIntent)
        finish() // Finish the account selection activity
    }

    override fun onAuthenticationSuccess() {
       Log.i(BiometricAuthenticator.TAG,"Authentication successful in AccountSelectActivity")
    }
}