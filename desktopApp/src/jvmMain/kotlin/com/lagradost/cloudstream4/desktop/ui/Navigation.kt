package com.lagradost.cloudstream4.desktop.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lagradost.cloudstream4.desktop.data.MediaEntry

enum class Tab(val title: String) {
    Home("Home"),
    Search("Search"),
    Library("Library"),
    Downloads("Downloads"),
    Extensions("Extensions"),
    Settings("Settings"),
}

/** A single episode or movie that can be played */
data class Playable(
    val data: String,
    val title: String,
    val season: Int? = null,
    val episode: Int? = null,
    val posterUrl: String? = null,
)

sealed interface Route {
    data class Result(val media: MediaEntry) : Route

    /** [index] is the item in [playlist] to start with, so next/previous episode works */
    data class Player(val media: MediaEntry, val playlist: List<Playable>, val index: Int) : Route
}

class Navigator {
    var tab by mutableStateOf(Tab.Home)
        private set
    val stack = mutableStateListOf<Route>()

    val current: Route? get() = stack.lastOrNull()

    fun select(tab: Tab) {
        this.tab = tab
        stack.clear()
    }

    fun push(route: Route) {
        stack.add(route)
    }

    /** Replace the top of the stack, used when switching episodes in the player */
    fun replace(route: Route) {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
        stack.add(route)
    }

    fun back(): Boolean {
        if (stack.isEmpty()) return false
        stack.removeAt(stack.lastIndex)
        return true
    }
}

val LocalNavigator = compositionLocalOf<Navigator> { error("No navigator") }
