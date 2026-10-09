package com.lagradost.cloudstream4.state

/**
 * This ImmutableState file is used to make UI more reliable and faster by leveraging immutability.
 *
 * Because the UI rendering and ViewModel logic should run on different threads, it is very dangerous
 * to have mutable state in the viewmodel that can be observed by the UI layer. Therefore, we either
 * have to make a copy of the entire state, or we use persistant data structures.
 *
 * Persistant data structures offers us a O(0) copy to safely send to the UI layer, at the cost of
 * O(log(n)) updates. However, in practice this means that any update we make to a list used in UI
 * is free compared to a deep copy. Additionally, this means that we can not have concurrency issues
 * (ConcurrentModificationException) because nothing actually can be modified. Each function creates
 * a new copy.
 * */

import androidx.annotation.CheckResult
import androidx.compose.runtime.Immutable
import com.lagradost.cloudstream3.utils.Levenshtein
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentSet

/** Default filter function by matching the name, useful for local search */
@Immutable
data class FilterByQuery<Value>(
    val query: String,
    private val ratio: Int = 50,
    private val onEmpty: Boolean = true,
    val ignoreCase: Boolean = true,
    private val transform: (Value) -> String,
) : Function1<Value, Boolean> {
    override fun invoke(value: Value): Boolean {
        if (query.isEmpty()) return onEmpty
        val transformed = transform(value)
        return Levenshtein.ratio(
            query,
            if (ignoreCase) transformed.lowercase() else transformed
        ) > ratio
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        val other = other as? FilterByQuery<Value> ?: return false
        if (query != other.query) return false
        if (ratio != other.ratio) return false
        if (transform != other.transform) return false
        return true
    }

    override fun hashCode(): Int {
        var result = ratio
        result = 31 * result + query.hashCode()
        result = 31 * result + transform.hashCode()
        return result
    }

    @CheckResult
    fun update(newQuery: String): FilterByQuery<Value> {
        val lowerCaseQuery = if (ignoreCase) newQuery.lowercase() else newQuery
        return if (lowerCaseQuery == query) {
            this
        } else {
            copy(query = lowerCaseQuery)
        }
    }
}

/** Default sorting by name (Ascending A->Z), use `rev()` if Z->A is desired */
@Immutable
data class SortByName<Value>(
    val ignoreCase: Boolean = true,
    val transform: (Value) -> String,
) : Comparator<Value> {
    override fun compare(p0: Value?, p1: Value?): Int {
        if (p0 == p1) return 0
        if (p0 == null) return -1
        if (p1 == null) return 1
        return transform(p0).compareTo(transform(p1), ignoreCase)
    }
}

fun <T> sortByLong(keyExtractor: ((T) -> Long)): Comparator<T> {
    return Comparator { c1: T, c2: T ->
        keyExtractor(c1).compareTo(keyExtractor(c2))
    }
}

fun <T> sortByFloat(keyExtractor: ((T) -> Float)): Comparator<T> {
    return Comparator { c1: T, c2: T ->
        keyExtractor(c1).compareTo(keyExtractor(c2))
    }
}

fun <T> sortByInt(keyExtractor: ((T) -> Int)): Comparator<T> {
    return Comparator { c1: T, c2: T ->
        keyExtractor(c1).compareTo(keyExtractor(c2))
    }
}

fun <T> Comparator<T>.rev(): Comparator<T> {
    return Comparator { c1: T, c2: T ->
        this@rev.compare(c2, c1)
    }
}

/** Helper function to apply updater directly to a specific item instead of doing the get put manually */
fun <K, V> PersistentMap<K, V>.edit(key: K, updater: V.() -> V): PersistentMap<K, V> {
    val value = get(key) ?: return this
    val newValue = updater(value)
    return putting(key, newValue)
}

/**
 * **Associative** and **Invertible** derived state of the SearchableData values, useful for
 * counting/accumulating some value of the SearchableData values without the
 * headache of keeping track of correctly outside the SearchableData class when we might have:
 *
 * 1. Exceptions due to cancellation causing weird control-flow
 * 2. `updating` calls that need both the old and new value, but to store the derived state we need to
 * store the old and new values outside the `updating` call.
 * 3. `updateState` that might be called twice due to a race
 * */
interface DerivedState<Self : DerivedState<Self, Value>, Value> {
    /** State = {} */
    fun clearing(): Self

