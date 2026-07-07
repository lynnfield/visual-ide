# End-to-end example — rung 2, step 2 (T-function + state projection)

Rung 2 of the ladder in `docs/design.md` §6, step 2 of `docs/implementation-plan.md`. Goal:
introduce the T-function (design.md §1.6, §5.1) and the derived `<Name>UiStateFlow`, validating
the **projection half of H3** (state projection is mechanically generable) and **H7** (a
`Show`-bound port renders as a T and derives a `UiStateFlow` leaf). Step 1's wiring half of H3
(`docs/example-rung2.md`) is unaffected — this step only adds to it.

## The T-function scheme

A leaf port (`Action`) now carries an `isTFunction: MutableState<Boolean>` marker (default
`false`), toggled via a checkbox in its `Render`. Per design.md §5.1, T-ness is **invisible at the
function level** — `Action.generate()` and `Action.parse()` are unchanged, so it never appears in
the function file and does not round-trip through `parse()` (recovering it would need
`parseAssembly`, which isn't implemented — see the rung 2 step 1 limitations). It only affects
`ActionDefinition.generateAssembly()` and the new `generateUiStateFlow()`.

Threading T-ness through `inferType` required widening the ports map's value type from a bare
`Pair<String, String>` to a new `ActionLayout.PortSignature(inputType, outputType, isTFunction)`
— every node's `inferType` signature changed accordingly (mechanical; no behavior change for
non-`Action` nodes, which just forward the map).

`generateAssembly()` now gives each T-function port a default:

```
`<port>`: com.genovich.components.Action<In, Out> = com.genovich.components.Show(`uiStateFlow`.`<port>Flow`)
```

