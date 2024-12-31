package com.lagradost.cloudstream3.ui.player

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.text.Spanned
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.core.animation.addListener
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.media3.common.Format.NO_VALUE
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.navigation.findNavController
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.LoadResponse.Companion.getAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.getImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.getMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.getTMDbId
import com.lagradost.cloudstream3.databinding.DialogOnlineSubtitlesBinding
import com.lagradost.cloudstream3.databinding.FragmentPlayerBinding
import com.lagradost.cloudstream3.databinding.PlayerSelectSourceAndSubsBinding
import com.lagradost.cloudstream3.databinding.PlayerSelectTracksBinding
import com.lagradost.cloudstream3.databinding.SubtitleSettingsDialogBinding
import com.lagradost.cloudstream3.mvvm.*
import com.lagradost.cloudstream3.subtitles.AbstractSubApi
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.subtitleProviders
import com.lagradost.cloudstream3.ui.player.CS3IPlayer.Companion.preferredAudioTrackLanguage
import com.lagradost.cloudstream3.ui.player.CustomDecoder.Companion.updateForcedEncoding
import com.lagradost.cloudstream3.ui.player.PlayerSubtitleHelper.Companion.toSubtitleMimeType
import com.lagradost.cloudstream3.ui.player.source_priority.QualityDataHelper
import com.lagradost.cloudstream3.ui.player.source_priority.QualityProfileDialog
import com.lagradost.cloudstream3.ui.result.*
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.ui.subtitles.SUBTITLE_AUTO_SELECT_KEY
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment.Companion.getAutoSelectLanguageISO639_1
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppContextUtils.html
import com.lagradost.cloudstream3.utils.AppContextUtils.sortSubs
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.runOnMainThread
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showDialog
import com.lagradost.cloudstream3.utils.SubtitleHelper.fromTwoLettersToLanguage
import com.lagradost.cloudstream3.utils.SubtitleHelper.languages
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.hideSystemUI
import com.lagradost.cloudstream3.utils.UIHelper.popCurrentPage
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import com.lagradost.safefile.SafeFile
import kotlinx.coroutines.Job
import java.io.Serializable
import java.util.*
import kotlin.math.abs

class GeneratorPlayer : FullScreenPlayer() {
    companion object {
        private var lastUsedGenerator: IGenerator? = null
        fun newInstance(generator: IGenerator, syncData: HashMap<String, String>? = null): Bundle {
            Log.i(TAG, "newInstance = $syncData")
            lastUsedGenerator = generator
            return Bundle().apply {
                if (syncData != null) putSerializable("syncData", syncData)
            }
        }

        val subsProviders
            get() = subtitleProviders.filter { provider ->
                (provider as? AbstractSubApi)?.let { !it.requiresLogin || it.loginInfo() != null }
                    ?: true
            }
        val subsProvidersIsActive
            get() = subsProviders.isNotEmpty()
    }


    private var titleRez = 3
    private var limitTitle = 0

    private lateinit var viewModel: PlayerGeneratorViewModel //by activityViewModels()
    private lateinit var sync: SyncViewModel
    private var currentLinks: Set<Pair<ExtractorLink?, ExtractorUri?>> = setOf()
    private var currentSubs: Set<SubtitleData> = setOf()

    private var currentSelectedLink: Pair<ExtractorLink?, ExtractorUri?>? = null
    private var currentSelectedSubtitles: SubtitleData? = null
    private var currentMeta: Any? = null
    private var nextMeta: Any? = null
    private var isActive: Boolean = false
    private var isNextEpisode: Boolean = false // this is used to reset the watch time

    private var preferredAutoSelectSubtitles: String? = null // null means do nothing, "" means none

    private var binding: FragmentPlayerBinding? = null

    private fun startLoading() {
        player.release()
        currentSelectedSubtitles = null
        isActive = false
        binding?.overlayLoadingSkipButton?.isVisible = false
        binding?.playerLoadingOverlay?.isVisible = true
    }

    private fun setSubtitles(subtitle: SubtitleData?): Boolean {
        // If subtitle is changed -> Save the language
        if (subtitle != currentSelectedSubtitles) {
            val subtitleLanguage639 = if (subtitle == null) {
                // "" is No Subtitles
                ""
            } else if (subtitle.languageCode != null) {
                // Could be "English 4" which is why it is trimmed.
                val trimmedLanguage = subtitle.languageCode.replace(Regex("\\d"), "").trim()

                languages.firstOrNull { language ->
                    language.languageName.equals(trimmedLanguage, ignoreCase = true) ||
                            language.ISO_639_1 == subtitle.languageCode
                }?.ISO_639_1
            } else {
                null
            }

            if (subtitleLanguage639 != null) {
                setKey(SUBTITLE_AUTO_SELECT_KEY, subtitleLanguage639)
                preferredAutoSelectSubtitles = subtitleLanguage639
            }
        }

        currentSelectedSubtitles = subtitle
        //Log.i(TAG, "setSubtitles = $subtitle")
        return player.setPreferredSubtitles(subtitle)
    }

    override fun embeddedSubtitlesFetched(subtitles: List<SubtitleData>) {
        viewModel.addSubtitles(subtitles.toSet())
    }

    override fun onTracksInfoChanged() {
        val tracks = player.getVideoTracks()
        playerBinding?.playerTracksBtt?.isVisible =
            tracks.allVideoTracks.size > 1 || tracks.allAudioTracks.size > 1
        // Only set the preferred language if it is available.
        // Otherwise it may give some users audio track init failed!
        if (tracks.allAudioTracks.any { it.language == preferredAudioTrackLanguage }) {
            player.setPreferredAudioTrack(preferredAudioTrackLanguage)
        }
    }

    override fun playerStatusChanged() {
        super.playerStatusChanged()
        if (player.getIsPlaying()) {
            viewModel.forceClearCache = false
        }
    }

    private fun noSubtitles(): Boolean {
        return setSubtitles(null)
    }

    private fun getPos(): Long {
        val durPos = DataStoreHelper.getViewPos(viewModel.getId()) ?: return 0L
        if (durPos.duration == 0L) return 0L
        if (durPos.position * 100L / durPos.duration > 95L) {
            return 0L
        }
        return durPos.position
    }

    private var currentVerifyLink: Job? = null

    private fun loadExtractorJob(extractorLink: ExtractorLink?) {
        currentVerifyLink?.cancel()

        extractorLink?.let { link ->
            currentVerifyLink = ioSafe {
                if (link.extractorData != null) {
                    getApiFromNameNull(link.source)?.extractorVerifierJob(link.extractorData)
                }
            }
        }
    }

