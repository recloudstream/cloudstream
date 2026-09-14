package com.lagradost.cloudstream3

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchNavigationTest {
    @Test
    fun `phone search reselection requests focus`() {
        assertTrue(
            shouldFocusSearchOnReselection(
                currentDestinationId = R.id.navigation_search,
                selectedDestinationId = R.id.navigation_search,
                isPhoneLayout = true,
            )
        )
    }

    @Test
    fun `tv search reselection keeps existing focus behavior`() {
        assertFalse(
            shouldFocusSearchOnReselection(
                currentDestinationId = R.id.navigation_search,
                selectedDestinationId = R.id.navigation_search,
                isPhoneLayout = false,
            )
        )
    }

    @Test
    fun `first search selection does not request focus`() {
        assertFalse(
            shouldFocusSearchOnReselection(
                currentDestinationId = R.id.navigation_home,
                selectedDestinationId = R.id.navigation_search,
                isPhoneLayout = true,
            )
        )
    }

    @Test
    fun `non-search reselection does not request focus`() {
        assertFalse(
            shouldFocusSearchOnReselection(
                currentDestinationId = R.id.navigation_home,
                selectedDestinationId = R.id.navigation_home,
                isPhoneLayout = true,
            )
        )
    }
}
