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
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

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

        val regexInput = EditText(ctx).apply {
            hint = ctx.getString(R.string.source_regex_hint)
            setText(QualityDataHelper.getProfileSetting(profile.id, ProfileSettings.SourceRegex))
            isSingleLine = true
            setTextColor(ctx.getColor(android.R.color.white))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val regexPriority = QualityDataHelper.getProfileSetting(profile.id, ProfileSettings.SourceRegexPriority)
        val priorityText = TextView(ctx).apply {
            text = regexPriority.toString()
            gravity = android.view.Gravity.CENTER
            minWidth = (40 * ctx.resources.displayMetrics.density).toInt()
            setTextColor(ctx.getColor(android.R.color.white))
        }
        val minusBtn = android.widget.ImageView(ctx).apply {
            setImageResource(com.lagradost.cloudstream3.R.drawable.baseline_remove_24)
            background = ctx.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackgroundBorderless)).getDrawable(0)
            setPadding(8, 8, 8, 8)
            isFocusable = true
            setOnClickListener {
                val v = (priorityText.text.toString().toIntOrNull() ?: 0) - 1
                priorityText.text = v.coerceAtLeast(0).toString()
            }
        }
        val plusBtn = android.widget.ImageView(ctx).apply {
            setImageResource(com.lagradost.cloudstream3.R.drawable.ic_baseline_add_24)
            background = ctx.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackgroundBorderless)).getDrawable(0)
            setPadding(8, 8, 8, 8)
            isFocusable = true
            setOnClickListener {
                val v = (priorityText.text.toString().toIntOrNull() ?: 0) + 1
                priorityText.text = v.toString()
            }
        }
        val regexRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(
                (12 * ctx.resources.displayMetrics.density).toInt(),
                (8 * ctx.resources.displayMetrics.density).toInt(),
                (12 * ctx.resources.displayMetrics.density).toInt(),
                (4 * ctx.resources.displayMetrics.density).toInt()
            )
            addView(regexInput)
            addView(minusBtn)
            addView(priorityText)
            addView(plusBtn)
        }
        // Insert regex row into sort_sources_holder, after the "Sources" title
        binding.sortSourcesHolder.addView(regexRow, 1)

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

                val pattern = regexInput.text.toString().trim()
                if (pattern.isNotEmpty()) {
                    val joined = pattern.split(",").map { it.trim() }.filter { it.isNotEmpty() }.joinToString("|")
                    if (runCatching { Regex(joined, RegexOption.IGNORE_CASE) }.isFailure) {
                        android.widget.Toast.makeText(ctx, ctx.getString(R.string.source_regex_invalid), android.widget.Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                }
                QualityDataHelper.setProfileSetting(profile.id, ProfileSettings.SourceRegex, pattern)
                QualityDataHelper.setProfileSetting(profile.id, ProfileSettings.SourceRegexPriority, priorityText.text.toString().toIntOrNull() ?: 0)

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