    override fun onDownload(event: DownloadEvent) {
        super.onDownload(event)
        showDownloadProgress(event)
    }

    private fun showDownloadProgress(event: DownloadEvent) {
        activity?.runOnUiThread {
            playerBinding?.downloadedProgress?.apply {
                val indeterminate = event.totalBytes <= 0 || event.downloadedBytes <= 0
                isIndeterminate = indeterminate
                if (!indeterminate) {
                    max = (event.totalBytes / 1000).toInt()
                    progress = (event.downloadedBytes / 1000).toInt()
                }
            }
            playerBinding?.downloadedProgressText.setText(
                txt(
                    R.string.download_size_format,
                    android.text.format.Formatter.formatShortFileSize(
                        context,
                        event.downloadedBytes
                    ),
                    android.text.format.Formatter.formatShortFileSize(context, event.totalBytes)
                )
            )
            val downloadSpeed =
                android.text.format.Formatter.formatShortFileSize(context, event.downloadSpeed)
            playerBinding?.downloadedProgressSpeedText?.text =
                    // todo string fmt
                event.connections?.let { connections ->
                    "%s/s - %d Connections".format(downloadSpeed, connections)
                } ?: downloadSpeed

            // don't display when done
            playerBinding?.downloadedProgressSpeedText?.isGone =
                event.downloadedBytes != 0L && event.downloadedBytes - 1024 >= event.totalBytes
        }
    }

    private fun loadLink(link: Pair<ExtractorLink?, ExtractorUri?>?, sameEpisode: Boolean) {
        if (link == null) return

        // manage UI
        binding?.playerLoadingOverlay?.isVisible = false
        val isTorrent =
            link.first?.type == ExtractorLinkType.MAGNET || link.first?.type == ExtractorLinkType.TORRENT

        playerBinding?.downloadHeader?.isVisible = false
        playerBinding?.downloadHeaderToggle?.isVisible = isTorrent

        showDownloadProgress(DownloadEvent(0, 0, 0, null))

        uiReset()
        currentSelectedLink = link
        currentMeta = viewModel.getMeta()
        nextMeta = viewModel.getNextMeta()
        //  setEpisodes(viewModel.getAllMeta() ?: emptyList())
        isActive = true
        setPlayerDimen(null)
        setTitle()
        if (!sameEpisode)
            hasRequestedStamps = false

        loadExtractorJob(link.first)
        // load player
        context?.let { ctx ->
            val (url, uri) = link
            player.loadPlayer(
                ctx,
                sameEpisode,
                url,
                uri,
                startPosition = if (sameEpisode) null else {
                    if (isNextEpisode) 0L else getPos()
                },
                currentSubs,
                (if (sameEpisode) currentSelectedSubtitles else null) ?: getAutoSelectSubtitle(
                    currentSubs, settings = true, downloads = true
                ),
                preview = isFullScreenPlayer
            )
        }

        if (!sameEpisode)
            player.addTimeStamps(listOf()) // clear stamps
    }

    private fun closestQuality(target: Int?): Qualities {
        if (target == null) return Qualities.Unknown
        return Qualities.entries.minBy { abs(it.value - target) }
    }

    private fun getLinkPriority(
        qualityProfile: Int,
        link: Pair<ExtractorLink?, ExtractorUri?>
    ): Int {
        val (linkData, _) = link

        val qualityPriority = QualityDataHelper.getQualityPriority(
            qualityProfile,
            closestQuality(linkData?.quality)
        )
        val sourcePriority =
            QualityDataHelper.getSourcePriority(qualityProfile, linkData?.source)

        // negative because we want to sort highest quality first
        return qualityPriority + sourcePriority
    }

    private fun sortLinks(qualityProfile: Int): List<Pair<ExtractorLink?, ExtractorUri?>> {
        return currentLinks.sortedBy {
            -getLinkPriority(qualityProfile, it)
        }
    }

    data class TempMetaData(
        var episode: Int? = null,
        var season: Int? = null,
        var name: String? = null,
        var imdbId: String? = null,
    )

    private fun getMetaData(): TempMetaData {
        val meta = TempMetaData()

        when (val newMeta = currentMeta) {
            is ResultEpisode -> {
                if (!newMeta.tvType.isMovieType()) {
                    meta.episode = newMeta.episode
                    meta.season = newMeta.season
                }
                meta.name = newMeta.headerName
            }

            is ExtractorUri -> {
                if (newMeta.tvType?.isMovieType() == false) {
                    meta.episode = newMeta.episode
                    meta.season = newMeta.season
                }
                meta.name = newMeta.headerName
            }
        }
        return meta
    }

    override fun openOnlineSubPicker(
        context: Context, loadResponse: LoadResponse?, dismissCallback: (() -> Unit)
    ) {
        val providers = subsProviders
        val isSingleProvider = subsProviders.size == 1

        val dialog = Dialog(context, R.style.AlertDialogCustomBlack)
        val binding =
            DialogOnlineSubtitlesBinding.inflate(LayoutInflater.from(context), null, false)
        dialog.setContentView(binding.root)

        var currentSubtitles: List<AbstractSubtitleEntities.SubtitleEntity> = emptyList()
        var currentSubtitle: AbstractSubtitleEntities.SubtitleEntity? = null

        fun getName(entry: AbstractSubtitleEntities.SubtitleEntity, withLanguage: Boolean): String {
            if (entry.lang.isBlank() || !withLanguage) {
                return entry.name
            }
            val language = fromTwoLettersToLanguage(entry.lang.trim()) ?: entry.lang
            return "$language ${entry.name}"
        }

        val layout = R.layout.sort_bottom_single_choice_double_text
        val arrayAdapter =
            object : ArrayAdapter<AbstractSubtitleEntities.SubtitleEntity>(dialog.context, layout) {
                fun setHearingImpairedIcon(
                    imageViewEnd: ImageView?, position: Int
                ) {
                    if (imageViewEnd == null) return
                    val isHearingImpaired =
                        currentSubtitles.getOrNull(position)?.isHearingImpaired ?: false

                    val drawableEnd = if (isHearingImpaired) {
                        ContextCompat.getDrawable(
                            context, R.drawable.ic_baseline_hearing_24
                        )?.apply {
                            setTint(
                                ContextCompat.getColor(
                                    context, R.color.textColor
                                )
                            )
                        }
                    } else null

                    imageViewEnd.setImageDrawable(drawableEnd)
                }

                @SuppressLint("SetTextI18n")
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = convertView ?: LayoutInflater.from(context).inflate(layout, null)

                    val item = getItem(position)

                    val mainTextView = view.findViewById<TextView>(R.id.main_text)
                    val secondaryTextView = view.findViewById<TextView>(R.id.secondary_text)
                    val drawableEnd = view.findViewById<ImageView>(R.id.drawable_end)

                    mainTextView?.text = item?.let { getName(it, false) }

                    val language =
                        item?.let { fromTwoLettersToLanguage(it.lang.trim()) ?: it.lang } ?: ""
                    val providerSuffix =
                        if (isSingleProvider || item == null) "" else " · ${item.source}"
                    secondaryTextView?.text = language + providerSuffix

                    setHearingImpairedIcon(drawableEnd, position)
                    return view
                }
            }

