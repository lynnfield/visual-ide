# End-to-end example — rung 1 (real per-port typing)

Rung 1 of the ladder in `docs/design.md` §6. Goal: replace the placeholder `<Input, Output>`
generics with per-port types inferred from the graph, so the generated function **type-checks**
(validating **H2**).

## The typing scheme (structural inference)

Types are inferred from the diagram structure alone — no domain-type knowledge required. Each
node contributes an `inferType(input, fresh, ports)` step (`ActionLayout.inferType`):

- **`Action`** (leaf port) — mints a fresh output type variable, records the port as
  `Action<input, fresh>`, returns the fresh var. Repeated port names reuse the first typing.
- **`Passing`** (sequence) — folds the input type through its children; the output of one is the
  input of the next.
- **`RepeatWhileActive`** (loop) — threads its input into the body, discards the body's output,
  returns `Nothing` (a loop never returns normally).
- **`RetryUntilResult`** (decorator) — passes the body's type through unchanged.

`ActionDefinition.generate()` then declares the class with the threaded type parameters
(`Input, T1..Tn`), typed ports, and the inferred output type.

## Expected generated `GuessLoop`

```kotlin
class `GuessLoop`<Input, T1, T2>(
    val `readGuess`: com.genovich.components.Action<Input, T1>,
    val `checkGuess`: com.genovich.components.Action<T1, T2>,
) : com.genovich.components.Action<Input, Nothing>() {
    override suspend operator fun invoke(input: Input): Nothing =
        com.genovich.components.repeatWhileActive {
            input
            .let { `readGuess`(it) }
            .let { `checkGuess`(it) }
        }
}
```

`readGuess: Action<Input, T1>` feeds `checkGuess: Action<T1, T2>`; the loop's `T2` is discarded
and the function returns `Nothing`. This type-checks — the rung-0 shared `<Input, Output>` did not
(it fed `Output` where `Input` was expected).

## Files changed

- `actions/ActionLayout.kt` — added the `inferType` step to the interface.
- `actions/Action.kt`, `Passing.kt`, `RepeatWhileActive.kt`, `RetryUntilResult.kt` —
  `inferType` implementations.
- `actions/ActionDefinition.kt` — `generate()` now threads type variables and typed ports;
  added `INPUT_TYPE` / `NOTHING_TYPE` constants.
- `GuessLoopGenerateTest` — assertions updated for the threaded types.
- `GuessLoopRoundTripTest` — added `testGuessLoopTypeChecks` (H2): configures the generated
  `GuessLoop` and runs `checkHighlighting(false, false, false)`, which fails on any type error.

## How to run

```
./gradlew :plugin:test
```

## Verification notes (couldn't run here)

- The H2 type-check relies on the `repeatWhileActive` stub being `suspend inline` — inlining lets
  the loop body call the suspend `invoke` operators. If it were non-inline, the body's suspend
  calls would not compile.
- Round-trip (H1) is unaffected: parsing ignores type parameters, so `generate ∘ parse ∘ generate`
  remains a fixed point on the typed output.

## Limitations / notes (feed back into §6)

- Inference is **structural** (type variables threaded through the graph). Reading *concrete*
  declared types from the source (via the future `KotlinAnalysis` port) is a later enhancement.
- Repeated port names reuse the first inferred typing — fine for distinct ports, but a program that
  uses one port at two incompatible types would need unification (not yet modeled).
- The function's output type is inferred, not a free parameter; a loop yields `Nothing`.
