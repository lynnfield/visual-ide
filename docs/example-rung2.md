# End-to-end example — rung 2, step 1 (assembly file generation)

Rung 2 of the ladder in `docs/design.md` §6, step 1 of `docs/implementation-plan.md`. Goal:
generate the `<Name>Assembly` dependency-plane factory alongside the function file, validating the
**wiring half of H3** (two-file derivability, design.md §3.2–§3.3). The state-projection half
(`UiStateFlow`, T-functions) is step 2 and still open.

## The assembly scheme

`ActionDefinition.signature()` is now the single place that runs the rung-1 `inferType` pass; it
returns `{ typeParameters, ports, outputType }` and is shared by `generate()` (unchanged output)
and the new `generateAssembly()`.

`generateAssembly()` produces a factory function: every leaf port becomes a **required**
parameter (no defaults yet — those arrive with T-functions and child assemblies in later steps),
and the body just constructs the definition's class by name, one named argument per port.

## Expected generated `GuessLoopAssembly`

```kotlin
fun <Input, T1, T2> `GuessLoopAssembly`(
    `readGuess`: com.genovich.components.Action<Input, T1>,
    `checkGuess`: com.genovich.components.Action<T1, T2>,
): `GuessLoop`<Input, T1, T2> =
    `GuessLoop`(
        `readGuess` = `readGuess`,
        `checkGuess` = `checkGuess`,
    )
```

(Kotlin doesn't care about the exact indentation the generator emits — `save()` reformats via
`CodeStyleManager` before writing to disk; the snippet above is the reformatted shape.)

## Files changed

- `actions/ActionDefinition.kt` — extracted `signature()` (shared type-inference pass, returns the
  new `Signature` data class); `generate()` now uses it; added `generateAssembly()` and the
  `ASSEMBLY_SUFFIX` constant.
- `toolWindow/VisualIdeToolWindowFactory.kt` — `save()` now builds and writes both the function
  PSI file and the assembly PSI file (looping over both for the create/replace/reformat steps),
  opening only the function file in the editor afterward (same UX as before).
- `GuessLoopAssemblyTest` (new) — `testGeneratesTypedFactory` asserts the generated text shape
  in-memory (no IDE fixture); `testAssemblyTypeChecks` configures the function class as a
  dependency and the assembly as the checked file, asserting `checkHighlighting(false, false,
  false)` is clean (H3 wiring, mirrors `GuessLoopRoundTripTest.testGuessLoopTypeChecks` for H2).

## How to run

```
./gradlew :plugin:test --tests "com.genovich.visualide.GuessLoopAssemblyTest"
```

## Verification notes

- Ran `./gradlew :plugin:compileKotlin :plugin:compileTestKotlin` (clean) and the full
  `./gradlew :plugin:test` suite (all green, including the pre-existing rung 0/1 tests) in this
  environment — unlike the caveat in `CLAUDE.md`, the platform test framework download succeeded
  here.
- First pass emitted `` fun `GuessLoopAssembly`<Input, T1, T2>(...) ``, copying the class
  declaration's `Name<T>(...)` shape. `checkHighlighting` caught it immediately:
  `DEPRECATED_TYPE_PARAMETER_SYNTAX` — a Kotlin function's type parameters go *before* the name
  (`fun <T> name(...)`), unlike a class. Fixed and reflected in the snippet above; this is exactly
  the kind of mistake H3's compile check exists to catch.

## Limitations / notes (feed back into §6 and step 2)

- **No `parseAssembly` yet** (the step 1 stretch goal) — the assembly file is generated but not
  parsed back, so the dependency plane doesn't round-trip yet. Parsing a top-level function's
  parameter list + constructor call needs new UAST handling beyond the current expression parsers
  (flagged as a cross-cutting risk in the implementation plan).
- **No default wiring.** Every port is a required parameter; child-assembly defaults and
  `Show(...)` T-function defaults are step 2's job.
- **Decorator wrapping turned out to be a non-issue for this model.** The plan anticipated
  needing to wrap the constructed instance when the top-level body node is `RetryUntilResult`
  (design.md's dependency-plane decoration, §3.2 point 3: `RetryUntilResult(EntryPoint(...))`).
  But in this engine, `RetryUntilResult` is a body-tree node whose `generate()` already emits
  `com.genovich.components.retryUntilResult { ... }` *inside* the class's own `invoke()` — the
  class itself does the decorating, regardless of what's at the root of its body. So
  `generateAssembly()` needs no special case: it only ever constructs the class and wires ports.
  This is worth flagging if a future rung introduces assembly-level (not body-level) decoration.
