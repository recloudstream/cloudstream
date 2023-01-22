package com.lagradost.cloudstream3.ui.library

import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.SyncApis
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe

enum class ListSorting(@StringRes val stringRes: Int) {
    Query(R.string.none),
    RatingHigh(R.string.sort_rating_desc),
    RatingLow(R.string.sort_rating_asc),
    UpdatedNew(R.string.sort_updated_new),
    UpdatedOld(R.string.sort_updated_old),
    AlphabeticalA(R.string.sort_alphabetical_a),
    AlphabeticalZ(R.string.sort_alphabetical_z),
}

class LibraryViewModel : ViewModel() {
    private val _pages: MutableLiveData<List<SyncAPI.Page>> = MutableLiveData(emptyList())
    val pages: LiveData<List<SyncAPI.Page>> = _pages

    private val _currentApiName: MutableLiveData<String> = MutableLiveData("")
    val currentApiName: LiveData<String> = _currentApiName

    private val availableSyncApis = SyncApis.filter { it.hasAccount() }

    // TODO REMEMBER SELECTION
    var currentSyncApi = availableSyncApis.firstOrNull()
        private set

    val availableApiNames: List<String> = availableSyncApis.map { it.name }

    val sortingMethods = listOf(
        ListSorting.RatingHigh,
        ListSorting.RatingLow,
//        ListSorting.UpdatedNew,
//        ListSorting.UpdatedOld,
        ListSorting.AlphabeticalA,
        ListSorting.AlphabeticalZ,
    )

    var currentSortingMethod: ListSorting = sortingMethods.first()
        private set

    fun switchList(name: String) {
        currentSyncApi = availableSyncApis[availableApiNames.indexOf(name)]
        _currentApiName.postValue(currentSyncApi?.name)
        reloadPages(true)
    }

    fun sort(method: ListSorting, query: String? = null) {
        val currentList = pages.value ?: return
        currentSortingMethod = method
        currentList.forEachIndexed { _, page ->
            page.sort(method, query)
        }
        _pages.postValue(currentList)
    }

    fun reloadPages(forceReload: Boolean) {
        // Only skip loading if its not forced and pages is not empty
        if (!forceReload && pages.value?.isNotEmpty() == true) return

        ioSafe {
            currentSyncApi?.let { repo ->
                _currentApiName.postValue(repo.name)
                val library = (repo.getPersonalLibrary() as? Resource.Success)?.value ?: return@let

                val listSubset = library.allLibraryItems.groupBy { it.listName }
                val allLists = library.allListNames.associateWith { emptyList<SyncAPI.LibraryItem>() }

                val filledLists = allLists + listSubset

                val pages = filledLists.map {
                    SyncAPI.Page(
                        it.key,
                        it.value
                    )
                }
                _pages.postValue(pages)
            }
        }
    }
}