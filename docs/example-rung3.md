# End-to-end example — rung 2, step 2 (T-function + state projection)

Rung 2 of the ladder in `docs/design.md` §6, step 2 of `docs/implementation-plan.md`. Goal:
introduce the T-function (design.md §1.6, §5.1) and the derived `<Name>UiStateFlow`, validating
the **projection half of H3** (state projection is mechanically generable) and **H7** (a
`Show`-bound port renders as a T and derives a `UiStateFlow` leaf). Step 1's wiring half of H3
(`docs/example-rung2.md`) is unaffected — this step only adds to it.

This is the fourth design tried for this step. Three earlier ones — a boolean `isTFunction` flag on
the leaf `Action` node, a distinct `TFunction` leaf node type, then a `Set<String>` of T-function
port names on `ActionDefinition` — all worked (compiled, passed identical tests) but were dropped:
the first two for the reasons in "Why attach at the definition, not the leaf?" below; the third
because "which ports are T-functions" is really "which ports have a `Show`-shaped default," and a
bare `Set<String>` doesn't say that — see "Why a port → binding map, not a name set?" below.

## The T-function scheme

A port is a T-function purely by its `ActionDefinition.portDefaults` entry being `Show` —
`portDefaults: MutableState<Map<String, PortDefault>>`, attached to the *definition*, not to
whichever leaf node happens to produce the port. `PortDefault` is a sealed interface with `Show`
(`actions/Show.kt`) as its only implementation. Leaf `Action` nodes are completely unaware of
T-functions; the body tree carries no T-function concept at all. `ActionDefinition.Render()` shows
a checkbox per derived port (`signature().ports.keys`) toggling whether its entry is `Show` or
absent.

**Why attach at the definition, not the leaf?** Per design.md §5.1, T-ness is **invisible at the
function level** — the generated function file never differs based on it. A flag or a distinct
node type on the leaf makes the *leaf's identity* carry information that only the *assembly*
consumes, and (per design.md §2.2's "the node palette is open/extensible, not a closed vocabulary"
goal) baking a specific recognized boundary component into the node vocabulary itself works against
making that recognition customizable later. Attaching the map at the definition level instead
means: the body tree stays byte-for-byte the same regardless of which ports are T-functions, and
"which ports are T-functions" is a single, inspectable, edit-in-place fact about the definition —
closer to what design.md calls an "attachable" concept than an intrinsic one.

**Why a port → binding map, not a name set?** The previous iteration of this design used
`tFunctionPorts: Set<String>` — a port's name was either in the set or not. That's really encoding
"this port's default is `Show`" indirectly through set membership, with `Show` itself only showing
up later, hardcoded into `generateAssembly()`'s string templating. Naming the actual value
(`PortDefault`, with `Show` as its only case today) instead of a boolean-shaped set makes "is a
T-function" a literal identity check (`portDefaults.value[portName] == Show`) and gives the type
system a place to grow: a second `PortDefault` case (e.g. wiring a port to a child
`*Assembly(...)` call, design.md §3.2 point 1) extends the sealed interface instead of requiring
a second same-shaped `Set<String>` alongside the first, or a rename of what the existing set means.

`generateAssembly()` gives each port whose `portDefaults` entry is `Show` a default:

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
T-function — "which screen is live" (design.md §3.3). It returns `null` when no port's default is
`Show` (nothing to project), and `save()` skips writing the third file in that case.

## `Show`, the recognizer

`actions/Show.kt` is a small standalone object recognizing `com.genovich.components.Show(flow)` —
the exact expression `generateAssembly()` emits for a T-function default — mirroring how
`RepeatWhileActive`/`RetryUntilResult` match their own FQN. It is **not** an
`ActionLayout.UExpressionParser`: `Show` binds a *dependency-plane* default-parameter value, not a
function-body node, so it has no place in `ActionLayout.parse`'s dispatcher (which only ever sees
function-file expressions). Nothing calls `Show.parse` yet — it's scaffolding for a future
`parseAssembly` (rung 2 step 1's still-open stretch goal), which would use it to recover
`portDefaults` from an already-generated assembly on reopen. See `ShowTest.kt`, which exercises
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

- `actions/ActionDefinition.kt` — new `portDefaults: MutableState<Map<String, PortDefault>>`
  constructor parameter and the `PortDefault` sealed interface it's keyed to; `Render()` shows a
  checkbox per derived port; `generateAssembly()` threads the `uiStateFlow` parameter and per-port
  `Show(...)` defaults; new `generateUiStateFlow()`; new companion constants
  (`UI_STATE_FLOW_SUFFIX`, `FLOW_SUFFIX`, `UI_STATE_FLOW_PARAM_NAME`, and the new
  `com.genovich.components.*` FQNs — `Show`'s own FQN lives on `Show.SHOW_FQN`, not duplicated
  here).
- `actions/Show.kt` (new) — the recognizer described above, now also implementing
  `ActionDefinition.PortDefault`. `Action.kt`, `Passing.kt`, `RepeatWhileActive.kt`,
  `RetryUntilResult.kt`, `ActionLayout.kt` are all untouched by this step — T-ness never threads
  through `inferType`/`ports`, unlike the first two earlier designs.
- `toolWindow/VisualIdeToolWindowFactory.kt` — `save()` now also writes `<Name>UiStateFlow.kt` when
  `generateUiStateFlow()` is non-null.
- `plugin/src/test/testData/specimen/Components.kt` — stub growth (see above).
- `GuessLoopAssemblyTest` — specimen's `guessLoopDefinition()` now passes
  `portDefaults = mapOf("readGuess" to Show)`; assertions updated for the new parameter shape.
  `GuessLoopGenerateTest`/`GuessLoopRoundTripTest` needed **no changes** — proof the body tree is
  genuinely unaffected by T-function attachment.
- `GuessLoopUiStateFlowTest` (new) — asserts the generated shape in-memory, that an empty
  `portDefaults` produces `null`, and that the generated class type-checks (`checkHighlighting`).
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
  pre-existing rung 0/1/2-step-1 tests) in this environment — both for the original
  `tFunctionPorts`-based version of this step and again after the `portDefaults` reshape described
  above; the reshape changed zero test assertions beyond how each test *constructs* a definition
  (`tFunctionPorts = setOf("readGuess")` → `portDefaults = mapOf("readGuess" to Show)`), confirming
  it's a pure representation change with no behavior difference.
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
  so nothing recovers `portDefaults` from generated code on reopen — it only exists in the live,
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
