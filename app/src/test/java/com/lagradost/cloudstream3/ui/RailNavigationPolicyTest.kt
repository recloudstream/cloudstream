package com.lagradost.cloudstream3.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RailNavigationPolicyTest {
    @Test fun horizontalRowsOnlyRevealAtTheFirstItem() {
        assertTrue(RailNavigationPolicy.isLeadingItem(0, true, 1))
        assertFalse(RailNavigationPolicy.isLeadingItem(1, true, 1))
        assertFalse(RailNavigationPolicy.isLeadingItem(6, true, 1))
    }
    @Test fun gridsRevealAtTheLeadingColumn() {
        assertTrue(RailNavigationPolicy.isLeadingItem(8, false, 4))
        assertFalse(RailNavigationPolicy.isLeadingItem(9, false, 4))
        assertFalse(RailNavigationPolicy.isLeadingItem(-1, false, 4))
    }
}
