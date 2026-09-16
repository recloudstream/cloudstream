package com.mihon.common.preference

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.reflect.KProperty1

/** Adds a store to modify a specific state field by field */
data class StatePreferenceData<T, V>(
    val state: MutableState<T>,
    val get: (T) -> V,
    val set: T.(V) -> T,
) : PreferenceData<V> {
    override fun key(): String = ""

    override fun get(): V {
        return get(state.value)
    }

    override fun set(value: V) {
        state.value = set(state.value, value)
    }

    override fun isSet(): Boolean {
        throw NotImplementedError()
    }

    override fun delete() {
        throw NotImplementedError()
    }

    override fun defaultValue(): V {
        throw NotImplementedError()
    }

    override fun changes(): Flow<V> =
        snapshotFlow { state.value }.map { s -> get(s) }.distinctUntilChanged()

    override fun stateIn(scope: CoroutineScope): StateFlow<V> {
        return changes().stateIn(scope, SharingStarted.Eagerly, get())
    }
}

data class StatePreferenceStore<T>(val state: MutableState<T>) {
    fun <V> field(
        property: KProperty1<T, V>,
        update: T.(V) -> T,
    ): PreferenceData<V> {
        return StatePreferenceData(state, property::get, update)
    }

    fun <V> field(
        get: T.() -> V,
        set: T.(V) -> T,
    ): PreferenceData<V> {
        return StatePreferenceData(state, get, set)
    }
}