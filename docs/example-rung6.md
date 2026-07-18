# End-to-end example — rung 5 (value plumbing, Branch, and the full game)

Rung 5 of the ladder in `docs/design.md` §6 (§6.3 point 5), Step 5 of
`docs/implementation-plan.md`. Goal: express a real body with value manipulation and n-way
branching, and complete the guess-the-number specimen — validating **H6** (no-opaque-text value
plumbing needs named SSA `val`s, not `Passing`-as-pipe) and completing **H7** (T-function +
projection, now exercised with two T-functions instead of one).

## Named SSA `val`s — the H6 break

`Passing` no longer generates a `.let{}` chain. Each step gets its own `stepN` binding, wrapped in
`run { }` so the whole pipeline still satisfies `ActionLayout.generate`'s "one expression" contract
(design.md §2.7):

```kotlin
run {
    val step1 = `readGuess`(input)
    val step2 = `checkGuess`(step1)
    step2
}
```

`stepN` is **positional**, not a user-authored node id — design.md §2.7 wants "node id = val name";
per-node naming is a future UI feature (a text field on each step, like `Action`'s), out of scope
here. The prediction in design.md's H6 entry ("`Passing`-as-pipe is insufficient") was correct: this
is exactly the change that makes `Ref` (below) possible — `step1` stays in lexical scope for every
later step in the same block, unlike the old anonymous `.let { it }` chain, which discarded each
value the moment the next step ran.

### The IR grew a real statement/block shape

`analysis.Expr`'s `Lambda` no longer carries a single `singleReturnExpression: Expr?` — it carries
`statements: List<Stmt>`, a new sealed type with two cases: `ValStmt(name, initializer)` and
`ExprStmt(expr)` (the block's trailing expression, or a side-effecting statement). A
`Lambda.singleReturnExpression` extension property (`(statements.singleOrNull() as?
ExprStmt)?.expr`) preserves the old call sites in `RepeatWhileActive`/`RetryUntilResult`, which
still only need "the lambda's one expression," unchanged.

`KotlinAnalysis.toExpr`'s new `toStmt` helper converts a block's UAST expressions into `Stmt`s:
a `UDeclarationsExpression` wrapping a single `ULocalVariable` becomes a `ValStmt`; anything else
becomes an `ExprStmt`, unwrapping UAST's implicit-return wrappers first — a `UReturnExpression` for
a lambda's trailing expression, or (new, see below) a `UYieldExpression` for a `when`-arm's result.
Total/best-effort like the rest of the adapter: an unrecognized shape falls back to a bare
`ExprStmt` rather than throwing.

## `Ref` — naming a value already in scope

`Ref` (design.md §2.5) is the plumbing that makes named SSA vals useful instead of just cosmetic: a
free-text node (same UX as `Action`'s port name) that **ignores its own pipeline input** and emits
whatever name it's given. `Ref("input")` inside a `repeatWhileActive { }` lambda still resolves —
Kotlin closures capture the enclosing `invoke(input)` parameter — recovering a value even after a
later step has shadowed the pipeline's "current" value.

Its `inferType` needed a real scope, not a guess. `ActionLayout.inferType` gained a fourth
parameter, `scope: MutableMap<String, String>`, threaded everywhere `ports` already is:
`ActionDefinition.signature()` seeds it with `input -> Input`; `Passing.inferType` additionally
records each step's inferred type under its `stepN` name as it folds. `Ref.inferType` is then just
`scope[name.value] ?: input` — a real lookup, falling back to the ambient input type (never
throwing, matching the rest of this structural, best-effort type pass) when the name isn't in
scope. The first version of this hard-coded `Ref` to always mean "the composite's own input" and
broke the moment the specimen needed a *second* kind of reference (`Ref("step1")`, recovering
`askGuess`'s result) — real scope tracking replaced it, not a wider hack.

## `Tuple` — `a.to(b)`

Both operands are fed the **same** ambient value (a fan-out, like `RepeatWhileActive`/
`RetryUntilResult` already pass their own input into a nested slot unchanged) — not a chain.
`Tuple(first, second).generate(input) = "${first.generate(input)}.to(${second.generate(input)})"`;
`inferType` returns `kotlin.Pair<firstType, secondType>`.

**Canonical form uses `.to(...)` dot-call syntax, not the infix `a to b` operator form.** The
engine's IR only recognizes named dot-calls/qualified-calls (`QualifiedCall`/`Call`) reliably;
infix-operator UAST recognition (`UBinaryExpression`) is a distinct, more involved shape this rung
doesn't add. `a.to(b)` is semantically identical Kotlin — `to` is simply an infix-declared
extension function, callable either way — so nothing is lost, just a cosmetic deviation from
design.md §2.5's literal example.

Resolving `to`'s FQN empirically (a scratch probe test, since when it comes to the compiled-name
questions this project has been repeatedly surprised — `kotlin.StandardKt.let` for `let` — trusting
the same shape for `to` was wrong: `to`'s `kotlinFqName` resolves to the plain **`kotlin.to`**, no
file-facade segment). Lesson: don't extrapolate a new stdlib symbol's resolved FQN from a different
symbol's; check.

## `Branch` — n-way sealed dispatch

`Branch` (design.md §1.5/D2) generates `when (val branch = input) { is Case1 -> …; is Case2 -> …;
…; else -> TODO(...) }`. The subject is always bound to a fixed name (`branch`) — canonical form,
so parsing never needs to recover a user-chosen name.

**Every case is inferred against the same (sealed-supertype) input type, not the narrowed
`is Case ->` subtype.** This structural type-inference pass has no flow-sensitive smart-casting —
only per-port-name placeholder types. That's still sound generated Kotlin: passing a narrower type
where a uniform, wider-typed port expects the supertype always type-checks; the real Kotlin compiler
smart-casts the *value* inside each arm, our engine just doesn't need to track that narrowing to
generate correct code. `Branch`'s own result type is the first case's inferred type — no unification
across cases, correct here because every case actually produces `Unit`.

**The trailing `else -> TODO(...)` arm is required, and is regenerated boilerplate, not modeled
data.** Real compiler-enforced exhaustiveness would need the `when` subject typed as the actual
sealed supertype (`Comparison`), but this engine's type inference only ever mints opaque type
variables (rung 1, H2) — Kotlin can't prove exhaustiveness against a bare, unconstrained type
parameter from `is Case ->` checks alone, and the code fails to compile without `else`. The
diagram's own completeness invariant (design.md §1.5 — "incomplete until every case is wired") is
enforced at the model level instead (every `Branch.Case` must have a wired body); `Branch.parse`
drops the `else` arm on read (its `caseValues` are empty, so it never resolves to an `is Type`
check) rather than trying to round-trip it.

### The IR grew a `when`-shape

`analysis.Expr` gained `WhenExpr` (`subjectName`, `subjectInitializer`, `cases: List<WhenCase>`) and
`WhenCase` (`caseTypeQualifiedName`, `statements: List<Stmt>` — the same block shape `Lambda` uses,
so a case body can grow SSA structure later). `KotlinAnalysis.toExpr` handles `USwitchExpression`:
the subject comes from unwrapping its `UDeclarationsExpression`; each
`USwitchClauseExpressionWithBody`'s single `caseValues` entry is a `UBinaryExpressionWithType`
(`is Case` — UAST's shape for a type-check condition), whose `.typeReference.getQualifiedName()`
gives the case's FQN (same pattern `ClassInfo.superTypeQualifiedNames` already uses for
`uastSuperTypes`).

One surprise the probe (dumping `when`'s UAST tree via reflection before writing the IR — see
`docs/example-rung5.md`'s Step 4 precedent for the same technique) didn't fully surface: a
`when`-arm's result value is wrapped in a `UYieldExpression`, not a `UReturnExpression` like a
lambda's trailing expression. `toStmt` unwraps both.

## The full specimen: `GuessGame`

```kotlin
askGuess  : Action<Secret, Guess>                    // T-function (Show)
compare   : Action<Pair<Secret, Guess>, Comparison>   // leaf; input via a Tuple node
showResult: Action<Comparison, Unit>                  // T-function (Show)
```

Body: `RepeatWhileActive(Passing[askGuess, Tuple(Ref("input"), Ref("step1")), compare,
Branch[TooLow, TooHigh, Correct → showResult]])`, generating:

```kotlin
com.genovich.components.repeatWhileActive {
    run {
        val step1 = `askGuess`(input)
        val step2 = input.to(step1)
        val step3 = `compare`(step2)
        val step4 = when (val branch = step3) {
            is specimen.TooLow -> `showResult`(branch)
            is specimen.TooHigh -> `showResult`(branch)
            is specimen.Correct -> `showResult`(branch)
            else -> TODO("implement body")
        }
        step4
    }
}
```

`showResult` is called from **three** branch arms sharing one port name — deliberately exercising
`docs/implementation-plan.md`'s "repeated port names reuse the first inferred typing, no
unification" caveat, not hitting it by accident: since every arm is typed against the same
(sealed-supertype) input per Branch's typing note above, all three calls agree on `Action<T2,
Unit>`, so "first occurrence wins" is simply correct here, not merely tolerated.

`GuessGameRoundTripTest` covers: the `generate ∘ parse ∘ generate` fixed point (H1/H6) including
structural checks that the round-tripped body is still `Loop(Passing[Action, Tuple, Action,
Branch])` with the branch cases in order; `checkHighlighting` on the function file alone (H2); and
`checkHighlighting` on the function file + assembly + `UiStateFlow` together (H3/H7, now with two
T-function ports instead of one).

`specimen/Comparison.kt` (new fixture) declares the domain sealed type as flat top-level objects —
`sealed interface Comparison` / `object TooLow : Comparison` / etc. — not nested inside
`Comparison`, so `Branch`'s generated `is specimen.TooLow ->` uses a simple FQN with no dotted
nested-class syntax to recognize on parse.

## Files changed

- `analysis/Expr.kt` — `Lambda.singleReturnExpression` field replaced by `statements: List<Stmt>`
  plus an extension property of the same name; new `Stmt`/`ValStmt`/`ExprStmt`; new
  `WhenExpr`/`WhenCase` (+ `WhenCase.singleReturnExpression` extension).
- `analysis/KotlinAnalysis.kt` — new `toStmt`/`localVariableOf` helpers; `toExpr` handles
  `USwitchExpression`.
- `actions/ActionLayout.kt` — `inferType` gained a fourth `scope: MutableMap<String, String>`
  parameter; new parsers (`Ref`, `Tuple`, `Branch`) registered in the dispatcher.
- `actions/Passing.kt` — `generate`/`parse` rewritten for named SSA vals (see above);
  `inferType` records each step's type into `scope`.
- `actions/RepeatWhileActive.kt`, `actions/RetryUntilResult.kt` — `inferType` threads `scope`
  through unchanged; `parse` uses the `Lambda.singleReturnExpression` extension instead of a field.
- `actions/Action.kt` — `inferType` threads `scope` through unchanged (doesn't use it).
- `actions/Ref.kt`, `actions/Tuple.kt`, `actions/Branch.kt` (new) — the three new node types.
- `actions/ActionDefinition.kt` — `signature()` seeds `scope` with `input -> Input`.
- `ui/AddNewLayoutSelector.kt` — context-menu entries for `Ref`/`Tuple`/`Branch`.
- `plugin/src/test/testData/specimen/Comparison.kt` (new) — the domain sealed type + cases.
- `GuessGameRoundTripTest.kt` (new) — the full specimen, replacing `GuessLoopGenerateTest`'s and
  `GuessLoopChecksumTest`'s hardcoded `.let{}`-shape assertions with the new named-`val` shape
  (`GuessLoopRoundTripTest`'s docstring updated to match; its assertions were already
  shape-agnostic).

**Not implemented this rung** (deferred; see "Limitations" below): `Construct`, `Copy`, `Project`,
`Select`, `Guard`, `Not` — design.md §2.5's other value-plumbing nodes — and `Parallel`/race
(§5.2, D13, the plan's optional stretch rung).

## How to run

```
./gradlew :plugin:test --tests "com.genovich.visualide.GuessLoop*" --tests "com.genovich.visualide.GuessGame*" --tests "com.genovich.visualide.ShowTest" --tests "com.genovich.visualide.ActionsPackageHostAgnosticTest"
```

## Verification notes

- Full `./gradlew :plugin:test --no-build-cache --rerun-tasks` green.
- `GuessGameRoundTripTest`'s fixed-point assertion is the strongest evidence: named SSA vals,
  `Ref`, `Tuple`, and `Branch` all round-trip losslessly through the same `Expr`/`Stmt` IR
  Step 4 introduced, with no UAST leaking into `actions` (`ActionsPackageHostAgnosticTest` still
  green) despite two brand-new IR shapes (`Lambda.statements`, `WhenExpr`).
- Two real bugs were caught only by actually running the round-trip against IntelliJ's resolver,
  not by reasoning about the shape on paper: `Tuple`'s guessed FQN for `to` (`kotlin.TuplesKt.to`,
  wrong — extrapolated from `let`'s `kotlin.StandardKt.let`) and `Ref`'s first "always means the
  composite's `input`" implementation (too narrow the moment a second reference target appeared).
  Both were found by writing the full specimen test *before* trusting either.

## Limitations / notes (feed back into §6 and future work)

- **Only `Tuple` and `Ref` are implemented from design.md §2.5's value-plumbing palette.**
  `Construct`, `Copy`, `Project`, `Select`, `Guard`, `Not` remain unimplemented — the concrete
  worked example in design.md §6.2 ("Target (rung 5)") only requires `Tuple` (`secret to guess`)
  and `Branch` to validate H6/H7, and `Ref` turned out to be a necessary third primitive underneath
  `Tuple` (see above), not a substitute for the others. Each of the remaining six is a bounded,
  independent follow-up: same `generate`/`parse`/`inferType`/`Render` shape as every existing node.
- **`Ref`'s scope is per-body, not the full nesting hierarchy.** `scope` is a single mutable map
  threaded through one `inferType` call tree; a `Ref` inside a nested composite's own body (a
  future rung, once composites can nest — design.md §1.7) wouldn't see the parent's scope. Not
  reachable today since nesting isn't implemented, but worth flagging before it is.
- **No real loop-exit mechanism.** `repeatWhileActive` (rung 0's primitive) has no break/return in
  the current node catalog — design.md §1.4's `updateLoop(initial){ step }` "Loop" operator
  (state-threading, naturally exitable) is a different, unimplemented primitive. `GuessGame`'s
  `Branch` demonstrates *structural* round-trip of n-way dispatch, not "stop asking once correct"
  play behavior — every case, including `Correct`, just calls `showResult` and the loop continues.
  A real exitable loop is future work, not scoped to this rung's acceptance criterion.
- **Branch exhaustiveness is a diagram-level invariant, not a compiler-enforced one** — see the
  "Branch" section above. Revisit if/when the type system grows real bounded domain types (design.md
  §1.3's type editor is aspirational, not implemented).
- **`Parallel`/race (§5.2, D13)** — the plan's optional stretch — not attempted this rung.
