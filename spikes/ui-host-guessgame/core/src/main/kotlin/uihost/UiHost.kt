package uihost

import com.genovich.components.Action
import com.genovich.components.Show
import com.genovich.components.UiState
import com.genovich.components.parallel
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

/** Every request that is waiting for an answer right now, in one host. */
class Live internal constructor(internal val byPort: Map<Port<*, *>, Request<*, *>>) {
    operator fun contains(port: Port<*, *>): Boolean = port in byPort

    @Suppress("UNCHECKED_CAST")
    operator fun <I, O> get(port: Port<I, O>): Request<I, O> = byPort.getValue(port) as Request<I, O>

    override fun toString() = byPort.keys.toString()

    companion object {
        val None = Live(emptyMap())
    }
}

/** How a child scope relates to what is already on screen around it. */
enum class Placement {
    /** Replaces its siblings (an open article over Home). Back closes it. */
    Cover,

    /** Shown next to its siblings as an optional slot (a mini-player). Back skips it. */
    Beside,
}

/**
 * Identifies a scope and the screens interface that draws it. Generated next to each feature's
 * `XScreens<R>` interface, so a parent never needs the child's screens type: only the key.
 *
 * [render] is generated too; it is the only place that casts, and the cast is safe because
 * implementations can only be registered through the generated, typed `register` extension.
 */
abstract class ScopeKey(val name: String, val placement: Placement = Placement.Cover) {
    abstract fun <R> render(screens: Screens<R>, host: UiHost): R?
    override fun toString() = name
}

/**
 * The client's screen implementations, by scope key. A feature loaded later registers itself
 * when it arrives; the only runtime failure left is "not registered yet".
 */
class Screens<R> {
    private val byKey = mutableMapOf<ScopeKey, Any>()

    /** Untyped entry point for generated `register` extensions only. */
    fun registerUnchecked(key: ScopeKey, implementation: Any) {
        byKey[key] = implementation
    }

    /** Lookup for generated `ScopeKey.render` only. */
    fun implementationFor(key: ScopeKey): Any =
        byKey[key] ?: error("No screens registered for scope '$key'")

    /** Draws [host] (any level of the tree) with whatever was registered for its key. */
    fun render(host: UiHost): R? = host.key.render(this, host)
}

/**
 * The external receiver of every T-function in one scope. Hosts form a tree: a child host
 * exists exactly while its scope (a feature) is running.
 *
 * Logic never sees this class: the assembly turns each port into an ordinary `Action` with
 * [show], and scope operators such as [onDemandFeature] open children with [scope].
 */
class UiHost private constructor(
    val key: ScopeKey,
    private val parent: UiHost?,
    /** The parent's port that closes this scope, if any; Back answers it. */
    private val closePort: Port<*, Unit>?,
) {
    constructor(key: ScopeKey) : this(key, null, null)

    private val _live = MutableStateFlow(Live.None)
    val live: StateFlow<Live> = _live

    private val _children = MutableStateFlow<List<UiHost>>(emptyList())
    val children: StateFlow<List<UiHost>> = _children

    private val _version = MutableStateFlow(0L)

    /** Bumps on any change anywhere below this host. Clients redraw from the root on it. */
    val version: StateFlow<Long> = _version

    private fun changed() {
        _version.update { it + 1 }
        parent?.changed()
    }

    /** A T-function for [port], answered by whoever draws this host. Reuses the library's `Show`. */
    fun <I, O> show(port: Port<I, O>): Action<I, O> = Show { state: UiState<I, O>? ->
        _live.update { current ->
            Live(if (state == null) current.byPort - port else current.byPort + (port to Request(port, state)))
        }
        changed()
    }

    /** Runs [block] in a child host tagged with [key]; the child is removed when [block] ends. */
    suspend fun <T> scope(key: ScopeKey, closePort: Port<*, Unit>? = null, block: suspend (UiHost) -> T): T {
        val child = UiHost(key, this, closePort)
        _children.update { it + child }
        changed()
        try {
            return block(child)
        } finally {
            _children.update { it - child }
            changed()
        }
    }

    fun child(key: ScopeKey): UiHost? = _children.value.firstOrNull { it.key == key }

    /**
     * System Back: answers the close request of the deepest open [Placement.Cover] scope.
     * Returns `false` when nothing can be closed, so the platform should handle Back itself.
     */
    fun back(): Boolean {
        for (child in _children.value.asReversed()) {
            if (child.key.placement != Placement.Cover) continue
            if (child.back()) return true
            val close = child.closePort ?: continue
            if (close in _live.value) {
                _live.value[close].answer(Unit)
                return true
            }
        }
        return false
    }

    /** Suspends until [port] has a waiting request. Handy for scripted clients and tests. */
    suspend fun <I, O> awaitRequest(port: Port<I, O>): Request<I, O> = live.first { port in it }[port]

    /** Suspends until a child scope with [key] is open. */
    suspend fun awaitChild(key: ScopeKey): UiHost = children.first { list -> list.any { it.key == key } }
        .first { it.key == key }

    override fun toString() = "$key${_live.value}${_children.value}"
}

/**
 * The library's OnDemandFeature, host-aware: waits on [open], then runs a fresh feature in a
 * child scope until [close] is answered. [content] is built per opening, so on-demand assembly
 * and the child host are the same moment.
 */
fun <I, F> onDemandFeature(
    host: UiHost,
    open: Port<I, F>,
    close: Port<I, Unit>,
    key: ScopeKey,
    content: (UiHost) -> Action<F, Nothing>,
): Action<I, Unit> = Action { input ->
    val opened = host.show(open)(input)
    host.scope(key, closePort = close) { child ->
        parallel<I, Unit>(input, { host.show(close)(it) }, { content(child)(opened) })
    }
}

/** Runs a feature that is always open (no open/close ports) in its own child scope. */
fun <I, O> scoped(host: UiHost, key: ScopeKey, content: (UiHost) -> Action<I, O>): Action<I, O> =
    Action { input -> host.scope(key) { child -> content(child)(input) } }
