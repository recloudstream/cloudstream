package com.lagradost.cloudstream3.tv.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.tv.model.TvSettingsAction
import com.lagradost.cloudstream3.tv.model.TvSettingsUiState
import com.lagradost.cloudstream4.AppSettings
import com.lagradost.cloudstream4.compose.ActionHandler
import com.lagradost.cloudstream4.compose.DefaultStateContainer
import com.lagradost.cloudstream4.compose.StateContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings state over EXISTING [AppSettings] — no second prefs store.
 * Snapshot refresh on enter/resume/write only (no preference polling spam).
 */
class TvSettingsViewModel(
    application: Application,
) : AndroidViewModel(application),
    StateContainer<TvSettingsUiState> by DefaultStateContainer(TvSettingsUiState.Loading),
    ActionHandler<TvSettingsAction> {

    private val settings = AppSettings(application.applicationContext)
    private val adapter = TvSettingsAdapter(application.applicationContext, settings)

    /** One-shot side effect for the screen (recreate / open focus probe). */
    var pendingSideEffect: SideEffect? = null
        private set

    fun consumeSideEffect(): SideEffect? {
        val e = pendingSideEffect
        pendingSideEffect = null
        return e
    }

    sealed interface SideEffect {
        data object RecreateActivity : SideEffect
        data object OpenFocusProbe : SideEffect
    }

    override fun onAction(action: TvSettingsAction) {
        when (action) {
            TvSettingsAction.Refresh -> refresh()
            is TvSettingsAction.ToggleBoolean -> {
                adapter.toggleBoolean(action.id)
                refresh()
            }
            is TvSettingsAction.SelectEnum -> {
                when (val result = adapter.selectEnum(action.id, action.optionKey)) {
                    TvSettingsAdapter.ApplyResult.NeedsRecreate -> {
                        pendingSideEffect = SideEffect.RecreateActivity
                        refresh()
                    }
                    TvSettingsAdapter.ApplyResult.Applied -> refresh()
                    TvSettingsAdapter.ApplyResult.Unknown -> Unit
                }
            }
            is TvSettingsAction.ApplyMultiSelect -> {
                when (adapter.applyMultiSelect(action.id, action.values)) {
                    TvSettingsAdapter.ApplyResult.Applied -> refresh()
                    TvSettingsAdapter.ApplyResult.NeedsRecreate -> refresh()
                    TvSettingsAdapter.ApplyResult.Unknown -> Unit
                }
            }
            is TvSettingsAction.InvokeAction -> {
                if (action.id == TvSettingsAdapter.ID_FOCUS_PROBE) {
                    pendingSideEffect = SideEffect.OpenFocusProbe
                }
            }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            val catalog = withContext(Dispatchers.IO) { adapter.buildCatalog() }
            updateState { TvSettingsUiState.Ready(catalog) }
        }
    }
}
