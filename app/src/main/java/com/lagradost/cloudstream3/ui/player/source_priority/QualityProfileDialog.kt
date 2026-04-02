package com.lagradost.cloudstream3.ui.player.source_priority

import android.app.Dialog
import androidx.annotation.StyleRes
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.PlayerQualityProfileDialogBinding
import com.lagradost.cloudstream3.ui.player.source_priority.QualityDataHelper.getAllSourcePriorityNames
import com.lagradost.cloudstream3.ui.player.source_priority.QualityDataHelper.getProfileName
import com.lagradost.cloudstream3.ui.player.source_priority.QualityDataHelper.getProfiles
import com.lagradost.cloudstream3.utils.Coroutines.ioWork
import com.lagradost.cloudstream3.utils.txt
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showMultiDialog
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding
import com.lagradost.cloudstream3.utils.setText

/** Simplified ExtractorLink for the quality profile dialog */
data class LinkSource(
    val source: String
) {
    constructor(extractorLink: ExtractorLink) : this(extractorLink.source)
}


class QualityProfileDialog private constructor(
    val activity: FragmentActivity,
    @StyleRes val themeRes: Int,
    private val links: List<LinkSource>,
    private val usedProfile: Int?,
    private val profileSelectionCallback: ((QualityDataHelper.QualityProfile) -> Unit)?,
    private val useProfileSelection: Boolean
) : Dialog(activity, themeRes) {
    constructor(
        activity: FragmentActivity,
        @StyleRes themeRes: Int,
        links: List<LinkSource>,
        usedProfile: Int,
        profileSelectionCallback: ((QualityDataHelper.QualityProfile) -> Unit),
    ) : this(activity, themeRes, links, usedProfile, profileSelectionCallback, true)

    constructor(
        activity: FragmentActivity,
        @StyleRes themeRes: Int,
        links: List<LinkSource>
    ) : this(activity, themeRes, links, null, null, false)

    companion object {
        // Run on IO as this may be a heavy operation
        suspend fun getAllDefaultSources(): List<LinkSource> = ioWork {
            getProfiles().flatMap {
                getAllSourcePriorityNames(it.id)
            }.distinct().map { LinkSource(it) }
        }
    }

    override fun show() {
        val binding = PlayerQualityProfileDialogBinding.inflate(this.layoutInflater, null, false)

        setContentView(binding.root)
        fixSystemBarsPadding(binding.root)
        binding.apply {
            fun getCurrentProfile(): QualityDataHelper.QualityProfile? {
                return (profilesRecyclerview.adapter as? ProfilesAdapter)?.getCurrentProfile()
            }

            fun refreshProfiles() {
                if (usedProfile != null) {
                    currentlySelectedProfileText.setText(getProfileName(usedProfile))
                }
                (profilesRecyclerview.adapter as? ProfilesAdapter)?.submitList(getProfiles())
            }

            profilesRecyclerview.adapter = ProfilesAdapter(
                usedProfile,
            ) { oldIndex: Int?, newIndex: Int ->
                profilesRecyclerview.adapter?.notifyItemChanged(newIndex)
                selectedItemHolder.alpha = 1f
                if (oldIndex != null) {
                    profilesRecyclerview.adapter?.notifyItemChanged(oldIndex)
                }
            }

            refreshProfiles()

            editBtt.setOnClickListener {
                getCurrentProfile()?.let { profile ->
                    SourcePriorityDialog(context, themeRes, links, profile) {
                        refreshProfiles()
                    }.show()
                }
            }


            setDefaultBtt.setOnClickListener {
                val currentProfile = getCurrentProfile() ?: return@setOnClickListener
                val choices =
                    QualityDataHelper.QualityProfileType.entries.filter { it != QualityDataHelper.QualityProfileType.None }
                val choiceNames = choices.map { txt(it.stringRes).asString(context) }
                val selectedIndices = choices.mapIndexed { index, type -> index to type }
                    .filter { currentProfile.types.contains(it.second) }.map { it.first }

                activity.showMultiDialog(
                    choiceNames,
                    selectedIndices,
                    txt(R.string.set_default).asString(context),
                    {},
                    { index ->
                        val pickedChoices = index.mapNotNull { choices.getOrNull(it) }

                        pickedChoices.forEach { pickedChoice ->
                            // Remove previous picks
                            if (pickedChoice.unique) {
                                getProfiles().filter { it.types.contains(pickedChoice) }.forEach {
                                    QualityDataHelper.removeQualityProfileType(it.id, pickedChoice)
                                }
                            }

                            QualityDataHelper.addQualityProfileType(currentProfile.id, pickedChoice)
                        }

                        refreshProfiles()
                    })
            }

            cancelBtt.isVisible = useProfileSelection
            useBtt.isVisible = useProfileSelection
            applyBtt.isVisible = !useProfileSelection

            if (useProfileSelection) {
                cancelBtt.setOnClickListener {
                    this@QualityProfileDialog.dismissSafe()
                }

                useBtt.setOnClickListener {
                    getCurrentProfile()?.let {
                        profileSelectionCallback?.invoke(it)
                        this@QualityProfileDialog.dismissSafe()
                    }
                }
            } else {
                applyBtt.setOnClickListener {
                    this@QualityProfileDialog.dismissSafe()
                }
            }
        }
        super.show()
    }
}