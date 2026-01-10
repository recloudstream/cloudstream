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
            showToast("Sync started...")
            FirestoreSyncManager.pushAllLocalData(requireContext())
            // Brief delay to allow sync to happen then update UI
            view?.postDelayed({ updateStatusUI() }, 2000)
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
        }
    }

    private fun connect() {
        val config = FirestoreSyncManager.SyncConfig(
            apiKey = binding?.syncApiKey?.text?.toString() ?: "",
            projectId = binding?.syncProjectId?.text?.toString() ?: "",
            appId = binding?.syncAppId?.text?.toString() ?: ""
        )
        
        FirestoreSyncManager.initialize(requireContext(), config)
        showToast("Connecting to Firebase...")
        // Delay update to allow initialization to start
        view?.postDelayed({ updateStatusUI() }, 3000)
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
        } else {
            binding?.syncConnectBtn?.text = "Connect & Sync"
        }
    }
}
