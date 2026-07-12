# Implementation plan — next steps (for Claude Code)

Handoff plan continuing the end-to-end example ladder in `docs/design.md` §6. Read `design.md`
(decisions D1–D17) and `docs/example-rung0.md` / `example-rung1.md` first — they define the
architecture and what "done" means for the earlier rungs.

## Status

- **Rung 0 (done)** — round-trip of `GuessLoop = RepeatWhileActive(Passing[readGuess, checkGuess])`;
  `generate ∘ parse ∘ generate` is a fixed point (H1). Green.
- **Rung 1 (done)** — structural per-port typing via `ActionLayout.inferType`; generated function
  type-checks (H2). Green.
- **Rung 2 / Step 1 (done)** — assembly file generation (`ActionDefinition.generateAssembly()`);
  `save()` emits both `<Name>.kt` and `<Name>Assembly.kt`. Validates the *wiring* half of H3. See
  `docs/example-rung2.md`. `parseAssembly` (the round-trip stretch goal) is still open.
- **Rung 2 / Step 2 (done)** — T-function ports, attached via `ActionDefinition.portDefaults` (a
  definition-level `Map<String, PortDefault>`, not a leaf-node flag or type — see
  `docs/example-rung3.md` for why), and the derived `<Name>UiStateFlow` projection
  (`ActionDefinition.generateUiStateFlow()`). `save()` emits the third file when a definition has
  T-function ports. Validates the *projection* half of H3, plus H7. `com.genovich.components.Show`
  is the only recognized T-function binding and the only implementation of `PortDefault`
  (`actions/Show.kt`); customizable/multi-kind recognition is an open hypothesis (design.md §5.1).

The engine lives in `plugin/src/main/kotlin/com/genovich/visualide/actions/`:
`ActionLayout` (node interface: `Render` / `generate` / `inferType` / `parse`), the nodes
(`Action`, `Passing`, `RepeatWhileActive`, `RetryUntilResult`, `TodoStub`), `Show` (a recognizer,
not a node — see Step 2 below), and `ActionDefinition` (the function file: `signature()` +
`generate()` + `generateAssembly()` + `generateUiStateFlow()` + `parse(UClass)`, plus the
`portDefaults` state and the `PortDefault` sealed interface). The tool window's `save()`
(`toolWindow/VisualIdeToolWindowFactory.kt`) writes the function file, its assembly, and (when
applicable) its state projection.

## Conventions any new work MUST follow

1. **Per-node self-description.** A new node type implements `Render` (Jewel/Compose — Material3
   crashes in the platform classloader), `generate(input)`, `inferType(...)`, and a companion
   `ActionLayout.UExpressionParser` `parse`, and is registered in the `ActionLayout.parse`
   dispatcher. `generate` and `parse` are two halves of one contract — change them in lockstep.
2. **Fully-qualified generated code.** Parsers match resolved FqNames (e.g.
   `com.genovich.components.repeatWhileActive`), so generation must emit fully-qualified calls; a
   hand-written import form will not parse. This is deliberate (design.md D6).
3. **Deterministic generation.** One canonical form (fixed ordering, naming, formatting) so
   `generate ∘ parse ∘ generate` stays a fixed point. Every rung keeps H1 green.
4. **Test harness.** IntelliJ platform tests (`BasePlatformTestCase`). Attach `kotlin-stdlib` via
   a `LightProjectDescriptor` (see `GuessLoopRoundTripTest.STDLIB_DESCRIPTOR`) and add the
   `com.genovich.components` stubs from `plugin/src/test/testData/specimen/Components.kt`. Grow the
   stubs as rungs need new symbols. Use `checkHighlighting(false,false,false)` to assert generated
   code type-checks; round-trip tests assert a `generate ∘ parse ∘ generate` fixed point.
5. **Respect the design decisions.** Especially D3 (two-file split), D5 (assembly exposes every
   node as a defaulted param), D8 (`@Node`/`@Diagram`), D12 (host-agnostic engine behind ports).

---

## Step 1 — Assembly file generation (Rung 2, H3 wiring) — done

**Goal.** Generate the `<Name>Assembly` factory (dependency-plane) alongside the function file,
and emit both from `save()`.

