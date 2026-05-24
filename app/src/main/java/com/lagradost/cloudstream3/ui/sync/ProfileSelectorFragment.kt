package com.lagradost.cloudstream3.ui.sync

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentProfileSelectorBinding
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.cloudstream3.syncproviders.SyncProfile
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import kotlinx.coroutines.launch
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.setKey
import com.lagradost.cloudstream3.ui.setup.HAS_DONE_SETUP_KEY

class ProfileSelectorFragment : Fragment() {
    companion object {
        const val TAG = "ProfileSelectorFragment"
        var hasFirebaseLoggedIn = false
    }

    private var _binding: FragmentProfileSelectorBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ProfileAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileSelectorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        loadProfiles()
    }

    private fun setupRecyclerView() {
        adapter = ProfileAdapter { profile, isEditMode ->
            handleProfileClick(profile, isEditMode)
        }

        val screenWidthDp = context?.resources?.configuration?.screenWidthDp ?: 320
        val spanCount = if (screenWidthDp >= 600) 4 else 2

        binding.profileSelectorGrid.apply {
            layoutManager = GridLayoutManager(context, spanCount)
            adapter = this@ProfileSelectorFragment.adapter
        }
    }

    private fun setupListeners() {
        binding.profileSelectorManageBtn.setOnClickListener {
            val nextEditMode = !adapter.isEditMode
            adapter.isEditMode = nextEditMode
            
            if (nextEditMode) {
                binding.profileSelectorManageBtn.text = "Done"
                binding.profileSelectorTitle.text = "Manage Profiles"
            } else {
                binding.profileSelectorManageBtn.text = "Manage"
                binding.profileSelectorTitle.text = "Who's Watching?"
            }
        }

        binding.profileSelectorBackBtn.setOnClickListener {
            activity?.onBackPressed()
        }

        binding.profileSelectorInfo.setOnClickListener {
            // Skip sync
            val ctx = requireContext()
            if (ctx.getKey<Boolean>(HAS_DONE_SETUP_KEY, false) != true) {
                ctx.setKey(HAS_DONE_SETUP_KEY, true)
                activity?.navigate(R.id.action_navigation_profile_selector_to_navigation_home)
            } else {
                activity?.onBackPressed()
            }
        }
    }

    private fun loadProfiles() {
        _binding?.profileSelectorLoading?.isVisible = true
        lifecycleScope.launch {
            try {
                val profiles = AccountManager.firebaseApi.getProfiles()
                val mutableProfiles = profiles.toMutableList()
                
                if (mutableProfiles.size < 5) {
                    mutableProfiles.add(
                        SyncProfile(
                            id = ProfileAdapter.ADD_PROFILE_ID,
                            name = "Add Profile"
                        )
                    )
                }
                
                adapter.submitList(mutableProfiles)
            } catch (e: Exception) {
                logError(e)
                Toast.makeText(context, "Failed to load profiles", Toast.LENGTH_SHORT).show()
            } finally {
                _binding?.profileSelectorLoading?.isVisible = false
            }
        }
    }

    private fun handleProfileClick(profile: SyncProfile, isEditMode: Boolean) {
        if (profile.id == ProfileAdapter.ADD_PROFILE_ID) {
            val dialog = ProfileEditorDialog.newInstance(null)
            dialog.onProfileSaved = { loadProfiles() }
            dialog.show(childFragmentManager, ProfileEditorDialog.TAG)
        } else if (isEditMode) {
            val dialog = ProfileEditorDialog.newInstance(profile)
            dialog.onProfileSaved = { loadProfiles() }
            dialog.show(childFragmentManager, ProfileEditorDialog.TAG)
        } else {
            // Regular selection mode
            if (profile.isLocked) {
                val dialog = PinEntryDialog.newInstance(profile)
                dialog.onPinVerified = {
                    selectProfileAndExit(profile)
                }
                dialog.show(childFragmentManager, PinEntryDialog.TAG)
            } else {
                selectProfileAndExit(profile)
            }
        }
    }

    private fun selectProfileAndExit(profile: SyncProfile) {
        val ctx = context ?: return
        lifecycleScope.launch {
            _binding?.profileSelectorLoading?.isVisible = true
            try {
                profile.lastUsed = System.currentTimeMillis()
                AccountManager.firebaseApi.saveProfile(profile)
                AccountManager.firebaseApi.selectProfile(profile)
                
                // Trigger real-time sync with local stores
                val success = AccountManager.firebaseApi.syncLocalToFirestore(ctx)
                if (success) {
                    Toast.makeText(
                        ctx, 
                        "Switched to ${profile.name} and synced!", 
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        ctx, 
                        "Switched to ${profile.name}", 
                        Toast.LENGTH_SHORT
                    ).show()
                }
                
                
                hasFirebaseLoggedIn = true

                if (ctx.getKey<Boolean>(HAS_DONE_SETUP_KEY, false) != true) {
                    ctx.setKey(HAS_DONE_SETUP_KEY, true)
                    activity?.navigate(R.id.action_navigation_profile_selector_to_navigation_home)
                } else {
                    activity?.onBackPressed()
                }
            } catch (e: Exception) {
                logError(e)
            } finally {
                _binding?.profileSelectorLoading?.isVisible = false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
