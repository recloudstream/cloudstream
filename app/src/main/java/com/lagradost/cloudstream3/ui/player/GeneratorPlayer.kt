package com.lagradost.cloudstream3.ui.player

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.button.MaterialButton
import com.hippo.unifile.UniFile
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.player.PlayerSubtitleHelper.Companion.toSubtitleMimeType
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorUri
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.hideSystemUI
import com.lagradost.cloudstream3.utils.UIHelper.popCurrentPage
import kotlinx.android.synthetic.main.fragment_player.*
import kotlinx.android.synthetic.main.player_custom_layout.*

// TODO Auto select subtitles
class GeneratorPlayer : FullScreenPlayer() {
    companion object {
        private var lastUsedGenerator: IGenerator? = null
        fun newInstance(generator: IGenerator): Bundle {
            lastUsedGenerator = generator
            return Bundle()
        }
    }

    private lateinit var viewModel: PlayerGeneratorViewModel //by activityViewModels()
    private var currentLinks: Set<Pair<ExtractorLink?, ExtractorUri?>> = setOf()
    private var currentSubs: Set<SubtitleData> = setOf()

    private var currentSelectedLink: Pair<ExtractorLink?, ExtractorUri?>? = null
    private var currentSelectedSubtitles: SubtitleData? = null
    private var currentMeta: Any? = null
    private var nextMeta: Any? = null
    private var isActive: Boolean = false
    private var isNextEpisode: Boolean = false // this is used to reset the watch time

    private fun startLoading() {
        player.release()
        currentSelectedSubtitles = null
        isActive = false
        overlay_loading_skip_button?.isVisible = false
        player_loading_overlay?.isVisible = true
    }

    private fun setSubtitles(sub: SubtitleData?): Boolean {
        currentSelectedSubtitles = sub
        return player.setPreferredSubtitles(sub)
    }

    private fun noSubtitles(): Boolean {
        return setSubtitles(null)
    }

    private fun loadLink(link: Pair<ExtractorLink?, ExtractorUri?>?, sameEpisode: Boolean) {
        if (link == null) return

        // manage UI
        player_loading_overlay?.isVisible = false
        uiReset()
        currentSelectedLink = link
        currentMeta = viewModel.getMeta()
        nextMeta = viewModel.getNextMeta()
        isActive = true
        setPlayerDimen(null)
        setTitle()

        // load player
        context?.let { ctx ->
            val (url, uri) = link
            player.loadPlayer(
                ctx,
                sameEpisode,
                url,
                uri,
                startPosition = if (sameEpisode) null else {
                    if (isNextEpisode) 0L else (DataStoreHelper.getViewPos(viewModel.getId())?.position
                        ?: 0L)
                },
                currentSubs,
            )
        }
    }

    private fun sortLinks(useQualitySettings: Boolean = true): List<Pair<ExtractorLink?, ExtractorUri?>> {
        return currentLinks.sortedBy {
            val (linkData, _) = it
            var quality = linkData?.quality ?: Qualities.Unknown.value

            // we set all qualities above current max as max -1
            if (useQualitySettings && quality > currentPrefQuality) {
                quality = currentPrefQuality - 1
            }
            // negative because we want to sort highest quality first
            -(quality)
        }
    }

    private fun openSubPicker() {
        subsPathPicker.launch(
            arrayOf(
                "text/vtt",
                "application/x-subrip",
                "text/plain",
                "text/str",
                "application/octet-stream"
            )
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
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                ctx.contentResolver.takePersistableUriPermission(uri, flags)

                val file = UniFile.fromUri(ctx, uri)
                println("Loaded subtitle file. Selected URI path: $uri - Name: ${file.name}")
                // DO NOT REMOVE THE FILE EXTENSION FROM NAME, IT'S NEEDED FOR MIME TYPES
                val name = file.name ?: uri.toString()

                val subtitleData = SubtitleData(
                    name,
                    uri.toString(),
                    SubtitleOrigin.DOWNLOADED_FILE,
                    name.toSubtitleMimeType()
                )

                setSubtitles(subtitleData)

                // this is used instead of observe, because observe is too slow
                val subs = currentSubs.toMutableSet()
                subs.add(subtitleData)
                player.setActiveSubtitles(subs)
                player.reloadPlayer(ctx)

                viewModel.addSubtitles(setOf(subtitleData))

                selectSourceDialog?.dismissSafe()

                showToast(
                    activity,
                    String.format(ctx.getString(R.string.player_loaded_subtitles), name),
                    Toast.LENGTH_LONG
                )
            }
        }