        dialog.show()
        binding.cancelBtt.setOnClickListener {
            dialog.dismissSafe()
        }

        binding.subtitleAdapter.choiceMode = AbsListView.CHOICE_MODE_SINGLE
        binding.subtitleAdapter.adapter = arrayAdapter

        binding.subtitleAdapter.setOnItemClickListener { _, _, position, _ ->
            currentSubtitle = currentSubtitles.getOrNull(position) ?: return@setOnItemClickListener
        }

        var currentLanguageTwoLetters: String = getAutoSelectLanguageISO639_1()


        fun setSubtitlesList(list: List<AbstractSubtitleEntities.SubtitleEntity>) {
            currentSubtitles = list
            arrayAdapter.clear()
            arrayAdapter.addAll(currentSubtitles)
        }

        val currentTempMeta = getMetaData()

        // bruh idk why it is not correct
        val color = ColorStateList.valueOf(context.colorFromAttribute(R.attr.colorAccent))
        binding.searchLoadingBar.progressTintList = color
        binding.searchLoadingBar.indeterminateTintList = color

        observeNullable(viewModel.currentSubtitleYear) {
            // When year is changed search again
            binding.subtitlesSearch.setQuery(binding.subtitlesSearch.query, true)
            binding.yearBtt.text = it?.toString() ?: txt(R.string.none).asString(context)
        }

        binding.yearBtt.setOnClickListener {
            val none = txt(R.string.none).asString(context)
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            val earliestYear = 1900

            val years = (currentYear downTo earliestYear).toList()
            val options = listOf(none) + years.map {
                it.toString()
            }

            val selectedIndex = viewModel.currentSubtitleYear.value
                ?.let {
                    // + 1 since none also takes a space
                    years.indexOf(it) + 1
                }
                ?.takeIf { it >= 0 } ?: 0

            activity?.showDialog(
                options,
                selectedIndex,
                txt(R.string.year).asString(context),
                true, {
                }, { index ->
                    viewModel.setSubtitleYear(years.getOrNull(index - 1))
                }
            )
        }

        binding.subtitlesSearch.setOnQueryTextListener(object :
            androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                binding.searchLoadingBar.show()
                ioSafe {
                    val search =
                        AbstractSubtitleEntities.SubtitleSearch(
                            query = query ?: return@ioSafe,
                            imdbId = loadResponse?.getImdbId(),
                            tmdbId = loadResponse?.getTMDbId()?.toInt(),
                            malId = loadResponse?.getMalId()?.toInt(),
                            aniListId = loadResponse?.getAniListId()?.toInt(),
                            epNumber = currentTempMeta.episode,
                            seasonNumber = currentTempMeta.season,
                            lang = currentLanguageTwoLetters.ifBlank { null },
                            year = viewModel.currentSubtitleYear.value
                        )
                    val results = providers.amap {
                        try {
                            it.search(search)
                        } catch (e: Exception) {
                            null
                        }
                    }.filterNotNull()
                    val max = results.maxOfOrNull { it.size } ?: return@ioSafe

                    // very ugly
                    val items = ArrayList<AbstractSubtitleEntities.SubtitleEntity>()
                    val arrays = results.size
                    for (index in 0 until max) {
                        for (i in 0 until arrays) {
                            items.add(results[i].getOrNull(index) ?: continue)
                        }
                    }

                    // ugly ik
                    activity?.runOnUiThread {
                        setSubtitlesList(items)
                        binding.searchLoadingBar.hide()
                    }
                }

                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return true
            }
        })

        binding.searchFilter.setOnClickListener { view ->
            val lang639_1 = languages.map { it.ISO_639_1 }
            activity?.showDialog(languages.map { it.languageName },
                lang639_1.indexOf(currentLanguageTwoLetters),
                view?.context?.getString(R.string.subs_subtitle_languages)
                    ?: return@setOnClickListener,
                true,
                { }) { index ->
                currentLanguageTwoLetters = lang639_1[index]
                binding.subtitlesSearch.setQuery(binding.subtitlesSearch.query, true)
            }
        }

        binding.applyBtt.setOnClickListener {
            currentSubtitle?.let { currentSubtitle ->
                providers.firstOrNull { it.idPrefix == currentSubtitle.idPrefix }?.let { api ->
                    ioSafe {
                        val subtitles =
                            api.getResource(currentSubtitle).getSubtitles().map { resource ->
                                SubtitleData(
                                    name = resource.name ?: getName(currentSubtitle, true),
                                    url = resource.url,
                                    origin = resource.origin,
                                    mimeType = resource.url.toSubtitleMimeType(),
                                    headers = currentSubtitle.headers,
                                    currentSubtitle.lang
                                )
                            }
                        if (subtitles.isNotEmpty()) {
                            runOnMainThread {
                                addAndSelectSubtitles(*subtitles.toTypedArray())
                            }
                        }
                    }
                }
            }
            dialog.dismissSafe()
        }

        dialog.setOnDismissListener {
            dismissCallback.invoke()
        }

        dialog.show()
        binding.subtitlesSearch.setQuery(currentTempMeta.name, true)
        //TODO: Set year text from currently loaded movie on Player
        //dialog.subtitles_search_year?.setText(currentTempMeta.year)
    }

    @OptIn(UnstableApi::class)
    private fun openSubPicker() {
        try {
            subsPathPicker.launch(
                arrayOf(
                    "text/plain",
                    "text/str",
                    "application/octet-stream",
                    MimeTypes.TEXT_UNKNOWN,
                    MimeTypes.TEXT_VTT,
                    MimeTypes.TEXT_SSA,
                    MimeTypes.APPLICATION_TTML,
                    MimeTypes.APPLICATION_MP4VTT,
                    MimeTypes.APPLICATION_SUBRIP,
                )
            )
        } catch (e: Exception) {
            logError(e)
        }
    }

    private fun addAndSelectSubtitles(
        vararg subtitleData: SubtitleData
    ) {
        if (subtitleData.isEmpty()) return
        val selectedSubtitle = subtitleData.first()
        val ctx = context ?: return

        val subs = currentSubs + subtitleData

        // this is used instead of observe(viewModel._currentSubs), because observe is too slow
        player.setActiveSubtitles(subs)

        // Save current time as to not reset player to 00:00
        player.saveData()
        player.reloadPlayer(ctx)

        setSubtitles(selectedSubtitle)
        viewModel.addSubtitles(subtitleData.toSet())

        selectSourceDialog?.dismissSafe()

        showToast(
            String.format(ctx.getString(R.string.player_loaded_subtitles), selectedSubtitle.name),
            Toast.LENGTH_LONG
        )
    }

    // Open file picker
    private val subsPathPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            normalSafeApiCall {
                // It lies, it can be null if file manager quits.
                if (uri == null) return@normalSafeApiCall
                val ctx = context ?: AcraApplication.context ?: return@normalSafeApiCall
                // RW perms for the path
                ctx.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )

                val file = SafeFile.fromUri(ctx, uri)
                val fileName = file?.name()
                println("Loaded subtitle file. Selected URI path: $uri - Name: $fileName")
                // DO NOT REMOVE THE FILE EXTENSION FROM NAME, IT'S NEEDED FOR MIME TYPES
                val name = fileName ?: uri.toString()

                val subtitleData = SubtitleData(
                    name,
                    uri.toString(),
                    SubtitleOrigin.DOWNLOADED_FILE,
                    name.toSubtitleMimeType(),
                    emptyMap(),
                    null
                )

                addAndSelectSubtitles(subtitleData)
            }
        }

    var selectSourceDialog: Dialog? = null
