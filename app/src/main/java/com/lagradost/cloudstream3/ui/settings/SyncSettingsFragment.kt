package com.lagradost.cloudstream3.ui.settings

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.databinding.FragmentSyncSettingsBinding
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.setKey
import com.lagradost.cloudstream3.utils.FirestoreSyncManager
import com.lagradost.cloudstream3.utils.UIHelper.clipboardHelper
import com.lagradost.cloudstream3.utils.txt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SyncSettingsFragment : BaseFragment<FragmentSyncSettingsBinding>(
    BaseFragment.BindingCreator.Inflate(FragmentSyncSettingsBinding::inflate)
) {
    override fun fixLayout(view: View) {
        // No special layout fixes needed currently
    }

    override fun onBindingCreated(binding: FragmentSyncSettingsBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)
        
        binding?.syncToolbar?.setNavigationOnClickListener {
            activity?.onBackPressedDispatcher?.onBackPressed()
        }

        setupInputs()
        updateStatusUI()

        binding?.syncConnectBtn?.setOnClickListener {
            connect()
        }

        binding?.syncNowBtn?.setOnClickListener {
            showToast("Syncing...")
            FirestoreSyncManager.pushAllLocalData(requireContext(), immediate = true)
            // Brief delay to allow sync to happen then update UI
            view?.postDelayed({ updateStatusUI() }, 1000)
        }

        binding.syncCopyLogsBtn.setOnClickListener {
            val logs = FirestoreSyncManager.getLogs()
            if (logs.isBlank()) {
                showToast("No logs available yet.")
            } else {
                clipboardHelper(txt("Sync Logs"), logs)
                showToast("Logs copied to clipboard")
            }
        }
    }

    private fun setupInputs() {
        val context = requireContext()
        binding?.apply {
            syncApiKey.setText(context.getKey(FirestoreSyncManager.FIREBASE_API_KEY, ""))
            syncProjectId.setText(context.getKey(FirestoreSyncManager.FIREBASE_PROJECT_ID, ""))
            syncAppId.setText(context.getKey(FirestoreSyncManager.FIREBASE_APP_ID, ""))
            
            val checkBtn = {
                syncConnectBtn.isEnabled = syncApiKey.text?.isNotBlank() == true &&
                        syncProjectId.text?.isNotBlank() == true &&
                        syncAppId.text?.isNotBlank() == true
            }
            
            syncApiKey.doAfterTextChanged { checkBtn() }
            syncProjectId.doAfterTextChanged { checkBtn() }
            syncAppId.doAfterTextChanged { checkBtn() }
            checkBtn()

            // Bind granular toggles
            setupGranularToggle(syncAppearanceLayout, FirestoreSyncManager.SYNC_SETTING_APPEARANCE, "Appearance", "Sync theme, colors, and layout preferences.")
            setupGranularToggle(syncPlayerLayout, FirestoreSyncManager.SYNC_SETTING_PLAYER, "Player Settings", "Sync subtitle styles, player gestures, and video quality.")
            setupGranularToggle(syncDownloadsLayout, FirestoreSyncManager.SYNC_SETTING_DOWNLOADS, "Downloads", "Sync download paths and parallel download limits.")
            setupGranularToggle(syncGeneralLayout, FirestoreSyncManager.SYNC_SETTING_GENERAL, "General Settings", "Sync miscellaneous app-wide preferences.")
            
            setupGranularToggle(syncAccountsLayout, FirestoreSyncManager.SYNC_SETTING_ACCOUNTS, "User Profiles", "Sync profile names, avatars, and linked accounts.")
            setupGranularToggle(syncBookmarksLayout, FirestoreSyncManager.SYNC_SETTING_BOOKMARKS, "Bookmarks", "Sync your watchlist and favorite items.")
            setupGranularToggle(syncResumeWatchingLayout, FirestoreSyncManager.SYNC_SETTING_RESUME_WATCHING, "Watch Progress", "Sync where you left off on every movie/episode.")
            
            setupGranularToggle(syncRepositoriesLayout, FirestoreSyncManager.SYNC_SETTING_REPOSITORIES, "Source Repositories", "Sync the list of added plugin repositories.")
            setupGranularToggle(syncPluginsLayout, FirestoreSyncManager.SYNC_SETTING_PLUGINS, "Installed Plugins", "Sync which online plugins are installed.")
            
            setupGranularToggle(syncHomepageLayout, FirestoreSyncManager.SYNC_SETTING_HOMEPAGE_API, "Home Provider", "Sync which homepage source is currently active.")
            setupGranularToggle(syncPinnedLayout, FirestoreSyncManager.SYNC_SETTING_PINNED_PROVIDERS, "Pinned Providers", "Sync your pinned providers on the home screen.")
        }
    }

    private fun setupGranularToggle(row: com.lagradost.cloudstream3.databinding.SyncItemRowBinding, key: String, title: String, desc: String) {
        row.syncItemTitle.text = title
        row.syncItemDesc.text = desc
        val current = requireContext().getKey(key, true) ?: true
        row.syncItemSwitch.isChecked = current
        
        row.syncItemSwitch.setOnCheckedChangeListener { _, isChecked ->
            requireContext().setKey(key, isChecked)
        }
    }

    private fun connect() {
        val config = FirestoreSyncManager.SyncConfig(
            apiKey = binding?.syncApiKey?.text?.toString() ?: "",
            projectId = binding?.syncProjectId?.text?.toString() ?: "",
            appId = binding?.syncAppId?.text?.toString() ?: ""
        )
        
        FirestoreSyncManager.initialize(requireContext(), config)
        showToast("Initial sync started...")
        view?.postDelayed({ updateStatusUI() }, 1500)
    }

    private fun updateStatusUI() {
        val enabled = FirestoreSyncManager.isEnabled(requireContext())
        binding?.syncStatusCard?.isVisible = enabled
        if (enabled) {
            val isOnline = FirestoreSyncManager.isOnline()
            binding?.syncStatusText?.text = if (isOnline) "Connected" else "Disconnected (Check Logs)"
            binding?.syncStatusText?.setTextColor(
                if (isOnline) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")
            )
            binding?.syncConnectBtn?.text = "Reconnect"
            
            val lastSync = FirestoreSyncManager.getLastSyncTime(requireContext())
            if (lastSync != null) {
                val sdf = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
                binding?.syncLastTime?.text = sdf.format(Date(lastSync))
            } else {
                binding?.syncLastTime?.text = "Never"
            }
            
            binding?.syncAppSettingsCard?.isVisible = true
            binding?.syncLibraryCard?.isVisible = true
            binding?.syncExtensionsCard?.isVisible = true
            binding?.syncInterfaceCard?.isVisible = true
            
            // Re-sync switch states visually
            binding?.apply {
                syncAppearanceLayout.syncItemSwitch.isChecked = requireContext().getKey(FirestoreSyncManager.SYNC_SETTING_APPEARANCE, true) ?: true
                syncPlayerLayout.syncItemSwitch.isChecked = requireContext().getKey(FirestoreSyncManager.SYNC_SETTING_PLAYER, true) ?: true
                syncDownloadsLayout.syncItemSwitch.isChecked = requireContext().getKey(FirestoreSyncManager.SYNC_SETTING_DOWNLOADS, true) ?: true
                syncGeneralLayout.syncItemSwitch.isChecked = requireContext().getKey(FirestoreSyncManager.SYNC_SETTING_GENERAL, true) ?: true
                
                syncAccountsLayout.syncItemSwitch.isChecked = requireContext().getKey(FirestoreSyncManager.SYNC_SETTING_ACCOUNTS, true) ?: true
                syncBookmarksLayout.syncItemSwitch.isChecked = requireContext().getKey(FirestoreSyncManager.SYNC_SETTING_BOOKMARKS, true) ?: true
                syncResumeWatchingLayout.syncItemSwitch.isChecked = requireContext().getKey(FirestoreSyncManager.SYNC_SETTING_RESUME_WATCHING, true) ?: true
                
                syncRepositoriesLayout.syncItemSwitch.isChecked = requireContext().getKey(FirestoreSyncManager.SYNC_SETTING_REPOSITORIES, true) ?: true
                syncPluginsLayout.syncItemSwitch.isChecked = requireContext().getKey(FirestoreSyncManager.SYNC_SETTING_PLUGINS, true) ?: true
                
                syncHomepageLayout.syncItemSwitch.isChecked = requireContext().getKey(FirestoreSyncManager.SYNC_SETTING_HOMEPAGE_API, true) ?: true
                syncPinnedLayout.syncItemSwitch.isChecked = requireContext().getKey(FirestoreSyncManager.SYNC_SETTING_PINNED_PROVIDERS, true) ?: true
            }
        } else {
            binding?.syncConnectBtn?.text = "Connect & Sync"
            binding?.syncAppSettingsCard?.isVisible = false
            binding?.syncLibraryCard?.isVisible = false
            binding?.syncExtensionsCard?.isVisible = false
            binding?.syncInterfaceCard?.isVisible = false
        }
    }
}