    /** State = State + value */
    fun adding(value: Value): Self

    /** State = State - value */
    fun removing(value: Value): Self
}

/**
 * For state that updated a lot we might need to calculate some accumulation, then
 * this is used to more ergonomically and efficiently do a `.filter {}` or `.count {}`.
 */
@Immutable
data class SearchableDataState<Key, Value, State : DerivedState<State, Value>>(
    private val internalData: SearchableData<Key, Value>,
    val state: State
) {
    val data get() = internalData.data
    val filtered get() = internalData.filtered
    val sorted get() = internalData.sorted
    val filteredBy get() = internalData.filteredBy
    val sortedBy get() = internalData.sortedBy

    @CheckResult
    fun sortingBy(newSorting: Comparator<Value>): SearchableDataState<Key, Value, State> =
        copy(internalData = internalData.sortingBy(newSorting))

    @CheckResult
    fun filteringBy(newFilter: ((Value) -> Boolean)?): SearchableDataState<Key, Value, State> =
        copy(internalData = internalData.filteringBy(newFilter))

    @CheckResult
    fun updating(
        key: Key,
        updater: Value.() -> Value
    ): SearchableDataState<Key, Value, State> {
        val oldItem = internalData.data[key] ?: return this
        val newInternalData = internalData.updating(key, updater)

        /**
         * State = State - old + new, and we require that new exists if old exists,
         * otherwise we have a bug in updating
         * */
        val newItem = requireNotNull(newInternalData.data[key])
        return copy(internalData = newInternalData, state = state.removing(oldItem).adding(newItem))
    }

    @CheckResult
    fun removing(
        key: Key,
    ): SearchableDataState<Key, Value, State> {
        val oldItem = internalData.data[key] ?: return this
        return copy(internalData = internalData.removing(key), state = state.removing(oldItem))
    }

    @CheckResult
    fun adding(key: Key, item: Value): SearchableDataState<Key, Value, State> {
        val oldItem = internalData.data[key]

        /** If it already exists then treat it as a replacing operation */
        val newState = if (oldItem != null) {
            state.removing(oldItem)
        } else {
            state
        }
        return copy(internalData = internalData.adding(key, item), state = newState.adding(item))
    }

    @CheckResult
    fun setTo(
        newData: PersistentMap<Key, Value>,
        newSortedBy: Comparator<Value> = internalData.sortedBy,
        newFilteredBy: ((Value) -> Boolean)? = internalData.filteredBy
    ): SearchableDataState<Key, Value, State> {
        var newState = this.state.clearing()
        for (entry in newData.values) {
            newState = newState.adding(entry)
        }
        return copy(
            internalData = internalData.setTo(
                newData = newData,
                newSortedBy = newSortedBy,
                newFilteredBy = newFilteredBy
            ), state = newState
        )
    }

    @CheckResult
    fun clearing() = copy(internalData = internalData.clearing(), state = state.clearing())

    companion object {
        @CheckResult
        fun <Key, Value, State : DerivedState<State, Value>> from(
            state: State,
            data: PersistentMap<Key, Value>,
            sortedBy: Comparator<Value>,
            filteredBy: ((Value) -> Boolean)? = null,
        ): SearchableDataState<Key, Value, State> {
            var newState = state.clearing()
            for (entry in data.values) {
                newState = newState.adding(entry)
            }
            return SearchableDataState(
                state = newState,
                internalData = SearchableData.from(
                    data = data,
                    sortedBy = sortedBy,
                    filteredBy = filteredBy
                )
            )
        }
    }
}

/**
 * Helper to filter and sort local items, optimized for update heavy workloads
 * without concurrency issues or exceptions. No method should throw or modify state!
 *
 * Updating the filter or sorting order will run in near linear time with the input, but item update
 * is optimized to run in O(log(n)) if the update does not affect the filtering or sorting order.
 *
 * Data.keys --(filteredBy)--> filtered --(sortedBy)--> sorted
 * */
