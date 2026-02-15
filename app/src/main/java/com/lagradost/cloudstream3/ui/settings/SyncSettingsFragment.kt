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
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.txt
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.fragment.app.FragmentActivity
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.cloudstream3.databinding.ItemSyncPluginBinding
import com.lagradost.cloudstream3.plugins.PluginData
import com.lagradost.cloudstream3.ui.settings.SettingsFragment
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.MainActivity.Companion.syncUpdatedEvent

class SyncSettingsFragment : BaseFragment<FragmentSyncSettingsBinding>(
    BaseFragment.BindingCreator.Inflate(FragmentSyncSettingsBinding::inflate)
) {
    data class SyncToggle(
        val key: String,
        val title: String,
        val desc: String
    )

    override fun fixLayout(view: View) {
        // Fix for TV: ensure items are focusable
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
            val ctx = context ?: return@setOnClickListener
            showToast(txt(R.string.sync_syncing))
            FirestoreSyncManager.syncNow(ctx)
            view?.postDelayed({ updateUI() }, 1000)
        }

        /*binding.syncCopyLogsBtn.setOnClickListener {
            val logs = FirestoreSyncManager.getLogs()
            if (logs.isBlank()) {
                showToast(txt(R.string.sync_no_logs))
            } else {
                clipboardHelper(txt(R.string.sync_logs_label), logs)
                showToast(txt(R.string.sync_logs_copied))
            }
        }*/
        
        // Toggle Database Config Visibility
        binding.syncConfigHeader.setOnClickListener {
             val isVisible = binding.syncConfigContainer.isVisible
             binding.syncConfigContainer.isVisible = !isVisible
             // Rotation animation
             val arrow = binding.syncConfigHeader.getChildAt(1)
             arrow.animate().rotation(if (isVisible) 0f else 180f).setDuration(200).start()
        }
        
        // Auto-expand if not enabled/connected
        val ctx = context ?: return
        val isEnabled = FirestoreSyncManager.isEnabled(ctx)
        binding.syncConfigContainer.isVisible = !isEnabled
        if (!isEnabled) {
             binding.syncConfigHeader.getChildAt(1).rotation = 180f
        }
        
        updateUI()

        // Refresh UI when sync data changes (like new plugins detected)
        MainActivity.syncUpdatedEvent += { success: Boolean ->
             if (success) main { updateUI() }
        }
    }

    private fun setupDatabaseConfigInputs(binding: FragmentSyncSettingsBinding) {
        val context = context ?: return
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
        // Direct Account Management for Firebase
        binding.syncAccountActionBtn.setOnClickListener {
             val activity = activity as? FragmentActivity ?: return@setOnClickListener
             val api = AccountManager.FirebaseRepo(AccountManager.firebaseApi)
             val info = api.authUser()
             val index = api.accounts.indexOfFirst { account -> account.user.id == info?.id }
             
             if (api.accounts.isNotEmpty()) {
                 if (api.accounts.size > 1) {
                     SettingsAccount.showAccountSwitch(activity, api)
                 } else {
                     SettingsAccount.showLoginInfo(activity, api, info, index)
                 }
             } else {
                 SettingsAccount.addAccount(activity, api)
             }
        }
    }

    private fun setupPluginActions(binding: FragmentSyncSettingsBinding) {
        binding.syncInstallPluginsBtn.setOnClickListener {
            showToast(txt(R.string.sync_installing_plugins))
             ioSafe {
                 val act = activity ?: return@ioSafe
                 FirestoreSyncManager.installAllPending(act)
                 main { updateUI() }
             }
        }
        
        binding.syncIgnorePluginsBtn.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            // Updated to use the new robust ignore logic
            FirestoreSyncManager.ignoreAllPendingPlugins(ctx)
            updateUI()
            showToast(txt(R.string.sync_pending_cleared))
        }
    }

    private fun setupGranularToggles(binding: FragmentSyncSettingsBinding) {
        binding.apply {
            setupToggleCategory(syncAppearanceRecycler, listOf(
                SyncToggle(FirestoreSyncManager.SYNC_SETTING_APPEARANCE, getString(R.string.sync_appearance_title), getString(R.string.sync_appearance_desc))
            ))
            setupToggleCategory(syncPlayerRecycler, listOf(
                SyncToggle(FirestoreSyncManager.SYNC_SETTING_PLAYER, getString(R.string.sync_player_title), getString(R.string.sync_player_desc))
            ))
            setupToggleCategory(syncDownloadsRecycler, listOf(
                SyncToggle(FirestoreSyncManager.SYNC_SETTING_DOWNLOADS, getString(R.string.sync_downloads_title), getString(R.string.sync_downloads_desc))
            ))
            setupToggleCategory(syncGeneralRecycler, listOf(
                SyncToggle(FirestoreSyncManager.SYNC_SETTING_GENERAL, getString(R.string.sync_general_title), getString(R.string.sync_general_desc))
            ))
            
            setupToggleCategory(syncAccountsRecycler, listOf(
                SyncToggle(FirestoreSyncManager.SYNC_SETTING_ACCOUNTS, getString(R.string.sync_user_profiles_title), getString(R.string.sync_user_profiles_desc))
            ))
            setupToggleCategory(syncBookmarksRecycler, listOf(
                SyncToggle(FirestoreSyncManager.SYNC_SETTING_BOOKMARKS, getString(R.string.sync_bookmarks_title), getString(R.string.sync_bookmarks_desc))
            ))
            setupToggleCategory(syncResumeWatchingRecycler, listOf(
                SyncToggle(FirestoreSyncManager.SYNC_SETTING_RESUME_WATCHING, getString(R.string.sync_watch_progress_title), getString(R.string.sync_watch_progress_desc))
            ))
            
            setupToggleCategory(syncRepositoriesRecycler, listOf(
                SyncToggle(FirestoreSyncManager.SYNC_SETTING_REPOSITORIES, getString(R.string.sync_repositories_title), getString(R.string.sync_repositories_desc))
            ))
            setupToggleCategory(syncPluginsRecycler, listOf(
                SyncToggle(FirestoreSyncManager.SYNC_SETTING_PLUGINS, getString(R.string.sync_plugins_title), getString(R.string.sync_plugins_desc))
            ))
            
            setupToggleCategory(syncHomepageRecycler, listOf(
                SyncToggle(FirestoreSyncManager.SYNC_SETTING_HOMEPAGE_API, getString(R.string.sync_home_provider_title), getString(R.string.sync_home_provider_desc))
            ))
            setupToggleCategory(syncPinnedRecycler, listOf(
                SyncToggle(FirestoreSyncManager.SYNC_SETTING_PINNED_PROVIDERS, getString(R.string.sync_pinned_providers_title), getString(R.string.sync_pinned_providers_desc))
            ))
        }
    }

    private fun setupToggleCategory(recycler: RecyclerView, toggles: List<SyncToggle>) {
        recycler.adapter = ToggleAdapter(toggles)
        recycler.isNestedScrollingEnabled = false
    }



    private fun connect(binding: FragmentSyncSettingsBinding) {
        val config = FirestoreSyncManager.SyncConfig(
            apiKey = binding.syncApiKey.text?.toString() ?: "",
            projectId = binding.syncProjectId.text?.toString() ?: "",
            appId = binding.syncAppId.text?.toString() ?: ""
        )
        
        val ctx = context ?: return
        FirestoreSyncManager.initialize(ctx, config)
        showToast(txt(R.string.sync_connecting))
        view?.postDelayed({ updateUI() }, 1500)
    }

    private fun updateUI() {
        val binding = binding ?: return
        val context = context ?: return
        
        val enabled = FirestoreSyncManager.isEnabled(context)
        val isOnline = FirestoreSyncManager.isOnline()
        val isLogged = FirestoreSyncManager.isLogged()
        
        updateConnectionStatus(binding, context, enabled, isOnline, isLogged)
        updateAuthState(binding, context, isLogged)
        updatePendingPlugins(binding, context, enabled)
    }

    private fun updateConnectionStatus(
        binding: FragmentSyncSettingsBinding,
        context: android.content.Context,
        enabled: Boolean,
        isOnline: Boolean,
        isLogged: Boolean
    ) {
        binding.syncStatusCard.isVisible = enabled
        binding.syncAccountCard.isVisible = enabled
        
        if (!enabled) {
            binding.syncConnectBtn.text = getString(R.string.sync_connect_database)
            return
        }

        if (isLogged) {
            binding.syncStatusText.text = getString(R.string.sync_status_connected)
            binding.syncStatusText.setTextColor(context.getColor(R.color.sync_status_connected))
        } else if (isOnline) {
            binding.syncStatusText.text = getString(R.string.sync_status_login_needed)
            binding.syncStatusText.setTextColor(context.getColor(R.color.sync_status_login_needed))
        } else {
            val error = FirestoreSyncManager.lastInitError
            binding.syncStatusText.text = if (error != null) getString(R.string.sync_status_error_prefix, error) else getString(R.string.sync_status_disconnected)
            binding.syncStatusText.setTextColor(context.getColor(R.color.sync_status_error))
        }

        val lastSync = FirestoreSyncManager.getLastSyncTime(context)
        binding.syncLastTime.text = lastSync?.let { it: Long ->
            SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()).format(Date(it))
        } ?: getString(R.string.sync_never)
    }

    private fun updateAuthState(
        binding: FragmentSyncSettingsBinding,
        context: android.content.Context,
        isLogged: Boolean
    ) {
        if (isLogged) {
            val email = FirestoreSyncManager.getUserEmail() ?: getString(R.string.sync_unknown_user_label)
            binding.syncAccountStatus.text = getString(R.string.sync_signed_in_as, email)
            binding.syncAccountStatus.setTextColor(context.getColor(R.color.sync_status_connected))
            binding.syncAccountActionBtn.text = getString(R.string.sync_manage_accounts)
        } else {
            binding.syncAccountStatus.text = getString(R.string.sync_not_logged_in)
            binding.syncAccountStatus.setTextColor(context.getColor(R.color.sync_status_error))
            binding.syncAccountActionBtn.text = getString(R.string.sync_login_via_accounts)
        }

        val contentVisible = isLogged
        binding.syncAppSettingsCard.isVisible = contentVisible
        binding.syncLibraryCard.isVisible = contentVisible
        binding.syncExtensionsCard.isVisible = contentVisible
        binding.syncInterfaceCard.isVisible = contentVisible
    }

    private fun updatePendingPlugins(
        binding: FragmentSyncSettingsBinding,
        context: android.content.Context,
        enabled: Boolean
    ) {
        val pendingPlugins = FirestoreSyncManager.getPendingPlugins(context)

        if (pendingPlugins.isNotEmpty() && enabled) {
            binding.syncPendingPluginsCard.isVisible = true
            binding.syncPendingTitle.text = getString(R.string.sync_new_plugins_detected, pendingPlugins.size)

            val adapter = PluginAdapter(pendingPlugins) { plugin: PluginData, action: String ->
                when (action) {
                    "INSTALL" -> {
                        ioSafe {
                            val activity = activity ?: return@ioSafe
                            val success = FirestoreSyncManager.installPendingPlugin(activity, plugin)
                            main { if (success) updateUI() }
                        }
                    }
                    "IGNORE" -> {
                        FirestoreSyncManager.ignorePendingPlugin(context, plugin)
                        updateUI()
                    }
                }
            }
            binding.syncPendingPluginsRecycler.adapter = adapter
            binding.syncPendingPluginsRecycler.isVisible = true
        } else {
            binding.syncPendingPluginsCard.isVisible = false
        }
    }

    inner class PluginAdapter(
        private val items: List<PluginData>,
        private val callback: (PluginData, String) -> Unit
    ) : RecyclerView.Adapter<PluginAdapter.PluginViewHolder>() {

        inner class PluginViewHolder(val binding: ItemSyncPluginBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PluginViewHolder {
            val binding = ItemSyncPluginBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return PluginViewHolder(binding)
        }

        override fun onBindViewHolder(holder: PluginViewHolder, position: Int) {
            val item = items[position]
            holder.binding.itemPluginName.text = item.internalName
            holder.binding.itemPluginInstall.setOnClickListener { callback(item, "INSTALL") }
            holder.binding.itemPluginIgnore.setOnClickListener { callback(item, "IGNORE") }
            
            // TV Navigation support: Don't let root intercept focus from buttons
            holder.binding.root.isFocusable = false
            holder.binding.root.isClickable = false
        }

        override fun getItemCount() = items.size
    }

    inner class ToggleAdapter(
        private val items: List<SyncToggle>
    ) : RecyclerView.Adapter<ToggleAdapter.ToggleViewHolder>() {

        inner class ToggleViewHolder(val binding: com.lagradost.cloudstream3.databinding.SyncItemRowBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ToggleViewHolder {
            val binding = com.lagradost.cloudstream3.databinding.SyncItemRowBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ToggleViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ToggleViewHolder, position: Int) {
            val item = items[position]
            holder.binding.syncItemTitle.text = item.title
            holder.binding.syncItemDesc.text = item.desc
            
            val ctx = holder.binding.root.context
            val current = ctx.getKey(item.key, true) ?: true
            
            holder.binding.syncItemSwitch.setOnCheckedChangeListener(null)
            holder.binding.syncItemSwitch.isChecked = current
            holder.binding.syncItemSwitch.setOnCheckedChangeListener { _, isChecked ->
                ctx.setKey(item.key, isChecked)
            }
            
            // TV Navigation support
            holder.binding.root.isFocusable = true
            holder.binding.root.isClickable = true
            holder.binding.root.setOnClickListener {
                holder.binding.syncItemSwitch.toggle()
            }
        }

        override fun getItemCount() = items.size
    }
}
