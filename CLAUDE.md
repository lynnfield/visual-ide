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
- **`docs/example-rung2.md`** — rung 2 / step 1: assembly file generation
  (`ActionDefinition.generateAssembly()`), the wiring half of H3.
- **`docs/example-rung3.md`** — rung 2 / step 2: T-function ports (`Show`) and the derived
  `<Name>UiStateFlow` projection, the projection half of H3, plus H7.
- **`docs/example-rung4.md`** — rung 2 / step 3: `@Node`/`@Diagram(checksum)` annotations and
  drift detection, validating H4.
- **`docs/example-rung5.md`** — rung 4: the engine IR / `KotlinAnalysis` UAST-adapter boundary,
  validating H5 (no `org.jetbrains.uast` imports left in the node parsers).
- **`docs/example-rung6.md`** — rung 5: named SSA `val`s replacing `Passing`'s `.let{}` pipe
  (the predicted H6 break), `Tuple`/`Ref` value-plumbing nodes, `Branch` over a sealed type, and
  the full `GuessGame` specimen (H6, completes H7).
- **`docs/example-rung7.md`** — not a new §6 rung (the ladder ended at rung 5); closes rung 2 /
  step 1's `parseAssembly` stretch goal (design.md §2.8's dependency-plane round-trip): a new
  `FunctionInfo` IR shape for top-level functions, `KotlinAnalysis.parseFunction`, and
  `ActionDefinition.parseAssembly` recover `portDefaults` from a parsed `<Name>Assembly.kt`.

These example docs are progress logs for the rung ladder in `docs/design.md` §6 — see
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

## Git workflow

`.codebase-memory/` (the `codebase-memory-mcp` index artifact) is tracked in git. Whenever you
make a commit, stage and include any pending changes under `.codebase-memory/` alongside the code
changes, so the checked-in index stays in sync with the commit it describes.

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
   its own generated shape in source and reconstructs the model from it.

Current node types, each in its own file:

- `Action` — a leaf port call (`` `name`(input) ``).
- `Passing` — a linear pipeline of named SSA `val`s wrapped in `run { }` (reworked in rung 5 from
  a `.let { }` chain, confirming the predicted H6 break); models the Sequence operator.
- `Ref` — names/re-reads a value already in scope (needed under `Tuple` to recover an earlier
  value after a later step consumes it).
- `Tuple` — pairs two in-scope values via the dot-call `a.to(b)` form (not the infix `a to b`
  operator, so it stays a resolvable qualified call).
- `Branch` — n-way dispatch over a named sealed type; generates an exhaustive `when` with a
  required trailing `else -> TODO(...)` arm (structural type inference only mints opaque type
  variables, so Kotlin can't prove exhaustiveness itself).
- `RepeatWhileActive` — an infinite loop (`repeatWhileActive { }`); returns `Nothing`. No
  break/return primitive yet, so a `Branch` case can't actually exit the loop (see design.md
  §1.4's unimplemented `updateLoop`).
- `RetryUntilResult` — a decorator that retries its body until it returns without throwing.
- `TodoStub` — placeholder generated/rendered when a slot is empty.

Of design.md §2.5's full value-plumbing palette, only `Tuple` and `Ref` are implemented;
`Construct`/`Copy`/`Project`/`Select`/`Guard`/`Not` are not — each is an independent, bounded
follow-up in the same shape as the existing nodes.

`ActionLayout.parse` (the dispatcher in `ActionLayout.kt`) races all node parsers concurrently
against a UAST expression and requires **exactly one** to succeed — zero or multiple matches are
both errors (ambiguous grammar is a bug, not a feature). Parsers match on fully-qualified resolved
names (e.g. `com.genovich.components.repeatWhileActive`, `kotlin.StandardKt.let`), so **only
canonical, fully-qualified generated code round-trips** — hand-written code using imports is not
guaranteed to parse. This is a deliberate scope boundary (see `docs/example-rung0.md`), not a bug.

`ActionDefinition` is the top-level container (maps to a generated Kotlin class): it holds a
name, a mutable `body: ActionLayout?`, derives constructor dependency ports automatically by
walking the body's leaf `Action`s, threads type variables through `inferType` to produce a
type-checking generated class, and (`portDefaults: MutableState<Map<String, PortDefault>>`)
attaches each derived port's assembly-plane default, when it has one other than "required" (design.md
§1.6, §5.1) — see "T-function recognition" below.

### T-function recognition (`actions/Show.kt`)

A port is a T-function purely by its `ActionDefinition.portDefaults` entry being `Show` — attached
at the *definition* level, not baked into a leaf node's type or a flag (two earlier designs tried
both; see `docs/example-rung3.md`). `PortDefault` is a sealed interface (`ActionDefinition.kt`)
with `Show` (`actions/Show.kt`) as its only implementation today — `com.genovich.components.Show`
is the only recognized T-function binding, matched by `Show.parse`, a standalone recognizer,
**not** an `ActionLayout.UExpressionParser` and not registered in `ActionLayout.parse`'s
dispatcher, since `Show` marks a *dependency-plane* (assembly) default value, not a function-body
node. `ActionDefinition.parseAssembly` (design.md §2.8, `docs/example-rung7.md`) calls `Show.parse`
per assembly parameter with a default expression, to recover `portDefaults` when a project is
reopened — see "Dependency-plane round-trip" below. Whether T-function recognition should be
customizable (a registry, not one hardcoded FQN) or support multiple T-function *kinds* is an open
hypothesis — see design.md §5.1's "Open question" note — not implemented.

### Dependency-plane round-trip (`ActionDefinition.parseAssembly`)

