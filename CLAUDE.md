# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this
repository.

## What this project is

An IntelliJ/Android Studio plugin that lets a developer author programs as diagrams and generates
Kotlin code from them (and parses generated code back into the diagram), for a specific
function-composition architecture where every unit of logic is an `Action` (a `suspend (Input) ->
Output` functional object carrying its dependencies in its constructor).

Read `docs/design.md` first — it is the authoritative architecture spec (core model, operator
catalog, diagram↔code round-trip contract, two-file `fn:`/`asm:` generation scheme, decisions log
D1–D17, and the rung-by-rung implementation ladder in §6). The root `README.md` is a
chronological dev diary/stream-of-consciousness, not documentation — don't treat it as a spec, but
it's useful for *why* a given piece of code looks the way it does.

## Code exploration

This repo is indexed with `codebase-memory-mcp`. Use it **first** for any structural code
question — before grepping or reading files blind:

- `search_graph` / `search_code` to find functions, classes, or text across the project.
- `trace_path` to follow call chains (e.g. who calls `ActionLayout.parse`, or how a
  `generate()`/`inferType()`/`parse()` triple connects).
- `get_code_snippet` for exact symbol source.
- `get_architecture` for the project-wide structure/cluster/hotspot overview.
- `manage_adr(mode='get')` to pull the stored Architecture Decision Record for this project
  instead of re-deriving the architecture from scratch — update it (`mode='update'`) when a
  change meaningfully shifts the architecture, patterns, or tradeoffs described in it.
  If the index looks stale (recent commits not reflected), re-run `index_repository`.

## `docs/` folder

- **`docs/design.md`** — the architecture spec, see above. Read this before making any change to
  the `ActionLayout` model, codegen, or parsing.
- **`docs/example-rung0.md`** — rung 0 write-up: the first end-to-end round-trip milestone
  (`GuessLoop` = `RepeatWhileActive(Passing[Action, Action])`), validating H1 (round-trip
  idempotence) with a shared `<Input, Output>` placeholder type. Explains why parsers require
  fully-qualified calls.
- **`docs/example-rung1.md`** — rung 1 write-up: replaces the placeholder types with real
  per-port structural type inference, validating H2 (the generated code type-checks, not just
  parses). Documents the `inferType` scheme per node type and its known limitations (no
  unification across repeated port names).

These two example docs are progress logs for the rung ladder in `docs/design.md` §6 — see
"Implementation status" below for what's done vs. still open. When a new rung is completed, add a
matching `docs/example-rungN.md` following the same structure (what was added, expected generated
code, files changed, verification notes, limitations feeding back into §6).

## Repository layout

This is a Kotlin Multiplatform Gradle project, but only one module currently has real work in it:

