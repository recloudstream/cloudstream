package com.lagradost.cloudstream3.utils

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.navigation.NavOptions
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.isEpisodeBased
import com.lagradost.cloudstream3.ui.result.ResultFragment
import com.lagradost.cloudstream3.ui.result.START_ACTION_TV_MODE
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import java.util.ArrayDeque

object TvModeHelper {
    enum class TvModeContentMode(val value: Int, @StringRes val labelRes: Int) {
        SERIES_ONLY(0, R.string.tv_mode_content_series_only),
        MOVIES_ONLY(1, R.string.tv_mode_content_movies_only),
        BOTH(2, R.string.tv_mode_content_both),
        ;

        companion object {
            fun fromValue(value: Int): TvModeContentMode {
                return entries.firstOrNull { it.value == value } ?: BOTH
            }
        }
    }

    enum class TvModeDubPreference(val value: Int, @StringRes val labelRes: Int) {
        PREFER_DUBBED(0, R.string.tv_mode_dub_prefer_dubbed),
        PREFER_SUBBED(1, R.string.tv_mode_dub_prefer_subbed),
        RANDOM(2, R.string.tv_mode_dub_random),
        ;

        companion object {
            fun fromValue(value: Int): TvModeDubPreference {
                return entries.firstOrNull { it.value == value } ?: PREFER_DUBBED
            }
        }
    }

    enum class TvModeSeasonMode(val value: Int, @StringRes val labelRes: Int) {
        SELECTED_SEASON_ONLY(0, R.string.tv_mode_season_selected),
        ANY_SEASON(1, R.string.tv_mode_season_any),
        ;

        companion object {
            fun fromValue(value: Int): TvModeSeasonMode {
                return entries.firstOrNull { it.value == value } ?: SELECTED_SEASON_ONLY
            }
        }
    }

    enum class HomeQuickActionMode(val value: Int, @StringRes val labelRes: Int) {
        NONE(0, R.string.home_quick_action_none),
        RANDOM(1, R.string.home_quick_action_random),
        TV_MODE(2, R.string.home_quick_action_tv_mode),
        ;

        companion object {
            fun fromValue(value: Int): HomeQuickActionMode {
                return entries.firstOrNull { it.value == value } ?: NONE
            }
        }
    }

    enum class TvModePlayerStartMode(val value: Int, @StringRes val labelRes: Int) {
        CURRENT_SHOW(0, R.string.tv_mode_player_start_current_show),
        GLOBAL_RANDOM(1, R.string.tv_mode_player_start_global_random),
        ;

        companion object {
            fun fromValue(value: Int): TvModePlayerStartMode {
                return entries.firstOrNull { it.value == value } ?: CURRENT_SHOW
            }
        }
    }

    private data class ResultNavigationData(
        val url: String,
        val apiName: String,
        val name: String,
    )

    private sealed interface TvModeSession {
        var managedPlayback: Boolean

        data class Global(
            val candidates: List<SearchResponse>,
            val recentUrls: ArrayDeque<String> = ArrayDeque(),
            val rejectedUrls: LinkedHashSet<String> = linkedSetOf(),
            override var managedPlayback: Boolean = false,
        ) : TvModeSession

        data class LocalShow(
            val navigation: ResultNavigationData,
            val selectedSeason: Int?,
            val seasonMode: TvModeSeasonMode,
            val recentEpisodeIds: ArrayDeque<Int> = ArrayDeque(),
            val rejectedEpisodeIds: LinkedHashSet<Int> = linkedSetOf(),
            override var managedPlayback: Boolean = false,
        ) : TvModeSession {
            fun matches(primaryUrl: String?, fallbackUrl: String?): Boolean {
                return navigation.url == primaryUrl || navigation.url == fallbackUrl
            }
        }
    }

    private var currentSession: TvModeSession? = null
    private var cachedHomepageCandidates: List<SearchResponse> = emptyList()

    fun hasSession(): Boolean {
        return currentSession != null
    }

