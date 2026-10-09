package com.lagradost.cloudstream3.ui.settings.testing

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.utils.TestingUtils.TestResultProvider
import com.lagradost.cloudstream3.utils.TestingUtils.runSingleProviderTest
import com.lagradost.cloudstream4.state.ActionHandler
import com.lagradost.cloudstream4.state.DefaultStateContainer
import com.lagradost.cloudstream4.state.DerivedState
import com.lagradost.cloudstream4.state.FilterByQuery
import com.lagradost.cloudstream4.state.Log
import com.lagradost.cloudstream4.state.LogItem
import com.lagradost.cloudstream4.state.LogLevel
import com.lagradost.cloudstream4.state.MultiActiveQuery
import com.lagradost.cloudstream4.state.SearchableDataState
import com.lagradost.cloudstream4.state.SortByName
import com.lagradost.cloudstream4.state.StateContainer
import com.lagradost.cloudstream4.state.UniqueItem
import com.lagradost.cloudstream4.state.edit
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

@Immutable
data class ImmutableTestResult(
    val name: String,
    val language: String,
    val plugin: String?,
    override val uuid: Uuid,
    val log: PersistentList<LogItem> = persistentListOf(),
    val highestLogLevel: LogLevel = LogLevel.Verbose,
    val isLoading: Boolean = false,
    val result: TestResultProvider? = null,
) : UniqueItem {
    fun addMessage(message: LogItem): ImmutableTestResult {
        val lastMessage = log.lastOrNull()

        /** If we have a very similar message at almost the same time,
         * then append it instead of adding a new message*/
        if (lastMessage != null
            && lastMessage.level == message.level
            && lastMessage.tag == message.tag
            && (message.date - lastMessage.date) < 100.milliseconds
        ) {
            return copy(
                log = log.replacingAt(log.lastIndex, lastMessage.copy(
                    message = lastMessage.message + "\n" + message.message,
                    date = message.date
                ))
            )
        }

        return copy(log = log.adding(message))
    }
}

@Immutable
data class FilterTestResults(
    val searchFilter: FilterByQuery<ImmutableTestResult> = FilterByQuery(query = "") { it.name },
    val levelFilter: PersistentSet<LogLevel> = LogLevel.entries.toPersistentSet(),
) : Function1<ImmutableTestResult, Boolean> {
    override fun invoke(result: ImmutableTestResult): Boolean =
        levelFilter.contains(result.highestLogLevel) && searchFilter(result)

    fun setQuery(newQuery: String): FilterTestResults {
        return copy(searchFilter = searchFilter.update(newQuery))
    }

    fun setLevel(newLevel: PersistentSet<LogLevel>): FilterTestResults {
        return copy(levelFilter = newLevel)
    }
}

@Immutable
data class DerivedTestState(
    val highestLogLevelCount: PersistentMap<LogLevel, Int> = zero
) : DerivedState<DerivedTestState, ImmutableTestResult> {
    companion object {
        val zero = LogLevel.entries.associateWith { 0 }.toPersistentMap()
    }

    override fun clearing(): DerivedTestState = DerivedTestState()
    override fun adding(value: ImmutableTestResult): DerivedTestState =
        copy(highestLogLevelCount = highestLogLevelCount.edit(value.highestLogLevel) { plus(1) })

    override fun removing(value: ImmutableTestResult): DerivedTestState =
        copy(highestLogLevelCount = highestLogLevelCount.edit(value.highestLogLevel) { minus(1) })
}


/**
 * To avoid issues with the uniqueness of a MainAPI, we use an Uuid to refer to each MainAPI
 * */
@Immutable
data class TestState(
    val sorting: SortByName<ImmutableTestResult> = SortByName { it.name },
    val filtering: FilterTestResults = FilterTestResults(),
    val items: SearchableDataState<Uuid, ImmutableTestResult, DerivedTestState> = SearchableDataState.from(
        state = DerivedTestState(),
        data = persistentMapOf(),
        sortedBy = sorting,
        filteredBy = filtering
    ),
    val activeQueries: Int = 0,
    val internalApis: PersistentMap<Uuid, MainAPI> = persistentMapOf(),
) {
    val total: Int = internalApis.size
}


@Immutable
sealed class TestAction {
    object Start : TestAction()
    object Cancel : TestAction()
    data class Init(val apis: List<MainAPI>) : TestAction()
    data class StartSingle(val uuid: Uuid) : TestAction()
    data class ClearSingle(val uuid: Uuid) : TestAction()
    data class CancelSingle(val uuid: Uuid) : TestAction()
    data class Search(val query: String) : TestAction()
    data class SetLevel(val level: PersistentSet<LogLevel>) : TestAction()
}

