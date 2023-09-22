package com.lagradost.cloudstream3.utils

import android.content.Context
import android.content.DialogInterface
import android.text.Editable
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isGone
import androidx.core.widget.doOnTextChanged
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.getKeys
import com.lagradost.cloudstream3.AcraApplication.Companion.removeKey
import com.lagradost.cloudstream3.AcraApplication.Companion.removeKeys
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.databinding.WhoIsWatchingAccountEditBinding
import com.lagradost.cloudstream3.databinding.WhoIsWatchingBinding
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.WhoIsWatchingAdapter
import com.lagradost.cloudstream3.ui.result.FOCUS_SELF
import com.lagradost.cloudstream3.ui.result.UiImage
import com.lagradost.cloudstream3.ui.result.VideoWatchState
import com.lagradost.cloudstream3.ui.result.setImage
import com.lagradost.cloudstream3.ui.result.setLinearListLayout
import com.lagradost.cloudstream3.utils.AppUtils.setDefaultFocus
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe

const val VIDEO_POS_DUR = "video_pos_dur"
const val VIDEO_WATCH_STATE = "video_watch_state"
const val RESULT_WATCH_STATE = "result_watch_state"
const val RESULT_WATCH_STATE_DATA = "result_watch_state_data"
const val RESULT_SUBSCRIBED_STATE_DATA = "result_subscribed_state_data"
const val RESULT_RESUME_WATCHING = "result_resume_watching_2" // changed due to id changes
const val RESULT_RESUME_WATCHING_OLD = "result_resume_watching"
const val RESULT_RESUME_WATCHING_HAS_MIGRATED = "result_resume_watching_migrated"
const val RESULT_EPISODE = "result_episode"
const val RESULT_SEASON = "result_season"
const val RESULT_DUB = "result_dub"

object DataStoreHelper {
    // be aware, don't change the index of these as Account uses the index for the art
    private val profileImages = arrayOf(
        R.drawable.profile_bg_dark_blue,
        R.drawable.profile_bg_blue,
        R.drawable.profile_bg_orange,
        R.drawable.profile_bg_pink,
        R.drawable.profile_bg_purple,
        R.drawable.profile_bg_red,
        R.drawable.profile_bg_teal
    )

    data class Account(
        @JsonProperty("keyIndex")
        val keyIndex: Int,
        @JsonProperty("name")
        val name: String,
        @JsonProperty("customImage")
        val customImage: String? = null,
        @JsonProperty("defaultImageIndex")
        val defaultImageIndex: Int,
    ) {
        val image: UiImage
            get() = customImage?.let { UiImage.Image(it) } ?: UiImage.Drawable(
                profileImages.getOrNull(defaultImageIndex) ?: profileImages.first()
            )
    }

    const val TAG = "data_store_helper"
    private var accounts by PreferenceDelegate("$TAG/account", arrayOf<Account>())
    var selectedKeyIndex by PreferenceDelegate("$TAG/account_key_index", 0)
    val currentAccount: String get() = selectedKeyIndex.toString()

    /**
     * Get or set the current account homepage.
     * Setting this does not automatically reload the homepage.
     */
    var currentHomePage: String?
        get() = getKey("$currentAccount/$USER_SELECTED_HOMEPAGE_API")
        set(value) {
            val key = "$currentAccount/$USER_SELECTED_HOMEPAGE_API"
            if (value == null) {
                removeKey(key)
            } else {
                setKey(key, value)
            }
        }

    private fun setAccount(account: Account, refreshHomePage: Boolean) {
        selectedKeyIndex = account.keyIndex
        showToast(account.name)
        MainActivity.bookmarksUpdatedEvent(true)
        if (refreshHomePage) {
            MainActivity.reloadHomeEvent(true)
        }
    }

