package com.lagradost.cloudstream3.ui.home

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.utils.AppContextUtils.filterProviderByPreferredMedia

internal object MultipleHomeSources {
    fun active(context: Context): List<MainAPI> {
        val current = com.lagradost.cloudstream3.utils.DataStoreHelper.currentHomePage
        return if (current == CombinedHomeApi.NAME) selected(context)
        else listOfNotNull(com.lagradost.cloudstream3.APIHolder.getApiFromNameNull(current)).filter { it.hasMainPage }
    }
    fun selected(context: Context): List<MainAPI> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val names = prefs.getStringSet("home_selected_sources", emptySet()).orEmpty()
        return context.filterProviderByPreferredMedia().filter {
            it.hasMainPage && (prefs.getBoolean("home_all_sources", false) || it.name in names)
        }.distinctBy { it.name }
    }

    fun show(context: Context, current: String?, callback: (String) -> Unit) {
        val providers = context.filterProviderByPreferredMedia().filter { it.hasMainPage }.distinctBy { it.name }
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val saved = prefs.getStringSet("home_selected_sources", current?.let { setOf(it) } ?: emptySet()).orEmpty()
        val chosen = if (prefs.getBoolean("home_all_sources", false)) providers.map { it.name }.toMutableSet() else saved.toMutableSet()
        val names = providers.map { it.name }.toTypedArray()
        fun apply(all: Boolean) {
            prefs.edit { putBoolean("home_all_sources", all); putStringSet("home_selected_sources", chosen.toSet()) }
            callback(if (chosen.isEmpty()) APIRepository.noneApi.name else CombinedHomeApi.NAME)
        }
        AlertDialog.Builder(context).setTitle(R.string.home_multiple_sources)
            .setMultiChoiceItems(names, BooleanArray(names.size) { names[it] in chosen }) { _, index, checked ->
                if (checked) chosen.add(names[index]) else chosen.remove(names[index])
            }.setPositiveButton(R.string.sort_apply) { _, _ -> apply(false) }
            .setNeutralButton(R.string.home_all_sources) { _, _ -> chosen.addAll(names); apply(true) }
            .setNegativeButton(android.R.string.cancel, null).show()
    }
}
