package com.lagradost.cloudstream3.ui.library

import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.SyncApis
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.currentAccount

enum class ListSorting(@StringRes val stringRes: Int) {
    Query(R.string.none),
    RatingHigh(R.string.sort_rating_desc),
    RatingLow(R.string.sort_rating_asc),
    UpdatedNew(R.string.sort_updated_new),
    UpdatedOld(R.string.sort_updated_old),
    AlphabeticalA(R.string.sort_alphabetical_a),
    AlphabeticalZ(R.string.sort_alphabetical_z),
}

const val LAST_SYNC_API_KEY = "last_sync_api"

class LibraryViewModel : ViewModel() {
    fun switchPage(page : Int) {
        _currentPage.postValue(page)
    }

    private val _currentPage: MutableLiveData<Int> = MutableLiveData(0)
    val currentPage: LiveData<Int> = _currentPage
    
    private val _pages: MutableLiveData<Resource<List<SyncAPI.Page>>> = MutableLiveData(null)
    val pages: LiveData<Resource<List<SyncAPI.Page>>> = _pages

    private val _currentApiName: MutableLiveData<String> = MutableLiveData("")
    val currentApiName: LiveData<String> = _currentApiName

    private val availableSyncApis
        get() = SyncApis.filter { it.hasAccount() }

    var currentSyncApi = availableSyncApis.let { allApis ->
        val lastSelection = getKey<String>("$currentAccount/$LAST_SYNC_API_KEY")
        availableSyncApis.firstOrNull { it.name == lastSelection } ?: allApis.firstOrNull()
    }
        private set(value) {
            field = value
            setKey("$currentAccount/$LAST_SYNC_API_KEY", field?.name)
        }

    val availableApiNames: List<String>
        get() = availableSyncApis.map { it.name }

    var sortingMethods = emptyList<ListSorting>()
        private set

    var currentSortingMethod: ListSorting? = sortingMethods.firstOrNull()
        private set

    fun switchList(name: String) {
        currentSyncApi = availableSyncApis[availableApiNames.indexOf(name)]
        _currentApiName.postValue(currentSyncApi?.name)
        reloadPages(true)
    }

    fun sort(method: ListSorting, query: String? = null) = ioSafe {
        val value = _pages.value ?: return@ioSafe
        if (value is Resource.Success) {
            sort(method, query, value.value)
        }
    }

    private fun sort(method: ListSorting, query: String? = null, items: List<SyncAPI.Page>) {
        currentSortingMethod = method
        DataStoreHelper.librarySortingMode = method.ordinal

        items.forEach { page ->
            page.sort(method, query)
        }
        _pages.postValue(Resource.Success(items))
    }

    fun reloadPages(forceReload: Boolean) {
        // Only skip loading if its not forced and pages is not empty
        if (!forceReload && (pages.value as? Resource.Success)?.value?.isNotEmpty() == true &&
            currentSyncApi?.requireLibraryRefresh != true
        ) return

        ioSafe {
            currentSyncApi?.let { repo ->
                _currentApiName.postValue(repo.name)
                _pages.postValue(Resource.Loading())
                val libraryResource = repo.getPersonalLibrary()
                if (libraryResource is Resource.Failure) {
                    _pages.postValue(libraryResource)
                    return@let
                }
                val library = (libraryResource as? Resource.Success)?.value ?: return@let

                sortingMethods = library.supportedListSorting.toList()
                repo.requireLibraryRefresh = false

                val pages = library.allLibraryLists.map {
                    SyncAPI.Page(
                        it.name,
                        it.items
                    )
                }

                val desiredSortingMethod =
                    ListSorting.values().getOrNull(DataStoreHelper.librarySortingMode)
                if (desiredSortingMethod != null && library.supportedListSorting.contains(desiredSortingMethod)) {
                    sort(desiredSortingMethod, null, pages)
                } else {
                    // null query = no sorting
                    sort(ListSorting.Query, null, pages)
                }
            }
        }
    }

    init {
        MainActivity.reloadLibraryEvent += ::reloadPages
    }

    override fun onCleared() {
        MainActivity.reloadLibraryEvent -= ::reloadPages
        super.onCleared()
    }
}