    private fun editAccount(context: Context, account: Account, isNewAccount: Boolean) {
        val binding =
            WhoIsWatchingAccountEditBinding.inflate(LayoutInflater.from(context), null, false)
        val builder =
            AlertDialog.Builder(context, R.style.AlertDialogCustom)
                .setView(binding.root)

        var currentEditAccount = account
        val dialog = builder.show()
        binding.accountName.text = Editable.Factory.getInstance()?.newEditable(account.name)
        binding.accountName.doOnTextChanged { text, _, _, _ ->
            currentEditAccount = currentEditAccount.copy(name = text?.toString() ?: "")
        }

        binding.deleteBtt.isGone = isNewAccount
        binding.deleteBtt.setOnClickListener {
            val dialogClickListener =
                DialogInterface.OnClickListener { _, which ->
                    when (which) {
                        DialogInterface.BUTTON_POSITIVE -> {
                            // remove all keys as well as the account, note that default wont get
                            // deleted from currentAccounts, as it is not part of "accounts",
                            // but the watch keys will
                            removeKeys(account.keyIndex.toString())
                            val currentAccounts = accounts.toMutableList()
                            currentAccounts.removeIf { it.keyIndex == account.keyIndex }
                            accounts = currentAccounts.toTypedArray()

                            // update UI
                            setAccount(getDefaultAccount(context), true)
                            MainActivity.bookmarksUpdatedEvent(true)
                            dialog?.dismissSafe()
                        }

                        DialogInterface.BUTTON_NEGATIVE -> {}
                    }
                }

            try {
                AlertDialog.Builder(context).setTitle(R.string.delete).setMessage(
                    context.getString(R.string.delete_message).format(
                        currentEditAccount.name
                    )
                )
                    .setPositiveButton(R.string.delete, dialogClickListener)
                    .setNegativeButton(R.string.cancel, dialogClickListener)
                    .show().setDefaultFocus()
            } catch (t: Throwable) {
                logError(t)
                // ye you somehow fucked up formatting did you?
            }
        }

        binding.cancelBtt.setOnClickListener {
            dialog?.dismissSafe()
        }

        binding.profilePic.setImage(account.image)
        binding.profilePic.setOnClickListener {
            // rolls the image forwards once
            currentEditAccount =
                currentEditAccount.copy(defaultImageIndex = (currentEditAccount.defaultImageIndex + 1) % profileImages.size)
            binding.profilePic.setImage(currentEditAccount.image)
        }

        binding.applyBtt.setOnClickListener {
            val currentAccounts = accounts.toMutableList()

            val overrideIndex =
                currentAccounts.indexOfFirst { it.keyIndex == currentEditAccount.keyIndex }

            // if an account is found that has the same keyIndex then override that one, if not then append it
            if (overrideIndex != -1) {
                currentAccounts[overrideIndex] = currentEditAccount
            } else {
                currentAccounts.add(currentEditAccount)
            }

            // Save the current homepage for new accounts
            val currentHomePage = DataStoreHelper.currentHomePage

            // set the new default account as well as add the key for the new account
            setAccount(currentEditAccount, false)
            DataStoreHelper.currentHomePage = currentHomePage

            accounts = currentAccounts.toTypedArray()

            dialog.dismissSafe()
        }
    }

    private fun getDefaultAccount(context: Context): Account {
        return accounts.let { currentAccounts ->
            currentAccounts.getOrNull(currentAccounts.indexOfFirst { it.keyIndex == 0 }) ?: Account(
                keyIndex = 0,
                name = context.getString(R.string.default_account),
                defaultImageIndex = 0
            )
        }
    }

    fun showWhoIsWatching(context: Context) {
        val binding: WhoIsWatchingBinding = WhoIsWatchingBinding.inflate(
            LayoutInflater.from(context)
        )

        val showAccount = accounts.toMutableList().apply {
            val item = getDefaultAccount(context)
            remove(item)
            add(0, item)
        }

        val builder =
            BottomSheetDialog(context)
        builder.setContentView(binding.root)
        val accountName = context.getString(R.string.account)

        binding.profilesRecyclerview.setLinearListLayout(
            isHorizontal = true,
            nextUp = FOCUS_SELF,
            nextDown = FOCUS_SELF,
            nextLeft = FOCUS_SELF,
            nextRight = FOCUS_SELF
        )
        binding.profilesRecyclerview.adapter = WhoIsWatchingAdapter(
            selectCallBack = { account ->
                setAccount(account, true)
                builder.dismissSafe()
            },
            addAccountCallback = {
                val currentAccounts = accounts
                val remainingImages =
                    profileImages.toSet() - currentAccounts.filter { it.customImage == null }
                        .mapNotNull { profileImages.getOrNull(it.defaultImageIndex) }.toSet()
                val image =
                    profileImages.indexOf(remainingImages.randomOrNull() ?: profileImages.random())
                val keyIndex = (currentAccounts.maxOfOrNull { it.keyIndex } ?: 0) + 1

                // create a new dummy account
                editAccount(
                    context,
                    Account(
                        keyIndex = keyIndex,
                        name = "$accountName $keyIndex",
                        customImage = null,
                        defaultImageIndex = image
                    ), isNewAccount = true
                )
                builder.dismissSafe()
            },
            editCallBack = { account ->
                editAccount(
                    context, account, isNewAccount = false
                )
                builder.dismissSafe()
            }
        ).apply {
            submitList(showAccount)
        }

        builder.show()
    }