**Note (as implemented).** Kotlin function type parameters go *before* the name
(`fun <Input, T1, T2> \`GuessLoopAssembly\`(...)`), not after like a class — the snippet below
predates that correction. The decorator-wrapping bullet turned out not to apply to the current
node model: `RetryUntilResult` already embeds `retryUntilResult { }` inside the class's own
`invoke()` (see `RetryUntilResult.generate`), so when it's the top-level body node the class
itself already does the decorating — `generateAssembly()` stays uniform (construct ports, call
the constructor) with no special case. `parseAssembly` (the stretch goal) was not implemented.

**Tasks.**
- Extract a shared `signature()` helper on `ActionDefinition` that runs the `inferType` pass once
  and returns `{ typeParameters, ports (name -> in/out), outputType }`. Use it in both `generate()`
  and the new `generateAssembly()`.
- Add `ActionDefinition.generateAssembly(): String` producing:
  ```kotlin
  fun `GuessLoopAssembly`<Input, T1, T2>(
      `readGuess`: com.genovich.components.Action<Input, T1>,
      `checkGuess`: com.genovich.components.Action<T1, T2>,
  ): `GuessLoop`<Input, T1, T2> = `GuessLoop`(
      readGuess = `readGuess`,
      checkGuess = `checkGuess`,
  )
  ```
  For now, external leaf ports become **required parameters** (no defaults — defaults arrive with
  T-functions/child assemblies in later steps). If the top node is a decorator
  (`RetryUntilResult`), wrap the constructed function in it (design.md §3.2 "decoration").
- Wire `save()` to write both `<Name>.kt` and `<Name>Assembly.kt` (replace the `assemblyFileName`
  stub), reformatting both.
- (Stretch) Add `parseAssembly(...)` recovering the port wiring from the assembly function's
  parameters + construction call, to round-trip the dependency plane.

**Files.** `actions/ActionDefinition.kt`, `toolWindow/VisualIdeToolWindowFactory.kt`, tests.

**Tests.** `GuessLoopAssemblyTest`: assert the factory has the typed params and constructs
`GuessLoop`; `checkHighlighting` proves it compiles.

**Acceptance.** Both files generate and type-check; `save()` emits both. (Stretch: assembly
round-trips.) Validates the wiring half of H3.

## Step 2 — T-function (Show) attachment + UiStateFlow projection (Rung 3, H3 projection + H7) — done

**Goal.** Introduce the T-function and the derived `<Name>UiStateFlow`. The projection is
undemonstrable without a `Show`-bound port, so it is sequenced here, right after the assembly.

