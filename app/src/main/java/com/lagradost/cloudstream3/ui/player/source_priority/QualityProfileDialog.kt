package com.lagradost.cloudstream3.ui.player.source_priority

import android.app.Dialog
import android.view.View
import android.widget.TextView
import androidx.annotation.StyleRes
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.player.source_priority.QualityDataHelper.getProfileName
import com.lagradost.cloudstream3.ui.player.source_priority.QualityDataHelper.getProfiles
import com.lagradost.cloudstream3.ui.result.txt
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import kotlinx.android.synthetic.main.player_quality_profile_dialog.*

class QualityProfileDialog(
    val activity: FragmentActivity,
    @StyleRes val themeRes: Int,
    private val links: List<ExtractorLink>,
    private val usedProfile: Int,
    private val profileSelectionCallback: (QualityDataHelper.QualityProfile) -> Unit
) : Dialog(activity, themeRes) {
    override fun show() {
        setContentView(R.layout.player_quality_profile_dialog)
        val profilesRecyclerView: RecyclerView = profiles_recyclerview
        val useBtt: View = use_btt
        val editBtt: View = edit_btt
        val cancelBtt: View = cancel_btt
        val defaultBtt: View = set_default_btt
        val currentProfileText: TextView = currently_selected_profile_text
        val selectedItemActionsHolder: View = selected_item_holder

        fun getCurrentProfile(): QualityDataHelper.QualityProfile? {
            return (profilesRecyclerView.adapter as? ProfilesAdapter)?.getCurrentProfile()
        }

        fun refreshProfiles() {
            currentProfileText.text = getProfileName(usedProfile).asString(context)
            (profilesRecyclerView.adapter as? ProfilesAdapter)?.updateList(getProfiles())
        }

        profilesRecyclerView.adapter = ProfilesAdapter(
            mutableListOf(),
            usedProfile,
        ) { oldIndex: Int?, newIndex: Int ->
            profilesRecyclerView.adapter?.notifyItemChanged(newIndex)
            selectedItemActionsHolder.alpha = 1f
            if (oldIndex != null) {
                profilesRecyclerView.adapter?.notifyItemChanged(oldIndex)
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


        defaultBtt.setOnClickListener {
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
            this.dismissSafe()
        }

        useBtt.setOnClickListener {
            getCurrentProfile()?.let {
                profileSelectionCallback.invoke(it)
                this.dismissSafe()
            }
        }

        super.show()
    }
}