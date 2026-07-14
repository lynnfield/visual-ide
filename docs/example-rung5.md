# End-to-end example — rung 4 (engine IR / `KotlinAnalysis` boundary)

Rung 4 of the ladder in `docs/design.md` §6 (§6.3 point 4), Step 4 of
`docs/implementation-plan.md`. Goal: interpose the engine's own IR between UAST and the node
model (design.md D12), so `com.genovich.visualide.actions` no longer touches UAST/PSI directly,
validating **H5** (an engine IR can sit between UAST and the model without breaking round-trip).

## The new `analysis` package

Three new files under `plugin/src/main/kotlin/com/genovich/visualide/analysis/`:

- **`Expr.kt`** — a sealed IR with exactly the four shapes the current grammar's parsers need
  (`call, qualified call, lambda-with-single-return, reference`, per the plan's task list):
  - `Reference` — a name that doesn't decompose further (a local `val`, a bare property access).
  - `Call` — `receiver.method(arguments)` or a receiver-less call (`readGuess(it)`, `TODO()`).
  - `QualifiedCall` — `receiver.selector` (`input.let { … }`, a fully-qualified
    `com.genovich.components.repeatWhileActive { … }`).
  - `Lambda` — collapses UAST's three-node unwrap (`ULambdaExpression` →
    `UBlockExpression.expressions.single()` → must-be-`UReturnExpression` → `.returnExpression`)
    into one field, `singleReturnExpression: Expr?`, null when the lambda doesn't have that shape.
    Every node parser needing a lambda body wanted exactly this and nothing else, so the adapter
    normalizes it once instead of every parser repeating the same four-step unwrap.

  Resolution (`resolvedName`/`resolvedQualifiedName`) is a field on every `Expr`, computed eagerly
  by the adapter (mirrors UAST's `tryResolveNamed()`, itself defined generically for any
  expression) — different parsers need it off different shapes (a bare `Reference` for a port
  call's receiver in `Action.parse`, a `QualifiedCall` for an FQN-call chain link in
  `Passing`/`RepeatWhileActive`/`RetryUntilResult`/`Show`), so it isn't scoped to one variant.

- **`ClassInfo.kt`** — the class-level counterpart, exactly the surface `ActionDefinition.parse`
  needs: `name`, `superTypeQualifiedNames`, `invokeBody: Expr?`, `diagramChecksum: String?` (the
  rung-3 `@Diagram` checksum read, folded in here rather than staying UAST-annotation-shaped).
  Not named as one of the plan's four expression shapes — added because the acceptance criterion
  ("the `actions` package no longer imports `org.jetbrains.uast`") covers the *whole* package,
  including `ActionDefinition.parse`'s `UClass`-level entry point, not just node-level expression
  parsing.