class TestViewModel2 : ViewModel(), StateContainer<TestState> by DefaultStateContainer(TestState()),
    ActionHandler<TestAction> {
    private val dispatcher = MultiActiveQuery<Uuid>(Dispatchers.IO)

    override fun onAction(action: TestAction) {
        when (action) {
            TestAction.Cancel -> {
                viewModelScope.launch {
                    dispatcher.cancelAll()
                }
            }

            TestAction.Start -> {
                viewModelScope.launch {
                    runAll()
                }
            }

            is TestAction.StartSingle -> {
                viewModelScope.launch {
                    runSingleTest(action.uuid)
                }
            }

            is TestAction.CancelSingle -> {
                viewModelScope.launch {
                    dispatcher.cancelOne(action.uuid)
                }
            }

            is TestAction.ClearSingle -> {
                viewModelScope.launch {
                    /** Replace the existing work by cancelling and then clearing */
                    dispatcher.launch(action.uuid) {
                        updateState {
                            copy(items = items.updating(action.uuid) {
                                copy(
                                    log = log.cleared(),
                                    highestLogLevel = LogLevel.Verbose,
                                    result = null
                                )
                            })
                        }
                    }
                }
            }

            is TestAction.Init -> {
                updateState {
                    /** We do a reverse map lookup here to keep the old data and insert new elements */
                    val inverseMap = internalApis.map { entry -> entry.value to entry.key }.toMap()

                    val newInternalApis =
                        /** Make sure it is distinct to avoid possible runtime crashes */
                        action.apis.distinct()
                            /** Keep the old uuid if it exists */
                            .associateBy { api -> inverseMap[api] ?: Uuid.random() }
                            .toPersistentMap()

                    /** By default, they are idle, but we also keep the old data */
                    val newData = (newInternalApis.mapValues { entry ->
                        // val pluginInstance = (entry.value.sourcePlugin?.let { PluginManager.plugins[it] } as? Plugin)?.filename
                        // There is no way to get the icon without a get request...
                        ImmutableTestResult(
                            language = entry.value.lang,
                            name = entry.value.name,
                            plugin = null, // The readable plugin name is wonky af to get
                            uuid = entry.key,
                        )
                    } + items.data).toPersistentMap()

                    val map = LogLevel.entries.associateWith { 0 }.toMutableMap()
                    for (entry in newData) {
                        map[entry.value.highestLogLevel] = map[entry.value.highestLogLevel]!! + 1
                    }

                    copy(
                        internalApis = newInternalApis,
                        items = items.setTo(newData),
                    )
                }
            }

            is TestAction.Search -> {
                updateState {
                    val newFiltering = filtering.setQuery(action.query)
                    copy(filtering = newFiltering, items = items.filteringBy(newFiltering))
                }
            }

            is TestAction.SetLevel -> {
                updateState {
                    val newFiltering = filtering.setLevel(action.level)
                    copy(filtering = newFiltering, items = items.filteringBy(newFiltering))
                }
            }
        }
    }

    private suspend fun runAll() {
        val apis = state.value.items.sorted

        for (api in apis) {
            runSingleTest(api)
        }
    }

    private suspend fun runSingleTest(uuid: Uuid) {
        val internalApi = state.value.internalApis[uuid] ?: return

        dispatcher.launch(uuid) {
            updateState {
                val newItems = items.updating(uuid) {
                    copy(
                        isLoading = true,
                        log = persistentListOf(),
                        highestLogLevel = LogLevel.Verbose,
                        result = null,
                    )
                }
                copy(
                    /**
                     * We assume that runSingleProviderTest will not crash, so the final updateState
                     * will always be called and decrement this
                     * */
                    activeQueries = activeQueries + 1,
                    items = newItems
                )
            }

            val logger = object : Log {
                override fun log(item: LogItem) {
                    updateState {
                        copy(items = items.updating(uuid) {
                            addMessage(item)
                        })
                    }
                }
            }

            val result = runSingleProviderTest(internalApi, logger)

            updateState {
                copy(
                    activeQueries = activeQueries - 1,
                    items = items.updating(uuid) {
                        copy(
                            isLoading = false,
                            result = result,
                            /** Only set the log level when done to prevent items from sorting directly */
                            highestLogLevel = log.fold(
                                highestLogLevel
                            ) { acc, item -> acc.highestLogLevel(item.level) }
                        )
                    })
            }
        }
    }
}