//    var selectTracksDialog: AlertDialog? = null

    override fun showMirrorsDialogue() {
        try {
            currentSelectedSubtitles = player.getCurrentPreferredSubtitle()
            //println("CURRENT SELECTED :$currentSelectedSubtitles of $currentSubs")
            context?.let { ctx ->
                val isPlaying = player.getIsPlaying()
                player.handleEvent(CSPlayerEvent.Pause, PlayerEventSource.UI)
                val currentSubtitles = sortSubs(currentSubs)

                val sourceDialog = Dialog(ctx, R.style.AlertDialogCustomBlack)
                val binding =
                    PlayerSelectSourceAndSubsBinding.inflate(LayoutInflater.from(ctx), null, false)
                sourceDialog.setContentView(binding.root)

                selectSourceDialog = sourceDialog

                sourceDialog.show()
                val providerList = binding.sortProviders
                val subtitleList = binding.sortSubtitles

                val loadFromFileFooter: TextView =
                    layoutInflater.inflate(R.layout.sort_bottom_footer_add_choice, null) as TextView

                loadFromFileFooter.text = ctx.getString(R.string.player_load_subtitles)
                loadFromFileFooter.setOnClickListener {
                    openSubPicker()
                }
                subtitleList.addFooterView(loadFromFileFooter)

                var shouldDismiss = true

                binding.subtitleSettingsBtt.setOnClickListener {
                    normalSafeApiCall {
                        SubtitlesFragment().show(this.parentFragmentManager, "SubtitleSettings")
                    }
                }

                fun dismiss() {
                    if (isPlaying) {
                        player.handleEvent(CSPlayerEvent.Play)
                    }
                    activity?.hideSystemUI()
                }

                if (subsProvidersIsActive) {
                    val currentLoadResponse = viewModel.getLoadResponse()

                    val loadFromOpenSubsFooter: TextView = layoutInflater.inflate(
                        R.layout.sort_bottom_footer_add_choice, null
                    ) as TextView

                    loadFromOpenSubsFooter.text =
                        ctx.getString(R.string.player_load_subtitles_online)

                    loadFromOpenSubsFooter.setOnClickListener {
                        shouldDismiss = false
                        sourceDialog.dismissSafe(activity)
                        openOnlineSubPicker(it.context, currentLoadResponse) {
                            dismiss()
                        }
                    }
                    subtitleList.addFooterView(loadFromOpenSubsFooter)
                }

                var sourceIndex = 0
                var startSource = 0
                var sortedUrls = emptyList<Pair<ExtractorLink?, ExtractorUri?>>()

                fun refreshLinks(qualityProfile: Int) {
                    sortedUrls = sortLinks(qualityProfile)
                    if (sortedUrls.isEmpty()) {
                        sourceDialog.findViewById<LinearLayout>(R.id.sort_sources_holder)?.isGone =
                            true
                    } else {
                        startSource = sortedUrls.indexOf(currentSelectedLink)
                        sourceIndex = startSource

                        val sourcesArrayAdapter =
                            ArrayAdapter<String>(ctx, R.layout.sort_bottom_single_choice)

                        sourcesArrayAdapter.addAll(sortedUrls.map { (link, uri) ->
                            val name = link?.name ?: uri?.name ?: "NULL"
                            "$name ${Qualities.getStringByInt(link?.quality)}"
                        })

                        providerList.choiceMode = AbsListView.CHOICE_MODE_SINGLE
                        providerList.adapter = sourcesArrayAdapter
                        providerList.setSelection(sourceIndex)
                        providerList.setItemChecked(sourceIndex, true)

                        providerList.setOnItemClickListener { _, _, which, _ ->
                            sourceIndex = which
                            providerList.setItemChecked(which, true)
                        }
                    }
                }

                refreshLinks(currentQualityProfile)

                sourceDialog.setOnDismissListener {
                    if (shouldDismiss) dismiss()
                    selectSourceDialog = null
                }

                val subtitleIndexStart = currentSubtitles.indexOf(currentSelectedSubtitles) + 1
                var subtitleIndex = subtitleIndexStart

                val subsArrayAdapter = ArrayAdapter<Spanned>(ctx, R.layout.sort_bottom_single_choice)
                subsArrayAdapter.add(ctx.getString(R.string.no_subtitles).html())
                subsArrayAdapter.addAll(currentSubtitles.map { it.name.html() })

                subtitleList.adapter = subsArrayAdapter
                subtitleList.choiceMode = AbsListView.CHOICE_MODE_SINGLE

                subtitleList.setSelection(subtitleIndex)
                subtitleList.setItemChecked(subtitleIndex, true)

                subtitleList.setOnItemClickListener { _, _, which, _ ->
                    if (which > currentSubtitles.size) {
                        // Since android TV is funky the setOnItemClickListener will be triggered
                        // instead of setOnClickListener when selecting. To override this we programmatically
                        // click the view when selecting an item outside the list.

                        // Cheeky way of getting the view at that position to click it
                        // to avoid keeping track of the various footers.
                        // getChildAt() gives null :(
                        val child = subtitleList.adapter.getView(which, null, subtitleList)
                        child?.performClick()
                    } else {
                        subtitleIndex = which
                        subtitleList.setItemChecked(which, true)
                    }
                }

                binding.cancelBtt.setOnClickListener {
                    sourceDialog.dismissSafe(activity)
                }

                fun setProfileName(profile: Int) {
                    binding.sourceSettingsBtt.setText(
                        QualityDataHelper.getProfileName(
                            profile
                        )
                    )
                }
                setProfileName(currentQualityProfile)

                binding.profilesClickSettings.setOnClickListener {
                    val activity = activity ?: return@setOnClickListener
                    QualityProfileDialog(
                        activity,
                        R.style.AlertDialogCustomBlack,
                        currentLinks.mapNotNull { it.first },
                        currentQualityProfile
                    ) { profile ->
                        currentQualityProfile = profile.id
                        setProfileName(profile.id)
                        refreshLinks(profile.id)
                    }.show()
                }

                binding.subtitlesEncodingFormat.apply {
                    val settingsManager = PreferenceManager.getDefaultSharedPreferences(ctx)

                    val prefNames = ctx.resources.getStringArray(R.array.subtitles_encoding_list)
                    val prefValues = ctx.resources.getStringArray(R.array.subtitles_encoding_values)

                    val value = settingsManager.getString(
                        ctx.getString(R.string.subtitles_encoding_key), null
                    )
                    val index = prefValues.indexOf(value)
                    text = prefNames[if (index == -1) 0 else index]
                }

                binding.subtitlesEncodingFormat.setOnClickListener {
                    val settingsManager = PreferenceManager.getDefaultSharedPreferences(ctx)

                    val prefNames = ctx.resources.getStringArray(R.array.subtitles_encoding_list)
                    val prefValues = ctx.resources.getStringArray(R.array.subtitles_encoding_values)

                    val currentPrefMedia = settingsManager.getString(
                        ctx.getString(R.string.subtitles_encoding_key), null
                    )

                    shouldDismiss = false
                    sourceDialog.dismissSafe(activity)

                    val index = prefValues.indexOf(currentPrefMedia)
                    activity?.showDialog(prefNames.toList(),
                        if (index == -1) 0 else index,
                        ctx.getString(R.string.subtitles_encoding),
                        true,
                        {}) {
                        settingsManager.edit().putString(
                            ctx.getString(R.string.subtitles_encoding_key), prefValues[it]
                        ).apply()
                        updateForcedEncoding(ctx)
                        dismiss()
                        player.seekTime(-1) // to update subtitles, a dirty trick
                    }
                }

                binding.applyBtt.setOnClickListener {
                    var init = false
                    if (sourceIndex != startSource) {
                        init = true
                    }
                    if (subtitleIndex != subtitleIndexStart) {
                        init = init || if (subtitleIndex <= 0) {
                            noSubtitles()
                        } else {
                            currentSubtitles.getOrNull(subtitleIndex - 1)?.let {
                                setSubtitles(it)
                            } ?: false
                        }
                    }
                    if (init) {
                        sortedUrls.getOrNull(sourceIndex)?.let {
                            loadLink(it, true)
                        }
                    }
                    sourceDialog.dismissSafe(activity)
                }
            }
        } catch (e: Exception) {
            logError(e)
        }
    }

    override fun showTracksDialogue() {
        try {
            //println("CURRENT SELECTED :$currentSelectedSubtitles of $currentSubs")
            context?.let { ctx ->
                val tracks = player.getVideoTracks()

                val isPlaying = player.getIsPlaying()
                player.handleEvent(CSPlayerEvent.Pause)

                val currentVideoTracks = tracks.allVideoTracks.sortedBy {
                    it.height?.times(-1)
                }
                val currentAudioTracks = tracks.allAudioTracks
                val binding: PlayerSelectTracksBinding =
                    PlayerSelectTracksBinding.inflate(LayoutInflater.from(ctx), null, false)
                val trackDialog = Dialog(ctx, R.style.AlertDialogCustomBlack)
                trackDialog.setContentView(binding.root)
                trackDialog.show()

//                selectTracksDialog = tracksDialog

                val videosList = binding.videoTracksList
                val audioList = binding.autoTracksList

                binding.videoTracksHolder.isVisible = currentVideoTracks.size > 1
                binding.audioTracksHolder.isVisible = currentAudioTracks.size > 1

                fun dismiss() {
                    if (isPlaying) {
                        player.handleEvent(CSPlayerEvent.Play)
                    }
                    activity?.hideSystemUI()
                }

                val videosArrayAdapter =
                    ArrayAdapter<String>(ctx, R.layout.sort_bottom_single_choice)

                videosArrayAdapter.addAll(currentVideoTracks.mapIndexed { index, format ->
                    format.label
                        ?: (if (format.height == NO_VALUE || format.width == NO_VALUE) index else "${format.width}x${format.height}").toString()
                })

                videosList.choiceMode = AbsListView.CHOICE_MODE_SINGLE
                videosList.adapter = videosArrayAdapter

                // Sometimes the data is not the same because some data gets resolved at different stages i think
                var videoIndex = currentVideoTracks.indexOf(tracks.currentVideoTrack).takeIf {
                    it != -1
                } ?: currentVideoTracks.indexOfFirst {
                    tracks.currentVideoTrack?.id == it.id
                }

                videosList.setSelection(videoIndex)
                videosList.setItemChecked(videoIndex, true)

                videosList.setOnItemClickListener { _, _, which, _ ->
                    videoIndex = which
                    videosList.setItemChecked(which, true)
                }

                trackDialog.setOnDismissListener {
                    dismiss()
//                    selectTracksDialog = null
                }

                var audioIndexStart = currentAudioTracks.indexOf(tracks.currentAudioTrack).takeIf {
                    it != -1
                } ?: currentVideoTracks.indexOfFirst {
                    tracks.currentAudioTrack?.id == it.id
                }

                val audioArrayAdapter =
                    ArrayAdapter<String>(ctx, R.layout.sort_bottom_single_choice)
//                audioArrayAdapter.add(ctx.getString(R.string.no_subtitles))
                audioArrayAdapter.addAll(currentAudioTracks.mapIndexed { index, format ->
                    format.label ?: format.language?.let { fromTwoLettersToLanguage(it) }
                    ?: index.toString()
                })

                audioList.adapter = audioArrayAdapter
                audioList.choiceMode = AbsListView.CHOICE_MODE_SINGLE

                audioList.setSelection(audioIndexStart)
                audioList.setItemChecked(audioIndexStart, true)

                audioList.setOnItemClickListener { _, _, which, _ ->
                    audioIndexStart = which
                    audioList.setItemChecked(which, true)
                }

                binding.cancelBtt.setOnClickListener {
                    trackDialog.dismissSafe(activity)
                }

                binding.applyBtt.setOnClickListener {
                    val currentTrack = currentAudioTracks.getOrNull(audioIndexStart)
                    player.setPreferredAudioTrack(
                        currentTrack?.language, currentTrack?.id
                    )

                    val currentVideo = currentVideoTracks.getOrNull(videoIndex)
                    val width = currentVideo?.width ?: NO_VALUE
                    val height = currentVideo?.height ?: NO_VALUE
                    if (width != NO_VALUE && height != NO_VALUE) {
                        player.setMaxVideoSize(width, height, currentVideo?.id)
                    }

                    trackDialog.dismissSafe(activity)
                }
            }
        } catch (e: Exception) {
            logError(e)
        }
    }


    override fun playerError(exception: Throwable) {
        Log.i(TAG, "playerError = $currentSelectedLink")
        if (!hasNextMirror()) {
            viewModel.forceClearCache = true
        }
        super.playerError(exception)
    }

    private fun noLinksFound() {
        viewModel.forceClearCache = true

        showToast(R.string.no_links_found_toast, Toast.LENGTH_SHORT)
        activity?.popCurrentPage()
    }

    private fun startPlayer() {
        if (isActive) return // we don't want double load when you skip loading

        val links = sortLinks(currentQualityProfile)
        if (links.isEmpty()) {
            noLinksFound()
            return
        }
        loadLink(links.first(), false)
    }

    override fun nextEpisode() {
        isNextEpisode = true
        player.release()
        viewModel.loadLinksNext()
    }

    override fun prevEpisode() {
        isNextEpisode = true
        player.release()
        viewModel.loadLinksPrev()
    }

    override fun hasNextMirror(): Boolean {
        val links = sortLinks(currentQualityProfile)
        return links.isNotEmpty() && links.indexOf(currentSelectedLink) + 1 < links.size
    }

    override fun nextMirror() {
        val links = sortLinks(currentQualityProfile)
        if (links.isEmpty()) {
            noLinksFound()
            return
        }

        val newIndex = links.indexOf(currentSelectedLink) + 1
        if (newIndex >= links.size) {
            noLinksFound()
            return
        }

        loadLink(links[newIndex], true)
    }

    override fun onDestroy() {
        ResultFragment.updateUI()
        currentVerifyLink?.cancel()
        super.onDestroy()
    }

    var maxEpisodeSet: Int? = null
    var hasRequestedStamps: Boolean = false
    override fun playerPositionChanged(position: Long, duration: Long) {
        // Don't save livestream data
        if ((currentMeta as? ResultEpisode)?.tvType?.isLiveStream() == true) return

        // Don't save NSFW data
        if ((currentMeta as? ResultEpisode)?.tvType == TvType.NSFW) return

        if (duration <= 0L) return // idk how you achieved this, but div by zero crash
        if (!hasRequestedStamps) {
            hasRequestedStamps = true
            val fetchStamps = context?.let { ctx ->
                val settingsManager = PreferenceManager.getDefaultSharedPreferences(ctx)
                settingsManager.getBoolean(
                    ctx.getString(R.string.enable_skip_op_from_database),
                    true
                )
            } ?: true
            if (fetchStamps)
                viewModel.loadStamps(duration)
        }

        viewModel.getId()?.let {
            DataStoreHelper.setViewPos(it, position, duration)
        }

        val percentage = position * 100L / duration

        val nextEp = percentage >= NEXT_WATCH_EPISODE_PERCENTAGE
        val resumeMeta = if (nextEp) nextMeta else currentMeta
        if (resumeMeta == null && nextEp) {
            // remove last watched as it is the last episode and you have watched too much
            when (val newMeta = currentMeta) {
                is ResultEpisode -> {
                    DataStoreHelper.removeLastWatched(newMeta.parentId)
                }

                is ExtractorUri -> {
                    DataStoreHelper.removeLastWatched(newMeta.parentId)
                }
            }
        } else {
            // save resume
            when (resumeMeta) {
                is ResultEpisode -> {
                    DataStoreHelper.setLastWatched(
                        resumeMeta.parentId,
                        resumeMeta.id,
                        resumeMeta.episode,
                        resumeMeta.season,
                        isFromDownload = false
                    )
                }

                is ExtractorUri -> {
                    DataStoreHelper.setLastWatched(
                        resumeMeta.parentId,
                        resumeMeta.id,
                        resumeMeta.episode,
                        resumeMeta.season,
                        isFromDownload = true
                    )
                }
            }
        }

        var isOpVisible = false
        when (val meta = currentMeta) {
            is ResultEpisode -> {
                if (percentage >= UPDATE_SYNC_PROGRESS_PERCENTAGE && (maxEpisodeSet
                        ?: -1) < meta.episode
                ) {
                    context?.let { ctx ->
                        val settingsManager = PreferenceManager.getDefaultSharedPreferences(ctx)
                        if (settingsManager.getBoolean(
                                ctx.getString(R.string.episode_sync_enabled_key), true
                            )
                        ) maxEpisodeSet = meta.episode
                        sync.modifyMaxEpisode(meta.totalEpisodeIndex ?: meta.episode)
                    }
                }

                if (meta.tvType.isAnimeOp()) isOpVisible = percentage < SKIP_OP_VIDEO_PERCENTAGE
            }
        }

        playerBinding?.playerSkipOp?.isVisible = isOpVisible

        when {
            isLayout(PHONE) ->
                playerBinding?.playerSkipEpisode?.isVisible =
                    !isOpVisible && viewModel.hasNextEpisode() == true

            else ->
                playerBinding?.playerGoForwardRoot?.isVisible = viewModel.hasNextEpisode() == true
        }

        if (percentage >= PRELOAD_NEXT_EPISODE_PERCENTAGE) {
            viewModel.preLoadNextLinks()
        }
    }

    private fun getAutoSelectSubtitle(
        subtitles: Set<SubtitleData>, settings: Boolean, downloads: Boolean
    ): SubtitleData? {
        val langCode = preferredAutoSelectSubtitles ?: return null
        val lang = fromTwoLettersToLanguage(langCode) ?: return null
        if (downloads) {
            return subtitles.firstOrNull { sub ->
                (sub.origin == SubtitleOrigin.DOWNLOADED_FILE && sub.name == context?.getString(
                    R.string.default_subtitles
                ))
            }
        }

        sortSubs(subtitles).firstOrNull { sub ->
            val t = sub.name.replace(Regex("[^A-Za-z]"), " ").trim()
            (settings) && t == lang || t.startsWith(lang) || t == langCode
        }?.let { sub ->
            return sub
        }

        return null
    }

    private fun autoSelectFromSettings(): Boolean {
        // auto select subtitle based of settings
        val langCode = preferredAutoSelectSubtitles
        val current = player.getCurrentPreferredSubtitle()
        Log.i(TAG, "autoSelectFromSettings = $current")
        context?.let { ctx ->
            if (current != null) {
                if (setSubtitles(current)) {
                    player.saveData()
                    player.reloadPlayer(ctx)
                    player.handleEvent(CSPlayerEvent.Play)
                    return true
                }
            } else if (!langCode.isNullOrEmpty()) {
                getAutoSelectSubtitle(
                    currentSubs, settings = true, downloads = false
                )?.let { sub ->
                    if (setSubtitles(sub)) {
                        player.saveData()
                        player.reloadPlayer(ctx)
                        player.handleEvent(CSPlayerEvent.Play)
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun autoSelectFromDownloads(): Boolean {
        if (player.getCurrentPreferredSubtitle() == null) {
            getAutoSelectSubtitle(currentSubs, settings = false, downloads = true)?.let { sub ->
                context?.let { ctx ->
                    if (setSubtitles(sub)) {
                        player.saveData()
                        player.reloadPlayer(ctx)
                        player.handleEvent(CSPlayerEvent.Play)
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun autoSelectSubtitles() {
        //Log.i(TAG, "autoSelectSubtitles")
        normalSafeApiCall {
            if (!autoSelectFromSettings()) {
                autoSelectFromDownloads()
            }
        }
    }

    private fun getPlayerVideoTitle(): String {
        var headerName: String? = null
        var subName: String? = null
        var episode: Int? = null
        var season: Int? = null
        var tvType: TvType? = null

        when (val meta = currentMeta) {
            is ResultEpisode -> {
                headerName = meta.headerName
                subName = meta.name
                episode = meta.episode
                season = meta.season
                tvType = meta.tvType
            }

            is ExtractorUri -> {
                headerName = meta.headerName
                subName = meta.name
                episode = meta.episode
                season = meta.season
                tvType = meta.tvType
            }
        }
        context?.let { ctx ->
            //Generate video title
            val playerVideoTitle = if (headerName != null) {
                (headerName + if (tvType.isEpisodeBased() && episode != null) if (season == null) " - ${
                    ctx.getString(
                        R.string.episode
                    )
                } $episode"
                else " \"${ctx.getString(R.string.season_short)}${season}:${
                    ctx.getString(
                        R.string.episode_short
                    )
                }${episode}\""
                else "") + if (subName.isNullOrBlank() || subName == headerName) "" else " - $subName"
            } else {
                ""
            }
            return playerVideoTitle
        }
        return ""
    }


    @SuppressLint("SetTextI18n")
    fun setTitle() {
        var playerVideoTitle = getPlayerVideoTitle()

        //Hide title, if set in setting
        if (limitTitle < 0) {
            playerBinding?.playerVideoTitle?.visibility = View.GONE
        } else {
            //Truncate video title if it exceeds limit
            val differenceInLength = playerVideoTitle.length - limitTitle
            val margin = 3 //If the difference is smaller than or equal to this value, ignore it
            if (limitTitle > 0 && differenceInLength > margin) {
                playerVideoTitle = playerVideoTitle.substring(0, limitTitle - 1) + "..."
            }
        }
        val isFiller: Boolean? = (currentMeta as? ResultEpisode)?.isFiller

        playerBinding?.playerEpisodeFillerHolder?.isVisible = isFiller ?: false
        playerBinding?.playerVideoTitle?.text = playerVideoTitle
    }

    @SuppressLint("SetTextI18n")
    fun setPlayerDimen(widthHeight: Pair<Int, Int>?) {
        val extra = if (widthHeight != null) {
            val (width, height) = widthHeight
            "- ${width}x${height}"
        } else {
            ""
        }

        val source = currentSelectedLink?.first?.name ?: currentSelectedLink?.second?.name ?: "NULL"

        val title = when (titleRez) {
            0 -> ""
            1 -> extra
            2 -> source
            3 -> "$source $extra"
            else -> ""
        }
        playerBinding?.playerVideoTitleRez?.apply {
            text = title
            isVisible = title.isNotBlank()
        }
    }

    override fun playerDimensionsLoaded(width: Int, height: Int) {
        super.playerDimensionsLoaded(width, height)
        setPlayerDimen(width to height)
    }

    private fun unwrapBundle(savedInstanceState: Bundle?) {
        Log.i(TAG, "unwrapBundle = $savedInstanceState")
        savedInstanceState?.let { bundle ->
            sync.addSyncs(bundle.getSafeSerializable<HashMap<String, String>>("syncData"))
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        // this is used instead of layout-television to follow the settings and some TV devices are not classified as TV for some reason
        layout =
            if (isLayout(TV or EMULATOR)) R.layout.fragment_player_tv else R.layout.fragment_player

        viewModel = ViewModelProvider(this)[PlayerGeneratorViewModel::class.java]
        sync = ViewModelProvider(this)[SyncViewModel::class.java]

        viewModel.attachGenerator(lastUsedGenerator)
        unwrapBundle(savedInstanceState)
        unwrapBundle(arguments)

        val root = super.onCreateView(inflater, container, savedInstanceState) ?: return null
        binding = FragmentPlayerBinding.bind(root)
        return root
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    var timestampShowState = false

    var skipAnimator: ValueAnimator? = null
    var skipIndex = 0

    private fun displayTimeStamp(show: Boolean) {
        if (timestampShowState == show) return
        skipIndex++
        timestampShowState = show
        playerBinding?.skipChapterButton?.apply {
            val showWidth = 170.toPx
            val noShowWidth = 10.toPx
            //if((show && width == showWidth) || (!show && width == noShowWidth)) {
            //    return
            //}
            val to = if (show) showWidth else noShowWidth
            val from = if (!show) showWidth else noShowWidth

            skipAnimator?.cancel()
            isVisible = true

            // just in case
            val lay = layoutParams
            lay.width = from
            layoutParams = lay
            skipAnimator = ValueAnimator.ofInt(
                from, to
            ).apply {
                addListener(onEnd = {
                    if (show) {
                        if (!isShowing) {
                            // Automatically request focus if the menu is not opened
                            playerBinding?.skipChapterButton?.requestFocus()
                        }
                    } else {
                        playerBinding?.skipChapterButton?.isVisible = false
                        if (!isShowing) {
                            // Automatically return focus to play pause
                            playerBinding?.playerPausePlay?.requestFocus()
                        }
                    }
                })
                addUpdateListener { valueAnimator ->
                    val value = valueAnimator.animatedValue as Int
                    val layoutParams: ViewGroup.LayoutParams = layoutParams
                    layoutParams.width = value
                    setLayoutParams(layoutParams)
                }
                duration = 500
                start()
            }
        }
    }

    override fun onTimestampSkipped(timestamp: EpisodeSkip.SkipStamp) {
        displayTimeStamp(false)
    }

    override fun onTimestamp(timestamp: EpisodeSkip.SkipStamp?) {
        if (timestamp != null) {
            playerBinding?.skipChapterButton?.setText(timestamp.uiText)
            displayTimeStamp(true)
            val currentIndex = skipIndex
            playerBinding?.skipChapterButton?.handler?.postDelayed({
                if (skipIndex == currentIndex)
                    displayTimeStamp(false)
            }, 6000)
        } else {
            displayTimeStamp(false)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        var langFilterList = listOf<String>()
        var filterSubByLang = false

        context?.let { ctx ->
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(ctx)
            titleRez = settingsManager.getInt(ctx.getString(R.string.prefer_limit_title_rez_key), 3)
            limitTitle = settingsManager.getInt(ctx.getString(R.string.prefer_limit_title_key), 0)
            updateForcedEncoding(ctx)

            filterSubByLang =
                settingsManager.getBoolean(getString(R.string.filter_sub_lang_key), false)
            if (filterSubByLang) {
                val langFromPrefMedia = settingsManager.getStringSet(
                    this.getString(R.string.provider_lang_key), mutableSetOf("en")
                )
                langFilterList = langFromPrefMedia?.mapNotNull {
                    fromTwoLettersToLanguage(it)?.lowercase() ?: return@mapNotNull null
                } ?: listOf()
            }
        }

        unwrapBundle(savedInstanceState)
        unwrapBundle(arguments)

        sync.updateUserData()

        preferredAutoSelectSubtitles = getAutoSelectLanguageISO639_1()

        if (currentSelectedLink == null) {
            viewModel.loadLinks()
        }

        binding?.overlayLoadingSkipButton?.setOnClickListener {
            startPlayer()
        }

        binding?.playerLoadingGoBack?.setOnClickListener {
            exitFullscreen()
            player.release()
            activity?.popCurrentPage()
        }

        playerBinding?.downloadHeader?.setOnClickListener {
            it?.isVisible = false
        }

        playerBinding?.downloadHeaderToggle?.setOnClickListener {
            playerBinding?.downloadHeader?.let {
                it.isVisible = !it.isVisible
            }
        }

        observe(viewModel.currentStamps) { stamps ->
            player.addTimeStamps(stamps)
        }

        observe(viewModel.loadingLinks) {
            when (it) {
                is Resource.Loading -> {
                    startLoading()
                }

                is Resource.Success -> {
                    // provider returned false
                    //if (it.value != true) {
                    //    showToast(activity, R.string.unexpected_error, Toast.LENGTH_SHORT)
                    //}
                    startPlayer()
                }

                is Resource.Failure -> {
                    showToast(it.errorString, Toast.LENGTH_LONG)
                    startPlayer()
                }
            }
        }

        observe(viewModel.currentLinks) {
            currentLinks = it
            val turnVisible = it.isNotEmpty() && lastUsedGenerator?.canSkipLoading == true
            val wasGone = binding?.overlayLoadingSkipButton?.isGone == true
            binding?.overlayLoadingSkipButton?.isVisible = turnVisible

            normalSafeApiCall {
                if (currentLinks.any { link ->
                        getLinkPriority(currentQualityProfile, link) >=
                                QualityDataHelper.AUTO_SKIP_PRIORITY
                    }
                ) {
                    startPlayer()
                }
            }

            if (turnVisible && wasGone) {
                binding?.overlayLoadingSkipButton?.requestFocus()
            }
        }

        observe(viewModel.currentSubs) { set ->
            val setOfSub = mutableSetOf<SubtitleData>()
            if (langFilterList.isNotEmpty() && filterSubByLang) {
                Log.i("subfilter", "Filtering subtitle")
                langFilterList.forEach { lang ->
                    Log.i("subfilter", "Lang: $lang")
                    setOfSub += set.filter {
                        it.name.contains(lang, ignoreCase = true) ||
                                it.origin != SubtitleOrigin.URL
                    }
                }
                currentSubs = setOfSub
            } else {
                currentSubs = set
            }
            player.setActiveSubtitles(set)

            // If the file is downloaded then do not select auto select the subtitles
            // Downloaded subtitles cannot be selected immediately after loading since
            // player.getCurrentPreferredSubtitle() cannot fetch data from non-loaded subtitles
            // Resulting in unselecting the downloaded subtitle
            if (set.lastOrNull()?.origin != SubtitleOrigin.DOWNLOADED_FILE) {
                autoSelectSubtitles()
            }
        }
    }
}

@Suppress("DEPRECATION")
inline fun <reified T : Serializable> Bundle.getSafeSerializable(key: String): T? =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) getSerializable(key) as? T else getSerializable(
        key,
        T::class.java
    )