package com.lagradost.cloudstream3.ui.settings.testing

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.utils.Coroutines.threadSafeListOf
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
        MutableLiveData<List<Pair<MainAPI, TestingUtils.TestResultProvider>>>(
            emptyList()
        )

    val providerResults: LiveData<List<Pair<MainAPI, TestingUtils.TestResultProvider>>> =
        _providerResults

    private var scope: CoroutineScope? = null
    val isRunningTest
        get() = scope != null

    private var filter = ProviderFilter.All
    private val providers = threadSafeListOf<Pair<MainAPI, TestingUtils.TestResultProvider>>()
    private var passed = 0
    private var failed = 0
    private var total = 0

    private fun updateProgress() {
        _providerProgress.postValue(TestProgress(passed, failed, total))
        postProviders()
    }

    private fun postProviders() {
        synchronized(providers) {
            val filtered = when (filter) {
                ProviderFilter.All -> providers
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

    private fun addProvider(api: MainAPI, results: TestingUtils.TestResultProvider) {
        synchronized(providers) {
            val index = providers.indexOfFirst { it.first == api }
            if (index == -1) {
                providers.add(api to results)
                if (results.success) passed++ else failed++
            } else {
                providers[index] = api to results
            }
            updateProgress()
        }
    }

    fun init() {
        val apis = APIHolder.allProviders
        total = apis.size
        updateProgress()
    }

    fun startTest() {
        scope = CoroutineScope(Dispatchers.Default)

        val apis = APIHolder.allProviders
        total = apis.size
        failed = 0
        passed = 0
        providers.clear()
        updateProgress()

        TestingUtils.getDeferredProviderTests(scope ?: return, apis, ::println) { api, result ->
            addProvider(api, result)
        }
    }

    fun stopTest() {
        scope?.cancel()
        scope = null
    }
}