    fun isEnabled(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
            context.getString(R.string.tv_mode_button_key),
            false
        )
    }

    fun shouldForceContinuousPlayback(context: Context): Boolean {
        return isManagedPlayback(context) && isLoopEnabled(context)
    }

    fun isManagedPlayback(context: Context): Boolean {
        return isEnabled(context) && (currentSession?.managedPlayback == true)
    }

    fun isLoopEnabled(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
            context.getString(R.string.tv_mode_loop_key),
            true
        )
    }

    fun isLocalSession(primaryUrl: String?, fallbackUrl: String? = null): Boolean {
        val session = currentSession as? TvModeSession.LocalShow ?: return false
        return session.matches(primaryUrl, fallbackUrl)
    }

    fun getLocalSeasonFilter(primaryUrl: String?, fallbackUrl: String? = null): Int? {
        val session = currentSession as? TvModeSession.LocalShow ?: return null
        if (!session.matches(primaryUrl, fallbackUrl)) return null
        return if (session.seasonMode == TvModeSeasonMode.SELECTED_SEASON_ONLY) {
            session.selectedSeason
        } else {
            null
        }
    }

    fun getRecentEpisodeIds(primaryUrl: String?, fallbackUrl: String? = null): Set<Int> {
        val session = currentSession as? TvModeSession.LocalShow ?: return emptySet()
        if (!session.matches(primaryUrl, fallbackUrl)) return emptySet()
        return session.recentEpisodeIds.toSet()
    }

    fun getRejectedEpisodeIds(primaryUrl: String?, fallbackUrl: String? = null): Set<Int> {
        val session = currentSession as? TvModeSession.LocalShow ?: return emptySet()
        if (!session.matches(primaryUrl, fallbackUrl)) return emptySet()
        return session.rejectedEpisodeIds.toSet()
    }

    fun rememberHomepageCandidates(candidates: List<SearchResponse>) {
        cachedHomepageCandidates = candidates.distinctBy { it.url }
    }

    fun getHomeQuickActionMode(context: Context): HomeQuickActionMode {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val key = context.getString(R.string.home_quick_action_key)
        if (preferences.contains(key)) {
            return HomeQuickActionMode.fromValue(
                preferences.getInt(key, HomeQuickActionMode.NONE.value)
            )
        }

        return when {
            preferences.getBoolean(context.getString(R.string.random_button_key), false) -> {
                HomeQuickActionMode.RANDOM
            }

            preferences.getBoolean(context.getString(R.string.tv_mode_button_key), false) -> {
                HomeQuickActionMode.TV_MODE
            }

            else -> HomeQuickActionMode.NONE
        }
    }

    fun getPlayerStartMode(context: Context): TvModePlayerStartMode {
        return TvModePlayerStartMode.fromValue(
            PreferenceManager.getDefaultSharedPreferences(context).getInt(
                context.getString(R.string.tv_mode_player_start_key),
                TvModePlayerStartMode.CURRENT_SHOW.value
            )
        )
    }

    fun getEligibleCandidates(context: Context, candidates: List<SearchResponse>): List<SearchResponse> {
        val contentMode = getContentMode(context)
        return candidates.filter { it.matchesContentMode(contentMode) }.ifEmpty { candidates }
    }

    fun resolveDubStatus(context: Context, available: Collection<DubStatus>): DubStatus? {
        val normalized = available.distinct()
        if (normalized.isEmpty()) return null

        return when (getDubPreference(context)) {
            TvModeDubPreference.PREFER_DUBBED -> {
                normalized.firstOrNull { it == DubStatus.Dubbed }
                    ?: normalized.firstOrNull { it == DubStatus.Subbed }
                    ?: normalized.randomOrNull()
            }

            TvModeDubPreference.PREFER_SUBBED -> {
                normalized.firstOrNull { it == DubStatus.Subbed }
                    ?: normalized.firstOrNull { it == DubStatus.Dubbed }
                    ?: normalized.randomOrNull()
            }

            TvModeDubPreference.RANDOM -> normalized.randomOrNull()
        }
    }

    @Synchronized
    fun startFromHome(activity: Activity?, candidates: List<SearchResponse>): Boolean {
        if (!startGlobalSession(activity, candidates)) return false
        return playNextFromSession(activity, replaceExisting = false)
    }

    @Synchronized
    fun startGlobalSession(activity: Activity?, candidates: List<SearchResponse>): Boolean {
        if (activity == null) return false
        if (!isEnabled(activity)) return false

        val distinctCandidates = candidates.distinctBy { it.url }
        if (distinctCandidates.isEmpty()) {
            stopSession()
            showToast(activity, R.string.tv_mode_no_eligible_titles, Toast.LENGTH_SHORT)
            return false
        }

        rememberHomepageCandidates(distinctCandidates)
        currentSession = TvModeSession.Global(candidates = distinctCandidates)
        return true
    }

    @Synchronized
    fun startGlobalSessionFromCache(
        activity: Activity?,
        fallbackCandidates: List<SearchResponse> = emptyList(),
    ): Boolean {
        val candidates = cachedHomepageCandidates.ifEmpty {
            fallbackCandidates.distinctBy { it.url }
        }
        return startGlobalSession(activity, candidates)
    }

    @Synchronized
    fun startForResult(
        activity: Activity?,
        url: String,
        apiName: String,
        name: String,
        selectedSeason: Int?,
    ): Boolean {
        if (activity == null) return false
        if (!isEnabled(activity)) return false

        currentSession = TvModeSession.LocalShow(
            navigation = ResultNavigationData(url = url, apiName = apiName, name = name),
            selectedSeason = selectedSeason,
            seasonMode = getSeasonMode(activity),
        )
        return true
    }

    @Synchronized
    fun playNextFromSession(activity: Activity?, replaceExisting: Boolean): Boolean {
        val session = currentSession ?: return false
        val validActivity = activity ?: return false
        if (!isEnabled(validActivity)) {
            stopSession()
            return false
        }

        session.managedPlayback = false

        return when (session) {
            is TvModeSession.Global -> {
                val next = pickCandidate(validActivity, session) ?: run {
                    stopSession()
                    showToast(validActivity, R.string.tv_mode_no_playable_content, Toast.LENGTH_SHORT)
                    return false
                }

                navigateToResult(validActivity, next, replaceExisting)
                true
            }

            is TvModeSession.LocalShow -> {
                navigateToStoredResult(validActivity, session.navigation, replaceExisting)
                true
            }
        }
    }

    @Synchronized
    fun markPlaybackManaged(
        primaryUrl: String?,
        episodeId: Int? = null,
        fallbackUrl: String? = null,
    ) {
        currentSession?.let { session ->
            session.managedPlayback = true
            when (session) {
                is TvModeSession.Global -> {
                    if (!primaryUrl.isNullOrBlank()) {
                        rememberRecentUrl(session, primaryUrl)
                    } else if (!fallbackUrl.isNullOrBlank()) {
                        rememberRecentUrl(session, fallbackUrl)
                    }
                }

                is TvModeSession.LocalShow -> {
                    if (session.matches(primaryUrl, fallbackUrl) && episodeId != null) {
                        rememberRecentEpisode(session, episodeId)
                    }
                }
            }
        }
    }

    @Synchronized
    fun rejectPlayback(
        primaryUrl: String?,
        episodeId: Int? = null,
        fallbackUrl: String? = null,
    ) {
        currentSession?.let { session ->
            session.managedPlayback = false
            when (session) {
                is TvModeSession.Global -> {
                    resolveSessionUrl(primaryUrl, fallbackUrl)?.let { url ->
                        session.rejectedUrls.add(url)
                    }
                }

                is TvModeSession.LocalShow -> {
                    if (session.matches(primaryUrl, fallbackUrl) && episodeId != null) {
                        session.rejectedEpisodeIds.add(episodeId)
                    }
                }
            }
        }
    }

    @Synchronized
    fun clearManagedPlayback() {
        currentSession?.managedPlayback = false
    }

    @Synchronized
    fun stopSession() {
        currentSession = null
    }

    fun acceptsLoadedContent(
        context: Context,
        isMovie: Boolean,
        hasEpisodeContent: Boolean,
        primaryUrl: String?,
        fallbackUrl: String? = null,
    ): Boolean {
        if (isLocalSession(primaryUrl, fallbackUrl)) {
            return hasEpisodeContent
        }

        return when (getContentMode(context)) {
            TvModeContentMode.SERIES_ONLY -> hasEpisodeContent
            TvModeContentMode.MOVIES_ONLY -> isMovie
            TvModeContentMode.BOTH -> isMovie || hasEpisodeContent
        }
    }

    fun shouldIncludeInContinueWatching(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
            context.getString(R.string.tv_mode_continue_watching_key),
            true
        )
    }

    fun isStallProtectionEnabled(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
            context.getString(R.string.tv_mode_stall_protection_key),
            false
        )
    }

    fun getStallRetryLimit(context: Context): Int {
        return PreferenceManager.getDefaultSharedPreferences(context).getInt(
            context.getString(R.string.tv_mode_retry_limit_key),
            3
        )
    }

    fun getLoadingTimeoutSeconds(context: Context): Int {
        return PreferenceManager.getDefaultSharedPreferences(context).getInt(
            context.getString(R.string.tv_mode_loading_timeout_key),
            20
        )
    }

    private fun getContentMode(context: Context): TvModeContentMode {
        return TvModeContentMode.fromValue(
            PreferenceManager.getDefaultSharedPreferences(context).getInt(
                context.getString(R.string.tv_mode_content_key),
                TvModeContentMode.BOTH.value
            )
        )
    }

    private fun getDubPreference(context: Context): TvModeDubPreference {
        return TvModeDubPreference.fromValue(
            PreferenceManager.getDefaultSharedPreferences(context).getInt(
                context.getString(R.string.tv_mode_dub_preference_key),
                TvModeDubPreference.PREFER_DUBBED.value
            )
        )
    }

    private fun getSeasonMode(context: Context): TvModeSeasonMode {
        return TvModeSeasonMode.fromValue(
            PreferenceManager.getDefaultSharedPreferences(context).getInt(
                context.getString(R.string.tv_mode_season_scope_key),
                TvModeSeasonMode.SELECTED_SEASON_ONLY.value
            )
        )
    }

    private fun navigateToResult(activity: Activity, card: SearchResponse, replaceExisting: Boolean) {
        val isPhoneLayout = isLayout(PHONE)
        val globalNavigationId = if (isPhoneLayout) {
            R.id.global_to_navigation_results_phone
        } else {
            R.id.global_to_navigation_results_tv
        }
        val resultDestinationId = if (isPhoneLayout) {
            R.id.navigation_results_phone
        } else {
            R.id.navigation_results_tv
        }
        val navOptions = if (replaceExisting) {
            NavOptions.Builder()
                .setPopUpTo(resultDestinationId, true)
                .build()
        } else {
            null
        }

        activity.navigate(
            globalNavigationId,
            ResultFragment.newInstance(card, START_ACTION_TV_MODE),
            navOptions
        )
    }

    private fun navigateToStoredResult(
        activity: Activity,
        navigation: ResultNavigationData,
        replaceExisting: Boolean,
    ) {
        val isPhoneLayout = isLayout(PHONE)
        val globalNavigationId = if (isPhoneLayout) {
            R.id.global_to_navigation_results_phone
        } else {
            R.id.global_to_navigation_results_tv
        }
        val resultDestinationId = if (isPhoneLayout) {
            R.id.navigation_results_phone
        } else {
            R.id.navigation_results_tv
        }
        val navOptions = if (replaceExisting) {
            NavOptions.Builder()
                .setPopUpTo(resultDestinationId, true)
                .build()
        } else {
            null
        }

        activity.navigate(
            globalNavigationId,
            ResultFragment.newInstance(
                navigation.url,
                navigation.apiName,
                navigation.name,
                START_ACTION_TV_MODE,
                0
            ),
            navOptions
        )
    }

    private fun pickCandidate(
        context: Context,
        session: TvModeSession.Global,
    ): SearchResponse? {
        val availableCandidates = session.candidates
            .filterNot { candidate -> session.rejectedUrls.contains(candidate.url) }
        if (availableCandidates.isEmpty()) return null

        val preferredCandidates = getEligibleCandidates(context, availableCandidates)
        val candidatePool = if (preferredCandidates.isNotEmpty()) {
            preferredCandidates
        } else {
            availableCandidates
        }

        val freshCandidates = candidatePool.filterNot { candidate ->
            session.recentUrls.contains(candidate.url)
        }

        return (if (freshCandidates.isNotEmpty()) freshCandidates else candidatePool).randomOrNull()
    }

    private fun rememberRecentUrl(session: TvModeSession.Global, url: String) {
        session.recentUrls.remove(url)
        session.recentUrls.addLast(url)

        val maxRecentSize = when {
            session.candidates.size <= 4 -> 2
            session.candidates.size <= 12 -> 4
            else -> 8
        }

        while (session.recentUrls.size > maxRecentSize) {
            if (session.recentUrls.isNotEmpty()) {
                session.recentUrls.removeFirst()
            }
        }
    }

    private fun rememberRecentEpisode(session: TvModeSession.LocalShow, episodeId: Int) {
        session.recentEpisodeIds.remove(episodeId)
        session.recentEpisodeIds.addLast(episodeId)

        val maxRecentSize = 6
        while (session.recentEpisodeIds.size > maxRecentSize) {
            if (session.recentEpisodeIds.isNotEmpty()) {
                session.recentEpisodeIds.removeFirst()
            }
        }
    }

    private fun SearchResponse.matchesContentMode(contentMode: TvModeContentMode): Boolean {
        return when (contentMode) {
            TvModeContentMode.SERIES_ONLY -> type.isTvModeSeries()
            TvModeContentMode.MOVIES_ONLY -> type.isTvModeMovie()
            TvModeContentMode.BOTH -> type.isTvModeSeries() || type.isTvModeMovie()
        }
    }

    private fun resolveSessionUrl(primaryUrl: String?, fallbackUrl: String?): String? {
        return primaryUrl?.takeIf { it.isNotBlank() } ?: fallbackUrl?.takeIf { it.isNotBlank() }
    }

    private fun TvType?.isTvModeSeries(): Boolean {
        return this?.isEpisodeBased() == true || this == TvType.OVA
    }

    private fun TvType?.isTvModeMovie(): Boolean {
        return this == TvType.Movie || this == TvType.AnimeMovie
    }
}
