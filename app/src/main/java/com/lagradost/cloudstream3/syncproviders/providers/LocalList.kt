package com.lagradost.cloudstream3.syncproviders.providers

import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.syncproviders.AuthData
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.library.ListSorting
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.Coroutines.ioWork
import com.lagradost.cloudstream3.utils.DataStoreHelper.getAllFavorites
import com.lagradost.cloudstream3.utils.DataStoreHelper.getAllSubscriptions
import com.lagradost.cloudstream3.utils.DataStoreHelper.getAllWatchStateIds
import com.lagradost.cloudstream3.utils.DataStoreHelper.getBookmarkedData
import com.lagradost.cloudstream3.utils.DataStoreHelper.getResultWatchState
import com.lagradost.cloudstream3.utils.txt

class LocalList : SyncAPI() {
    override val name = "Local"
    override val idPrefix = "local"

    override val icon: Int = R.drawable.ic_baseline_storage_24
    override val requiresLogin = false
    override val createAccountUrl = null
    override var requireLibraryRefresh = true
    override val syncIdName = SyncIdName.LocalList

    override suspend fun library(auth : AuthData?): SyncAPI.LibraryMetadata? {
        val watchStatusIds = ioWork {
            getAllWatchStateIds()?.map { id ->
                Pair(id, getResultWatchState(id))
            }
        }?.distinctBy { it.first } ?: return null

        val list = ioWork {
            val isTrueTv = isLayout(TV)

            val baseMap = WatchType.entries.filter { it != WatchType.NONE }.associate {
                // None is not something to display
                it.stringRes to emptyList<SyncAPI.LibraryItem>()
            } + mapOf(
                R.string.favorites_list_name to emptyList()
            ) + if (!isTrueTv) {
                mapOf(
                    R.string.subscription_list_name to emptyList()
                )
            } else {
                emptyMap()
            }

            val watchStatusMap = watchStatusIds.groupBy { it.second.stringRes }.mapValues { group ->
                group.value.mapNotNull {
                    getBookmarkedData(it.first)?.toLibraryItem(it.first.toString())
                }
            }

            val favoritesMap = mapOf(R.string.favorites_list_name to getAllFavorites().mapNotNull {
                it.toLibraryItem()
            })

            // Don't show subscriptions on TV
            val result = if (isTrueTv) {
                baseMap + watchStatusMap + favoritesMap
            } else {
                val subscriptionsMap =
                    mapOf(R.string.subscription_list_name to getAllSubscriptions().mapNotNull {
                        it.toLibraryItem()
                    })

                baseMap + watchStatusMap + subscriptionsMap + favoritesMap
            }

            result
        }

        return LibraryMetadata(
            list.map { LibraryList(txt(it.key), it.value) },
            setOf(
                ListSorting.AlphabeticalA,
                ListSorting.AlphabeticalZ,
                ListSorting.UpdatedNew,
                ListSorting.UpdatedOld,
                ListSorting.ReleaseDateNew,
                ListSorting.ReleaseDateOld,
//                ListSorting.RatingHigh,
//                ListSorting.RatingLow,

            )
        )
    }
}