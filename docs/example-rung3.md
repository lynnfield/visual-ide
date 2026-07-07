# End-to-end example — rung 2, step 2 (T-function + state projection)

Rung 2 of the ladder in `docs/design.md` §6, step 2 of `docs/implementation-plan.md`. Goal:
introduce the T-function (design.md §1.6, §5.1) and the derived `<Name>UiStateFlow`, validating
the **projection half of H3** (state projection is mechanically generable) and **H7** (a
`Show`-bound port renders as a T and derives a `UiStateFlow` leaf). Step 1's wiring half of H3
(`docs/example-rung2.md`) is unaffected — this step only adds to it.

## The T-function scheme

A T-function is a **distinct leaf node type**, `TFunction`, alongside `Action` — not a marker on
`Action` (an earlier version of this rung tried that; see the note at the end of this section).
Structurally it's a near-twin of `Action`: same `name` state, same `generate()`/`inferType()`
shape, added to the diagram via its own "T-function" entry in the add-node context menu
(`AddNewLayoutSelector.kt`) alongside "Action".

Per design.md §5.1, T-ness is **invisible at the function level** — `TFunction.generate()` emits
the exact same shape as `Action.generate()` (`` `name`(input) ``), so it never appears differently
in the function file. A consequence of that: parsing generated code can **never recover a
`TFunction`** from a leaf call — there is nothing in the text to distinguish it from a plain
`Action`, so a reparsed `TFunction` always comes back as an `Action` (see `TFunction`'s KDoc).
`TFunction` therefore has no `ActionLayout.UExpressionParser` and is not registered in
`ActionLayout.parse`'s dispatcher — a deliberate, documented exception to the "every node type has
a parser" convention, not an oversight. This is the same round-trip limitation the marker-based
design had; only *where* the information lives changed, not whether it survives a reparse.

Both leaf types report into the same `ports` map during `inferType`, so `ActionDefinition` doesn't
need to know or care which leaf type produced a given port — it only reads
`ActionLayout.PortSignature.isTFunction` off the map entry, populated as `true` by `TFunction` and
`false` (the default) by `Action`. This is why `PortSignature` — the ports map's value type,
replacing a bare `Pair<String, String>` — carries `isTFunction` at all: it's the shared contract
between whichever leaf type minted the port and `generateAssembly()`/`generateUiStateFlow()`, which
don't otherwise see the leaf nodes themselves. Every node's `inferType` signature changed
accordingly (mechanical; no behavior change for `Passing`/`RepeatWhileActive`/`RetryUntilResult`,
which just forward the map).

**Why not a marker on `Action`?** That was this rung's first implementation, and it worked (same
generated output, same tests) — but a marker means every `Action` node carries a boolean that's
almost always false, and "is this leaf a T-function" is a yes/no toggle on an otherwise-identical
node rather than a distinct thing you add to the diagram. A separate node type makes T-functions a
first-class part of the node catalog (consistent with how `RepeatWhileActive`/`RetryUntilResult`/
etc. are each their own type) and lets the add-node menu offer it directly, at the cost of near-
duplicating `Action`'s four members (there was no clean way to share them without either inheritance
between two `data class` node types — awkward with `copy()`/`equals()` — or extracting a common
non-node helper that both `generate()`/`inferType()` call into, which would've been more indirection
than the ~10 duplicated lines justified at this scale).

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
- `actions/TFunction.kt` (new) — the T-function leaf node: `name` state, `Render`, `generate()`
  (identical shape to `Action`'s), `inferType()` (records `isTFunction = true`). No parser (see
  above).
- `actions/Action.kt` — unchanged from before this rung; `inferType` records `PortSignature`s with
  `isTFunction` defaulted `false`.
- `actions/Passing.kt`, `RepeatWhileActive.kt`, `RetryUntilResult.kt` — mechanical `inferType`
  signature update (`ports` map's new value type); no logic change.
- `actions/ActionDefinition.kt` — `generate()` updated for the new `PortSignature` field names;
  `generateAssembly()` threads the `uiStateFlow` parameter and per-port `Show(...)` defaults; new
  `generateUiStateFlow()`; new companion constants (`UI_STATE_FLOW_SUFFIX`, `FLOW_SUFFIX`,
  `UI_STATE_FLOW_PARAM_NAME`, and the new `com.genovich.components.*` FQNs).
- `toolWindow/VisualIdeToolWindowFactory.kt` — `save()` now also writes `<Name>UiStateFlow.kt` when
  `generateUiStateFlow()` is non-null.
- `ui/AddNewLayoutSelector.kt` — added a "T-function" entry to the add-node context menu, alongside
  "Action".
- `plugin/src/test/testData/specimen/Components.kt` — stub growth (see above).
- `GuessLoopGenerateTest`, `GuessLoopRoundTripTest`, `GuessLoopAssemblyTest` — specimen evolved so
  `readGuess` is a `TFunction`; assertions updated/added accordingly.
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
- T-ness itself also went through a revision after this rung first landed: it started as an
  `isTFunction` marker on `Action` (a toggleable boolean, checkbox in `Render`), then was reworked
  into the separate `TFunction` node type described above. Both compiled, both passed the exact
  same tests (down to identical generated-code assertions) — the marker's `PortSignature.isTFunction
  = isTFunction.value` just became `TFunction`'s hardcoded `isTFunction = true` — because
  `ports`/`PortSignature` was already the shared boundary between "whatever produced this port" and
  `generateAssembly()`/`generateUiStateFlow()`, so swapping the producer's shape didn't ripple past
  `Action`/`TFunction` themselves.

## Limitations / notes (feed back into §6 and later steps)

- **T-ness doesn't round-trip.** Like assembly wiring (rung 2 step 1), there is no `parseAssembly`,
  so nothing recovers a `TFunction` from generated code — a reparsed T-function leaf always comes
  back as a plain `Action`. This is an accepted extension of the existing gap, not a new one, and
  isn't specific to the node-type-vs-marker choice above — either shape has the same blind spot,
  since it's the function file's generated *text* that carries no signal either way.
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
