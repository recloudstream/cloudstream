package com.lagradost.cloudstream3.ui.settings.testing

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.utils.Coroutines.atomicListOf
import com.lagradost.cloudstream3.utils.TestingUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel

class TestViewModel : ViewModel() {
    data class TestProgress(
        val passed: Int,
        val failed: Int,
        val total: Int
    )

    enum class ProviderFilter {
        All,
        Passed,
        Failed
    }

    private val _providerProgress = MutableLiveData<TestProgress>(null)
    val providerProgress: LiveData<TestProgress> = _providerProgress

    private val _providerResults =
        MutableLiveData<List<Triple<MainAPI, TestingUtils.TestResultProvider, TestingUtils.Logger>>>(
            emptyList()
        )

    val providerResults: LiveData<List<Triple<MainAPI, TestingUtils.TestResultProvider, TestingUtils.Logger>>> =
        _providerResults

    private var scope: CoroutineScope? = null
    val isRunningTest
        get() = scope != null

    private var filter = ProviderFilter.All
    private val providers =
        atomicListOf<Triple<MainAPI, TestingUtils.TestResultProvider, TestingUtils.Logger>>()
    private var passed = 0
    private var failed = 0
    private var total = 0

    private fun updateProgress() {
        _providerProgress.postValue(TestProgress(passed, failed, total))
        postProviders()
    }

    private fun postProviders() {
        providers.withLock {
            val filtered = when (filter) {
                ProviderFilter.All -> providers.toList()
                ProviderFilter.Passed -> providers.filter { it.second.success }
                ProviderFilter.Failed -> providers.filter { !it.second.success }
            }
            _providerResults.postValue(filtered)
        }
    }

    fun setFilterMethod(filter: ProviderFilter) {
        if (this.filter == filter) return
        this.filter = filter
        postProviders()
    }

    private fun addProvider(
        api: MainAPI,
        results: TestingUtils.TestResultProvider,
        logger: TestingUtils.Logger
    ) {
        providers.withLock {
            val index = providers.indexOfFirst { it.first == api }
            val triple = Triple(api, results, logger)
            if (index == -1) {
                providers.add(triple)
                if (results.success) passed++ else failed++
            } else {
                providers[index] = triple
            }
            updateProgress()
        }
    }

    fun init() {
        total = APIHolder.allProviders.withLock { APIHolder.allProviders.size }
        updateProgress()
    }

    fun startTest() {
        scope = CoroutineScope(Dispatchers.Default)

        val apis = APIHolder.allProviders.withLock { APIHolder.allProviders.toTypedArray() }
        total = apis.size
        failed = 0
        passed = 0
        providers.clear()
        updateProgress()

        TestingUtils.getDeferredProviderTests(scope ?: return, apis) { api, result, log ->
            addProvider(api, result, log)
        }
    }

    fun stopTest() {
        scope?.cancel()
        scope = null
    }
}