- **`KotlinAnalysis.kt`** — the adapter (design.md §4.5's `KotlinAnalysis` port): `parseClass(UClass): ClassInfo`
  and `toExpr(UExpression): Expr`, the latter recursively converting a whole UAST subtree into the
  IR in one pass. This is the **only** file in the plugin that imports `org.jetbrains.uast` for
  this purpose — everything downstream (`actions`, and by extension every node's `generate`/`parse`)
  consumes `Expr`/`ClassInfo` only.

## What moved out of `actions`

Every node parser (`Action`, `Passing`, `RepeatWhileActive`, `RetryUntilResult`, `TodoStub`) and
`Show` (the T-function recognizer) now match on `Expr` instead of `UExpression`; `ActionLayout`'s
`UExpressionParser` interface is renamed `ExpressionParser<T>` (`parse(expression: Expr)`).
`ActionDefinition.parse` now takes a `ClassInfo` instead of a `UClass`. Net effect on `actions/*.kt`:

- Zero `org.jetbrains.uast.*` imports (the acceptance criterion).
- Zero `org.jetbrains.kotlin.name.FqName` / `org.jetbrains.kotlin.idea.base.psi.kotlinFqName`
  imports — FQN comparisons collapsed from `checkNotNull(tryResolveNamed()).let{checkNotNull(kotlinFqName)}.also{check(FqName(X)==it)}`
  to a single `check(it.resolvedQualifiedName == X)`, since resolution now happens once, eagerly,
  in the adapter.
- Zero `com.intellij.util.asSafely` / `org.jetbrains.kotlin.utils.{findIsInstanceAnd,firstIsInstanceOrNull}`
  imports in `ActionDefinition.kt` — the UAST unwrap chain they supported moved into
  `KotlinAnalysis.parseClass`.
- Zero `com.intellij.execution.processTools.mapFlat` in `Passing.kt` — `Lambda.singleReturnExpression`
  removed the multi-step `Result` chain it was threading through.
- `com.intellij.openapi.progress.runBlockingCancellable` (the concurrent-parser-race harness in
  `ActionLayout.parse`) and `com.intellij.openapi.diagnostic.getOrLogException` (exception logging
  in `ActionDefinition.parse`) are **kept** — out of scope. The plan's acceptance criterion and
  design.md D12's stated boundary are about the UAST/PSI *parsing* surface specifically; these are
  unrelated platform utilities (coroutine cancellation semantics, exception logging) that don't
  carry any UAST/PSI types across the boundary, and reimplementing them host-agnostically wasn't
  asked for and risks changing cancellation/logging behavior for no benefit at this rung.

## Callers updated

Everything *outside* `actions` that used to hand a raw `UClass`/`UExpression` to
`ActionDefinition.parse`/`Show.parse` now converts first via `KotlinAnalysis`:

- `VisualIdeToolWindowFactory.kt`'s `save()`-adjacent load path:
  `ActionDefinition.parse(KotlinAnalysis.parseClass(it))`.
- `GuessLoopRoundTripTest`, `GuessLoopChecksumTest`: same one-line change in their
  `parseDefinition` helper.
- `ShowTest`: its `parameterDefaultValue` helper now returns `Expr` (via `KotlinAnalysis.toExpr`)
  instead of `UExpression`.

These callers are host-specific glue (the tool window, IntelliJ-platform tests) and are expected to
keep importing UAST — the boundary is `actions` being host-agnostic, not "nothing in the plugin
touches UAST."

## The arch-test

`ActionsPackageHostAgnosticTest` (new) — a plain (non-platform) JUnit test, no IDE fixture — walks
`src/main/kotlin/com/genovich/visualide/actions/*.kt` and asserts no file's import list contains
`org.jetbrains.uast`. Deliberately a dumb text scan rather than something PSI/reflection-based: fast,
has no fixture cost, and needs no host runtime to check a *textual* import-list property.

## Files changed

- New: `analysis/Expr.kt`, `analysis/ClassInfo.kt`, `analysis/KotlinAnalysis.kt`.
- `actions/ActionLayout.kt`, `actions/Action.kt`, `actions/Passing.kt`,
  `actions/RepeatWhileActive.kt`, `actions/RetryUntilResult.kt`, `actions/TodoStub.kt`,
  `actions/Show.kt`, `actions/ActionDefinition.kt` — parsers reshaped onto `Expr`/`ClassInfo` as
  described above. `generate()`/`inferType()`/`Render()` on every node are **untouched** — this
  rung only touches the parse direction.
- `toolWindow/VisualIdeToolWindowFactory.kt` — one-line `KotlinAnalysis.parseClass` insertion.
- `GuessLoopRoundTripTest.kt`, `GuessLoopChecksumTest.kt`, `ShowTest.kt` — caller-side conversion,
  same pattern.
- `ActionsPackageHostAgnosticTest.kt` (new) — the arch-test.

## How to run

```
./gradlew :plugin:test --tests "com.genovich.visualide.GuessLoop*" --tests "com.genovich.visualide.ShowTest" --tests "com.genovich.visualide.ActionsPackageHostAgnosticTest"
```

## Verification notes

- Full `./gradlew :plugin:test` green, including a `--no-build-cache --rerun-tasks` pass (rung 2
  step 3's write-up records a stale-`instrumentedJar`/`composedJar` build-cache issue tied to the
  outdated IntelliJ Platform Gradle plugin version here; re-verifying without the cache is now
  routine after a constructor/signature-shaped change).
- No `generate()`/`inferType()`/round-trip *text* assertion changed — this rung is a pure internal
  refactor of the parse path's input type, not a behavior change. `GuessLoopRoundTripTest`'s
  byte-for-byte `generate ∘ parse ∘ generate` fixed-point assertion (H1) still passes unmodified,
  which is the strongest evidence the IR conversion is lossless for every shape currently in use.

## Limitations / notes (feed back into §6 and later steps)

- **The IR's four shapes are grammar-complete for the current node set only.** Step 5 (value
  plumbing: Tuple/Construct/Copy/Project/Select/Guard/Not, Branch, named SSA `val`s) will very
  likely need new `Expr` shapes (e.g. a `Literal`, a property-chain-aware `Project`, a `when`
  shape for `Branch`) — `Reference`'s catch-all `else` branch in `KotlinAnalysis.toExpr` currently
  absorbs everything not already named, which is fine as a placeholder but will need real shapes
  once those parsers start inspecting rather than passing through.
- **`ClassInfo`/`Expr` conversion is eager and whole-tree**, not lazy/streaming — `parseClass`
  walks the entire `invoke()` body once up front. Fine at this scale; would need revisiting only if
  a future rung's bodies get large enough for conversion cost to matter.
- **Two platform utilities remain in `actions`** (`runBlockingCancellable`, `getOrLogException`) —
  see "What moved out of `actions`" above for why they're out of scope. If a genuinely
  host-agnostic form factor is ever built, these are the two remaining seams to abstract.
- **No new host adapter exists yet for `ProjectFileSystem`/`EditorBridge`/`CanvasHost`** (design.md
  §4.5's other three ports) — only `KotlinAnalysis` is validated by this rung. The other three
  remain unvalidated hypotheses.
