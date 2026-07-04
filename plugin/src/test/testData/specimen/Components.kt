package com.genovich.components

/**
 * Minimal stubs of the `components` library — just enough for UAST to resolve the symbols the
 * round-trip parsers match on (Action.invoke, repeatWhileActive, retryUntilResult).
 *
 * This is the synthetic specimen's dependency surface (design doc §6.5). It is NOT the real
 * library; signatures are only as precise as resolution requires.
 */
abstract class Action<in Input, out Output> {
    abstract suspend operator fun invoke(input: Input): Output
}

suspend inline fun repeatWhileActive(block: () -> Unit): Nothing {
    while (true) {
        block()
    }
}

suspend inline fun <T> retryUntilResult(block: () -> T): T = block()
