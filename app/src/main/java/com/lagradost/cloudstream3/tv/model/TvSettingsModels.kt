package com.lagradost.cloudstream3.tv.model

/**
 * Immutable TV presentation models for Settings (Phase 11).
 * Not a duplicate of [com.lagradost.cloudstream4.AppSettings] — only what the 10ft UI needs.
 * Writes always go through existing PreferenceData / AppSettings APIs.
 */

enum class TvSettingsCategory(val label: String) {
    Playback("Playback"),
    Appearance("Appearance"),
    Language("Language"),
    Downloads("Downloads"),
    App("App"),
}

/** How a row is edited on TV. No free-text editors. */
enum class TvSettingControlKind {
    Boolean,
    Enum,
    Action,
    ReadOnly,
}

data class TvSettingOption(
    val key: String,
    val label: String,
)

data class TvSettingItem(
    val id: String,
    val category: TvSettingsCategory,
    val title: String,
    val summary: String? = null,
    val valueLabel: String? = null,
    val control: TvSettingControlKind,
    /** Current boolean when [control] is Boolean. */
    val booleanValue: Boolean = false,
    /** Options when [control] is Enum. */
    val options: List<TvSettingOption> = emptyList(),
    val selectedOptionKey: String? = null,
    /** Clear confirm before write — locale / recreate. */
    val requiresRestart: Boolean = false,
    val restartMessage: String? = null,
)

data class TvSettingsSection(
    val category: TvSettingsCategory,
    val items: List<TvSettingItem>,
)

data class TvSettingsCatalog(
    val sections: List<TvSettingsSection>,
    /** Local profile name only — no OAuth. */
    val accountDisplayName: String?,
)

sealed interface TvSettingsUiState {
    data object Loading : TvSettingsUiState
    data class Ready(val catalog: TvSettingsCatalog) : TvSettingsUiState
}

sealed interface TvSettingsAction {
    data object Refresh : TvSettingsAction
    data class ToggleBoolean(val id: String) : TvSettingsAction
    data class SelectEnum(val id: String, val optionKey: String) : TvSettingsAction
    data class InvokeAction(val id: String) : TvSettingsAction
}
