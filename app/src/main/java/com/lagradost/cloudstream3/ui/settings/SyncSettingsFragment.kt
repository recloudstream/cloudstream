package com.lagradost.cloudstream3.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.syncproviders.google.SyncManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SyncSettingsFragment : PreferenceFragmentCompat() {

    private val credentialManager by lazy { CredentialManager.create(requireContext()) }
    
    private val syncResolutionLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val resultCode = result.resultCode
        val resultData = result.data
        if (resultCode == android.app.Activity.RESULT_OK) {
            Toast.makeText(context, R.string.sync_auth_success, Toast.LENGTH_SHORT).show()
            if (resultData != null) {
                try {
                    val authResult = com.google.android.gms.auth.api.identity.Identity.getAuthorizationClient(requireContext())
                        .getAuthorizationResultFromIntent(resultData)
                    val email = authResult.toGoogleSignInAccount()?.email ?: "Connected Account"
                    SyncManager.onSignInSuccess(requireContext(), email)
                    updateUiState()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "cs3_sync_prefs"
        setPreferencesFromResource(R.xml.settings_sync, rootKey)

        findPreference<Preference>("sync_google_drive_connect")?.setOnPreferenceClickListener {
            val email = SyncManager.getConnectedEmail(requireContext())
            if (email == null) {
                launchSignIn()
            } else {
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle(R.string.sync_disconnect_confirm_title)
                    .setMessage(R.string.sync_disconnect_confirm_message)
                    .setPositiveButton(R.string.sync_disconnect_confirm_title) { _, _ ->
                        SyncManager.signOut(requireContext())
                        updateUiState()
                        Toast.makeText(context, R.string.sync_disconnected_toast, Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            true
        }

        findPreference<Preference>("sync_push_now")?.setOnPreferenceClickListener {
            SyncManager.push(requireContext())
            Toast.makeText(context, R.string.sync_push_started, Toast.LENGTH_SHORT).show()
            true
        }

        findPreference<Preference>("sync_pull_now")?.setOnPreferenceClickListener {
            SyncManager.pull(requireContext(), force = true)
            Toast.makeText(context, R.string.sync_pull_started, Toast.LENGTH_SHORT).show()
            true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateUiState()

        lifecycleScope.launch {
            SyncManager.syncEvents.collectLatest { result ->
                if (result is SyncManager.SyncResult.NeedsAuth) {
                    try {
                        syncResolutionLauncher.launch(
                            androidx.activity.result.IntentSenderRequest.Builder(result.pendingIntent.intentSender).build()
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    return@collectLatest
                }

                updateUiState()
                val msgText = when (result) {
                    is SyncManager.SyncResult.Push -> if (result.isSuccess) getString(R.string.sync_push_success) else getString(R.string.sync_push_failed) + (result.error?.let { " - $it" } ?: "")
                    is SyncManager.SyncResult.Pull -> if (result.isSuccess) getString(R.string.sync_pull_success) else getString(R.string.sync_pull_failed) + (result.error?.let { " - $it" } ?: "")
                    else -> getString(R.string.sync_pull_failed)
                }
                Toast.makeText(context, msgText, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun launchSignIn() {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(SyncManager.buildGoogleIdOption())
            .build()

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(requireContext(), request)
                val googleIdCred = GoogleIdTokenCredential.createFrom(result.credential.data)
                SyncManager.onSignInSuccess(requireContext(), googleIdCred.id)
                updateUiState()
                Toast.makeText(context, R.string.sync_connected_toast, Toast.LENGTH_SHORT).show()
            } catch (e: GetCredentialException) {
                android.util.Log.d("SyncSettings", "CredentialManager failed: ${e.message}. Trying AuthorizationClient fallback...")
                try {
                    val authRequest = com.google.android.gms.auth.api.identity.AuthorizationRequest.builder()
                        .setRequestedScopes(listOf(
                            com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/drive.appdata"),
                            com.google.android.gms.common.api.Scope("email")
                        ))
                        .build()
                    val authResult = com.google.android.gms.auth.api.identity.Identity.getAuthorizationClient(requireContext())
                        .authorize(authRequest)
                        .await()
                    
                    if (authResult.accessToken != null) {
                        val email = authResult.toGoogleSignInAccount()?.email ?: "Connected Account"
                        SyncManager.onSignInSuccess(requireContext(), email)
                        updateUiState()
                        Toast.makeText(context, R.string.sync_connected_toast, Toast.LENGTH_SHORT).show()
                    } else if (authResult.hasResolution() && authResult.pendingIntent != null) {
                        syncResolutionLauncher.launch(
                            androidx.activity.result.IntentSenderRequest.Builder(authResult.pendingIntent!!.intentSender).build()
                        )
                    } else {
                        Toast.makeText(context, e.message ?: e.type, Toast.LENGTH_LONG).show()
                    }
                } catch (fallbackEx: Exception) {
                    fallbackEx.printStackTrace()
                }
            }
        }
    }

    private fun updateUiState() {
        val ctx = context ?: return
        val email = SyncManager.getConnectedEmail(ctx)
        val isConnected = email != null
        val lastSync = SyncManager.getLastSyncTime(ctx)

        findPreference<Preference>("sync_google_drive_connect")?.apply {
            title = if (isConnected) {
                getString(R.string.sync_disconnect_title, email)
            } else {
                getString(R.string.sync_connect_title)
            }
            summary = if (isConnected) {
                getString(R.string.sync_connected_summary, email)
            } else {
                getString(R.string.sync_connect_summary)
            }
            if (isConnected) {
                icon = androidx.core.content.ContextCompat.getDrawable(ctx, R.drawable.ic_google_drive_connected)
            } else {
                icon = androidx.core.content.ContextCompat.getDrawable(ctx, R.drawable.ic_google_drive)
            }
        }

        findPreference<Preference>("sync_status")?.summary = when {
            !isConnected -> getString(R.string.sync_status_not_connected)
            lastSync == 0L -> getString(R.string.sync_status_never)
            else -> {
                val formatted = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(lastSync))
                getString(R.string.sync_status_last, formatted)
            }
        }

        val cloudPref = findPreference<Preference>("sync_cloud_status")
        cloudPref?.isVisible = isConnected
        if (isConnected) {
            cloudPref?.summary = getString(R.string.sync_status_cloud_fetching)
            lifecycleScope.launch {
                val meta = SyncManager.getCloudMetadata(ctx)
                val status = if (meta != null) {
                    val formatted = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(meta.updatedAt))
                    getString(R.string.sync_status_cloud, formatted)
                } else {
                    getString(R.string.sync_status_cloud_none)
                }
                cloudPref?.summary = status
            }
        }

        findPreference<Preference>("sync_push_now")?.isEnabled = isConnected
        findPreference<Preference>("sync_pull_now")?.isEnabled = isConnected
        findPreference<SwitchPreferenceCompat>("sync_auto_enabled")?.isEnabled = isConnected

    }
}
