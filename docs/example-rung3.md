# End-to-end example — rung 2, step 2 (T-function + state projection)

Rung 2 of the ladder in `docs/design.md` §6, step 2 of `docs/implementation-plan.md`. Goal:
introduce the T-function (design.md §1.6, §5.1) and the derived `<Name>UiStateFlow`, validating
the **projection half of H3** (state projection is mechanically generable) and **H7** (a
`Show`-bound port renders as a T and derives a `UiStateFlow` leaf). Step 1's wiring half of H3
(`docs/example-rung2.md`) is unaffected — this step only adds to it.

This is the third design tried for this step. Two earlier ones — a boolean `isTFunction` flag on
the leaf `Action` node, then a distinct `TFunction` leaf node type — both worked (compiled, passed
identical tests) but were dropped for the reasons in "Why attach at the definition, not the leaf?"
below.

## The T-function scheme

A port is a T-function purely by having its name in `ActionDefinition.tFunctionPorts` — a
`MutableState<Set<String>>` attached to the *definition*, not to whichever leaf node happens to
produce the port. Leaf `Action` nodes are completely unaware of T-functions; the body tree carries
no T-function concept at all. `ActionDefinition.Render()` shows a checkbox per derived port
(`signature().ports.keys`) toggling its membership in the set.

**Why attach at the definition, not the leaf?** Per design.md §5.1, T-ness is **invisible at the
function level** — the generated function file never differs based on it. A flag or a distinct
node type on the leaf makes the *leaf's identity* carry information that only the *assembly*
consumes, and (per design.md §2.2's "the node palette is open/extensible, not a closed vocabulary"
goal) baking a specific recognized boundary component into the node vocabulary itself works against
making that recognition customizable later. Attaching the set at the definition level instead
means: the body tree stays byte-for-byte the same regardless of which ports are T-functions, and
"which ports are T-functions" is a single, inspectable, edit-in-place fact about the definition —
closer to what design.md calls an "attachable" concept than an intrinsic one.

`generateAssembly()` gives each port named in `tFunctionPorts` a default:

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
T-function — "which screen is live" (design.md §3.3). It returns `null` when `tFunctionPorts` is
empty (nothing to project), and `save()` skips writing the third file in that case.

## `Show`, the recognizer

