package com.lagradost.cloudstream3.ui.setup

import android.view.View
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentSetupSyncBinding
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.utils.FirestoreSyncManager
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding

class SetupFragmentSync : BaseFragment<FragmentSetupSyncBinding>(
    BaseFragment.BindingCreator.Inflate(FragmentSetupSyncBinding::inflate)
) {

    override fun fixLayout(view: View) {
        fixSystemBarsPadding(view)
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun updateUI() {
        val binding = binding ?: return
        val context = context ?: return

        if (FirestoreSyncManager.isLogged()) {
            binding.syncDescriptionText.text = "Account Connected!\nYou are ready to sync."
            // Hide the "Yes, Setup Sync" button since it is already done
             binding.syncYesBtt.isVisible = false
            // The Next button is already named "Next" in XML
        } else {
             binding.syncDescriptionText.text = "With Firebase SYNC, you can sync all your settings with your other devices."
             binding.syncYesBtt.isVisible = true
        }
    }

    override fun onBindingCreated(binding: FragmentSetupSyncBinding) {
        // "Yes, Setup Sync" -> Go to Sync Settings
        binding.syncYesBtt.setOnClickListener {
            findNavController().navigate(R.id.action_navigation_setup_sync_to_navigation_settings_sync)
        }

        // "Next" -> Go to Next step (App Layout)
        binding.nextBtt.setOnClickListener {
             findNavController().navigate(R.id.action_navigation_setup_sync_to_navigation_setup_layout)
        }
        
        updateUI()
    }
}