**Note (as implemented).** Neither a marker/attribute on the leaf, a distinct node type, nor a plain
`Set<String>` (three shapes were tried and dropped — see `docs/example-rung3.md`'s design-history
note). Instead, `ActionDefinition.portDefaults: MutableState<Map<String, PortDefault>>` maps each
derived port to its assembly-plane default; `PortDefault` is a sealed interface with `Show` as its
only implementation today, so "is a T-function" is a literal identity check
(`portDefaults.value[portName] == Show`), not set membership. The body tree (`Action` and every
other node type) stays completely unaware of T-functions. `com.genovich.components.Show` is
recognized via a small standalone `Show.parse` utility (`actions/Show.kt`, not an
`ActionLayout.UExpressionParser` — it recognizes an assembly-plane expression, which has no place
in `ActionLayout.parse`'s dispatcher), scaffolding for a future `parseAssembly` that isn't wired up
yet. The `<Name>UiStateFlow` instance is threaded as the assembly's own defaulted *first* parameter
rather than a body-local `val`, since default-parameter expressions can only reference earlier
parameters, never body locals — this also gives it a D5 override seam for free.
Customizable/multi-kind T-function recognition is deferred as an open hypothesis (design.md §5.1),
not implemented; `PortDefault` being a sealed interface (rather than, say, a boolean) leaves room
for a future binding kind (e.g. wiring a port to a child `*Assembly(...)`) to extend it without
another reshape.

**Tasks.**
- Grow the `Components` stub with `UiState`, `Show`, `StateFlow`/`MutableStateFlow`, `combine`,
  `emitSelfWhenHaveValue` (minimal signatures for resolution — see the reference `components` repo).
- Model a port as a **T-function** (a leaf whose assembly default is `Show(flow)`). At the function
  level it still renders/generates as a leaf call; the T-ness only affects the assembly.
- In `generateAssembly()`: a T-function port gets a default `= com.genovich.components.Show(<Name>UiStateFlow.<port>Flow)`.
- Generate `<Name>UiStateFlow`: a class mirroring the function tree, `combine`-ing each
  T-function's flow, wrapped in a sealed "which screen is live" enumeration (design.md §3.3, §5.2).
- Evolve the specimen so `readGuess` (and later `showResult`) are T-functions.

**Tests.** Assembly with a `Show` default type-checks; the `UiStateFlow` class type-checks and its
sealed wrapper has one case per T-function.

**Acceptance.** T-function ports render distinctly (a checkbox per port in `ActionDefinition`'s own
`Render`; a dedicated T glyph on the canvas is out of scope for the engine tests), generate `Show`
defaults, and produce a compiling `UiStateFlow`. Validates H3 projection + H7.

## Step 3 — Annotations + checksum (Rung 4 → §6 rung 3, H4)

**Goal.** Emit `@Node`/`@Diagram(checksum, layout)` and detect drift.

**Tasks.**
- Define `@Node` and `@Diagram` annotations (design.md §2.4). Emit them on generated classes.
- Compute `checksum` over the **normalized** body (the canonical generated form). Store in
  `@Diagram`.
- On parse, read the checksum; add a drift check that recomputes and compares, flagging code edited
  out of band.
- `layout` can be a stub/no-op for now (cosmetic; §2.7).

**Tests.** Checksum stable across regenerate cycles; a hand-edited body is detected as drifted.

**Acceptance.** Round-trip still green with annotations present; drift detection works. Validates H4.

## Step 4 — Engine IR / KotlinAnalysis boundary (Rung 5 → §6 rung 4, H5)

**Goal.** Interpose the engine's own IR between UAST and the nodes (design.md D12), so the model is
not coupled to IntelliJ.

**Tasks.**
- Define a minimal engine IR for the expression shapes the parsers need (call, qualified call,
  lambda-with-single-return, reference).
- Add a `KotlinAnalysis` port: `UAST → IR` adapter (the only place `org.jetbrains.uast` is used).
- Refactor node `parse`s to consume IR instead of UAST directly.

**Tests.** Round-trip stays green; add a check/arch-test that the `actions` package no longer
imports `org.jetbrains.uast`.

**Acceptance.** Same behavior, no UAST types in the node logic. Validates H5.

## Step 5 — Value plumbing, Branch, and the full game (§6 rung 5, H6 + finish H7)

**Goal.** Express a real body with value manipulation and n-way branching; complete the
guess-the-number specimen.

**Tasks.**
- **Named SSA `val`s.** Replace `Passing`'s linear `.let{}` pipe with named `val` bindings
  (design.md §2.7) so intermediates can be reused. *This is the predicted H6 break — expect to
  rework `Passing` generation/parse.*
- **Value nodes** (design.md §2.5): `Tuple` (`a to b`), `Construct`, `Copy`, `Project`, `Select`,
  `Guard`, `Not`. Each with generate/parse/inferType/Render.
- **Branch** over a named `sealed` type (design.md §1.5, D2): exhaustive `when`, one out-port per
  case, incomplete until all cases wired.
- **Full specimen**: `Guess`/`Secret`/`sealed Comparison { TooLow; TooHigh; Correct }`; a round
  that asks (T-function), `compare(secret to guess)` (Tuple + leaf), shows result (T-function), and
  loops until `Correct` (Branch driving the loop). Optional **Parallel** rung (timeout racing the
  guess) — a race node (design.md §5.2, D13).

**Tests.** Round-trip + type-check for each new node; the full game round-trips and type-checks.

**Acceptance.** A real body with reuse and branching round-trips with no opaque text. Validates H6
and completes H7.

---

## Cross-cutting risks / notes

- **Parsing top-level assembly functions** (Step 1 stretch) and **`when`/sealed** (Step 5) need new
  UAST handling beyond the current expression parsers — budget for it.
- **`checkHighlighting`** can be sensitive to fixture setup; keep the stub minimal-but-valid and
  attach stdlib. Suspend calls inside `repeatWhileActive`/`Show` require those stubs to be
  `suspend inline`.
- **Repeated port names** currently reuse the first inferred typing (no unification) — fine until a
  program uses one port at two types; note if Step 5 hits it.
- Keep each rung's specimen change minimal and additive so earlier tests stay green.
