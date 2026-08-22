# End-to-end example — rung 2 step 1's stretch goal (`parseAssembly`)

Not a new rung on the `docs/design.md` §6 ladder (that ladder ended at rung 5, `docs/example-rung6.md`)
— this closes the one stretch goal rung 2 / step 1 left open: **`parseAssembly`**, the
dependency-plane round-trip (design.md §2.8), first flagged as missing in
`docs/example-rung2.md`'s "Limitations" and left open ever since (`docs/example-rung3.md` through
`docs/example-rung6.md` all still list it under "Not yet implemented" / "Known gaps").

## The gap this closes

`ActionDefinition.portDefaults` — which ports are T-functions — lives only at the definition
level, never in the body tree (`docs/example-rung3.md`'s design). Until now, the *only* way that
map got populated was the in-memory checkbox in `ActionDefinition.Render()`: reopening a project
and re-parsing `<Name>.kt` always produced a definition with `portDefaults = emptyMap()`, even if
`<Name>Assembly.kt` on disk had `readGuess` defaulted to `Show(...)`. The T-function attachment
was write-only.

## The scheme: mirror `generateAssembly`, nothing more

Design.md §2.8 describes a fuller dependency-plane grammar (child-`*Assembly(...)` wiring,
decorators, shared singletons). `ActionDefinition.generateAssembly()` doesn't emit any of that yet
— every port is either a required parameter or one defaulted to `Show(uiStateFlow.<port>Flow)`
(`docs/example-rung2.md`'s "Decorator wrapping turned out to be a non-issue" note explains why
decorators specifically are moot for this engine). So `parseAssembly` only needs to recognize
exactly those two shapes — same bounded-to-what-the-generator-produces discipline every prior rung
followed.

## New IR: `FunctionInfo`

Every node parser so far matches an *expression* inside a class's `invoke()` body — `ClassInfo`
(rung 4) only knows how to read a `UClass`. An assembly is a **top-level function**, a shape the
engine IR had no vocabulary for. `analysis/FunctionInfo.kt` adds it:

```kotlin
data class FunctionInfo(
    val name: String?,
    val parameters: List<Parameter>,   // Parameter(name, defaultValue: Expr?)
    val bodyExpression: Expr?,         // the `= expr` single-expression body
)
```

Deliberately thin: no parameter *types* are captured, because `ActionDefinition.signature()` (the
already-parsed function file) is the source of truth for port types — the assembly only needs to
say *which* ports got a default and *what* that default was.

`KotlinAnalysis.parseFunction(uMethod: UMethod): FunctionInfo` is the new (and only new) UAST
touchpoint, reusing the same body-extraction logic `parseClass` already used for `invoke()`
(`uastBody` as a block, find the `UReturnExpression`, convert). One surprise worth recording:
**Kotlin surfaces a top-level function's UAST node as a `UMethod` of a synthetic `<FileName>Kt`
facade `UClass`, not directly off `UFile`** — `ShowTest` had already documented this quirk in a
comment (it needed the same traversal to reach a parameter's default value); `parseFunction`'s
callers reach a `UMethod` the same way: `uFile.classes.first().methods.first()`.

## `ActionDefinition.parseAssembly`

```kotlin
fun parseAssembly(functionInfo: FunctionInfo, definition: ActionDefinition): ActionDefinition?
```

1. Name check: `functionInfo.name == "${definition.name}Assembly"`.
2. Body check: `functionInfo.bodyExpression` must be a `Call` whose `resolvedName` equals
   `definition.name` — i.e. the body really is `` `Name`(...) ``, a call to this definition's own
   constructor. (Confirmed empirically, not assumed: with no `<Name>.kt` class file in the parse
   fixture the constructor call doesn't resolve at all — `resolvedName` comes back `null` — so the
   caller must have the class file in scope. `docs/example-rung6.md`'s "don't extrapolate a
   resolved FQN" lesson generalizes to "don't extrapolate resolution at all without the referenced
   symbol in scope.")
3. For each parameter whose name matches one of `definition.signature().ports.keys`: no default →
   required, no `portDefaults` entry; a default that parses via `Show.parse(...)` → `portDefaults[port]
   = Show`. A parameter that isn't a port (`uiStateFlow` itself) is silently skipped rather than
   failing the whole parse — same best-effort convention as every other parser here.

Returns `definition.copy(portDefaults = mutableStateOf(recovered))` on success, `null` on a
name/body mismatch (this isn't this definition's own assembly).

## Wiring: `VisualIdeToolWindowFactory`

The tool window's file scan already turned a `UFile`'s classes into `ActionDefinition`s
(`ActionDefinition.parse(KotlinAnalysis.parseClass(it))`). A new `withRecoveredPortDefaults` step
looks for a sibling `<Name>Assembly.kt` next to the currently open file, parses its top-level
function, and merges the recovered `portDefaults` in — falling back to the definition unchanged if
there's no sibling file or it doesn't parse.

## Files changed

- `analysis/FunctionInfo.kt` (new) — the top-level-function IR shape.
- `analysis/KotlinAnalysis.kt` — new `parseFunction(uMethod: UMethod): FunctionInfo`.
- `actions/ActionDefinition.kt` — new `parseAssembly(functionInfo, definition): ActionDefinition?`
  in the companion object.
- `actions/Show.kt` — KDoc updated; `Show.parse` now has a real caller.
- `toolWindow/VisualIdeToolWindowFactory.kt` — new `withRecoveredPortDefaults` merge step in the
  file-scan `LaunchedEffect`.
- `GuessLoopParseAssemblyTest.kt` (new) — the required-only case, the `Show`-defaulted case, and a
  name-mismatch rejection case, all against real generated text (not hand-written stand-ins).

## How to run

```
./gradlew :plugin:test --tests "com.genovich.visualide.GuessLoopParseAssemblyTest"
```

## Verification notes

- Full `./gradlew :plugin:test --rerun-tasks` green, including `ActionsPackageHostAgnosticTest`
  (zero UAST imports added to `actions`) and every pre-existing rung 0–5 test.
- The `resolvedName`-based body check was validated by running, not assumed: a first pass against a
  fixture with only the assembly file present (no `<Name>.kt` class) showed `resolvedName = null`
  on the constructor call — the check would have silently rejected every real assembly. Adding the
  class file to the test fixture (matching what `GuessLoopAssemblyTest` already did) fixed it; the
  fix is in the test, not the production check, which was correct once the symbol it needs to
  resolve is actually in scope — exactly the situation a real project provides (the class file is
  always a sibling of its assembly).

## Limitations / notes (feed back into design.md §2.8)

- **Only what `generateAssembly` already emits round-trips**: required ports and
  `Show`-defaulted ports. Child-`*Assembly(...)` wiring, decorators, and shared singletons
  (§2.8's fuller grammar) aren't generated yet, so they aren't parsed either — this is a
  same-shape follow-up whenever `generateAssembly` grows those forms, not a limitation intrinsic
  to `parseAssembly`'s approach.
- **No drift detection on the assembly file.** The function file's `@Diagram(checksum = …)`
  covers the body tree only (rung 3, H4); the assembly has no equivalent checksum, so a
  hand-edited assembly (e.g. a manually added `Show(...)` default) is silently accepted rather
  than flagged the way a hand-edited body is.
- **`VisualIdeToolWindowFactory`'s merge step is untested** (Compose/tool-window glue isn't unit
  tested anywhere else in this codebase either — `save()` has no test) — coverage is at the
  `ActionDefinition.parseAssembly`/`KotlinAnalysis.parseFunction` engine level.