sourced from a `<Name>UiStateFlow` instance threaded in as the assembly's own **first** parameter
(defaulted to a fresh instance, so ordinary callers don't need to pass anything), following D5's
override-seam pattern — callers who want to observe the boundary state supply their own instance
and keep a reference to it. This shape is forced by a real Kotlin constraint: a parameter's default
expression may only reference *earlier parameters in the same list*, never a local `val` from the
function body — so `<Name>UiStateFlow` could not be a body-local instantiated after the fact, and
(since ports are still abstract type variables at this rung, not concrete domain types) it can't be
a singleton `object` either, because Kotlin objects can't be generic.

`generateUiStateFlow()` produces a class with one `MutableStateFlow<UiState<In, Out>?>` per
T-function port, `combine`d (via `emitSelfWhenHaveValue`) into a sealed `Screen` with one case per
T-function — "which screen is live" (design.md §3.3). It returns `null` when there are no
T-function ports (nothing to project), and `save()` skips writing the third file in that case.

## Expected generated `GuessLoopAssembly` (readGuess is now a T-function)

```kotlin
fun <Input, T1, T2> `GuessLoopAssembly`(
    `uiStateFlow`: `GuessLoopUiStateFlow`<Input, T1> = `GuessLoopUiStateFlow`(),
    `readGuess`: com.genovich.components.Action<Input, T1> = com.genovich.components.Show(`uiStateFlow`.`readGuessFlow`),
    `checkGuess`: com.genovich.components.Action<T1, T2>,
): `GuessLoop`<Input, T1, T2> =
    `GuessLoop`(
        `readGuess` = `readGuess`,
        `checkGuess` = `checkGuess`,
    )
```

## Expected generated `GuessLoopUiStateFlow`

```kotlin
class `GuessLoopUiStateFlow`<Input, T1> {
    val `readGuessFlow`: com.genovich.components.MutableStateFlow<com.genovich.components.UiState<Input, T1>?> =
        com.genovich.components.MutableStateFlow(null)

    sealed interface Screen<Input, T1> {
        data class `ReadGuess`<Input, T1>(val state: com.genovich.components.UiState<Input, T1>) : Screen<Input, T1>
    }

    val screen: com.genovich.components.StateFlow<Screen<Input, T1>?> =
        com.genovich.components.combine(
            com.genovich.components.emitSelfWhenHaveValue(`readGuessFlow`) { Screen.`ReadGuess`(it) }
        ) { cases -> cases.firstOrNull { it != null } }
}
```

Only `Input` and `T1` are threaded to `GuessLoopUiStateFlow` — its type parameter list is the
(order-preserving, deduplicated) subset of the class's own type parameters actually used by
T-function ports, not the full `Input, T1, T2` list; `checkGuess`'s `T2` is irrelevant to the
projection and correctly omitted.

## `Components.kt` stub growth

Added `StateFlow<out T>` / `MutableStateFlow<T>` (self-contained synthetic reactive holders, not
aliases to kotlinx — the fixture only needs to type-check, never run), `UiState<Input, Output>`,
`Show(flow)` (body is a `TODO()` stub — see limitations), `combine(vararg flows, transform)`, and
`emitSelfWhenHaveValue(flow, wrap)`.

## Files changed

- `actions/ActionLayout.kt` — added `PortSignature(inputType, outputType, isTFunction)`; widened
  `inferType`'s `ports` map to `MutableMap<String, PortSignature>`.
- `actions/Action.kt` — added `isTFunction` marker (+ constructor param, + a Jewel `Checkbox`
  toggle in `Render`); `inferType` now records it into `PortSignature`.
- `actions/Passing.kt`, `RepeatWhileActive.kt`, `RetryUntilResult.kt` — mechanical `inferType`
  signature update (`ports` map's new value type); no logic change.
- `actions/ActionDefinition.kt` — `generate()` updated for the new `PortSignature` field names;
  `generateAssembly()` threads the `uiStateFlow` parameter and per-port `Show(...)` defaults; new
  `generateUiStateFlow()`; new companion constants (`UI_STATE_FLOW_SUFFIX`, `FLOW_SUFFIX`,
  `UI_STATE_FLOW_PARAM_NAME`, and the new `com.genovich.components.*` FQNs).
- `toolWindow/VisualIdeToolWindowFactory.kt` — `save()` now also writes `<Name>UiStateFlow.kt` when
  `generateUiStateFlow()` is non-null.
- `plugin/src/test/testData/specimen/Components.kt` — stub growth (see above).
- `GuessLoopGenerateTest`, `GuessLoopRoundTripTest`, `GuessLoopAssemblyTest` — specimen evolved so
  `readGuess` is a T-function; assertions updated/added accordingly.
- `GuessLoopUiStateFlowTest` (new) — asserts the generated shape in-memory, that no T-function
  ports produces `null`, and that the generated class type-checks (`checkHighlighting`).

## How to run

```
./gradlew :plugin:test --tests "com.genovich.visualide.GuessLoop*"
```

## Verification notes

- Ran `./gradlew :plugin:compileKotlin :plugin:compileTestKotlin` (clean) and the full
  `./gradlew :plugin:test` suite (all green, 8 tests across the four `GuessLoop*` classes,
  including the pre-existing rung 0/1/2-step-1 tests) in this environment.
- First pass declared `emitSelfWhenHaveValue` as an **extension function** on `StateFlow<T?>`,
  called via `com.genovich.components.emitSelfWhenHaveValue(flow) { ... }` (regular-call syntax
  with the receiver as an explicit first argument). `checkHighlighting` caught it immediately:
  `UNRESOLVED_REFERENCE` — Kotlin extension functions can only be called with receiver-dot syntax,
  which requires an import; qualifying the call by its declaring package doesn't work the way a
  qualified *regular* function call does. Fixed by making it a plain top-level function taking the
  flow as its first parameter (still fully qualifiable with no import, matching D6).
- Second pass declared `Screen` as a plain (non-generic) `sealed interface` nested inside
  `GuessLoopUiStateFlow<Input, T1>`, expecting it to see the outer class's type parameters.
  `checkHighlighting` caught `Input`/`T1` as unresolved inside it: Kotlin's nested (non-`inner`)
  types don't inherit the enclosing class's type parameters — and sealed interfaces can't be
  declared `inner` at all (only classes can). Fixed by giving `Screen` and its case classes their
  own explicit type parameter list, reusing the same names; call sites inside the outer class
  still just write `Screen<Input, T1>`/`Screen.ReadGuess(it)` using the outer class's own type
  parameters as arguments, and constructor type-argument inference fills in the case class's own
  parameters from the lambda argument's type.
- The `uiStateFlow`-as-first-defaulted-parameter shape (rather than a body-local `val`) wasn't the
  first design considered — an earlier sketch tried instantiating `UiStateFlow` inside the assembly
  function's body and returning it alongside the constructed instance. That's not expressible as a
  single-expression factory with defaulted Show(...) parameters (default expressions can't see
  local vals), so it was dropped in favor of the parameter-based design documented above, which
  also happens to fit D5's override-seam pattern for free.

## Limitations / notes (feed back into §6 and later steps)

- **T-ness doesn't round-trip.** Like assembly wiring (rung 2 step 1), there is no `parseAssembly`,
  so nothing recovers a port's T-function marker from generated code — it only exists in the live,
  in-memory diagram model (or is set again by hand after a reparse). This is an accepted extension
  of the existing gap, not a new one.
- **Only one T-function port exercised.** `GuessLoop` only has `readGuess` as a T-function;
  `combine`'s vararg + `Screen`'s multi-case sealed wrapper are written to generalize to N
  T-function ports (relying on `StateFlow`'s declared covariance, `out T`, so each
  `emitSelfWhenHaveValue`-mapped flow's more specific `Screen.Case?` type widens to the shared
  `Screen<...>?` the `combine` call and `screen` property expect), but that path isn't exercised by
  a test yet — `showResult` becoming a second T-function is step 5's job (design.md §6.2 target
  specimen) and should be the first thing to validate it.
- **`Show`'s stub body is `TODO()`,** not the real `suspendCancellableCoroutine` send-wait-resume
  handshake (design.md §1.6) — the fixtures only need to type-check, never execute.
- **The constructed `UiStateFlow` instance still isn't consumed by anything outside the assembly
  call site** (e.g. an actual UI layer) beyond being an overridable parameter — genuinely wiring it
  into a rendering surface is out of scope for this rung's hypothesis (H3 projection is about
  *generability*, not end-to-end UI wiring) and remains open.
