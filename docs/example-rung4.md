# End-to-end example — rung 2, step 3 (integrity: `@Node`/`@Diagram` + checksum)

Rung 3 of the ladder in `docs/design.md` §6 (§6.3 point 3), step 3 of `docs/implementation-plan.md`.
Goal: emit `@Node`/`@Diagram(checksum, layout)` on generated classes and detect drift, validating
**H4** (a normal form exists whose checksum is stable across regenerate cycles).

## The annotations

`ActionDefinition.generate()` now emits two annotations above the class (design.md §2.4, D8/D9):

```kotlin
@com.genovich.components.Node
@com.genovich.components.Diagram(version = 1, checksum = "sha256:…")
class `GuessLoop`<Input, T1, T2>(...) : com.genovich.components.Action<Input, Nothing>() { ... }
```

- **`@Node`** — present on every generated class (face + interface, D8). Its descriptor is fully
  inferred from the Kotlin signature (D9: `Input`/`Output` from the `Action<I,O>` supertype,
  dependency ports from the constructor), so it carries no arguments yet — `label`/`category` exist
  on the annotation (mirroring the design.md §2.4 snippet) but nothing in `ActionDefinition`'s state
  tracks them today, so there's nothing to fill in. Emitted bare rather than with a redundant
  `label = "GuessLoop"` that would just restate the class name.
- **`@Diagram`** — present on every generated class too, because every `ActionDefinition` *is* a
  composite (IDE-authored) body by construction; there is currently no path that generates a
  leaf/custom-atom class (those are hand-written and never pass through `generate()` at all, so they
  never get an `@Diagram` — matching "a leaf/custom atom carries `@Node` and no `@Diagram`", design.md
  §2.4). `version` is a constant `1` for now (no format migrations exist yet). `layout` is omitted
  entirely — a no-op/stub per the plan's Step 3 scope (§2.7: cosmetic, auto-layout fallback if
  absent).

## The checksum

`checksum` is a `sha256:`-prefixed hex digest (`java.security.MessageDigest`) of the **body text**
— `body.value?.generate(input) ?: TodoStub.generate()`, the same string embedded as the `invoke()`
implementation — not the whole class (name, ports, type parameters). Two reasons for scoping it to
just the body:

1. **Avoids a chicken-and-egg problem.** The annotation is part of the class text; hashing the whole
   class (including its own annotation line) would make the checksum depend on itself.
2. **Matches design.md §2.7's "checksum is taken over this normalized form"** — the normalized
   *body*, not incidental framing like the class's type-parameter list or constructor.

A private `bodyCode()` helper computes this string once; both `generate()` (to embed it and the body
itself) and `parse()` (to recompute it for the drift check, from the *parsed* body) call it, so the
hash and the hashed content can never drift apart from each other.

## Drift detection

`ActionDefinition.parse()` reads the source class's `@Diagram(checksum = …)` value (via UAST's
`UAnnotated.findAnnotation` + `UAnnotation.findAttributeValue("checksum").evaluate()`) and compares
it against the checksum of the just-parsed-and-regenerated body. A mismatch means the class body was
edited by hand, out of band from the diagram tool, in a way that changed its semantics — flagged as
a new `ActionDefinition.isDrifted: Boolean` field (`false` by default: nothing to drift from for an
in-memory definition never built from source, and always `false` when the source has no `@Diagram`
at all, e.g. a leaf/custom node). Nothing currently *surfaces* `isDrifted` in the UI (`Render()`
untouched) — this step validates that drift is *detectable*, matching the acceptance criterion
("drift detection works"), not that it's surfaced yet.

## `Components.kt` stub growth

Added two bare `annotation class` declarations, `Node(label = "", category = "")` and
`Diagram(version = 1, checksum = "", layout = "")` — synthetic stand-ins for the real library's
annotations (not yet designed further than this), just enough for UAST to resolve
`com.genovich.components.Node`/`Diagram` and for `checkHighlighting` to accept them.

## Files changed

- `actions/ActionDefinition.kt` — new `isDrifted: Boolean = false` constructor parameter (both
  constructors); new private `bodyCode()`; new companion `checksumOf(bodyCode)`; `generate()` now
  emits the two annotation lines; `parse()` now extracts the stored checksum and sets `isDrifted`.
  New companion constants `COM_GENOVICH_COMPONENTS_NODE`, `COM_GENOVICH_COMPONENTS_DIAGRAM`,
  `INPUT_PARAM_NAME` (replacing the local `"input"` literal previously inlined in `generate()`).
- `plugin/src/test/testData/specimen/Components.kt` — the two annotation stubs.
- `GuessLoopChecksumTest` (new) — asserts both annotations appear in `generate()`'s output; asserts
  the checksum is stable across a `generate → parse → generate` cycle and that the round-tripped
  definition is not flagged drifted; asserts a hand-tampered body (pipeline order swapped, checksum
  left stale) parses with `isDrifted == true`.
- No other node type, `generateAssembly()`, or `generateUiStateFlow()` changed — `@Node`/`@Diagram`
  apply only to the function-file class per design.md §2.4's snippet; the assembly factory function
  and the `UiStateFlow` projection class are not themselves "nodes" in that sense.

## How to run

```
./gradlew :plugin:test --tests "com.genovich.visualide.GuessLoop*" --tests "com.genovich.visualide.ShowTest"
```

## Limitations / notes (feed back into §6 and later steps)

- **Drift is detected but not surfaced.** `isDrifted` is computed and available on the parsed model,
  but nothing in `Render()` or the tool window shows it to the user yet — that's UI work, out of
  scope for validating H4 itself.
- **The checksum covers the body only, not ports/type-parameters/class name.** A hand-edit that
  renames a constructor parameter or reorders ports without touching the `invoke()` body would not
  be flagged. Widening the hashed surface (e.g. to the full `signature()`) is straightforward if a
  later rung needs it, but the plan's Step 3 acceptance criterion ("a hand-edited body is detected
  as drifted") is scoped to the body.
- **`layout` is unimplemented**, per the plan ("can be a stub/no-op for now") — always omitted from
  the emitted annotation rather than written as an empty string, since Kotlin annotation parameters
  with defaults don't need to be spelled out.
- **`@Node`'s cosmetic fields (`label`/`category`) are never populated** — `ActionDefinition` has no
  state for them yet. Revisit alongside D9's "to revisit if inference proves insufficient" note if a
  future rung needs custom labels/categories in the palette.
