package com.lagradost.cloudstream3.ui.settings.extensions

import android.content.res.ColorStateList
import android.os.Bundle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.UIHelper.setImage
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import kotlinx.android.synthetic.main.fragment_plugin_details.*
import android.text.format.Formatter.formatFileSize
import android.util.Log
import androidx.core.view.isVisible
import com.lagradost.cloudstream3.plugins.VotingApi
import com.lagradost.cloudstream3.plugins.VotingApi.getVoteType
import com.lagradost.cloudstream3.plugins.VotingApi.getVotes
import com.lagradost.cloudstream3.plugins.VotingApi.vote
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.AcraApplication.Companion.openBrowser
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.plugins.VotingApi.canVote
import com.lagradost.cloudstream3.utils.SubtitleHelper.fromTwoLettersToLanguage
import com.lagradost.cloudstream3.utils.SubtitleHelper.getFlagFromIso
import kotlinx.android.synthetic.main.repository_item.view.*


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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_plugin_details, container, false)

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val metadata = data.plugin.second
        if (plugin_icon?.setImage(//plugin_icon?.height ?:
                metadata.iconUrl?.replace(
                    "%size%",
                    "$iconSize"
                )?.replace(
                    "%exact_size%",
                    "$iconSizeExact"
                ),
                null,
                errorImageDrawable = R.drawable.ic_baseline_extension_24
            ) != true
        ) {
            plugin_icon?.setImageResource(R.drawable.ic_baseline_extension_24)
        }
        plugin_name?.text = metadata.name.removeSuffix("Provider")
        plugin_version?.text = metadata.version.toString()
        plugin_description?.text = metadata.description ?: getString(R.string.no_data)
        plugin_size?.text = if (metadata.fileSize == null) getString(R.string.no_data) else formatFileSize(context, metadata.fileSize)
        plugin_author?.text = if (metadata.authors.isEmpty()) getString(R.string.no_data) else metadata.authors.joinToString(", ")
        plugin_status?.text = resources.getStringArray(R.array.extension_statuses)[metadata.status]
        plugin_types?.text = if ((metadata.tvTypes == null) || metadata.tvTypes.isEmpty()) getString(R.string.no_data) else metadata.tvTypes.joinToString(", ")
        plugin_lang?.text = if (metadata.language == null)
                getString(R.string.no_data)
        else
                "${getFlagFromIso(metadata.language)} ${fromTwoLettersToLanguage(metadata.language)}"

        github_btn.setOnClickListener {
            if (metadata.repositoryUrl != null) {
                openBrowser(metadata.repositoryUrl)
            }
        }

        if (!metadata.canVote()) {
            downvote.alpha = .6f
            upvote.alpha = .6f
        }

        if (data.isDownloaded) {
            // On local plugins page the filepath is provided instead of url.
            val plugin = PluginManager.urlPlugins[metadata.url] ?: PluginManager.plugins[metadata.url]
            if (plugin?.openSettings != null && context != null) {
                action_settings?.isVisible = true
                action_settings.setOnClickListener {
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
                action_settings?.isVisible = false
            }
        } else {
            action_settings?.isVisible = false
        }

        upvote.setOnClickListener {
            ioSafe {
                metadata.vote(VotingApi.VoteType.UPVOTE).main {
                    updateVoting(it)
                }
            }
        }
        downvote.setOnClickListener {
            ioSafe {
                metadata.vote(VotingApi.VoteType.DOWNVOTE).main {
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

    private fun updateVoting(value: Int) {
        val metadata = data.plugin.second
        plugin_votes.text = value.toString()
        when (metadata.getVoteType()) {
            VotingApi.VoteType.UPVOTE -> {
                upvote.imageTintList =  ColorStateList.valueOf(context?.colorFromAttribute(R.attr.colorPrimary) ?: R.color.colorPrimary)
                downvote.imageTintList = ColorStateList.valueOf(context?.colorFromAttribute(R.attr.white) ?: R.color.white)
            }
            VotingApi.VoteType.DOWNVOTE -> {
                downvote.imageTintList =  ColorStateList.valueOf(context?.colorFromAttribute(R.attr.colorPrimary) ?: R.color.colorPrimary)
                upvote.imageTintList = ColorStateList.valueOf(context?.colorFromAttribute(R.attr.white) ?: R.color.white)
            }
            VotingApi.VoteType.NONE -> {
                upvote.imageTintList =  ColorStateList.valueOf(context?.colorFromAttribute(R.attr.white) ?: R.color.white)
                downvote.imageTintList = ColorStateList.valueOf(context?.colorFromAttribute(R.attr.white) ?: R.color.white)
            }
        }
    }
}