`portDefaults` (which ports are T-functions) lives only at the definition level, never in the body
tree — so re-parsing `<Name>.kt` alone always yields `portDefaults = emptyMap()`.
`ActionDefinition.parseAssembly(functionInfo, definition)` recovers it from a parsed
`<Name>Assembly.kt`: `KotlinAnalysis.parseFunction(uMethod: UMethod): FunctionInfo` is the IR
adapter for a **top-level function** (a shape no prior parser needed, since every node parser
matches an expression inside a class's `invoke()` body) — note Kotlin surfaces a top-level
function's UAST node as a `UMethod` of a synthetic `<FileName>Kt` facade `UClass`, not directly off
`UFile`. `parseAssembly` is scoped to exactly what `generateAssembly` emits today (a required
parameter vs. a `Show`-defaulted one), not design.md §2.8's fuller dependency-plane grammar
(child-assembly wiring, decorators, shared singletons), which `generateAssembly` doesn't produce
yet either. `VisualIdeToolWindowFactory`'s file-scan calls it via `withRecoveredPortDefaults` to
merge recovered T-function attachment into a freshly reparsed definition. See
`docs/example-rung7.md`.

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
  the wiring half of H3. See `docs/example-rung2.md`. `parseAssembly` (dependency-plane
  round-trip) is now done too — see "Dependency-plane round-trip" above and `docs/example-rung7.md`.
- **Rung 2 / Step 2 (done)** — T-function ports (`ActionDefinition.portDefaults`, recognized via
  `com.genovich.components.Show` — see "T-function recognition" above) and the derived
  `<Name>UiStateFlow` projection (`ActionDefinition.generateUiStateFlow()`), the projection half of
  H3, plus H7. See `docs/example-rung3.md`.
- **Rung 2 / Step 3 (done)** — `@Node`/`@Diagram(checksum)` emitted on every generated class;
  drift detection (`ActionDefinition.isDrifted`) recomputes the checksum on parse and flags
  hand-edited bodies, though it isn't surfaced in any UI yet. `layout` stays an unimplemented
  stub. Validates H4. See `docs/example-rung4.md`.
- **Rung 4 (done)** — the engine IR / `KotlinAnalysis` UAST-adapter boundary (D12) is interposed;
  node parsers consume the IR (`com.genovich.visualide.analysis`), not raw UAST. Validates H5.
  See `docs/example-rung5.md`.
- **Rung 5 (done)** — named SSA `val`s (the predicted H6 break), `Tuple`/`Ref` value-plumbing
  nodes, `Branch` over a sealed type, and the full `GuessGame` specimen (two T-functions)
  round-trip and type-check. Validates H6, completes H7. See `docs/example-rung6.md`.
- **Known gaps, called out explicitly in the rung docs (not silently missing)**:
  - Loop exit isn't implemented — `repeatWhileActive` has no break/return, so `Branch` can't
    actually terminate a loop (design.md §1.4's `updateLoop` is a separate, unbuilt primitive).
  - Optional Parallel rung (D13, timeout-racing node) never attempted.
  - `Construct`/`Copy`/`Project`/`Select`/`Guard`/`Not` value-plumbing nodes not built (only
    `Tuple`+`Ref` landed — sufficient for the rung 5 target).
  - `parseAssembly` only round-trips what `generateAssembly` emits (required + `Show`-defaulted
    ports) — design.md §2.8's fuller grammar (child-assembly wiring, decorators, shared
    singletons) is generated by neither side yet.
  - Drift detection has no UI surface, and doesn't cover the assembly file (only the function
    file's body has a checksum).
  - Customizable/multi-kind T-function recognition (design.md §5.1) is still a hardcoded FQN.
  - See `docs/implementation-plan.md` for the full step-by-step history and
    `docs/design.md` §6.3–6.4 for the ladder these rungs climbed.

### Engine IR / `KotlinAnalysis` boundary, not raw UAST

Node parsers consume the engine's own IR (`com.genovich.visualide.analysis`: a sealed `Expr` with
call/qualified-call/lambda/reference shapes, plus `WhenExpr`/`WhenCase` for `Branch` and a
`ClassInfo` for the `ActionDefinition.parse` entry point), not `org.jetbrains.uast` directly —
`KotlinAnalysis` (`parseClass`/`toExpr`) is the sole UAST↔IR adapter, interposed in rung 4 to keep
the engine portable beyond IntelliJ (`docs/design.md` §4.5, D12). Resolution
(`resolvedName`/`resolvedQualifiedName`) is computed eagerly by the adapter and carried as a field
on every `Expr` variant. Don't import `org.jetbrains.uast` in a node's `parse` — extend the IR
instead if it can't express the new shape. Two platform utilities intentionally stayed out of the
IR as out-of-scope (`runBlockingCancellable`, `getOrLogException`) since neither carries
UAST/PSI types across the boundary.

### Tests

`plugin/src/test/kotlin/.../GuessLoop{Generate,RoundTrip}Test.kt` are the reference tests for the
round-trip contract, built around a synthetic "guess the number" specimen
(`plugin/src/test/testData/specimen/Components.kt` supplies minimal `com.genovich.components`
stubs so UAST can resolve symbols without depending on the real external library).
`GuessLoopRoundTripTest` uses `BasePlatformTestCase` with a custom `LightProjectDescriptor` that
attaches the real `kotlin-stdlib.jar` — the default light test project has no Kotlin runtime
attached, so `.let` (which `Passing.parse` matches on) won't resolve without it. Follow this
pattern for new node-type round-trip tests.