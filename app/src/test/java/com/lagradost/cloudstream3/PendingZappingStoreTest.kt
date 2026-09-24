package com.lagradost.cloudstream3

import com.lagradost.cloudstream3.ui.player.PendingZappingStore
import com.lagradost.cloudstream3.ui.player.ZappingChannel
import com.lagradost.cloudstream3.ui.player.ZappingContext
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingZappingStoreTest {
    @Test
    fun `consumes a single provider context when load url is normalized`() {
        PendingZappingStore.clear()
        val context = ZappingContext(
            channels = listOf(
                ZappingChannel("Mor Spor 1", "https://example.test/channel/1/", "TestProvider"),
                ZappingChannel("Mor Spor 2", "https://example.test/channel/2/", "TestProvider"),
            ),
            currentIndex = 0,
        )

        PendingZappingStore.put("https://example.test/channel/1/", "TestProvider", context)

        assertEquals(
            context,
            PendingZappingStore.consume(
                "https://example.test/normalized-live-url",
                "TestProvider",
                "Mor Spor 1"
            )
        )
    }
}
