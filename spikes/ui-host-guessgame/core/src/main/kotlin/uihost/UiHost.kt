package uihost

import com.genovich.components.Action
import com.genovich.components.Show
import com.genovich.components.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

/**
 * Names one T-function port. One per `Show`-bound port; visual-ide would generate these from
 * `ActionDefinition.portDefaults`.
 */
class Port<I, O>(val name: String) {
    override fun toString() = name
}

/** One waiting T-function call: the `UiState(input, callback)` package, tagged with its port. */
class Request<I, O>(val port: Port<I, O>, private val state: UiState<I, O>) {
    val input: I get() = state.input
    fun answer(output: O) = state.action(output)
}

/** Every request that is waiting for an answer right now. */
class Live internal constructor(internal val byPort: Map<Port<*, *>, Request<*, *>>) {
    operator fun contains(port: Port<*, *>): Boolean = port in byPort

    @Suppress("UNCHECKED_CAST")
    operator fun <I, O> get(port: Port<I, O>): Request<I, O> = byPort.getValue(port) as Request<I, O>

    override fun toString() = byPort.keys.toString()

    companion object {
        val None = Live(emptyMap())
    }
}

/**
 * The external receiver of every T-function in one program.
 *
 * Logic never sees this class: the assembly turns each port into an ordinary `Action` with
 * [show]. Any client (Compose, a console, a test, a bot) observes [live] and answers requests.
 * There is no per-node projection: whatever is waiting is what the UI shows.
 */
class UiHost {
    private val _live = MutableStateFlow(Live.None)
    val live: StateFlow<Live> = _live

    /**
     * A T-function for [port]. It reuses the library's own `Show`, so the send, wait, resume
     * handshake (`showAndGetResult`) is unchanged: `null` arrives when the call is answered or
     * cancelled, and the request is withdrawn.
     */
    fun <I, O> show(port: Port<I, O>): Action<I, O> = Show { state: UiState<I, O>? ->
        _live.update { current ->
            Live(if (state == null) current.byPort - port else current.byPort + (port to Request(port, state)))
        }
    }

    /** Suspends until [port] has a waiting request. Handy for scripted clients and tests. */
    suspend fun <I, O> awaitRequest(port: Port<I, O>): Request<I, O> = live.first { port in it }[port]
}
