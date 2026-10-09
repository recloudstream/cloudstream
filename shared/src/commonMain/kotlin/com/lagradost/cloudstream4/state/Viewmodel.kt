package com.lagradost.cloudstream4.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

/**
 * Use with the DefaultStateContainer
 *
 * `X : ViewModel(), StateContainer<XState> by DefaultStateContainer(XState)`
 */
interface StateContainer<State> {
    val state: StateFlow<State>
    fun updateState(reducer: State.() -> State)
}

/**
 * Use with the DefaultEffectContainer
 *
 * `X : ViewModel(), EffectContainer<XEffect> by DefaultEffectContainer()`
 */
interface EffectContainer<Effect> {
    val effect: Flow<Effect>
    suspend fun postEffect(builder: () -> Effect)
}

/**
 * Use an interface on the viewmodel directly
 *
 * `X : ViewModel(), ActionHandler<XAction>`
 */
interface ActionHandler<Action> {
    fun onAction(action: Action)
}

/**
 * The default implementation of a state container to allow by delegate.
 *
 * All state should be updated with updateState + copy to allow atomic operations
 * */
class DefaultStateContainer<State>(initialState: State) : StateContainer<State> {
    private val _state = MutableStateFlow(initialState)
    override val state: StateFlow<State> = _state.asStateFlow()

    /** Do not do expensive operations in updateState, as this can get rerun twice or more if we have concurrent writers */
    override fun updateState(reducer: State.() -> State) {
        _state.update(reducer)
    }
}

/**
 * The default implementation of an effect container to allow by delegate
 * */
class DefaultEffectContainer<Effect> : EffectContainer<Effect> {
    private val _effect = Channel<Effect>() // To ensure events are sent
    override val effect: Flow<Effect> = _effect.receiveAsFlow()

    override suspend fun postEffect(builder: () -> Effect) {
        _effect.send(builder())
    }
}

/**
 * Observe the viewmodel EffectContainer in the UI on the Main thread for e.g. toasts
 * */
@Composable
fun <T> ObserveEffect(flow: Flow<T>, onEvent: (T) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(flow, lifecycleOwner.lifecycle) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            withContext(Dispatchers.Main.immediate) { // To ensure events are sent
                flow.collect(onEvent)
            }
        }
    }
}

/** Single active job, replacing the old job by cancellation if launched again */
data class SingleActiveQuery(
    val dispatcher: CoroutineDispatcher,
    private var job: Job? = null,
    private val mutex: Mutex = Mutex(),
) {
    suspend fun cancel() {
        mutex.withLock {
            job?.cancel()
            job?.join()
            job = null
        }
    }

    suspend fun launch(block: suspend CoroutineScope.() -> Unit) {
        val currentScope = CoroutineScope(currentCoroutineContext())
        val obj: suspend CoroutineScope.() -> Unit = {
            try {
                withContext(dispatcher) {
                    block()
                }
            } catch (_: Throwable) {
                //logError(t)
            }
        }
        mutex.withLock {
            job?.cancel()
            job?.join()
            job = currentScope.launch(block = obj)
        }
    }
}

/** Multiple active jobs with labels, cancelling the old job if a new one is launched with the same label */
data class MultiActiveQuery<T>(
    val dispatcher: CoroutineDispatcher,
    private var supervisor: Job = SupervisorJob(),
    private val mutex: Mutex = Mutex(),
    private val queries: MutableMap<T, Job> = mutableMapOf()
) {
    suspend fun cancelAll() {
        mutex.withLock {
            supervisor.cancelAndJoin()
            queries.clear()
            supervisor = SupervisorJob()
        }
    }

    suspend fun cancelOne(label: T, message: String? = null) {
        mutex.withLock {
            queries.remove(label)?.let { job ->
                if(message != null) {
                    job.cancel(message)
                } else {
                    job.cancel()
                }
                job.join()
            }
        }
    }

    suspend fun launch(label: T, block: suspend CoroutineScope.() -> Unit) {
        val currentScope = CoroutineScope(currentCoroutineContext() + supervisor)
        val obj: suspend CoroutineScope.() -> Unit = {
            try {
                withContext(dispatcher) {
                    block()
                }
            } catch (_: Throwable) {
            } finally {
                // This actually does not need a `withContext(NonCancellable)`, because if this gets
                // canceled the callee will also clear this key. This means that it is free to skip
                // in case of cancellation
                mutex.withLock {
                    // Clean up the key so we do not leek shit
                    queries.remove(label)
                }
            }
        }

        mutex.withLock {
            queries[label]?.let { activeJob ->
                activeJob.cancel()
                activeJob.join()
            }
            queries[label] = currentScope.launch(block = obj)
        }
    }
}

/** Debounce Query to handle e.g. user search without spamming the endpoint */
data class DebounceQuery(
    private val pipe: MutableSharedFlow<String> = MutableSharedFlow(
        extraBufferCapacity = 64,
        replay = 64
    )
) {
    @OptIn(FlowPreview::class)
    suspend fun launch(collector: FlowCollector<String>) {
        pipe.debounce(200L.milliseconds).distinctUntilChanged()
            .flowOn(Dispatchers.Default)
            .collect(collector)
    }

    suspend fun emit(query: String) {
        this.pipe.emit(query)
    }
}

/**
 * This interface is used to mark each item as unique no matter the content.
 *
 * For UI, we might have to display the item as a list
 * To do this we use a randomized uuid to act as a unique "key" to act as a cheap hash function.
 * */
interface UniqueItem {
    val uuid: Uuid
}

/**
 * Allows the use of `UniqueItem by NonCopyUniqueItem()` on regular classes by using `Uuid.random()`
 *
 * HOWEVER: This is different from using `: UniqueItem` on a data class, because a `.copy(...)` will
 * generate a ***New*** `randomUuid` as opposed to keeping the same `randomUuid`.
 *
 * For data classes the preferred behavior is almost always:
 * `override val randomUuid : Uuid = Uuid.random()` and `: UniqueItem`
 * */
data class NonCopyUniqueItem(override val uuid: Uuid = Uuid.random()) : UniqueItem