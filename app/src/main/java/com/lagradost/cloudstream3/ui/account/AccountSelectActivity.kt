package com.lagradost.cloudstream3.ui.account

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.CommonActivity.loadThemes
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.ActivityAccountSelectBinding
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.account.AccountAdapter.Companion.VIEW_TYPE_EDIT_ACCOUNT
import com.lagradost.cloudstream3.ui.account.AccountAdapter.Companion.VIEW_TYPE_SELECT_ACCOUNT
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTvSettings
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.getAccounts
import com.lagradost.cloudstream3.utils.DataStoreHelper.setAccount
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute

class AccountSelectActivity : AppCompatActivity() {

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Are we editing and coming from MainActivity?
        val isEditingFromMainActivity = intent.getBooleanExtra(
            "isEditingFromMainActivity",
            false
        )

        val accounts = getAccounts(this@AccountSelectActivity)

        // Don't show account selection if there is only
        // one account that exists
        if (!isEditingFromMainActivity && accounts.count() <= 1) {
            navigateToMainActivity()
            return
        }

        CommonActivity.init(this)
        loadThemes(this)

        window.navigationBarColor = colorFromAttribute(R.attr.primaryBlackBackground)

        val binding = ActivityAccountSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val recyclerView: RecyclerView = binding.accountRecyclerView

        val viewModel = ViewModelProvider(this)[AccountViewModel::class.java]

        observe(viewModel.accounts) { liveAccounts ->
            val adapter = AccountAdapter(
                liveAccounts,
                // Handle the selected account
                accountSelectCallback = {
                    viewModel.handleAccountSelect(it,this@AccountSelectActivity)
                    observe(viewModel.isAllowedLogin) { isAllowedLogin ->
                        if (isAllowedLogin) {
                            // We are allowed to continue to MainActivity
                            navigateToMainActivity()
                        }
                    }
                },
                accountCreateCallback = { viewModel.handleAccountUpdate(it, this@AccountSelectActivity) },
                accountEditCallback = {
                    viewModel.handleAccountUpdate(it, this@AccountSelectActivity)

                    // We came from MainActivity, return there
                    // and switch to the edited account
                    if (isEditingFromMainActivity) {
                        setAccount(it, it.keyIndex != DataStoreHelper.selectedKeyIndex)
                        navigateToMainActivity()
                    }
                },
                accountDeleteCallback = { viewModel.handleAccountUpdate(this@AccountSelectActivity) }
            )

            recyclerView.adapter = adapter

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
        }

        if (isTvSettings()) {
            val spanSize = if (accounts.count() <= 6) {
                accounts.count()
            } else 6

            recyclerView.layoutManager = GridLayoutManager(this, spanSize)
        }
    }

    private fun navigateToMainActivity() {
        val mainIntent = Intent(this, MainActivity::class.java)
        startActivity(mainIntent)
        finish() // Finish the account selection activity
    }
}