package com.lagradost.cloudstream3.ui.settings.testing

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.utils.Coroutines.atomicListOf
import com.lagradost.cloudstream3.utils.TestingUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

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
    private val providers = atomicListOf<Pair<MainAPI, TestingUtils.TestResultProvider>>()
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

    private fun addProvider(api: MainAPI, results: TestingUtils.TestResultProvider) {
        providers.withLock {
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

        TestingUtils.getDeferredProviderTests(scope ?: return, apis) { api, result ->
            addProvider(api, result)
        }
    }

    fun stopTest() {
        scope?.cancel()
        scope = null
    }

    fun deleteExtensions(paths: List<String>) {
        viewModelScope.launch {
            paths.forEach { path ->
                PluginManager.deletePlugin(File(path))
            }
            providers.withLock {
                providers.removeAll { it.first.sourcePlugin in paths }
                passed = providers.count { it.second.success }
                failed = providers.count { !it.second.success }
            }
            total = APIHolder.allProviders.withLock { APIHolder.allProviders.size }
            updateProgress()
        }
    }

    fun disableExtensions(paths: List<String>) {
        viewModelScope.launch {
            paths.forEach { path ->
                PluginManager.setPluginDisabled(path, true)
            }
            providers.withLock {
                providers.removeAll { it.first.sourcePlugin in paths }
                passed = providers.count { it.second.success }
                failed = providers.count { !it.second.success }
            }
            total = APIHolder.allProviders.withLock { APIHolder.allProviders.size }
            updateProgress()
        }
    }

    fun getFailedExtensions(): List<Pair<String, String>> {
        return providers.withLock {
            val failed = providers.filter { !it.second.success }.mapNotNull {
                val path = it.first.sourcePlugin ?: return@mapNotNull null
                it.first.name to path
            }.toSet()

            val passedPaths = providers.filter { it.second.success }.mapNotNull { it.first.sourcePlugin }.toSet()

            failed.filter { it.second !in passedPaths }.distinctBy { it.second }
        }
    }

    fun retryFailed() {
        scope?.cancel()
        scope = CoroutineScope(Dispatchers.Default)

        val failedApis = providers.withLock {
            val failed = providers.filter { !it.second.success }.map { it.first }
            providers.removeAll { !it.second.success }
            failed
        }.toTypedArray()

        if (failedApis.isEmpty()) return

        failed = providers.count { !it.second.success }
        passed = providers.count { it.second.success }
        updateProgress()

        TestingUtils.getDeferredProviderTests(scope ?: return, failedApis) { api, result ->
            addProvider(api, result)
        }
    }
}