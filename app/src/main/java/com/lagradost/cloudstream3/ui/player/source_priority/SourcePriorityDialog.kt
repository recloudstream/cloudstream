package com.lagradost.cloudstream3.ui.player.source_priority

import android.app.Dialog
import android.content.Context
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.StyleRes
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.work.impl.constraints.controllers.ConstraintController
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.result.txt
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import kotlinx.android.synthetic.main.player_select_source_priority.*

class SourcePriorityDialog(
    ctx: Context,
    @StyleRes themeRes: Int,
    val links: List<ExtractorLink>,
    private val profile: QualityDataHelper.QualityProfile,
    /**
     * Notify that the profile overview should be updated, for example if the name has been updated
     * Should not be called excessively.
     **/
    private val updatedCallback: () -> Unit
) : Dialog(ctx, themeRes) {
    override fun show() {
        setContentView(R.layout.player_select_source_priority)
        val sourcesRecyclerView: RecyclerView = sort_sources
        val qualitiesRecyclerView: RecyclerView = sort_qualities
        val profileText: EditText = profile_text_editable
        val saveBtt: View = save_btt
        val exitBtt: View = close_btt
        val helpBtt: View = help_btt

        profileText.setText(QualityDataHelper.getProfileName(profile.id).asString(context))
        profileText.hint = txt(R.string.profile_number, profile.id).asString(context)

        sourcesRecyclerView.adapter = PriorityAdapter(
            links.map { link ->
                SourcePriority(
                    null,
                    link.source,
                    QualityDataHelper.getSourcePriority(profile.id, link.source)
                )
            }.distinctBy { it.name }.sortedBy { -it.priority }.toMutableList()
        )

        qualitiesRecyclerView.adapter = PriorityAdapter(
            Qualities.values().mapNotNull {
                SourcePriority(
                    it,
                    Qualities.getStringByIntFull(it.value).ifBlank { return@mapNotNull null },
                    QualityDataHelper.getQualityPriority(profile.id, it)
                )
            }.sortedBy { -it.priority }.toMutableList()
        )

        @Suppress("UNCHECKED_CAST") // We know the types
        saveBtt.setOnClickListener {
            val qualityAdapter = qualitiesRecyclerView.adapter as? PriorityAdapter<Qualities>
            val sourcesAdapter = sourcesRecyclerView.adapter as? PriorityAdapter<Nothing>

            val qualities = qualityAdapter?.items ?: emptyList()
            val sources = sourcesAdapter?.items ?: emptyList()

            qualities.forEach {
                val data = it.data as? Qualities ?: return@forEach
                QualityDataHelper.setQualityPriority(profile.id, data, it.priority)
            }

            sources.forEach {
                QualityDataHelper.setSourcePriority(profile.id, it.name, it.priority)
            }

            qualityAdapter?.updateList(qualities.sortedBy { -it.priority })
            sourcesAdapter?.updateList(sources.sortedBy { -it.priority })

            val savedProfileName = profileText.text.toString()
            if (savedProfileName.isBlank()) {
                QualityDataHelper.setProfileName(profile.id, null)
            } else {
                QualityDataHelper.setProfileName(profile.id, savedProfileName)
            }
            updatedCallback.invoke()
        }

        exitBtt.setOnClickListener {
            this.dismissSafe()
        }

        helpBtt.setOnClickListener {
            AlertDialog.Builder(context, R.style.AlertDialogCustom).apply {
                setMessage(R.string.quality_profile_help)
            }.show()
        }

        super.show()
    }
}