`actions/Show.kt` is a small standalone object recognizing `com.genovich.components.Show(flow)` —
the exact expression `generateAssembly()` emits for a T-function default — mirroring how
`RepeatWhileActive`/`RetryUntilResult` match their own FQN. It is **not** an
`ActionLayout.UExpressionParser`: `Show` binds a *dependency-plane* default-parameter value, not a
function-body node, so it has no place in `ActionLayout.parse`'s dispatcher (which only ever sees
function-file expressions). Nothing calls `Show.parse` yet — it's scaffolding for a future
`parseAssembly` (rung 2 step 1's still-open stretch goal), which would use it to recover
`tFunctionPorts` from an already-generated assembly on reopen. See `ShowTest.kt`, which exercises
it against the real text `generateAssembly()` produces, not a hand-written stand-in.

`com.genovich.components.Show` is, for now, the *only* recognized T-function binding — hardcoded,
not customizable. Whether recognition should be extensible (a registry of recognized boundary
components) and whether a project could need more than one *kind* of T-function binding (not just
more than one T-function *port*, which already works) are open questions, recorded in design.md
§5.1 as hypotheses to validate later, not decided here.

## Expected generated `GuessLoopAssembly` (readGuess attached as a T-function)

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

- `actions/ActionDefinition.kt` — new `tFunctionPorts: MutableState<Set<String>>` constructor
  parameter; `Render()` shows a checkbox per derived port; `generateAssembly()` threads the
  `uiStateFlow` parameter and per-port `Show(...)` defaults; new `generateUiStateFlow()`; new
  companion constants (`UI_STATE_FLOW_SUFFIX`, `FLOW_SUFFIX`, `UI_STATE_FLOW_PARAM_NAME`, and the
  new `com.genovich.components.*` FQNs — `Show`'s own FQN lives on `Show.SHOW_FQN`, not duplicated
  here).
- `actions/Show.kt` (new) — the recognizer described above. `Action.kt`, `Passing.kt`,
  `RepeatWhileActive.kt`, `RetryUntilResult.kt`, `ActionLayout.kt` are all untouched by this step —
  T-ness never threads through `inferType`/`ports`, unlike the two earlier designs.
- `toolWindow/VisualIdeToolWindowFactory.kt` — `save()` now also writes `<Name>UiStateFlow.kt` when
  `generateUiStateFlow()` is non-null.
- `plugin/src/test/testData/specimen/Components.kt` — stub growth (see above).
- `GuessLoopAssemblyTest` — specimen's `guessLoopDefinition()` now passes
  `tFunctionPorts = setOf("readGuess")`; assertions updated for the new parameter shape.
  `GuessLoopGenerateTest`/`GuessLoopRoundTripTest` needed **no changes** — proof the body tree is
  genuinely unaffected by T-function attachment.
- `GuessLoopUiStateFlowTest` (new) — asserts the generated shape in-memory, that an empty
  `tFunctionPorts` produces `null`, and that the generated class type-checks (`checkHighlighting`).
- `ShowTest` (new) — asserts `Show.parse` recognizes the exact default value
  `generateAssembly()` emits for a T-function port, and rejects an unrelated default value
  (the `uiStateFlow` parameter's own default), using real UAST resolution (`BasePlatformTestCase`).

## How to run

```
./gradlew :plugin:test --tests "com.genovich.visualide.GuessLoop*" --tests "com.genovich.visualide.ShowTest"
```

## Verification notes

- Ran `./gradlew :plugin:compileKotlin :plugin:compileTestKotlin` (clean) and the full
  `./gradlew :plugin:test` suite (all green, 10 tests across five classes, including the
  pre-existing rung 0/1/2-step-1 tests) in this environment.
- `emitSelfWhenHaveValue` is declared as a **plain top-level function**, not an extension on
  `StateFlow<T?>` — an extension function can only be called with receiver-dot syntax, which
  requires an import, and generated code calls every `com.genovich.components` symbol fully
  qualified with no imports (design.md D6). (This was caught by `checkHighlighting` during an
  earlier iteration of this same step — see the design-history note above.)
- `Screen` and its case classes redeclare their own type parameter list rather than inheriting the
  outer `GuessLoopUiStateFlow<Input, T1>`'s — Kotlin's nested (non-`inner`) types don't inherit the
  enclosing class's type parameters, and sealed interfaces can't be declared `inner` at all (only
  classes can). Call sites inside the outer class still just write `Screen<Input, T1>` /
  `Screen.ReadGuess(it)`, using the outer class's own type parameters as arguments.
- `UExpression` extraction in `ShowTest` goes through `UFile.classes.first().methods.first()`, not
  `UFile.declarations` (which doesn't exist on the `UFile` interface) — Kotlin top-level functions
  surface via UAST as methods of a synthetic facade class, not directly off `UFile`. And a
  `UParameter`'s default value is `UVariable.uastInitializer`, not `uastDefaultValue` (which
  doesn't exist either) — found by reading the actual UAST jar's `javap` output rather than
  guessing, after both wrong guesses failed to compile.

## Limitations / notes (feed back into §6 and later steps)

- **T-ness doesn't round-trip.** Like assembly wiring (rung 2 step 1), there is no `parseAssembly`,
  so nothing recovers `tFunctionPorts` from generated code on reopen — it only exists in the live,
  in-memory diagram model (or is set again by hand via the checkbox after a reparse). `Show.kt` is
  scaffolding toward closing this gap, not a fix for it yet.
- **Only one T-function port exercised.** `GuessLoop` only attaches `readGuess`; `combine`'s
  vararg + `Screen`'s multi-case sealed wrapper are written to generalize to N T-function ports
  (relying on `StateFlow`'s declared covariance, `out T`, so each `emitSelfWhenHaveValue`-mapped
  flow's more specific `Screen.Case?` type widens to the shared `Screen<...>?` the `combine` call
  and `screen` property expect), but that path isn't exercised by a test yet — `showResult`
  becoming a second T-function is step 5's job (design.md §6.2 target specimen) and should be the
  first thing to validate it.
- **`Show`'s stub body is `TODO()`,** not the real `suspendCancellableCoroutine` send-wait-resume
  handshake (design.md §1.6) — the fixtures only need to type-check, never execute.
- **Customizable/multiple-kind T-function recognition is an open hypothesis, not implemented** —
  see design.md §5.1's new "Open question" note. `Show` is the only recognized binding; nothing
  today validates a registry-based or multi-kind design.
- **The constructed `UiStateFlow` instance still isn't consumed by anything outside the assembly
  call site** (e.g. an actual UI layer) beyond being an overridable parameter — genuinely wiring it
  into a rendering surface is out of scope for this rung's hypothesis (H3 projection is about
  *generability*, not end-to-end UI wiring) and remains open.