@Immutable
data class SearchableData<Key, Value>(
    /**
     * This is where the actual values are stored, **before** filtering
     * only replicated once to make consistency easier.
     *
     * `filtered` and `sorted` should be used to index data, and this data structure makes sure that
     * if a key exists within sorted or filtered it **MUST** exist in data as well, so `data[sorted[...]]!!`
     * is a safe operation
     * */
    val data: PersistentMap<Key, Value>,
    /** The unsorted set of values **after** the filter is applied */
    val filtered: PersistentSet<Key>,
    /** The sorted array of value **after** the filter is applied */
    val sorted: PersistentList<Key>,
    /** Null = keep every item, otherwise apply the given
     * filter retain function to: *keep* on true, and *remove* on false */
    val filteredBy: ((Value) -> Boolean)?,
    /**
     * We must have some **transitive** sorting applied, as the sorting order of a Map is somewhat undefined.
     * Recommened is to use the Alphabetical name or even UUID if no actual value exist
     * */
    val sortedBy: Comparator<Value>,
) {
    @CheckResult
    fun sortingBy(newSorting: Comparator<Value>): SearchableData<Key, Value> {
        if (newSorting == sortedBy) {
            return this
        }
        return copy(
            sortedBy = newSorting,
            sorted = sort(data = data, filtered = filtered, sortedBy = newSorting)
        )
    }

    @CheckResult
    fun filteringBy(newFilter: ((Value) -> Boolean)?): SearchableData<Key, Value> {
        if (newFilter == filteredBy) {
            return this
        }
        val newFiltered = filter(data = data, filteredBy = newFilter)
        val newSorted = sort(data = data, filtered = newFiltered, sortedBy = sortedBy)
        return copy(filteredBy = newFilter, filtered = newFiltered, sorted = newSorted)
    }

    @CheckResult
    fun updating(
        key: Key,
        /** Does not guarantee that updater is called if key does not exist */
        updater: Value.() -> Value
    ): SearchableData<Key, Value> {
        val item = this.data[key]
        /** Item does not exist, callee problem as we do not want to throw an exception */
            ?: return this
        val newItem = updater(item)
        val newData = data.putting(key, newItem)

        val shouldContainItem = filteredBy == null || filteredBy(newItem)

        /**
         * Hidden -> Hidden, has no effect on the filtering or sorting
         * */
        if (!shouldContainItem && !filtered.contains(key)) {
            return copy(data = newData)
        }

        val newFiltered = if (shouldContainItem) {
            filtered.adding(key)
        } else {
            filtered.removing(key)
        }

        /** Shown -> Shown, If the size is the same then it did not do anything */
        if (newFiltered.size == filtered.size) {
            /** If the update did not change the ordering or filtering then do not sort again */
            if (sortedBy.compare(item, newItem) == 0) {
                return copy(data = newData)
            }

            return copy(
                data = newData,
                sorted = moveItem(
                    key = key,
                    item = newItem,
                    data = newData,
                    sorted = sorted,
                    sortedBy = sortedBy
                )
            )
        } else if (newFiltered.size < filtered.size) {
            /** Shown -> Hidden, If the update filtered out the item then just remove it from the list */
            return copy(data = newData, filtered = newFiltered, sorted = sorted.removing(key))
        } else
        {
            /** Hidden -> Shown, we only need to insert it to the sorted list */
            return copy(
                data = newData, filtered = newFiltered, sorted = addItemAt(
                    key = key,
                    item = item,
                    data = newData,
                    sorted = sorted,
                    sortedBy = sortedBy
                )
            )
        }
    }

    @CheckResult
    fun removing(
        key: Key,
    ): SearchableData<Key, Value> {
        val newData = data.removing(key)
        if (newData.size >= data.size) {
            /** Key did not exist, so we do not have to do anything :) */
            return this
        }

        val newFiltered = filtered.removing(key)
        if (newFiltered.size >= filtered.size) {
            /** Key was already filtered away,
             * so skip the sorted as well as it will not exist in sorted */
            return copy(data = newData)
        }

        return copy(data = newData, filtered = newFiltered, sorted = sorted.removing(key))
    }

    @CheckResult
    fun adding(key: Key, item: Value): SearchableData<Key, Value> {
        val newData = data.putting(key, item)

        /** Key already exists, so we call the update function with a constant key instead */
        if (newData.size == data.size) {
            return updating(key) { item }
        }

        /** If the item gets filtered away, then do not waste time to update sort */
        if (filteredBy != null && !filteredBy(item)) {
            return copy(data = newData)
        }

        val newFiltered = filtered.adding(key)

        return copy(
            data = newData,
            filtered = newFiltered,
            sorted = addItemAt(
                key = key,
                item = item,
                data = newData,
                sorted = sorted,
                sortedBy = sortedBy
            )
        )
    }

    @CheckResult
    fun setTo(
        newData: PersistentMap<Key, Value>,
        newSortedBy: Comparator<Value> = sortedBy,
        newFilteredBy: ((Value) -> Boolean)? = filteredBy
    ): SearchableData<Key, Value> {
        return from(data = newData, sortedBy = newSortedBy, filteredBy = newFilteredBy)
    }

    @CheckResult
    fun clearing() =
        copy(data = data.cleared(), filtered = filtered.cleared(), sorted = sorted.cleared())

    companion object {
        @CheckResult
        private fun <Key, Value> addItemAt(
            key: Key,
            item: Value,
            data: PersistentMap<Key, Value>,
            sorted: PersistentList<Key>,
            sortedBy: Comparator<Value>,
        ): PersistentList<Key> {
            /**
             * We assume that the Comparator is *transitive* and can therefore binary search for
             * the insertion point
             * */
            val foundMatch = sorted.binarySearch { mid ->
                sortedBy.compare(data[mid], item)
            }

            val insertionPoint = if (foundMatch >= 0) {
                /**
                 * If we find an equal element, then insert it after the exact match
                 *
                 * This should not happen often, and might lead to some weird behavior for two same
                 * elements, because this behavior does not guarantee a "stable" sort :/
                 *
                 * If this becomes a user UX issue then we need to add a field to addItemAt about
                 * the previous location
                 * */
                foundMatch
            } else {
                /** Otherwise insert it in the sorted order (using this very weird math by doc) */
                -1 - foundMatch
            }

            /** This is unfortunately still in O(n) because add
             * in the middle which is a deep copy even for PersistentList */
            return sorted.addingAt(insertionPoint, key)
        }

        @CheckResult
        private fun <Key, Value> moveItem(
            key: Key,
            item: Value,
            data: PersistentMap<Key, Value>,
            sorted: PersistentList<Key>,
            sortedBy: Comparator<Value>,
        ): PersistentList<Key> {
            val oldPosition = sorted.indexOf(key)
            require(oldPosition >= 0)

            /** Quick check if a>b or b>c, because then we need to move it */
            val needsMove = (oldPosition > 0 && sortedBy.compare(
                data[sorted[oldPosition - 1]],
                item
            ) > 0) || (oldPosition < (sorted.size - 1) && sortedBy.compare(
                item,
                data[sorted[oldPosition + 1]]
            ) > 0)

            if (!needsMove) {
                return sorted
            }

            return addItemAt(
                key = key,
                item = item,
                data = data,
                /** Move = remove + add */
                sorted = sorted.removingAt(oldPosition),
                sortedBy = sortedBy
            )
        }

        @CheckResult
        private fun <Key, Value> sort(
            data: PersistentMap<Key, Value>,
            filtered: PersistentSet<Key>,
            sortedBy: Comparator<Value>
        ): PersistentList<Key> = filtered
            .sortedWith { a, b -> sortedBy.compare(data[a], data[b]) }
            .toPersistentList()

        @CheckResult
        private fun <Key, Value> filter(
            data: PersistentMap<Key, Value>,
            filteredBy: ((Value) -> Boolean)? = null,
        ): PersistentSet<Key> {
            return if (filteredBy == null) data.keys.toPersistentSet() else {
                data.entries.mapNotNull { entry ->
                    if (filteredBy(entry.value)) entry.key else null
                }.toPersistentSet()
            }
        }

        @CheckResult
        fun <Key, Value> from(
            data: PersistentMap<Key, Value>,
            sortedBy: Comparator<Value>,
            filteredBy: ((Value) -> Boolean)? = null,
        ): SearchableData<Key, Value> {
            val filtered: PersistentSet<Key> =
                if (filteredBy == null) data.keys.toPersistentSet() else {
                    data.entries.mapNotNull { entry ->
                        if (filteredBy(entry.value)) entry.key else null
                    }.toPersistentSet()
                }

            val sorted = sort(data = data, filtered = filtered, sortedBy = sortedBy)

            return SearchableData(
                data = data,
                filtered = filtered,
                sorted = sorted,
                filteredBy = filteredBy,
                sortedBy = sortedBy,
            )
        }
    }
}