    var selectSourceDialog: AlertDialog? = null
    override fun showMirrorsDialogue() {
        currentSelectedSubtitles = player.getCurrentPreferredSubtitle()
        context?.let { ctx ->
            val isPlaying = player.getIsPlaying()
            player.handleEvent(CSPlayerEvent.Pause)
            val currentSubtitles = sortSubs(currentSubs)

            val sourceBuilder = AlertDialog.Builder(ctx, R.style.AlertDialogCustomBlack)
                .setView(R.layout.player_select_source_and_subs)

            val sourceDialog = sourceBuilder.create()
            selectSourceDialog = sourceDialog
            sourceDialog.show()
            val providerList =
                sourceDialog.findViewById<ListView>(R.id.sort_providers)!!
            val subtitleList =
                sourceDialog.findViewById<ListView>(R.id.sort_subtitles)!!
            val applyButton =
                sourceDialog.findViewById<MaterialButton>(R.id.apply_btt)!!
            val cancelButton =
                sourceDialog.findViewById<MaterialButton>(R.id.cancel_btt)!!

            val footer: TextView =
                layoutInflater.inflate(R.layout.sort_bottom_footer_add_choice, null) as TextView
            footer.text = ctx.getString(R.string.player_load_subtitles)
            footer.setOnClickListener {
                openSubPicker()
            }
            subtitleList.addFooterView(footer)

            var sourceIndex = 0
            var startSource = 0

            val sortedUrls = sortLinks(useQualitySettings = false)
            if (sortedUrls.isNullOrEmpty()) {
                sourceDialog.findViewById<LinearLayout>(R.id.sort_sources_holder)?.isGone = true
            } else {
                startSource = sortedUrls.indexOf(currentSelectedLink)
                sourceIndex = startSource

                val sourcesArrayAdapter =
                    ArrayAdapter<String>(ctx, R.layout.sort_bottom_single_choice)

                sourcesArrayAdapter.addAll(sortedUrls.map {
                    it.first?.name ?: it.second?.name ?: "NULL"
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

            sourceDialog.setOnDismissListener {
                if (isPlaying) {
                    player.handleEvent(CSPlayerEvent.Play)
                }
                activity?.hideSystemUI()
                selectSourceDialog = null
            }

            val subtitleIndexStart = currentSubtitles.indexOf(currentSelectedSubtitles) + 1
            var subtitleIndex = subtitleIndexStart

            val subsArrayAdapter =
                ArrayAdapter<String>(ctx, R.layout.sort_bottom_single_choice)
            subsArrayAdapter.add(getString(R.string.no_subtitles))
            subsArrayAdapter.addAll(currentSubtitles.map { it.name })

            subtitleList.adapter = subsArrayAdapter
            subtitleList.choiceMode = AbsListView.CHOICE_MODE_SINGLE

            subtitleList.setSelection(subtitleIndex)
            subtitleList.setItemChecked(subtitleIndex, true)

            subtitleList.setOnItemClickListener { _, _, which, _ ->
                subtitleIndex = which
                subtitleList.setItemChecked(which, true)
            }

            cancelButton.setOnClickListener {
                sourceDialog.dismissSafe(activity)
            }

            applyButton.setOnClickListener {
                var init = false
                if (sourceIndex != startSource) {
                    init = true
                }
                if (subtitleIndex != subtitleIndexStart) {
                    init = init || if (subtitleIndex <= 0) {
                        noSubtitles()
                    } else {
                        setSubtitles(currentSubtitles[subtitleIndex - 1])
                    }
                }
                if (init) {
                    loadLink(sortedUrls[sourceIndex], true)
                }
                sourceDialog.dismissSafe(activity)
            }
        }
    }

    private fun noLinksFound() {
        showToast(activity, R.string.no_links_found_toast, Toast.LENGTH_SHORT)
        activity?.popCurrentPage()
    }

    private fun startPlayer() {
        if (isActive) return // we don't want double load when you skip loading

        val links = sortLinks()
        if (links.isEmpty()) {
            noLinksFound()
            return
        }
        loadLink(links.first(), false)
    }

    override fun nextEpisode() {
        isNextEpisode = true
        viewModel.loadLinksNext()
    }

    override fun prevEpisode() {
        isNextEpisode = true
        viewModel.loadLinksPrev()
    }

    override fun nextMirror() {
        val links = sortLinks()
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

    override fun playerPositionChanged(posDur: Pair<Long, Long>) {
        val (position, duration) = posDur
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
                if (meta.tvType.isAnimeOp())
                    isOpVisible = percentage < SKIP_OP_VIDEO_PERCENTAGE
            }
        }
        player_skip_op?.isVisible = isOpVisible
        player_skip_episode?.isVisible = !isOpVisible && viewModel.hasNextEpisode() == true

        if (percentage > PRELOAD_NEXT_EPISODE_PERCENTAGE) {
            viewModel.preLoadNextLinks()
        }
    }

    @SuppressLint("SetTextI18n")
    fun setTitle() {
        var headerName: String? = null
        var episode: Int? = null
        var season: Int? = null
        var tvType: TvType? = null

        when (val meta = currentMeta) {
            is ResultEpisode -> {
                headerName = meta.headerName
                episode = meta.episode
                season = meta.season
                tvType = meta.tvType
            }
            is ExtractorUri -> {
                headerName = meta.headerName
                episode = meta.episode
                season = meta.season
                tvType = meta.tvType
            }
        }

        player_video_title?.text = if (headerName != null) {
            headerName +
                    if (tvType.isEpisodeBased() && episode != null)
                        if (season == null)
                            " - ${getString(R.string.episode)} $episode"
                        else
                            " \"${getString(R.string.season_short)}${season}:${getString(R.string.episode_short)}${episode}\""
                    else ""
        } else {
            ""
        }
    }

    @SuppressLint("SetTextI18n")
    fun setPlayerDimen(widthHeight: Pair<Int, Int>?) {
        val extra = if (widthHeight != null) {
            val (width, height) = widthHeight
            " - ${width}x${height}"
        } else {
            ""
        }
        player_video_title_rez?.text =
            (currentSelectedLink?.first?.name ?: currentSelectedLink?.second?.name
            ?: "NULL") + extra
    }

    override fun playerDimensionsLoaded(widthHeight: Pair<Int, Int>) {
        setPlayerDimen(widthHeight)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        viewModel = ViewModelProvider(this)[PlayerGeneratorViewModel::class.java]
        viewModel.attachGenerator(lastUsedGenerator)
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (currentSelectedLink == null) {
            viewModel.loadLinks()
        }

        overlay_loading_skip_button?.setOnClickListener {
            startPlayer()
        }

        player_loading_go_back?.setOnClickListener {
            player.release()
            activity?.popCurrentPage()
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
                    showToast(activity, it.errorString, Toast.LENGTH_LONG)
                    startPlayer()
                }
            }
        }

        observe(viewModel.currentLinks) {
            currentLinks = it
            overlay_loading_skip_button?.isVisible = it.isNotEmpty()
        }

        observe(viewModel.currentSubs) {
            currentSubs = it
            player.setActiveSubtitles(it)
        }
    }
}