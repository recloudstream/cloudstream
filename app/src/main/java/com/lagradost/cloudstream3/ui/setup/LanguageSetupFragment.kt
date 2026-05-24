package com.lagradost.cloudstream3.ui.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentLanguageSetupBinding
import com.lagradost.cloudstream3.databinding.ItemLanguageElegantBinding
import com.lagradost.cloudstream3.ui.settings.appLanguages
import com.lagradost.cloudstream3.ui.settings.getCurrentLocale
import com.lagradost.cloudstream3.ui.settings.nameNextToFlagEmoji
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.cloudstream3.utils.DataStore.setKey

const val HAS_DONE_SETUP_KEY = "HAS_DONE_SETUP"

class LanguageSetupFragment : Fragment() {

    private var _binding: FragmentLanguageSetupBinding? = null
    private val binding get() = _binding!!
    private var selectedLangIndex = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLanguageSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ctx = context ?: return
        val current = getCurrentLocale(ctx)
        val languageTagsIETF = appLanguages.map { it.second }
        val languageNames = appLanguages.map { it.nameNextToFlagEmoji() }
        selectedLangIndex = languageTagsIETF.indexOf(current).takeIf { it >= 0 } ?: 0

        val adapter = LanguageAdapter(languageNames) { index ->
            selectedLangIndex = index
        }
        binding.languageRecycler.adapter = adapter

        binding.languageSetupNextButton.setOnClickListener {
            val langTagIETF = languageTagsIETF[selectedLangIndex]
            CommonActivity.setLocale(activity, langTagIETF)
            PreferenceManager.getDefaultSharedPreferences(ctx).edit {
                putString(getString(R.string.locale_key), langTagIETF)
            }
            
            // Navigate based on sign in status
            if (AccountManager.firebaseApi.auth.currentUser != null) {
                // Do not mark setup as done yet, wait until they create a profile
                findNavController().navigate(R.id.action_navigation_setup_language_to_navigation_profile_selector)
            } else {
                // Skipped sync, so onboarding is completely finished
                ctx.setKey(HAS_DONE_SETUP_KEY, true)
                findNavController().navigate(R.id.action_navigation_setup_language_to_navigation_home)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class LanguageAdapter(
        private val languages: List<String>,
        private val onLanguageSelected: (Int) -> Unit
    ) : RecyclerView.Adapter<LanguageAdapter.LanguageViewHolder>() {

        inner class LanguageViewHolder(val binding: ItemLanguageElegantBinding) : RecyclerView.ViewHolder(binding.root) {
            init {
                binding.root.setOnClickListener {
                    val prevIndex = selectedLangIndex
                    onLanguageSelected(bindingAdapterPosition)
                    notifyItemChanged(prevIndex)
                    notifyItemChanged(bindingAdapterPosition)
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LanguageViewHolder {
            return LanguageViewHolder(
                ItemLanguageElegantBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        }

        override fun onBindViewHolder(holder: LanguageViewHolder, position: Int) {
            holder.binding.languageName.text = languages[position]
            holder.binding.languageCheck.isVisible = (position == selectedLangIndex)
        }

        override fun getItemCount() = languages.size
    }
}