- **`plugin/`** — the actual product: an IntelliJ platform plugin (Kotlin/JVM + Compose via
  Jewel). All active development happens here. Entry point is
  `VisualIdeToolWindowFactory` (registers a Compose tool window tab, parses the currently open
  file's `Action` classes into diagram models via UAST, and renders them).
- **`composeApp/`, `shared/`, `server/`** — leftover scaffolding from the KMP project template
  used to bootstrap the repo before the decision to pivot to an IntelliJ plugin (see README.md).
  Not part of the active product; don't extend these unless specifically asked to.

## Commands

All commands run from the repo root using the Gradle wrapper.

```bash
# Run the plugin in a sandbox IDE instance
./gradlew :plugin:runIde

# Run all plugin tests
./gradlew :plugin:test

# Run a single test class
./gradlew :plugin:test --tests "com.genovich.visualide.GuessLoopRoundTripTest"

# Build the plugin distribution
./gradlew :plugin:buildPlugin
```

Notes:

- `:plugin:test` uses `TestFrameworkType.Platform` (IntelliJ's test framework), which downloads a
  full IDE distribution on first run — this can be slow/impossible in sandboxed CI-like
  environments; if a test run can't complete here, say so rather than assuming green.
- The plugin targets Android Studio Otter 2025.2.1+ / IntelliJ Platform 2025.2.1 (see
  `gradle.properties`: `platformType=IC`, `platformVersion=2025.2.1`), Kotlin 2.2.21, JVM
  toolchain 21.
- UI in the plugin module uses Jewel components (`org.jetbrains.jewel.ui.component`), not
  Compose Material3 — Material3 crashes at runtime inside the IntelliJ platform classloader
  (`ComposerImpl` cast exception across classloaders). Any new plugin UI must use Jewel.

## Architecture of the `plugin` module

### The `ActionLayout` model (`plugin/src/main/kotlin/com/genovich/visualide/actions/`)

Everything the diagram can represent implements `ActionLayout`, a closed but growing set of node
types, each of which is simultaneously:

1. a Compose-renderable diagram node (`Render`),
2. a Kotlin code generator (`generate`),
3. a structural type-inference step (`inferType` — mints per-port type variables so generated
   code actually type-checks instead of sharing one placeholder `<Input, Output>`), and
4. a UAST parser (`companion object : ActionLayout.UExpressionParser<T>`, `parse`) that recognizes
   its own generated shape in source and reconstructs the model from it — with one deliberate
   exception, `TFunction` (below), whose `generate()` is indistinguishable from `Action`'s and so
   cannot have a parser without creating ambiguous-match errors in the dispatcher.

Current node types, each in its own file:

- `Action` — a leaf port call (`` `name`(input) ``).
- `TFunction` — a leaf port call, identical to `Action` at the function level, but a T-function
  (design.md §1.6, §5.1): its assembly default is `Show(flow)` instead of a required parameter,
  and it contributes a flow to the generated `<Name>UiStateFlow` projection. No parser (see above);
  a reparsed T-function leaf always comes back as a plain `Action` — see `docs/example-rung3.md`.
- `Passing` — a linear pipeline (`.let { }` chain); models the Sequence operator.
- `RepeatWhileActive` — an infinite loop (`repeatWhileActive { }`); returns `Nothing`.
- `RetryUntilResult` — a decorator that retries its body until it returns without throwing.
- `TodoStub` — placeholder generated/rendered when a slot is empty.

`ActionLayout.parse` (the dispatcher in `ActionLayout.kt`) races all node parsers concurrently
against a UAST expression and requires **exactly one** to succeed — zero or multiple matches are
both errors (ambiguous grammar is a bug, not a feature). Parsers match on fully-qualified resolved
names (e.g. `com.genovich.components.repeatWhileActive`, `kotlin.StandardKt.let`), so **only
canonical, fully-qualified generated code round-trips** — hand-written code using imports is not
guaranteed to parse. This is a deliberate scope boundary (see `docs/example-rung0.md`), not a bug.

`ActionDefinition` is the top-level container (maps to a generated Kotlin class): it holds a
name, a mutable `body: ActionLayout?`, derives constructor dependency ports automatically by
walking the body's leaf `Action`s, and threads type variables through `inferType` to produce a
type-checking generated class.

### Round-trip contract

The diagram is the source of truth (`docs/design.md` D6): `generate` is deterministic, `parse`
reconstructs the model from generate's own canonical output, and `generate ∘ parse ∘ generate`
must be a fixed point (H1). When changing any node's `generate()`, its `parse()` must be updated
in lockstep, and vice versa — they are two halves of one contract, verified by round-trip tests
(see `GuessLoopRoundTripTest`).

### Implementation status (the design doc's rung ladder, §6)

- **Rung 0 (done)** — round-trip works (H1) for `RepeatWhileActive(Passing[Action, Action])`
  using a shared `<Input, Output>` placeholder type.
- **Rung 1 (done)** — real per-port type inference (H2): each port gets its own inferred
  `Action<In, Out>` type via `inferType`, and the generated code actually type-checks.
- **Rung 2 / Step 1 (done)** — assembly file generation (`ActionDefinition.generateAssembly()`),
  the wiring half of H3. See `docs/example-rung2.md`. `parseAssembly` (dependency-plane round-trip)
  is still open.
- **Rung 2 / Step 2 (done)** — T-function ports, a distinct leaf node type (`TFunction`, no
  parser — see `docs/example-rung3.md`), and the derived `<Name>UiStateFlow` projection
  (`ActionDefinition.generateUiStateFlow()`), the projection half of H3, plus H7. See
  `docs/example-rung3.md`.
- **Not yet implemented**: `@Node`/`@Diagram` annotations and checksums, the engine IR /
  `KotlinAnalysis` host-abstraction boundary, value-plumbing nodes
  (Tuple/Construct/Copy/Project/Select/Guard/Not), Branch, and Parallel. See
  `docs/implementation-plan.md` (Steps 3–5) and `docs/design.md` §6.3–6.4 for the planned order.

### UAST, not raw PSI

Parsing goes through UAST (`org.jetbrains.uast`), not raw PSI directly — this is intentional
groundwork for the `KotlinAnalysis` host-abstraction boundary (`docs/design.md` §4.5, D12), which
is meant to keep the engine portable beyond IntelliJ eventually. Don't reach for raw PSI APIs in
node parsers unless UAST genuinely can't express what's needed.

### Tests

`plugin/src/test/kotlin/.../GuessLoop{Generate,RoundTrip}Test.kt` are the reference tests for the
round-trip contract, built around a synthetic "guess the number" specimen
(`plugin/src/test/testData/specimen/Components.kt` supplies minimal `com.genovich.components`
stubs so UAST can resolve symbols without depending on the real external library).
`GuessLoopRoundTripTest` uses `BasePlatformTestCase` with a custom `LightProjectDescriptor` that
attaches the real `kotlin-stdlib.jar` — the default light test project has no Kotlin runtime
attached, so `.let` (which `Passing.parse` matches on) won't resolve without it. Follow this
pattern for new node-type round-trip tests.