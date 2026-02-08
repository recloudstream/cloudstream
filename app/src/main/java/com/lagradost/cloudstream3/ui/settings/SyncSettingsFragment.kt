package com.lagradost.cloudstream3.ui.settings

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentSyncSettingsBinding
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.utils.getKey
import com.lagradost.cloudstream3.utils.setKey
import com.lagradost.cloudstream3.utils.FirestoreSyncManager
import com.lagradost.cloudstream3.utils.UIHelper.clipboardHelper
import com.lagradost.cloudstream3.utils.txt
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SyncSettingsFragment : BaseFragment<FragmentSyncSettingsBinding>(
    BaseFragment.BindingCreator.Inflate(FragmentSyncSettingsBinding::inflate)
) {
    override fun fixLayout(view: View) {
        // No special layout fixes needed currently
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    override fun onBindingCreated(binding: FragmentSyncSettingsBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)
        
        binding.syncToolbar.setNavigationOnClickListener {
            activity?.onBackPressedDispatcher?.onBackPressed()
        }

        setupDatabaseConfigInputs(binding)
        setupGranularToggles(binding)
        setupAuthActions(binding)
        setupPluginActions(binding)

        binding.syncConnectBtn.setOnClickListener {
            connect(binding)
        }

        binding.syncNowBtn.setOnClickListener {
            showToast("Syncing...")
            FirestoreSyncManager.syncNow(requireContext())
            view?.postDelayed({ updateUI() }, 1000)
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
        
        // Toggle Database Config Visibility
        binding.syncConfigHeader.setOnClickListener {
             val isVisible = binding.syncConfigContainer.isVisible
             binding.syncConfigContainer.isVisible = !isVisible
             // Rotation animation
             val arrow = binding.syncConfigHeader.getChildAt(1)
             arrow.animate().rotation(if (isVisible) 0f else 180f).setDuration(200).start()
        }
        
        // Auto-expand if not enabled/connected
        val isEnabled = FirestoreSyncManager.isEnabled(requireContext())
        binding.syncConfigContainer.isVisible = !isEnabled
        if (!isEnabled) {
             binding.syncConfigHeader.getChildAt(1).rotation = 180f
        }
        
        updateUI()
    }

    private fun setupDatabaseConfigInputs(binding: FragmentSyncSettingsBinding) {
        val context = requireContext()
        binding.apply {
            // Fix: Use getKey<String> to ensure we get the clean string value (handling JSON quotes if any)
            syncApiKey.setText(context.getKey<String>(FirestoreSyncManager.FIREBASE_API_KEY) ?: "")
            syncProjectId.setText(context.getKey<String>(FirestoreSyncManager.FIREBASE_PROJECT_ID) ?: "")
            syncAppId.setText(context.getKey<String>(FirestoreSyncManager.FIREBASE_APP_ID) ?: "")
            
            val checkBtn = {
                syncConnectBtn.isEnabled = syncApiKey.text?.isNotBlank() == true &&
                        syncProjectId.text?.isNotBlank() == true &&
                        syncAppId.text?.isNotBlank() == true
            }
            
            syncApiKey.doAfterTextChanged { checkBtn() }
            syncProjectId.doAfterTextChanged { checkBtn() }
            syncAppId.doAfterTextChanged { checkBtn() }
            checkBtn()
        }
    }

    private fun setupAuthActions(binding: FragmentSyncSettingsBinding) {
        binding.syncLoginRegisterBtn.setOnClickListener {
            val email = binding.syncEmailInput.text?.toString()?.trim() ?: ""
            val pass = binding.syncPasswordInput.text?.toString()?.trim() ?: ""
            
            if (email.isBlank() || pass.length < 6) {
                showToast("Please enter email and password (min 6 chars).", 1)
                return@setOnClickListener
            }
            
            binding.syncLoginRegisterBtn.isEnabled = false
            binding.syncLoginRegisterBtn.text = "Authenticating..."
            
            FirestoreSyncManager.loginOrRegister(email, pass) { success, msg ->
                main {
                    binding.syncLoginRegisterBtn.isEnabled = true
                    binding.syncLoginRegisterBtn.text = "Login / Register"
                    
                    if (success) {
                        showToast("Authenticated successfully!", 0)
                        updateUI()
                    } else {
                        showToast("Auth failed: $msg", 1)
                    }
                }
            }
        }
        
        binding.syncLogoutBtn.setOnClickListener {
            FirestoreSyncManager.logout(requireContext())
            updateUI()
        }
    }

    private fun setupPluginActions(binding: FragmentSyncSettingsBinding) {
        binding.syncInstallPluginsBtn.setOnClickListener {
            showToast("Installing all pending plugins...")
             ioSafe {
                 FirestoreSyncManager.installAllPending(requireActivity())
                 main { updateUI() }
             }
        }
        
        binding.syncIgnorePluginsBtn.setOnClickListener {
            // Updated to use the new robust ignore logic
            FirestoreSyncManager.ignoreAllPendingPlugins(requireContext())
            updateUI()
            showToast("Pending list cleared and ignored.")
        }
    }

    private fun setupGranularToggles(binding: FragmentSyncSettingsBinding) {
        binding.apply {
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

    private fun connect(binding: FragmentSyncSettingsBinding) {
        val config = FirestoreSyncManager.SyncConfig(
            apiKey = binding.syncApiKey.text?.toString() ?: "",
            projectId = binding.syncProjectId.text?.toString() ?: "",
            appId = binding.syncAppId.text?.toString() ?: ""
        )
        
        FirestoreSyncManager.initialize(requireContext(), config)
        showToast("Connecting...")
        view?.postDelayed({ updateUI() }, 1500)
    }

    private fun updateUI() {
        val binding = binding ?: return
        val context = context ?: return
        
        // 1. Connection Status
        val enabled = FirestoreSyncManager.isEnabled(context)
        val isOnline = FirestoreSyncManager.isOnline()
        val isLogged = FirestoreSyncManager.isLogged()
        
        // Status Card
        binding.syncStatusCard.isVisible = enabled
        
        // Account Card Visibility: Only show if enabled (connected to DB config)
        binding.syncAccountCard.isVisible = enabled
        
        if (enabled) {
            if (isLogged) {
                binding.syncStatusText.text = "Connected"
                binding.syncStatusText.setTextColor(Color.parseColor("#4CAF50")) // Green
            } else if (isOnline) {
                 // Connected to DB but not logged in
                 binding.syncStatusText.text = "Login Needed"
                 binding.syncStatusText.setTextColor(Color.parseColor("#FFC107")) // Amber/Yellow
            } else {
                 val error = FirestoreSyncManager.lastInitError
                 if (error != null) {
                     binding.syncStatusText.text = "Error: $error"
                 } else {
                     binding.syncStatusText.text = "Disconnected"
                 }
                 binding.syncStatusText.setTextColor(Color.parseColor("#F44336")) // Red
            }

            val lastSync = FirestoreSyncManager.getLastSyncTime(context)
            if (lastSync != null) {
                val sdf = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
                binding.syncLastTime.text = sdf.format(Date(lastSync))
            } else {
                binding.syncLastTime.text = "Never"
            }
        } else {
             binding.syncConnectBtn.text = "Connect Database"
        }

        // 2. Auth State
        if (isLogged) {
            val email = FirestoreSyncManager.getUserEmail() ?: "Unknown User"
            binding.syncAccountStatus.text = "Signed in as: $email"
            binding.syncAccountStatus.setTextColor(Color.parseColor("#4CAF50")) // Green
            binding.syncAuthInputContainer.isVisible = false
            binding.syncLogoutBtn.isVisible = true
            
            // Show content sections
            binding.syncAppSettingsCard.isVisible = true
            binding.syncLibraryCard.isVisible = true
            binding.syncExtensionsCard.isVisible = true
            binding.syncInterfaceCard.isVisible = true
        } else {
            binding.syncAccountStatus.text = "Not Logged In"
            binding.syncAccountStatus.setTextColor(Color.parseColor("#F44336")) // Red
            binding.syncAuthInputContainer.isVisible = true
            binding.syncLogoutBtn.isVisible = false
            
            // Hide content sections (require login)
            binding.syncAppSettingsCard.isVisible = false
            binding.syncLibraryCard.isVisible = false
            binding.syncExtensionsCard.isVisible = false
            binding.syncInterfaceCard.isVisible = false
        }
        
        // 3. Pending Plugins
        val pendingPlugins = FirestoreSyncManager.getPendingPlugins(context)
        if (pendingPlugins.isNotEmpty() && isLogged) {
            binding.syncPendingPluginsCard.isVisible = true
            binding.syncPendingPluginsList.removeAllViews()
            
            // Update Header with Count
            binding.syncPendingTitle.text = "New Plugins Detected (${pendingPlugins.size})"
            binding.syncPendingTitle.setOnLongClickListener {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                    .setTitle("Sync Debug Info")
                    .setMessage(FirestoreSyncManager.lastSyncDebugInfo)
                    .setPositiveButton("OK", null)
                    .show()
                true
            }
            
            pendingPlugins.forEach { plugin ->
                 val itemLayout = LinearLayout(context).apply {
                     orientation = LinearLayout.HORIZONTAL
                     setPadding(0, 10, 0, 10)
                     layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 
                        LinearLayout.LayoutParams.WRAP_CONTENT
                     )
                 }
                 
                 val nameView = TextView(context).apply {
                     text = plugin.internalName
                     textSize = 16f
                     setTextColor(Color.WHITE) // TODO: Get attr color
                     layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                 }
                 
                 // Install Button (Small)
                 val installBtn = com.google.android.material.button.MaterialButton(context).apply {
                     text = "Install"
                     textSize = 12f
                     setOnClickListener {
                         ioSafe {
                             val success = FirestoreSyncManager.installPendingPlugin(requireActivity(), plugin)
                             main { 
                                 if(success) updateUI() 
                             }
                         }
                     }
                 }
                 
                 // Dismiss Button (Small, Red)
                 val dismissBtn = com.google.android.material.button.MaterialButton(context).apply {
                     text = "X"
                     textSize = 12f
                     setBackgroundColor(Color.TRANSPARENT)
                     setTextColor(Color.RED)
                     setOnClickListener {
                         FirestoreSyncManager.ignorePendingPlugin(context, plugin)
                         updateUI()
                     }
                 }
                 
                 itemLayout.addView(nameView)
                 itemLayout.addView(dismissBtn)
                 itemLayout.addView(installBtn)
                 binding.syncPendingPluginsList.addView(itemLayout)
            }
        } else {
            binding.syncPendingPluginsCard.isVisible = false
        }
    }
}