    data class PosDur(
        @JsonProperty("position") val position: Long,
        @JsonProperty("duration") val duration: Long
    )

    fun PosDur.fixVisual(): PosDur {
        if (duration <= 0) return PosDur(0, duration)
        val percentage = position * 100 / duration
        if (percentage <= 1) return PosDur(0, duration)
        if (percentage <= 5) return PosDur(5 * duration / 100, duration)
        if (percentage >= 95) return PosDur(duration, duration)
        return this
    }

    /**
     * Used to display notifications on new episodes and posters in library.
     **/
    data class SubscribedData(
        @JsonProperty("id") override var id: Int?,
        @JsonProperty("subscribedTime") val bookmarkedTime: Long,
        @JsonProperty("latestUpdatedTime") val latestUpdatedTime: Long,
        @JsonProperty("lastSeenEpisodeCount") val lastSeenEpisodeCount: Map<DubStatus, Int?>,
        @JsonProperty("name") override val name: String,
        @JsonProperty("url") override val url: String,
        @JsonProperty("apiName") override val apiName: String,
        @JsonProperty("type") override var type: TvType? = null,
        @JsonProperty("posterUrl") override var posterUrl: String?,
        @JsonProperty("year") val year: Int?,
        @JsonProperty("quality") override var quality: SearchQuality? = null,
        @JsonProperty("posterHeaders") override var posterHeaders: Map<String, String>? = null,
    ) : SearchResponse {
        fun toLibraryItem(): SyncAPI.LibraryItem? {
            return SyncAPI.LibraryItem(
                name,
                url,
                id?.toString() ?: return null,
                null,
                null,
                null,
                latestUpdatedTime,
                apiName, type, posterUrl, posterHeaders, quality, this.id
            )
        }
    }

    data class BookmarkedData(
        @JsonProperty("id") override var id: Int?,
        @JsonProperty("bookmarkedTime") val bookmarkedTime: Long,
        @JsonProperty("latestUpdatedTime") val latestUpdatedTime: Long,
        @JsonProperty("name") override val name: String,
        @JsonProperty("url") override val url: String,
        @JsonProperty("apiName") override val apiName: String,
        @JsonProperty("type") override var type: TvType? = null,
        @JsonProperty("posterUrl") override var posterUrl: String?,
        @JsonProperty("year") val year: Int?,
        @JsonProperty("quality") override var quality: SearchQuality? = null,
        @JsonProperty("posterHeaders") override var posterHeaders: Map<String, String>? = null,
    ) : SearchResponse {
        fun toLibraryItem(id: String): SyncAPI.LibraryItem {
            return SyncAPI.LibraryItem(
                name,
                url,
                id,
                null,
                null,
                null,
                latestUpdatedTime,
                apiName, type, posterUrl, posterHeaders, quality, this.id
            )
        }
    }

    data class ResumeWatchingResult(
        @JsonProperty("name") override val name: String,
        @JsonProperty("url") override val url: String,
        @JsonProperty("apiName") override val apiName: String,
        @JsonProperty("type") override var type: TvType? = null,
        @JsonProperty("posterUrl") override var posterUrl: String?,
        @JsonProperty("watchPos") val watchPos: PosDur?,
        @JsonProperty("id") override var id: Int?,
        @JsonProperty("parentId") val parentId: Int?,
        @JsonProperty("episode") val episode: Int?,
        @JsonProperty("season") val season: Int?,
        @JsonProperty("isFromDownload") val isFromDownload: Boolean,
        @JsonProperty("quality") override var quality: SearchQuality? = null,
        @JsonProperty("posterHeaders") override var posterHeaders: Map<String, String>? = null,
    ) : SearchResponse

