package com.lagradost.cloudstream3.ui.player.source_priority

import android.app.Dialog
import androidx.annotation.StyleRes
import androidx.fragment.app.FragmentActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.PlayerQualityProfileDialogBinding
import com.lagradost.cloudstream3.ui.player.source_priority.QualityDataHelper.getProfileName
import com.lagradost.cloudstream3.ui.player.source_priority.QualityDataHelper.getProfiles
import com.lagradost.cloudstream3.ui.result.txt
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe

class QualityProfileDialog(
    val activity: FragmentActivity,
    @StyleRes val themeRes: Int,
    private val links: List<ExtractorLink>,
    private val usedProfile: Int,
    private val profileSelectionCallback: (QualityDataHelper.QualityProfile) -> Unit
) : Dialog(activity, themeRes) {
    override fun show() {

        val binding = PlayerQualityProfileDialogBinding.inflate(this.layoutInflater, null, false)

        setContentView(binding.root)//R.layout.player_quality_profile_dialog)
        /*val profilesRecyclerView: RecyclerView = profiles_recyclerview
        val useBtt: View = use_btt
        val editBtt: View = edit_btt
        val cancelBtt: View = cancel_btt
        val defaultBtt: View = set_default_btt
        val currentProfileText: TextView = currently_selected_profile_text
        val selectedItemActionsHolder: View = selected_item_holder*/
        binding.apply {
            fun getCurrentProfile(): QualityDataHelper.QualityProfile? {
                return (profilesRecyclerview.adapter as? ProfilesAdapter)?.getCurrentProfile()
            }

            fun refreshProfiles() {
                currentlySelectedProfileText.text = getProfileName(usedProfile).asString(context)
                (profilesRecyclerview.adapter as? ProfilesAdapter)?.updateList(getProfiles())
            }

            profilesRecyclerview.adapter = ProfilesAdapter(
                mutableListOf(),
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
                val choices = QualityDataHelper.QualityProfileType.values()
                    .filter { it != QualityDataHelper.QualityProfileType.None }
                val choiceNames = choices.map { txt(it.stringRes).asString(context) }

                activity.showBottomDialog(
                    choiceNames,
                    choices.indexOf(currentProfile.type),
                    txt(R.string.set_default).asString(context),
                    false,
                    {},
                    { index ->
                        val pickedChoice = choices.getOrNull(index) ?: return@showBottomDialog
                        // Remove previous picks
                        if (pickedChoice.unique) {
                            getProfiles().filter { it.type == pickedChoice }.forEach {
                                QualityDataHelper.setQualityProfileType(it.id, null)
                            }
                        }

                        QualityDataHelper.setQualityProfileType(currentProfile.id, pickedChoice)
                        refreshProfiles()
                    })
            }

            cancelBtt.setOnClickListener {
                this@QualityProfileDialog.dismissSafe()
            }

            useBtt.setOnClickListener {
                getCurrentProfile()?.let {
                    profileSelectionCallback.invoke(it)
                    this@QualityProfileDialog.dismissSafe()
                }
            }
        }
        super.show()
    }
}