package com.lagradost.cloudstream3.ui.library

import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.SyncApis
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import me.xdrop.fuzzywuzzy.FuzzySearch

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
    private val _pages: MutableLiveData<List<Page>> = MutableLiveData(emptyList())
    val pages: LiveData<List<Page>> = _pages

    private val _currentApiName: MutableLiveData<String> = MutableLiveData("")
    val currentApiName: LiveData<String> = _currentApiName

    private val listApis = SyncApis.filter { it.hasAccount() }
    private var currentApi = listApis.firstOrNull()

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

    fun switchList() {
        currentApi = listApis[(listApis.indexOf(currentApi) + 1) % listApis.size]
        loadPages()
    }

    fun sort(method: ListSorting, query: String? = null) {
        val currentList = pages.value ?: return
        currentSortingMethod = method
        currentList.forEachIndexed { index, page ->
            page.sort(method, query)
        }
        _pages.postValue(currentList)
    }

    fun loadPages() {
        ioSafe {
            currentApi?.let { repo ->
                val list = (repo.getPersonalLibrary() as? Resource.Success)?.value
                val pages = (list ?: emptyList()).groupBy { it.listName }.map {
                    Page(
                        it.key,
                        it.value
                    )
                }
                _pages.postValue(pages)
                _currentApiName.postValue(repo.name)
            }
        }
    }
}