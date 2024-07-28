package com.lagradost.cloudstream3

import com.lagradost.cloudstream3.ui.settings.extensions.PluginAdapter.Companion.findClosestBase2
import org.junit.Assert
import org.junit.Test

class PluginAdapterTest {
    @Test
    fun testFindClosestBase2() {
        Assert.assertEquals(16, findClosestBase2(0))
        Assert.assertEquals(256, findClosestBase2(170))
        Assert.assertEquals(256, findClosestBase2(256))
        Assert.assertEquals(512, findClosestBase2(257))
        Assert.assertEquals(512, findClosestBase2(700))
    }
}