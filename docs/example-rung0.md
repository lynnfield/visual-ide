# End-to-end example — rung 0 (guess-the-number)

Rung 0 of the ladder in `docs/design.md` §6. Goal: round-trip the simplest real-shaped function
using only node types the engine already implements, validating **H1** (parse ∘ generate is a
fixed point).

## What was added

- `plugin/src/test/testData/specimen/Components.kt` — minimal `com.genovich.components` stubs
  (`Action`, `repeatWhileActive`, `retryUntilResult`) so UAST can resolve the symbols the parsers
  match on. This is the synthetic specimen's dependency surface (not the real library).
- `plugin/src/test/kotlin/com/genovich/visualide/GuessLoopGenerateTest.kt` — lightweight check of
  the *generate* half (no IDE fixture).
- `plugin/src/test/kotlin/com/genovich/visualide/GuessLoopRoundTripTest.kt` — `BasePlatformTestCase`
  that builds the `GuessLoop` model, generates, parses back, and asserts a fixed point + structure.
- `plugin/build.gradle.kts` — enabled `testImplementation(libs.junit)`.

The specimen `GuessLoop` is `RepeatWhileActive(Passing[readGuess, checkGuess])`.

## How to run

```
./gradlew :plugin:test
# or a single test:
./gradlew :plugin:test --tests "com.genovich.visualide.GuessLoopRoundTripTest"
```

(The IntelliJ platform build downloads the IDE on first run, so it can't run in the design
sandbox — run it locally.)

## Expected outcome

- `GuessLoopGenerateTest` passes: the generated class declares both auto-derived ports, the loop,
  and the two piped leaf calls.
- `GuessLoopRoundTripTest` passes if **H1** holds: the generated code parses back into the same
  model and regenerates identical code.
- **H2 is intentionally NOT satisfied yet.** All generics are `<Input, Output>`, so the generated
  code parses but does **not** type-check (the second leaf receives `Output` where `Input` is
  expected). Real per-port typing is rung 1.

## Status: rung 0 green — H1 validated

Both tests pass. `generate ∘ parse ∘ generate` is a fixed point for `GuessLoop` and the model
survives the round-trip.

The three risks flagged before running resolved as follows:

1. **`runBlocking { smartReadAction(...) }` on the test EDT** — fine as written.
2. **UAST lambda-body assumption** — fine; `repeatWhileActive { … }`'s body parses as expected.
3. **Resolution** — this one bit. `Passing.parse` matches `.let` as `kotlin.StandardKt.let`, but
   `BasePlatformTestCase`'s default light project attaches **no Kotlin runtime**, so `.let` wouldn't
   resolve. Fixed by attaching the real `kotlin-stdlib.jar` via a `LightProjectDescriptor`
   (`PathManager.getJarPathForClass(Unit::class.java)` + `PsiTestUtil.addLibrary`). So the harness
   needs both the `components` stubs *and* the real stdlib on the fixture classpath.

## Findings already evident (feed back into §6)

- **Parsers require fully-qualified calls** (e.g. `com.genovich.components.repeatWhileActive`,
  a `UQualifiedReferenceExpression`). A hand-written specimen using imports parses as a plain
  `UCallExpression` and fails — so the round-trip is defined from `generate()` output, not arbitrary
  source. Worth deciding whether import-form should also be accepted.
- **H2 looms immediately:** the generic `<Input, Output>` scheme can't represent real multi-typed
  ports (rung 1).
- **H6 looms:** `Passing` is a linear `.let{}` pipe and can't express value reuse — expected to
  force named SSA `val`s (§2.7) at rung 5.