    /**
     * A datastore wide account for future implementations of a multiple account system
     **/

    fun getAllWatchStateIds(): List<Int>? {
        val folder = "$currentAccount/$RESULT_WATCH_STATE"
        return getKeys(folder)?.mapNotNull {
            it.removePrefix("$folder/").toIntOrNull()
        }
    }

    fun deleteAllResumeStateIds() {
        val folder = "$currentAccount/$RESULT_RESUME_WATCHING"
        removeKeys(folder)
    }

    fun deleteAllBookmarkedData() {
        val folder1 = "$currentAccount/$RESULT_WATCH_STATE"
        val folder2 = "$currentAccount/$RESULT_WATCH_STATE_DATA"
        removeKeys(folder1)
        removeKeys(folder2)
    }

    fun deleteBookmarkedData(id: Int?) {
        if (id == null) return
        removeKey("$currentAccount/$RESULT_WATCH_STATE", id.toString())
        removeKey("$currentAccount/$RESULT_WATCH_STATE_DATA", id.toString())
    }

    fun getAllResumeStateIds(): List<Int>? {
        val folder = "$currentAccount/$RESULT_RESUME_WATCHING"
        return getKeys(folder)?.mapNotNull {
            it.removePrefix("$folder/").toIntOrNull()
        }
    }

    private fun getAllResumeStateIdsOld(): List<Int>? {
        val folder = "$currentAccount/$RESULT_RESUME_WATCHING_OLD"
        return getKeys(folder)?.mapNotNull {
            it.removePrefix("$folder/").toIntOrNull()
        }
    }

    fun migrateResumeWatching() {
        // if (getKey(RESULT_RESUME_WATCHING_HAS_MIGRATED, false) != true) {
        setKey(RESULT_RESUME_WATCHING_HAS_MIGRATED, true)
        getAllResumeStateIdsOld()?.forEach { id ->
            getLastWatchedOld(id)?.let {
                setLastWatched(
                    it.parentId,
                    null,
                    it.episode,
                    it.season,
                    it.isFromDownload,
                    it.updateTime
                )
                removeLastWatchedOld(it.parentId)
            }
        }
        //}
    }

    fun setLastWatched(
        parentId: Int?,
        episodeId: Int?,
        episode: Int?,
        season: Int?,
        isFromDownload: Boolean = false,
        updateTime: Long? = null,
    ) {
        if (parentId == null) return
        setKey(
            "$currentAccount/$RESULT_RESUME_WATCHING",
            parentId.toString(),
            VideoDownloadHelper.ResumeWatching(
                parentId,
                episodeId,
                episode,
                season,
                updateTime ?: System.currentTimeMillis(),
                isFromDownload
            )
        )
    }

    private fun removeLastWatchedOld(parentId: Int?) {
        if (parentId == null) return
        removeKey("$currentAccount/$RESULT_RESUME_WATCHING_OLD", parentId.toString())
    }

    fun removeLastWatched(parentId: Int?) {
        if (parentId == null) return
        removeKey("$currentAccount/$RESULT_RESUME_WATCHING", parentId.toString())
    }

    fun getLastWatched(id: Int?): VideoDownloadHelper.ResumeWatching? {
        if (id == null) return null
        return getKey(
            "$currentAccount/$RESULT_RESUME_WATCHING",
            id.toString(),
        )
    }

    private fun getLastWatchedOld(id: Int?): VideoDownloadHelper.ResumeWatching? {
        if (id == null) return null
        return getKey(
            "$currentAccount/$RESULT_RESUME_WATCHING_OLD",
            id.toString(),
        )
    }

    fun setBookmarkedData(id: Int?, data: BookmarkedData) {
        if (id == null) return
        setKey("$currentAccount/$RESULT_WATCH_STATE_DATA", id.toString(), data)
        AccountManager.localListApi.requireLibraryRefresh = true
    }

    fun getBookmarkedData(id: Int?): BookmarkedData? {
        if (id == null) return null
        return getKey("$currentAccount/$RESULT_WATCH_STATE_DATA", id.toString())
    }

