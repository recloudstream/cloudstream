package com.lagradost.cloudstream3.ui.settings.extensions

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.format.Formatter.formatFileSize
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.AcraApplication.Companion.openBrowser
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentPluginDetailsBinding
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.plugins.VotingApi.canVote
import com.lagradost.cloudstream3.plugins.VotingApi.getVotes
import com.lagradost.cloudstream3.plugins.VotingApi.hasVoted
import com.lagradost.cloudstream3.plugins.VotingApi.vote
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.SubtitleHelper.fromTwoLettersToLanguage
import com.lagradost.cloudstream3.utils.SubtitleHelper.getFlagFromIso
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.utils.UIHelper.setImage
import com.lagradost.cloudstream3.utils.UIHelper.toPx


class PluginDetailsFragment(val data: PluginViewData) : BottomSheetDialogFragment() {

    companion object {
        private tailrec fun findClosestBase2(target: Int, current: Int = 16, max: Int = 512): Int {
            if (current >= max) return max
            if (current >= target) return current
            return findClosestBase2(target, current * 2, max)
        }

        private val iconSizeExact = 50.toPx
        private val iconSize by lazy {
            findClosestBase2(iconSizeExact, 16, 512)
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    var binding: FragmentPluginDetailsBinding? = null
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val localBinding = FragmentPluginDetailsBinding.inflate(inflater, container, false)
        binding = localBinding
        return localBinding.root
        //return inflater.inflate(R.layout.fragment_plugin_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val metadata = data.plugin.second
        binding?.apply {
            if (!pluginIcon.setImage(//plugin_icon?.height ?:
                    metadata.iconUrl?.replace(
                        "%size%",
                        "$iconSize"
                    )?.replace(
                        "%exact_size%",
                        "$iconSizeExact"
                    ),
                    null,
                    errorImageDrawable = R.drawable.ic_baseline_extension_24
                )
            ) {
                pluginIcon.setImageResource(R.drawable.ic_baseline_extension_24)
            }
            pluginName.text = metadata.name.removeSuffix("Provider")
            pluginVersion.text = metadata.version.toString()
            pluginDescription.text = metadata.description ?: getString(R.string.no_data)
            pluginSize.text =
                if (metadata.fileSize == null) getString(R.string.no_data) else formatFileSize(
                    context,
                    metadata.fileSize
                )
            pluginAuthor.text =
                if (metadata.authors.isEmpty()) getString(R.string.no_data) else metadata.authors.joinToString(
                    ", "
                )
            pluginStatus.text =
                resources.getStringArray(R.array.extension_statuses)[metadata.status]
            pluginTypes.text =
                if (metadata.tvTypes.isNullOrEmpty()) getString(R.string.no_data) else metadata.tvTypes.joinToString(
                    ", "
                )
            pluginLang.text = if (metadata.language == null)
                getString(R.string.no_data)
            else
                "${getFlagFromIso(metadata.language)} ${fromTwoLettersToLanguage(metadata.language)}"

            githubBtn.setOnClickListener {
                if (metadata.repositoryUrl != null) {
                    openBrowser(metadata.repositoryUrl)
                }
            }

            if (!metadata.canVote()) {
                upvote.alpha = .6f
            }

            if (data.isDownloaded) {
                // On local plugins page the filepath is provided instead of url.
                val plugin =
                    PluginManager.urlPlugins[metadata.url] ?: PluginManager.plugins[metadata.url]
                if (plugin?.openSettings != null && context != null) {
                    actionSettings.isVisible = true
                    actionSettings.setOnClickListener {
                        try {
                            plugin.openSettings!!.invoke(requireContext())
                        } catch (e: Throwable) {
                            Log.e(
                                "PluginAdapter",
                                "Failed to open ${metadata.name} settings: ${
                                    Log.getStackTraceString(e)
                                }"
                            )
                        }
                    }
                } else {
                    actionSettings.isVisible = false
                }
            } else {
                actionSettings.isVisible = false
            }

            upvote.setOnClickListener {
                ioSafe {
                    metadata.vote().main {
                        updateVoting(it)
                    }
                }
            }

            ioSafe {
                metadata.getVotes().main {
                    updateVoting(it)
                }
            }
        }
    }

    private fun updateVoting(value: Int) {
        val metadata = data.plugin.second
        binding?.apply {
            pluginVotes.text = value.toString()
            if (metadata.hasVoted()) {
                upvote.imageTintList = ColorStateList.valueOf(
                    context?.colorFromAttribute(R.attr.colorPrimary) ?: R.color.colorPrimary
                )
            } else {
                upvote.imageTintList = ColorStateList.valueOf(
                    context?.colorFromAttribute(R.attr.colorOnSurface) ?: R.color.white
                )
            }
        }
    }
}