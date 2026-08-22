package com.lagradost.cloudstream3.ui.player.source_priority

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import androidx.annotation.StyleRes
import androidx.appcompat.app.AlertDialog
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.PlayerSelectSourcePriorityBinding
import com.lagradost.cloudstream3.utils.txt
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding

class SourcePriorityDialog(
    val ctx: Context,
    @StyleRes val themeRes: Int,
    val links: List<LinkSource>,
    private val profile: QualityDataHelper.QualityProfile,
    /**
     * Notify that the profile overview should be updated, for example if the name has been updated
     * Should not be called excessively.
     **/
    private val updatedCallback: () -> Unit
) : Dialog(ctx, themeRes) {
    override fun show() {
        val binding =
            PlayerSelectSourcePriorityBinding.inflate(LayoutInflater.from(ctx), null, false)
        setContentView(binding.root)
        fixSystemBarsPadding(binding.root)

        binding.apply {
            profileTextEditable.setText(
                QualityDataHelper.getProfileName(profile.id).asString(context)
            )
            profileTextEditable.hint = txt(R.string.profile_number, profile.id).asString(context)

            sortSources.adapter = PriorityAdapter<Nothing?>(
            ).apply {
                val sortedLinks = links.map { link ->
                    SourcePriority(
                        null,
                        link.source,
                        QualityDataHelper.getSourcePriority(profile.id, link.source)
                    )
                }.distinctBy { it.name }.sortedBy { -it.priority }

                submitList(sortedLinks)
            }

            sortQualities.adapter = PriorityAdapter<Qualities>(
            ).apply {
                submitList(Qualities.entries.mapNotNull {
                    SourcePriority(
                        it,
                        Qualities.getStringByIntFull(it.value).ifBlank { return@mapNotNull null },
                        QualityDataHelper.getQualityPriority(profile.id, it)
                    )
                }.sortedBy { -it.priority })
            }

            @Suppress("UNCHECKED_CAST") // We know the types
            saveBtt.setOnClickListener {
                val qualityAdapter = sortQualities.adapter as? PriorityAdapter<Qualities>
                val sourcesAdapter = sortSources.adapter as? PriorityAdapter<Nothing?>

                val qualities = qualityAdapter?.immutableCurrentList ?: emptyList()
                val sources = sourcesAdapter?.immutableCurrentList ?: emptyList()

                qualities.forEach {
                    QualityDataHelper.setQualityPriority(profile.id, it.data, it.priority)
                }

                sources.forEach {
                    QualityDataHelper.setSourcePriority(profile.id, it.name, it.priority)
                }

                qualityAdapter?.submitList(qualities.sortedBy { -it.priority })
                sourcesAdapter?.submitList(sources.sortedBy { -it.priority })

                val savedProfileName = profileTextEditable.text.toString()
                if (savedProfileName.isBlank()) {
                    QualityDataHelper.setProfileName(profile.id, null)
                } else {
                    QualityDataHelper.setProfileName(profile.id, savedProfileName)
                }
                updatedCallback.invoke()
            }

            closeBtt.setOnClickListener {
                dismissSafe()
            }

            helpBtt.setOnClickListener {
                AlertDialog.Builder(context, R.style.AlertDialogCustom).apply {
                    setMessage(R.string.quality_profile_help)
                }.show()
            }

            settingsBtt.setOnClickListener {
                SourceProfileSettingsDialog(ctx, themeRes, profile.id).show()
            }
        }
        super.show()
    }
}