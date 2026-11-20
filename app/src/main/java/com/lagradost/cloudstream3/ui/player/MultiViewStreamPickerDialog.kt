package com.lagradost.cloudstream3.ui.player

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetBehavior
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.atomic.AtomicInteger
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import org.json.JSONObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MultiViewStreamPickerDialog(
    val maxCapacity: Int = 4,
    val onStreamsBatchSelected: (List<Pair<ExtractorLink, String>>) -> Unit
) : BottomSheetDialogFragment() {

    private val selectedItems = mutableSetOf<SearchResponse>()
    private var allStreams = mutableListOf<SearchResponse>()
    private var filteredStreams = mutableListOf<SearchResponse>()
    private var localStreams = mutableListOf<SearchResponse>()
    private val searchRequestId = AtomicInteger(0)
    private val extensionSearchMutex = Mutex()
    private var extensionSearchJob: Job? = null

    // View references promoted to fields so helper methods can access them
    private var slotCounter: TextView? = null
    private var confirmBtn: TextView? = null
    private var gridRecycler: RecyclerView? = null
    private var searchInput: EditText? = null
    private var addUrlBtn: TextView? = null
    private var chipGroup: com.google.android.material.chip.ChipGroup? = null
    private var lastResolutionError: String? = null

    private val categoryMap = mutableMapOf<String, MutableList<SearchResponse>>()
    private var currentCategoryFilter: String? = null

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { sheet ->
            sheet.layoutParams = sheet.layoutParams.apply {
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }
            BottomSheetBehavior.from(sheet).apply {
                skipCollapsed = true
                state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val dialogView = inflater.inflate(R.layout.dialog_kick_multiview_picker, container, false)

        val closeBtn = dialogView.findViewById<ImageView>(R.id.kick_picker_close_btn)
        val cancelBtn = dialogView.findViewById<TextView>(R.id.kick_cancel_btn)
        slotCounter = dialogView.findViewById(R.id.kick_slot_counter)
        confirmBtn = dialogView.findViewById(R.id.kick_confirm_btn)
        gridRecycler = dialogView.findViewById(R.id.kick_streams_grid)
        searchInput = dialogView.findViewById(R.id.kick_search_input)
        addUrlBtn = dialogView.findViewById(R.id.kick_add_url_btn)
        chipGroup = dialogView.findViewById(R.id.picker_category_chip_group)

        updateCounter()
        closeBtn?.setOnClickListener { dismiss() }
        cancelBtn?.setOnClickListener { dismiss() }

        val adapter = setupAdapter()
        populateLocalStreams()
        loadRemoteStreamsAsync()
        setupSearchInput()
        setupAddUrlButton()
        setupConfirmButton()

        return dialogView
    }

    private fun updateCounter() {
        val count = selectedItems.size
        slotCounter?.text = "$count/$maxCapacity"
        confirmBtn?.text = if (count > 0) "Confirm ($count)" else "Confirm"
    }

    private fun applyFilter() {
        val query = searchInput?.text?.toString()?.trim()?.lowercase() ?: ""
        filteredStreams.clear()

        val categoryFiltered = if (currentCategoryFilter.isNullOrBlank() || currentCategoryFilter == "All") {
            allStreams
        } else {
            categoryMap[currentCategoryFilter]
                ?: allStreams.filter { it.apiName.equals(currentCategoryFilter, ignoreCase = true) }
        }

        if (query.isBlank()) {
            filteredStreams.addAll(categoryFiltered.distinctBy { it.url })
        } else {
            filteredStreams.addAll(categoryFiltered.distinctBy { it.url }.filter {
                it.name.lowercase().contains(query) || it.apiName.lowercase().contains(query)
            })
            directStreamFromUrl(searchInput?.text?.toString()?.trim().orEmpty())?.let { directStream ->
                if (filteredStreams.none { it.url == directStream.url }) {
                    filteredStreams.add(0, directStream)
                }
            }
        }
        gridRecycler?.adapter?.notifyDataSetChanged()
    }

    private fun directStreamFromUrl(url: String): SearchResponse? {
        if (!url.startsWith("https://", ignoreCase = true) &&
            !url.startsWith("http://", ignoreCase = true)
        ) return null

        val host = Uri.parse(url).host?.lowercase() ?: return null
        val providerName = when {
            host == "kick.com" || host.endsWith(".kick.com") -> "Kick"
            host == "twitch.tv" || host.endsWith(".twitch.tv") -> "Twitch"
            host == "youtube.com" || host.endsWith(".youtube.com") ||
                host == "youtu.be" || host.endsWith(".youtu.be") -> "YouTube"
            else -> return null
        }

        val title = Uri.parse(url).pathSegments?.lastOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { segment ->
                try { java.net.URLDecoder.decode(segment, "UTF-8") } catch (_: Throwable) { segment }
            } ?: providerName

        @Suppress("DEPRECATION_ERROR")
        return com.lagradost.cloudstream3.LiveSearchResponse(
            name = "$providerName: $title",
            url = url,
            apiName = "Direct stream URL ($providerName)",
            type = com.lagradost.cloudstream3.TvType.Live,
            posterUrl = null,
            id = url.hashCode()
        )
    }

    private fun updateCategoryChips() {
        chipGroup?.removeAllViews()
        val providerNames = APIHolder.apis.map { it.name }
        val categories = mutableListOf("All") + providerNames + categoryMap.keys.toList()
        categories.distinct().forEach { catName ->
            val chip = com.google.android.material.chip.Chip(context).apply {
                text = catName
                isCheckable = true
                isChecked = catName == (currentCategoryFilter ?: "All")
                setOnClickListener {
                    currentCategoryFilter = catName
                    val existing = categoryMap[catName]
                    if (existing.isNullOrEmpty() && catName != "All") {
                        val matchingApi = APIHolder.apis.firstOrNull { it.name.equals(catName, ignoreCase = true) }
                        if (matchingApi != null) {
                            fetchCategoryStreamsAsync(matchingApi, catName)
                        }
                    }
                    applyFilter()
                }
            }
            chipGroup?.addView(chip)
        }
    }

    private fun loadRemoteStreamsAsync() {
        ioSafe {
            val remoteStreams = mutableListOf<SearchResponse>()
            APIHolder.apis.forEach { api ->
                try {
                    val repo = APIRepository(api)
                    val mainPageRes = repo.getMainPage(1)
                    if (mainPageRes is com.lagradost.cloudstream3.mvvm.Resource.Success) {
                        mainPageRes.value.filterNotNull().flatMap { it.items }.forEach { homePageList ->
                            val catName = homePageList.name
                            categoryMap.getOrPut(catName) { mutableListOf() }.addAll(homePageList.list)
                            categoryMap.getOrPut(api.name) { mutableListOf() }.addAll(homePageList.list)
                            remoteStreams.addAll(homePageList.list)
                        }
                    }
                } catch (_: Throwable) {}
            }

            if (remoteStreams.isNotEmpty()) {
                main {
                    if (!isAdded || view == null) return@main
                    allStreams.addAll(remoteStreams)
                    allStreams = allStreams.distinctBy { it.url }.toMutableList()
                    updateCategoryChips()
                    applyFilter()
                }
            }
        }
    }

    private fun fetchCategoryStreamsAsync(api: MainAPI, categoryName: String) {
        ioSafe {
            val fetched = mutableListOf<SearchResponse>()
            try {
                val repo = APIRepository(api)
                val mainPageRes = repo.getMainPage(1)
                if (mainPageRes is com.lagradost.cloudstream3.mvvm.Resource.Success) {
                    mainPageRes.value.filterNotNull().flatMap { it.items }.forEach { homePageList ->
                        fetched.addAll(homePageList.list)
                    }
                }
            } catch (_: Throwable) {}

            if (fetched.isNotEmpty()) {
                main {
                    if (!isAdded || view == null) return@main
                    categoryMap.getOrPut(categoryName) { mutableListOf() }.addAll(fetched)
                    categoryMap.getOrPut(api.name) { mutableListOf() }.addAll(fetched)
                    allStreams.addAll(fetched)
                    allStreams = allStreams.distinctBy { it.url }.toMutableList()
                    if (currentCategoryFilter == categoryName) {
                        applyFilter()
                    }
                }
            }
        }
    }

    private fun setupAdapter(): KickStreamsAdapter {
        gridRecycler?.layoutManager = GridLayoutManager(context, 3)
        val adapter = KickStreamsAdapter(filteredStreams, selectedItems) { streamItem ->
            if (selectedItems.contains(streamItem)) {
                selectedItems.remove(streamItem)
            } else {
                if (selectedItems.size >= maxCapacity) {
                    Toast.makeText(context, "Maximum $maxCapacity streams selected", Toast.LENGTH_SHORT).show()
                    return@KickStreamsAdapter
                }
                selectedItems.add(streamItem)
            }
            updateCounter()
            gridRecycler?.adapter?.notifyDataSetChanged()
        }
        gridRecycler?.adapter = adapter
        return adapter
    }

    // STEP 1: INSTANT LOCAL POPULATION (0ms Delay on Main Thread)
    private fun populateLocalStreams() {
        val streams = mutableListOf<SearchResponse>()

        // 1. In-memory matches from current provider (MultiPlayerManager.availableEpisodes)
        MultiPlayerManager.availableEpisodes.forEach { ep ->
            val title = ep.name ?: ep.headerName
            @Suppress("DEPRECATION_ERROR")
            val searchItem = com.lagradost.cloudstream3.LiveSearchResponse(
                name = title,
                url = ep.data,
                apiName = ep.apiName,
                type = ep.tvType,
                posterUrl = ep.poster,
                id = ep.id
            )
            streams.add(searchItem)
            val category = ep.headerName.takeIf { it.isNotBlank() } ?: ep.apiName
            categoryMap.getOrPut(category) { mutableListOf() }.add(searchItem)
            categoryMap.getOrPut(ep.apiName) { mutableListOf() }.add(searchItem)
        }

        // Keep the exact list from the home section the user opened available at the top.
        MultiPlayerManager.currentHomeSectionStreams.forEach { searchItem ->
            streams.add(searchItem)
            val sectionName = MultiPlayerManager.currentHomeSectionName
            if (!sectionName.isNullOrBlank()) {
                categoryMap.getOrPut(sectionName) { mutableListOf() }.add(searchItem)
            }
        }

        // 2. Alternative mirror sources
        MultiPlayerManager.availableSources.forEach { sourceLink ->
            @Suppress("DEPRECATION_ERROR")
            val searchItem = com.lagradost.cloudstream3.LiveSearchResponse(
                name = "${sourceLink.name} (${sourceLink.source})",
                url = sourceLink.url,
                apiName = sourceLink.source,
                type = com.lagradost.cloudstream3.TvType.Live,
                posterUrl = null,
                id = sourceLink.url.hashCode()
            )
            streams.add(searchItem)
            categoryMap.getOrPut("Sources") { mutableListOf() }.add(searchItem)
        }

        // 3. Bookmarks & Favorites
        val bookmarks = DataStoreHelper.getAllBookmarkedData()
        streams.addAll(bookmarks)
        if (bookmarks.isNotEmpty()) {
            categoryMap.getOrPut("Bookmarks") { mutableListOf() }.addAll(bookmarks)
        }

        DataStoreHelper.getAllFavorites().forEach { fav ->
            @Suppress("DEPRECATION_ERROR")
            val searchItem = com.lagradost.cloudstream3.LiveSearchResponse(
                name = fav.name,
                url = fav.url,
                apiName = fav.apiName,
                type = fav.type,
                posterUrl = fav.posterUrl,
                id = fav.id
            )
            streams.add(searchItem)
            categoryMap.getOrPut("Favorites") { mutableListOf() }.add(searchItem)
        }

        // Instantly display local streams with 0ms delay!
        localStreams = streams.distinctBy { it.url }.toMutableList()
        allStreams.clear()
        allStreams.addAll(localStreams)
        filteredStreams.clear()
        filteredStreams.addAll(allStreams)
        updateCategoryChips()
        gridRecycler?.adapter?.notifyDataSetChanged()
    }

    private fun setupSearchInput() {
        searchInput?.doOnTextChanged { text, _, _, _ ->
            val query = text?.toString()?.trim() ?: ""
            applyFilter()
            val requestId = searchRequestId.incrementAndGet()
            extensionSearchJob?.cancel()

            // Do not launch one request against every extension just because the picker opened.
            // Direct platform URLs are handled by the Add URL action and need no extension search.
            if (query.length < 2 || directStreamFromUrl(query) != null) {
                allStreams = localStreams.toMutableList()
                applyFilter()
                return@doOnTextChanged
            }

            extensionSearchJob = ioSafe {
                delay(500)
                extensionSearchMutex.withLock {
                    val results = mutableListOf<SearchResponse>()
                    val seenUrls = localStreams.mapTo(mutableSetOf()) { it.url }

                    // Search every installed extension, but one at a time and with a result cap.
                    // This avoids the previous eager multi-page crawl and unbounded fan-out.
                    for (api in APIHolder.apis) {
                        currentCoroutineContext().ensureActive()
                        if (searchRequestId.get() != requestId) return@withLock
                        if (results.size >= MAX_REMOTE_SEARCH_RESULTS) break

                        val response = try {
                            APIRepository(api).search(query, 1)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Throwable) {
                            null
                        }
                        val items = (response as? com.lagradost.cloudstream3.mvvm.Resource.Success)
                            ?.value?.items.orEmpty()
                        items.take(MAX_RESULTS_PER_EXTENSION).forEach { item ->
                            if (results.size < MAX_REMOTE_SEARCH_RESULTS && seenUrls.add(item.url)) {
                                results.add(item)
                            }
                        }
                    }

                    currentCoroutineContext().ensureActive()
                    if (searchRequestId.get() == requestId && results.isNotEmpty()) {
                        main {
                            if (!isAdded || view == null || searchRequestId.get() != requestId) return@main
                            allStreams = (localStreams + results).distinctBy { it.url }.toMutableList()
                            applyFilter()
                        }
                    }
                }
            }
        }
    }

    private fun setupAddUrlButton() {
        addUrlBtn?.setOnClickListener {
            val url = searchInput?.text?.toString()?.trim().orEmpty()
            val stream = directStreamFromUrl(url)
            if (stream == null) {
                Toast.makeText(
                    context,
                    "Paste a Kick, Twitch, or YouTube stream URL first",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (selectedItems.any { it.url.equals(stream.url, ignoreCase = true) }) {
                Toast.makeText(context, "This stream is already selected", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedItems.size >= maxCapacity) {
                Toast.makeText(context, "Maximum $maxCapacity streams selected", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            selectedItems.add(stream)
            updateCounter()
            gridRecycler?.adapter?.notifyDataSetChanged()
            Toast.makeText(context, "${stream.name.substringBefore(':')} added", Toast.LENGTH_SHORT).show()
        }
    }

    // Batch Confirm Selection
    private fun setupConfirmButton() {
        confirmBtn?.setOnClickListener {
            if (selectedItems.isEmpty()) {
                dismiss()
                return@setOnClickListener
            }

            confirmBtn?.isEnabled = false
            confirmBtn?.text = "Loading..."
            val itemsToLoad = selectedItems.toList()
            lastResolutionError = null

            ioSafe {
                val batchResult = resolveSelectedItems(itemsToLoad)
                main {
                    if (!isAdded) return@main
                    if (batchResult.size < itemsToLoad.size) {
                        Toast.makeText(
                            context,
                            lastResolutionError
                                ?: "Could not resolve one or more streams. Make sure Kick channels are live.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    if (batchResult.isNotEmpty()) onStreamsBatchSelected(batchResult)
                    dismiss()
                }
            }
        }
    }

    private suspend fun resolveSelectedItems(
        items: List<SearchResponse>
    ): List<Pair<ExtractorLink, String>> {
        val batchResult = mutableListOf<Pair<ExtractorLink, String>>()

        items.forEach { item ->
            val channelName = item.name
            val channelUrl = item.url
            val directProvider = directStreamFromUrl(channelUrl)?.apiName
                ?.substringAfterLast('(')
                ?.substringBefore(')')
            val api = APIHolder.getApiFromNameNull(item.apiName)
                ?: directProvider?.let { provider ->
                    APIHolder.apis.firstOrNull { it.name.contains(provider, ignoreCase = true) }
                        ?: APIHolder.apis.firstOrNull { it.name.contains("Live Hub", ignoreCase = true) }
                }
            val extractedLinks = mutableListOf<ExtractorLink>()

            if (api != null) {
                try {
                    val repo = APIRepository(api)
                    repo.loadLinks(channelUrl, isCasting = false, subtitleCallback = {}, callback = { extractedLink ->
                        extractedLinks.add(extractedLink)
                    })
                } catch (t: Throwable) {
                    android.util.Log.e("MultiViewPicker", "Error loadLinks for ${item.name}: $t")
                }
            }

            val (resolvedUrl, headers) = if (extractedLinks.isEmpty()) {
                try {
                    resolveLiveStreamUrlAndHeaders(channelUrl)
                } catch (t: Throwable) {
                    android.util.Log.e("MultiViewPicker", "Could not resolve stream ${item.name}: $t")
                    lastResolutionError = t.message ?: "Could not resolve ${item.name}"
                    return@forEach
                }
            } else {
                channelUrl to emptyMap()
            }

            if (extractedLinks.isEmpty() && !resolvedUrl.contains(".m3u8", ignoreCase = true)) {
                try {
                    loadExtractor(resolvedUrl, subtitleCallback = {}, callback = { extractedLinks.add(it) })
                } catch (_: Throwable) {}
            }

            val isM3u8 = resolvedUrl.contains(".m3u8") || resolvedUrl.contains("m3u8") ||
                    resolvedUrl.contains("playback.kick.com") || resolvedUrl.contains("twitch")
            val linkType = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO

            val selectedLink = extractedLinks.firstOrNull() ?: @Suppress("DEPRECATION_ERROR") ExtractorLink(
                source = item.apiName,
                name = channelName,
                url = resolvedUrl,
                referer = headers["Referer"] ?: "",
                quality = Qualities.Unknown.value,
                type = linkType,
                headers = headers
            )
            batchResult.add(selectedLink to channelName)
        }

        return batchResult
    }

    override fun onDestroyView() {
        searchRequestId.incrementAndGet()
        extensionSearchJob?.cancel()
        extensionSearchJob = null
        super.onDestroyView()
    }

    private companion object {
        const val MAX_RESULTS_PER_EXTENSION = 10
        const val MAX_REMOTE_SEARCH_RESULTS = 120
    }

    private suspend fun resolveLiveStreamUrlAndHeaders(url: String): Pair<String, Map<String, String>> {
        val cleanUrl = url.trim()

        val kickRegex = Regex("""(?:https?://)?(?:www\.)?kick\.com/([a-zA-Z0-9_-]+)""", RegexOption.IGNORE_CASE)
        val kickMatch = kickRegex.find(cleanUrl)
        if (kickMatch != null) {
            val channel = kickMatch.groupValues[1].lowercase()
            if (channel !in setOf("api", "categories", "video", "videos")) {
                val headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    "Referer" to "https://kick.com/",
                    "Origin" to "https://kick.com",
                    "Accept" to "application/json, text/plain, */*"
                )
                val channelResponse = app.get(
                    "https://kick.com/api/v2/channels/$channel",
                    headers = headers
                ).text
                val channelData = JSONObject(channelResponse)
                val liveData = channelData.optJSONObject("livestream")
                    ?: throw IllegalStateException("Kick channel $channel is offline")
                val playbackUrl = liveData.optString("playback_url")
                    .takeIf { it.startsWith("https://") }
                    ?: channelData.optString("playback_url").takeIf { it.startsWith("https://") }
                    ?: throw IllegalStateException("Kick did not return a playback URL for $channel")
                return playbackUrl to headers
            }
        }

        val twitchRegex = Regex("""(?:https?://)?(?:www\.)?twitch\.tv/([a-zA-Z0-9_-]+)""", RegexOption.IGNORE_CASE)
        val twitchMatch = twitchRegex.find(cleanUrl)
        if (twitchMatch != null) {
            throw IllegalStateException(
                "Twitch could not resolve this channel. Install or update the Twitch Discover or Live Hub extension."
            )
        }

        val defaultHeaders = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        )
        return cleanUrl to defaultHeaders
    }

    private class KickStreamsAdapter(
        val items: List<SearchResponse>,
        val selectedSet: Set<SearchResponse>,
        val onItemClick: (SearchResponse) -> Unit
    ) : RecyclerView.Adapter<KickStreamsAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val thumbnail: ImageView = view.findViewById(R.id.kick_stream_thumbnail)
            val liveBadge: TextView = view.findViewById(R.id.kick_live_badge)
            val checkmark: ImageView = view.findViewById(R.id.kick_checkmark_badge)
            val avatar: ImageView = view.findViewById(R.id.kick_avatar)
            val streamerName: TextView = view.findViewById(R.id.kick_streamer_name)
            val streamTitle: TextView = view.findViewById(R.id.kick_stream_title)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.kick_stream_card_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val isSelected = selectedSet.contains(item)

            holder.streamerName.text = item.name
            holder.streamTitle.text = item.apiName
            holder.thumbnail.loadImage(item.posterUrl)
            holder.avatar.loadImage(item.posterUrl)
            holder.checkmark.isVisible = isSelected

            holder.itemView.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }
}
