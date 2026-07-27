package com.genovich.components

/**
 * Minimal stubs of the `components` library — just enough for UAST to resolve the symbols the
 * round-trip parsers match on (Action.invoke, repeatWhileActive, retryUntilResult) and the
 * symbols the generated assembly / state-projection code (rung 2 step 2) resolves against
 * (Show, StateFlow/MutableStateFlow, UiState, combine, emitSelfWhenHaveValue).
 *
 * This is the synthetic specimen's dependency surface (design doc §6.5). It is NOT the real
 * library; signatures are only as precise as resolution requires — in particular, [Show]'s body
 * is a `TODO()` stub rather than the real send-wait-resume handshake (design.md §1.6), since these
 * fixtures only need to type-check, never run.
 */
abstract class Action<in Input, out Output> {
    abstract suspend operator fun invoke(input: Input): Output
}

/** Face + interface, every node (design.md §2.4, D8); descriptor is inferred from the Kotlin
 * signature (D9) — these fields are cosmetics only, not modeled by the engine yet. */
annotation class Node(val label: String = "", val category: String = "")

/** Composite bodies only (design.md §2.4): identity ([checksum], a hash of the normalized body,
 * for drift detection) and optional cosmetic [layout]. */
annotation class Diagram(val version: Int = 1, val checksum: String = "", val layout: String = "")

suspend inline fun repeatWhileActive(block: () -> Unit): Nothing {
    while (true) {
        block()
    }
}

suspend inline fun <T> retryUntilResult(block: () -> T): T = block()

interface StateFlow<out T> {
    val value: T
}

class MutableStateFlow<T>(initial: T) : StateFlow<T> {
    override var value: T = initial
}

/** The `UiState(input, callback)` handoff package a T-function sends across the boundary (§1.6). */
class UiState<Input, Output>(
    val input: Input,
    val callback: (Output) -> Unit,
)

/** Binds a boundary port to a flow the UI observes; the T-function itself (design.md §1.6, §5.1). */
fun <Input, Output> Show(flow: MutableStateFlow<UiState<Input, Output>?>): Action<Input, Output> =
    object : Action<Input, Output>() {
        override suspend fun invoke(input: Input): Output = TODO("answered by the UI observing the flow")
    }

/**
 * Maps a boundary flow to its sealed-case flow: [wrap]'d while live, `null` while idle.
 *
 * A plain function, not an extension — generated code calls every `com.genovich.components`
 * symbol fully qualified with no imports (design.md D6), and an extension function can only be
 * called via receiver-dot syntax, which requires importing it.
 */
fun <T, R> emitSelfWhenHaveValue(flow: StateFlow<T?>, wrap: (T) -> R): StateFlow<R?> =
    object : StateFlow<R?> {
        override val value: R? get() = flow.value?.let(wrap)
    }

/** Fans multiple flows into one, recomputing [transform] from their current values. */
fun <T, R> combine(vararg flows: StateFlow<T>, transform: (List<T>) -> R): StateFlow<R> =
    object : StateFlow<R> {
        override val value: R get() = transform(flows.map { it.value })
    }
