package com.lagradost.cloudstream3.ui.player.source_priority

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import androidx.annotation.StyleRes
import com.lagradost.cloudstream3.databinding.SourceProfileSettingsDialogBinding
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding

class SourceProfileSettingsDialog(
    val ctx: Context,
    @StyleRes themeRes: Int,
    val profile: Int
) : Dialog(ctx, themeRes) {
     override fun show() {
         val binding =
             SourceProfileSettingsDialogBinding.inflate(LayoutInflater.from(ctx), null, false)
         setContentView(binding.root)
         fixSystemBarsPadding(binding.root)

         binding.apply {
             var hideErrorSources = QualityDataHelper.getProfileSetting(profile, ProfileSettings.HideErrorSources)
             var hideNegativeSources = QualityDataHelper.getProfileSetting(profile, ProfileSettings.HideNegativeSources)

             profileHideErrorSources.isChecked = hideErrorSources
             profileHideErrorSources.setOnCheckedChangeListener { _, bool ->
                 hideErrorSources = bool
             }

             profileHideNegativeSources.isChecked = hideNegativeSources
             profileHideNegativeSources.setOnCheckedChangeListener { _, bool ->
                 hideNegativeSources = bool
             }

             applyBtt.setOnClickListener {
                 QualityDataHelper.setProfileSetting(profile, ProfileSettings.HideErrorSources, hideErrorSources)
                 QualityDataHelper.setProfileSetting(profile, ProfileSettings.HideNegativeSources, hideNegativeSources)
                 dismissSafe()
             }

             cancelBtt.setOnClickListener {
                 dismissSafe()
             }
         }
         super.show()
     }
}