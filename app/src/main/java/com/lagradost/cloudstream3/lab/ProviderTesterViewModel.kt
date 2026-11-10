package com.lagradost.cloudstream3.lab

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.mapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class ProviderTesterViewModel : ViewModel() {
    val output = MutableLiveData("Select a downloaded provider and a method. Calls run only when you press Run.")
    val running = MutableLiveData(false)
    private var call: Job? = null

    fun run(provider: MainAPI, request: ManualProviderRunner.Request) {
        if (running.value == true) return
        running.value = true
        output.value = "Calling ${provider.name}: ${request.method.title}…"
        call = viewModelScope.launch {
            val start = System.nanoTime()
            try {
                val json = withTimeout(120_000) {
                    withContext(Dispatchers.IO) {
                        val response = ManualProviderRunner.run(provider, request)
                        mapper.writerWithDefaultPrettyPrinter().writeValueAsString(response)
                    }
                }
                output.value = "${provider.name} · ${request.method.title}\nCompleted in ${(System.nanoTime() - start) / 1_000_000} ms\n\n$json"
            } catch (timeout: TimeoutCancellationException) {
                output.value = "Timed out after 120 seconds. The provider did not finish the call."
            } catch (cancelled: CancellationException) {
                output.value = "Call cancelled."
                throw cancelled
            } catch (error: Throwable) {
                output.value = "${provider.name} · ${request.method.title}\n\n${error.stackTraceToString()}"
            } finally {
                running.value = false
            }
        }
    }

    fun cancel() { call?.cancel() }
}