    fun getAllSubscriptions(): List<SubscribedData> {
        return getKeys("$currentAccount/$RESULT_SUBSCRIBED_STATE_DATA")?.mapNotNull {
            getKey(it)
        } ?: emptyList()
    }

    fun removeSubscribedData(id: Int?) {
        if (id == null) return
        AccountManager.localListApi.requireLibraryRefresh = true
        removeKey("$currentAccount/$RESULT_SUBSCRIBED_STATE_DATA", id.toString())
    }

    /**
     * Set new seen episodes and update time
     **/
    fun updateSubscribedData(id: Int?, data: SubscribedData?, episodeResponse: EpisodeResponse?) {
        if (id == null || data == null || episodeResponse == null) return
        val newData = data.copy(
            latestUpdatedTime = unixTimeMS,
            lastSeenEpisodeCount = episodeResponse.getLatestEpisodes()
        )
        setKey("$currentAccount/$RESULT_SUBSCRIBED_STATE_DATA", id.toString(), newData)
    }

    fun setSubscribedData(id: Int?, data: SubscribedData) {
        if (id == null) return
        setKey("$currentAccount/$RESULT_SUBSCRIBED_STATE_DATA", id.toString(), data)
        AccountManager.localListApi.requireLibraryRefresh = true
    }

    fun getSubscribedData(id: Int?): SubscribedData? {
        if (id == null) return null
        return getKey("$currentAccount/$RESULT_SUBSCRIBED_STATE_DATA", id.toString())
    }

    fun setViewPos(id: Int?, pos: Long, dur: Long) {
        if (id == null) return
        if (dur < 30_000) return // too short
        setKey("$currentAccount/$VIDEO_POS_DUR", id.toString(), PosDur(pos, dur))
    }

    fun getViewPos(id: Int?): PosDur? {
        if (id == null) return null
        return getKey("$currentAccount/$VIDEO_POS_DUR", id.toString(), null)
    }

    fun getVideoWatchState(id: Int?): VideoWatchState? {
        if (id == null) return null
        return getKey("$currentAccount/$VIDEO_WATCH_STATE", id.toString(), null)
    }

    fun setVideoWatchState(id: Int?, watchState: VideoWatchState) {
        if (id == null) return

        // None == No key
        if (watchState == VideoWatchState.None) {
            removeKey("$currentAccount/$VIDEO_WATCH_STATE", id.toString())
        } else {
            setKey("$currentAccount/$VIDEO_WATCH_STATE", id.toString(), watchState)
        }
    }

    fun getDub(id: Int): DubStatus? {
        return DubStatus.values()
            .getOrNull(getKey("$currentAccount/$RESULT_DUB", id.toString(), -1) ?: -1)
    }

    fun setDub(id: Int, status: DubStatus) {
        setKey("$currentAccount/$RESULT_DUB", id.toString(), status.ordinal)
    }

    fun setResultWatchState(id: Int?, status: Int) {
        if (id == null) return
        if (status == WatchType.NONE.internalId) {
            deleteBookmarkedData(id)
        } else {
            setKey("$currentAccount/$RESULT_WATCH_STATE", id.toString(), status)
        }
    }

    fun getResultWatchState(id: Int): WatchType {
        return WatchType.fromInternalId(
            getKey<Int>(
                "$currentAccount/$RESULT_WATCH_STATE",
                id.toString(),
                null
            )
        )
    }

    fun getResultSeason(id: Int): Int? {
        return getKey("$currentAccount/$RESULT_SEASON", id.toString(), null)
    }

    fun setResultSeason(id: Int, value: Int?) {
        setKey("$currentAccount/$RESULT_SEASON", id.toString(), value)
    }

    fun getResultEpisode(id: Int): Int? {
        return getKey("$currentAccount/$RESULT_EPISODE", id.toString(), null)
    }

    fun setResultEpisode(id: Int, value: Int?) {
        setKey("$currentAccount/$RESULT_EPISODE", id.toString(), value)
    }

    fun addSync(id: Int, idPrefix: String, url: String) {
        setKey("${idPrefix}_sync", id.toString(), url)
    }

    fun getSync(id: Int, idPrefixes: List<String>): List<String?> {
        return idPrefixes.map { idPrefix ->
            getKey("${idPrefix}_sync", id.toString())
        }
    }
}