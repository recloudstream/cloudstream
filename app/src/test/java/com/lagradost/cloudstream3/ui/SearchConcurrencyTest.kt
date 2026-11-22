package com.lagradost.cloudstream3.ui

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SearchConcurrencyTest {

    @Test
    fun semaphoreLimitsConcurrentExecutions() = runBlocking {
        val maxConcurrency = 5
        val semaphore = Semaphore(permits = maxConcurrency)
        val activeCount = AtomicInteger(0)
        val maxRecordedConcurrency = AtomicInteger(0)

        val jobs = (1..20).map {
            async(Dispatchers.Default) {
                semaphore.withPermit {
                    val current = activeCount.incrementAndGet()
                    maxRecordedConcurrency.updateAndGet { prev -> maxOf(prev, current) }
                    delay(50)
                    activeCount.decrementAndGet()
                }
            }
        }

        jobs.forEach { it.await() }
        assertTrue(
            "Max concurrent jobs should never exceed $maxConcurrency, was ${maxRecordedConcurrency.get()}",
            maxRecordedConcurrency.get() <= maxConcurrency
        )
        assertEquals(0, activeCount.get())
    }

    @Test
    fun timeoutAbortsHangingTasksAndReleasesPermit() = runBlocking {
        val semaphore = Semaphore(permits = 1)
        var timedOutTaskFinished = false
        var subsequentTaskFinished = false

        // Task 1: simulated slow scraper that hangs longer than timeout
        val task1Result = withTimeoutOrNull(100L) {
            semaphore.withPermit {
                delay(500L)
                timedOutTaskFinished = true
                "finished"
            }
        }
        assertEquals(null, task1Result)
        assertEquals(false, timedOutTaskFinished)

        // Task 2: must immediately acquire the released permit without deadlocking
        val task2Result = withTimeoutOrNull(200L) {
            semaphore.withPermit {
                subsequentTaskFinished = true
                "success"
            }
        }
        assertEquals("success", task2Result)
        assertEquals(true, subsequentTaskFinished)
    }
}
