# Visual IDE for Function-Composition Architecture — Design Document

**Status:** Draft, in active iteration
**Target language:** Kotlin (Kotlin Multiplatform, per the reference `components` library)
**Reference codebase:** [`lynnfield/components`](https://github.com/lynnfield/components)

---

## 0. Purpose

A tool that lets developers author programs by drawing diagrams instead of (or alongside)
writing code. The generated code follows a fixed architecture in which **everything is a
function** (a functional object that carries its dependencies). A program is a composition of
such functions. The diagram and the code are two views of the same thing: the core must be
**convertible to a diagram and back** without loss.

This document is built up section by section. Section 1 (the core model) is settled; later
sections are placeholders to be filled in subsequent iterations.

---

## 1. Core model & algebra

### 1.1 The block

The single atom of the system is an **Action**:

```kotlin
fun interface ActionBase<in Input, out Output> {
    suspend operator fun invoke(input: Input): Output
}

abstract class Action<in Input, out Output> : ActionBase<Input, Output>
```

An Action is a `suspend (Input) -> Output` packaged as a functional object so it can carry
its dependencies in its constructor. In the visual language a block has **two categories of
input**, and separating them is the most important structural decision in the model:

- **Data port** — the runtime `Input` the block is called with, producing `Output`.
  Exactly one input, one output (see §1.3 for how "multiple inputs" is handled).
- **Dependency ports** — the block's constructor parameters: the other Actions it composes
  and any plain configuration values. These are bound when the object is **constructed**,
  not when it is **called**.

### 1.2 The two planes

Every diagram therefore has two distinct kinds of edge:

- **Data plane (call-time):** an edge means *"the output of X is the input of Y."*
  This expresses execution order and dataflow — the body of a composite block.
- **Dependency plane (construction-time):** an edge means *"block Y uses block X to fill a
  dependency slot."* This expresses wiring.

"Functions created and linked upfront or on demand during execution" maps entirely onto the
dependency plane: an **upfront** edge is a dependency resolved at construction; an
**on-demand** edge is a *factory* dependency the block instantiates lazily when it is reached
at runtime. The two-file generation scheme (§3, TBD) also falls out of this plane: the
*definition* file declares dependency ports as abstract `Action` types; the *factory* file
fills them with default concrete implementations.

### 1.3 Types are algebraic data types

The IDE ships a **type editor** whose entire job is algebraic data types:

- **Products (AND)** — `data class` / tuples. Because an Action has exactly one `Input`,
  "a block that needs several inputs" is modeled as a single product type. The diagram shows
  explicit **pack / unpack** nodes that build or destructure the product. This keeps every
  port strictly 1-in / 1-out and keeps codegen and round-tripping simple.
  *(Decision D1.)*
- **Sums (OR)** — `sealed` types. Used as branch targets (§1.4). Each case is a named
  subclass.

Products and sums are duals, so one small symmetric piece of machinery covers both
"multiple inputs" and "multiple branches."

### 1.4 Operator catalog

A composite block's body is a graph built **only** from this closed set. Each operator is
both a typed combinator and a canonical Kotlin construct — which is what later makes lossless
round-tripping possible.

| Operator | Type | Canonical Kotlin form | Diagram |
|---|---|---|---|
| **Sequence** | `(I→M)`, `(M→O)` ⟹ `I→O` | `val m = f(i); g(m)` | edge: f.out → g.in |
| **Parallel / race** | `n × (I→O)` ⟹ `I→O` | `parallel(i, f1, f2, …)` | fan-out node, first result wins, rest cancelled |
| **Branch** | `I → S` (S sealed) then route | `when(x){ is A→…; is B→… }` (exhaustive) | split node, one labeled out-port per case |
| **Loop** | `S → S` + initial `S` | `updateLoop(initial){ step }` | feedback edge: out → in |
| **Error boundary** | body + injected `catch` ⟹ `OneOf<O,E>` | `Try { catch -> … }` | container with a `catch` port |
| **Boundary I/O (T-function)** | `I → O` via external system | `Show` / `UiState` send-wait-resume | node with external handshake port |

### 1.5 Branching: named sealed types + `OneOf` for 2-way

*(Decision D2.)*

- **Domain branching** routes over a **user-named `sealed` type**. The branch node
  introspects the subclasses and draws **one out-port per case, labeled with the case name**.
  Codegen is a flat, exhaustive `when`. Benefits: no nesting (single allocation, flat match),
  self-documenting diagrams (ports carry domain names), and a strong IDE invariant — a branch
  node is *incomplete* until every case has a wired out-port, so unhandled cases can be
  flagged visually the way Kotlin's exhaustiveness check works.
- **`OneOf`** (the library's 2-way `Either`) is kept **only** as the built-in *structural*
  sum: success-vs-error inside `Try`, and quick local present/absent splits where naming
  would be noise.
- **No `OneOfN` family is generated.** By the time a value has 5 cases, the cases deserve
  names — so they become a named sealed type, which stays flat and readable at any arity.

### 1.6 The T-function, precisely

The T-function has the same `(Input) -> Output` shape as any block, but its implementation
hands `UiState(input, callback)` to an external system (a `StateFlow` the UI observes),
suspends via `suspendCancellableCoroutine`, and resumes when that system invokes
`callback(output)`. Send → wait → receive.

It is the **only** operator that crosses the program boundary. Everything inside the program
is pure composition; T-functions are where UI and other async systems plug in.

> Naming: "T" is the literal shape of the node's glyph — input on the left arm, output on the
> right arm, and the vertical stem as the channel carrying the `UiState(input, callback)`
> package to the external system (see §5.1).

### 1.7 Composite blocks & nesting

A composite block's body **is** a data-plane graph of sub-blocks. The graph's entry is the
composite's `Input`; its exit is the composite's `Output`. Sub-blocks may be:

- other composites (recursive nesting),
- **atoms** — leaf functions the developer hand-edits,
- operators from the catalog.

This is the core authoring loop: create a block, and inside its body create and connect more
blocks, which defines both how the functions interact and how they execute.

### 1.8 Worked example — login loop

```
updateLoop(initial = NotLoggedIn):
  ShowLogin     : Unit → Credentials              // T-function (UI)
  Authenticate  : Credentials → AuthResult         // atom; AuthResult is a sealed type
  branch on AuthResult:
    Session      → ShowHome  : Session → Unit       // T-function
    BadPassword  → ShowError : Message → Unit  ↺     // T-function, loop back
    Locked       → ShowLocked: LockInfo → Unit ↺     // T-function, loop back
```

- Three (or more) **T-functions** sit at the edges.
- **`Authenticate`** is an **atom**; its own dependency port — an HTTP-client Action — is
  filled on the **dependency plane**.
- **`AuthResult`** is a named **sealed type**; the branch node shows one labeled out-port per
  case and is only complete when all three are wired.
- The whole thing is wrapped in a **loop**.

---

## 2. Diagram ↔ code mapping (the round-trip)

The load-bearing constraint: the core must convert to a diagram and back. General Kotlin is
too expressive to round-trip directly, so the mapping rests on a small set of decisions that
make the conversion total and lossless **up to normalization**.

### 2.1 Round-trip contract *(Decision D6 — diagram-primary)*

The diagram model is the source of truth. `generate: diagram → code` is deterministic (one
canonical normal form: fixed ordering, naming, formatting — no stylistic freedom).
`parse: code → diagram` reconstructs the graph from normal-form code. Hand-edits to structured
code are normalized on save (gofmt-style); byte-for-byte preservation of arbitrary edits is a
non-goal. Conforming hand-written code can be imported.

### 2.2 Everything is a node

A **node** is a function with (a) a typed interface and (b) a declared visual representation.
The IDE consumes every node as a **black box** — it needs the node's face and interface,
never its internals. This unifies three things that earlier looked distinct:

- **Stdlib nodes** ship with the tool: `parallel`, sequence, `select`, branch, loop, `Try`,
  plus the pure value-plumbing set (see §2.5). This is a generalization of the §1.4 operator
  catalog — those operators are simply pre-registered nodes, not privileged syntax.
- **Custom nodes** are developer-written functions annotated with a visual representation; the
  IDE adds them to the palette and consumes them as black boxes. An "atom" (§1.7) is just a
  **leaf node**.

The node palette is therefore **open/extensible**, not a closed vocabulary.

### 2.3 Ports vs. slots

A node connects to its surroundings two ways:

- **Ports** — typed *wired* connections: the `Input`, the `Output`, and the dependency/config
  ports (constructor parameters). Edges connect ports.
- **Slots** — nested *sub-diagram* regions a node contains: a branch's per-case bodies, a
  loop's step, a `Try` body, and a composite's own body. Slots are exactly why
  `when` / `whileActive` / `Try` use inline lambdas in the canonical Kotlin — those lambdas
  are the slots. A decorator such as `RetryUntilResult(action, …)` takes its inner node as a
  **port** (dependency-plane wiring), not a slot.

### 2.4 Composite vs. leaf nodes, and the two annotations

- A **composite** node's body is itself a diagram — IDE-authored, openable, round-tripped
  structurally.
- A **leaf** node's body is opaque Kotlin — developer-authored, consumed as a black box.

Both present the identical black-box interface to a parent. Two annotations carry the mapping:

```kotlin
@Node(label = "Auth", category = "network")                 // face + interface — EVERY node
@Diagram(version = 1, checksum = "sha256:…", layout = "…")  // composite bodies ONLY
class Auth(val httpPostFormForJson: HttpPostFormForJson) : Action<FirebaseUser, TokenPair>() { … }
```

- **`@Node`** *(Decision D8)* declares how to render and wire the black box: label, category,
  shape, and the port/slot layout. Present on every node, stdlib or custom. Its descriptor is
  **fully inferred** from the Kotlin signature *(Decision D9)* — `Input`/`Output` from the
  `Action<I,O>` supertype, dependency ports from the constructor — with `@Node` carrying only
  cosmetics for now. To revisit if inference proves insufficient (e.g. slot-vs-port ambiguity).
- **`@Diagram`** is present only when the body is an IDE-authored structural graph:
  `checksum` (a hash of the normalized body, for drift detection — flags code edited out of
  band) and optional `layout` (node positions keyed by node id; auto-layout fallback if
  absent). A leaf/custom atom carries `@Node` and **no** `@Diagram`.

### 2.5 The pure value-plumbing nodes

"Fine-grained: everything is a node" *(Decision D7)* means a composite body contains **no
opaque text** — every leaf is either another node (stdlib/custom) or one of the built-in
value-plumbing nodes:

- **Ref** — name a value in scope (`input`, a `val`, a port result); these are the wires
- **Project** — field/property access, chainable (`input.tokenPair.refreshToken`)
- **Construct** — call a product constructor (`User(a, b)`, `AccessToken(s)`)
- **Copy** — `x.copy(field = y, …)`
- **Tuple** — `a to b` (Pair; special-case Construct)
- **Literal** — constant (`"…"`, `null`, an enum case)
- **Select** — value-level `if/else` and elvis `?:` (distinct from the §1.4 sealed Branch)
- **Guard** — `check(pred){msg}` / `require(...)`: pass-through that throws on false
- **Not** — boolean negation, composed with Project for predicates (`!user.isAnonymous`)

These are just the pre-registered value palette; developers may register more. Anything that
would otherwise be arbitrary inline Kotlin (a JSON parser, a stdlib higher-order call,
platform glue) is instead a **leaf node** — written once, annotated, consumed as a black box.

### 2.6 Worked example — `LinkWithAccount` as a graph

```kotlin
override suspend fun invoke(input: User): User {
    val credential   = collectGoogleCredential(input)                        // port call
    val firebaseUser = linkWithCredential(input.firebaseUser to credential)  // port call + Tuple/Project
    check(!firebaseUser.isAnonymous) { "…" }                                 // Guard + Not + Project
    val tokenPair    = auth(firebaseUser)                                     // port call
    return input.copy(firebaseUser = firebaseUser, tokenPair = tokenPair)    // Copy
}
```

Nodes: three **port-call** nodes (`collectGoogleCredential`, `linkWithCredential`, `auth`),
one **Tuple**(+Project), one **Guard**(+Not+Project), one **Copy** — wired by the `val`
dataflow. No opaque text; the body is a complete serialization of the data-plane graph.

### 2.7 Canonical form & identity

The structured layer is SSA-like: each intermediate is a single-assignment `val`, named args
always, arguments in fixed (declared) order, `when` clauses in declaration order. Node id =
`val` name (unique within a body; nested slots get a path prefix). The `checksum` is taken
over this normalized form. Layout and identity live in `@Diagram`, keeping the body clean.

### 2.8 Dependency-plane round-trip

The assembly's default-expression tree maps directly to the dependency-plane diagram: child
`*Assembly(...)` calls are wiring, `Show(flow)` marks boundary T-functions, decorators are
wrapping nodes, and shared singletons (§3.5) are earlier params referenced by name in later
defaults (a fan-out edge). The `UiStateFlow` projection is **regenerated, not parsed**
(§3.3) — removing the messiest part of the reference code from the parser's burden.

---

## 3. Two-file generation (definition + assembly)

Grounded in the reference module (`online.smsvirtual.core`): every `FunctionName`
functional object has a corresponding `FunctionNameAssembly`. The split *is* the two planes
from §1.2.

### 3.1 The function file = data plane + abstract ports

A function file declares the functional object and nothing else:

```kotlin
class EntryPoint(
    val processUnauthorisedState: Action<Any, User>,   // abstract dependency port
    val processAuthorisedState: Action<User, Unit>,    // abstract dependency port
) : EntryPointType() {
    override suspend fun invoke(input: Any): Nothing =  // the data-plane graph
        whileActive { processAuthorisedState(processUnauthorisedState(input)) }
}
typealias EntryPointType = Action<Any, Nothing>
```

Properties the generator must preserve:

- Every dependency is typed as a broad `Action<I,O>` (or a shared typealias). The function
  knows its dependency **contracts**, never the concrete sibling or how it is built.
- `invoke` is pure composition from the §1.4 operator catalog.
- Imports are limited to `com.genovich.components` + shared contract types.

This is the independence target, and it is fully achievable: a function is generated from a
single block's body graph plus the *signatures* of its dependency ports.

### 3.2 The assembly file = dependency plane (factory)

An assembly is a factory function that does four jobs:

1. **Wiring** — fills each abstract port by recursively calling child `*Assembly(...)`.
2. **Override seams** — see §3.4 (open).
3. **Decoration** — wraps the core in cross-cutting Actions
   (`RetryUntilResult(EntryPoint(...))`, `RetryWithConsentOnException`).
4. **State projection** — builds the `FooUiStateFlow` (see §3.3).

### 3.3 The state projection is derivable, not authored

Each assembly's `FooUiStateFlow` mirrors the function tree one-to-one: one child flow per
dependency that produces UI, `combine`d and wrapped in a sealed "which screen is live"
enumeration. This is mechanical boilerplate today and becomes **pure codegen**: the IDE emits
it from the diagram's shape; the developer never writes it. The sealed wrapper's cases are
exactly the §1.5 named-sum pattern applied to "which child is currently showing UI."

### 3.4 Construction-time vs. call-time wiring

Two resolution modes, both present in the reference code:

- **Construction-time (upfront):** the default; an assembly builds the whole subtree when
  called.
- **Call-time (on demand):** when a dependency needs a runtime value to be built,
  construction is deferred into `invoke` via `AssembleAndRun { input -> … }`. Example:
  `ProcessAuthorisedStateAssembly` can only build `HttpPostFormForJson` after it has the
  `user` (for the access token). This is the dependency plane resolved at call-time, and the
  diagram must be able to mark a subtree as "assembled on demand from input X."

### 3.5 Shared dependencies are scoped singletons

`httpClient` / `baseUrl` / `auth` are threaded as *shared instances* into multiple children.
These are not per-node dependencies; they are **scoped singletons** within a subtree. The
model needs to represent this explicitly (a dependency-plane node fanning out to several
consumers) rather than leaving it implicit in default-parameter expressions — otherwise
overrides and round-tripping become ambiguous.

### 3.6 Module layout *(Decision D4)*

Two Gradle modules per function:

- **`fn:Foo`** — the function module. Depends only on `components` + the shared **contracts**
  module (domain types + `Action` typealiases). Cannot see its own assembly or any sibling.
- **`asm:Foo`** — the assembly module. Depends on `fn:Foo`, the `asm:*` modules of its direct
  children, and any leaf/platform implementations it wires in. Its child dependencies are
  fixed (the default wiring names them).

The compiler now enforces the independence boundary: a function physically cannot depend on
its assembly or its siblings. Cost: ~2N modules plus the contracts module, and a build graph
that mirrors the composition DAG. A shared **contracts** module is required so function
modules can reference dependency signatures without depending on implementations.

### 3.7 Override seams *(Decision D5 — status quo, revisit later)*

**Decision:** keep the reference pattern — every internal node is exposed as a defaulted
parameter, overridable at the call site. The costs below are accepted for now and flagged for
revisit once the round-trip grammar (§2) and IDE model (§4) are settled, since the
"edit-the-diagram-and-regenerate" alternative only becomes available once those exist.

The current reference pattern exposes **every internal node** as a defaulted parameter
(`login: LoginActionType = LoginAssembly(...)`). This conflates two unrelated needs:

- **design-time** "wire this subtree differently by default" — in a diagram-first generated
  world this belongs in the **diagram** (re-point a dependency edge, regenerate), not in a
  Kotlin default;
- **runtime/test** "swap a real impl for a fake / vary by platform" — a genuine override
  seam.

Costs of exposing every node: parameter **ordering coupling** (defaults reference earlier
params: `httpClient → httpPostFormForJson → auth → processAuthorisedState`), a **wide public
surface** (every internal node is API), **double-override ambiguity** (override `auth`, or
override `httpClient` so the default `auth` picks it up?), and **stale shared instances**
when a node is overridden but a scoped singleton is not.

**Proposed default (to confirm):** expose default params only for genuine **boundaries**
(platform/`expect` impls, callbacks, the state-flow holder) plus any internal node the
developer **explicitly marks "exposed"** in the IDE. Internal wiring is otherwise fixed by
generation and changed by editing the diagram. Small signatures, no ordering brittleness,
typed overrides exactly where intended.

---

## 4. IDE interaction model

The interaction layer is derived from the model rather than invented: the architecture makes
the dependency plane mostly automatic, so the authoring surface stays simple.

### 4.1 The core authoring loop

1. **Create a block** — name it and drop it where it's needed. `Input`/`Output` types are
   **inferred from the surrounding wiring** (the upstream output it consumes, the downstream
   input it feeds), since every edge is type-checked (D10) and types propagate along edges. You
   specify a type explicitly only when a port is left unconnected or you're introducing a new
   type (via the type editor, §4.4). The IDE scaffolds `fn:Foo` + `asm:Foo` (D4): an empty `Foo`
   body and a `FooAssembly`.
2. **Open its body** — a slot: an empty canvas with an `Input` source and an `Output` sink.
3. **Place and wire nodes** — drag from the palette (stdlib operators, value-plumbing nodes,
   other blocks, custom leaf nodes) and connect `Output → Input` along the data plane. Wiring
   is type-checked; a mismatched edge renders red.
4. **Drill or edit** — descend into a composite's body, or edit a leaf's opaque Kotlin (§4.5).

### 4.2 Single body canvas + derived dependency plane *(Decision D10)*

The data-plane body is the canvas; the dependency plane is **derived from placement** and
edited through a per-node **inspector**. The key simplification: *calling a node in a body
automatically declares it as a dependency port* of the composite. Dropping a `Login` node into
a body and wiring it both (a) adds the data-plane call and (b) declares `login: LoginActionType`,
whose assembly default is `LoginAssembly(...)`. One gesture edits and connects at once.

You therefore almost never draw dependency edges by hand. Explicit dependency-plane interaction
is needed only for:

- **overrides** (D5) — swap a node's default binding in the inspector;
- **shared singletons** (§3.5) — mark a node shared so several consumers get one instance (a
  fan-out the IDE renders distinctly);
- **on-demand construction** (§3.4) — mark a subtree `AssembleAndRun` from a runtime input.

### 4.3 Nesting: in-place infinite zoom, with separate-view focus *(Decision D11)*

Composite bodies and the slots of branch/loop/`Try` nodes nest arbitrarily deep. The primary
model is **in-place expansion**: a user opens a nested body on demand and it unfolds *within*
the parent canvas, and they can keep zooming in — to any depth — staying in one continuous
spatial context (semantic zoom over nested canvases). When the user wants to concentrate on a
single function, they can **open it separately** in its own view, detached from the parent.
In-place zoom keeps the whole structure navigable in context; the separate view provides focus
on demand.

### 4.4 Supporting surfaces

- **Palette** — stdlib nodes + custom/leaf nodes (open/extensible, D8), searchable and
  categorized via `@Node`.
- **Type editor** — products and sums (§1.3). Defining a sum makes it a branch target; the
  branch node shows one labeled out-port per case and stays in an error state until every case
  is wired (exhaustiveness, D2).
- **Validation** — the output is real Kotlin, so compiler errors surface inline. `@Diagram`
  checksums (D8) flag any *structured* code edited out of band; leaf-code edits are free.

### 4.5 Form factor and the host-abstraction boundary *(Decision D12)*

**Target the IntelliJ plugin first**, behind a host-abstraction boundary that keeps the form
factor swappable later. The choice is reversible at bounded cost — but only because the form
factor touches a thin adapter layer, not the core. That discipline must hold from day one: if
host types leak into the engine, portability is lost.

**Host-agnostic engine** (reused unchanged across any form factor): the model (§1), codegen
(§2–§3), the diagram document + `@Node`/`@Diagram` serialization, checksum validation, layout,
and the palette/type-editor logic. Pure Kotlin, no host types.

**Host adapters** (reimplemented per form factor, behind narrow ports):

- `KotlinAnalysis` — parse (code → the engine's own normalized IR) and type-info, for
  round-trip and edge type inference. IntelliJ supplies this via PSI/compiler; the engine
  consumes its **own IR, never PSI directly**. Building this port on a standalone-capable Kotlin
  analysis layer (rather than raw PSI) is what makes a later web/standalone move tractable — the
  exact layer is to validate.
- `ProjectFileSystem` — read/write the `fn:` / `asm:` modules.
- `EditorBridge` — open a leaf's opaque Kotlin in the host editor (IntelliJ now; an embedded
  editor such as Monaco elsewhere).
- `CanvasHost` — where the diagram renders.

**Canvas frontend (not yet decided):** the canvas frontend is left open. One
portability-friendly option is an embeddable **web frontend** rendered in a JCEF webview inside
IntelliJ — the same canvas could then run in a browser (web) or another webview (VS Code) with
little change. Whatever is chosen sits behind the `CanvasHost` port and must support the
semantic-zoom rendering (§4.3).

**Migration cost, later:** IntelliJ → VS Code or web reuses the engine (and the canvas frontend,
if built portably); you swap `KotlinAnalysis` (LSP or server-side), `ProjectFileSystem`
(backend/agent), and `EditorBridge`. A bounded adapter rewrite, not a core rewrite.

---

## 5. Representational details & residual questions

### 5.1 "T-function" naming *(Decision D17 — confirmed)*

The name is the **literal shape of the letter T**, and the glyph is prescriptive: the left arm
of the horizontal bar is the **input**, the right arm is the **output**, and the vertical stem
is the **channel** that transfers the "package" — the input together with its output callback —
down to the external system. A T-function node is therefore drawn as a T: input port on the left
arm, output port on the right arm, boundary channel descending from the stem (the
`UiState(input, callback)` handoff, §1.6).

Mechanically it is a boundary node: same `(Input) -> Output` shape as any node, bound to a `Show`
that emits `UiState(input, callback)` and suspends until the callback returns. At the *function*
level it is indistinguishable from any other port; in the *assembled* view a port bound to
`Show(flow)` takes the T glyph and is the source of one leaf in the state projection (§3.3).

**Open question — customizable/multiple T-function recognition.** The current implementation
(rung 2 step 2, `docs/example-rung3.md`) hardcodes `com.genovich.components.Show` as the *only*
recognized T-function binding (`actions/Show.kt`'s `Show.SHOW_FQN`); a port is a T-function purely
by being named in `ActionDefinition.tFunctionPorts`, a "known list" attached at the definition
level rather than baked into a leaf node's type or a flag. Multiple T-function *ports* per
definition are already supported (`generateAssembly`/`generateUiStateFlow` iterate the whole set).
Two things remain open, deferred until a concrete rung needs them:

- **Customizable recognition** — per §2.2's "node palette is open/extensible, not a closed
  vocabulary" goal, should the set of recognized boundary-binding components be a registry
  (matched by FQN, the way node parsers match their own FQN) rather than one hardcoded name?
  Nothing today needs a second kind, so this hasn't been validated against a real case.
- **Multiple T-function *kinds*** — if a project ever needs more than one boundary-binding shape
  (not just more than one T-function *port*, which already works), how would their flows combine
  in the `UiStateFlow` projection? One sealed `Screen` per kind? A single `Screen` with cases
  spanning all kinds (today's design, extended)? Undecided — no second kind exists yet to design
  against.

### 5.2 `parallel` / race nodes *(Decision D13)*

`parallel(input, f1, f2, …)` races its lanes on the same `input`; the **first** to complete
yields the output and the losers are cancelled. All lanes share the `Output` type, so there is
**no result-selection to draw** — the output port is simply fed by the first lane to finish.
Render it as a **race node**: the input fans into N lanes (slots), converging into one output
through a "first-wins" junction; loser-cancellation is implicit.

`repeatUntilFirst` is the race-plus-loop variant: lanes are typed either **exit** (produce the
terminal `R` → node output) or **loop** (produce `T` → feedback edge). Render as a race node
whose lanes carry an exit/loop marker; `Try` reuses the same race primitive internally
(action lane vs. first-error lane).

### 5.3 `Try` and the catch rail *(Decision D14)*

`Try { catch -> … }` is a **container node** wrapping a body slot, with output
`OneOf<Output, Error>`. Inside the slot it exposes a **catch rail** along the boundary: a body
node routes `OneOf.Second(error)` onto the rail, which short-circuits the container to
`OneOf.Second(error)`; normal body completion yields `OneOf.First(output)`. The catch rail is
drawn as a dedicated edge target on the container's inner edge — visually the error escape hatch
for everything in the slot.

### 5.4 Scoped singletons on the dependency plane *(Decision D15)*

A scoped singleton (e.g. `httpClient`, `baseUrl`, `auth`) is one instance shared by several
consumers in a subtree. **Render** it as a single dependency-plane node marked *shared*, with
fan-out edges to each consumer (distinct from per-node dependencies). The **scope** is the
assembly where it is declared; all descendants that need it receive that instance. **Generate**
it as a parameter on that assembly (with its default construction) referenced by name in the
child default expressions — exactly the reference pattern, but now an explicit modeled concept
rather than an implicit default-expression coincidence. The inspector marks a node shared and
sets its scope.

### 5.5 Per-node expansion & semantic-zoom (LOD) canvas *(Decision D16)*

Each inner node is **independently expandable**: a user expands whatever they need and leaves
everything else collapsed. Expansion is a per-node, user-controlled state — expanding one node
reveals its body graph while its siblings stay collapsed — and it composes with continuous zoom.

This makes **level-of-detail rendering** a hard requirement on the canvas: a collapsed node
shows only its `@Node` face (label/shape/ports); an expanded node reveals its body graph; a node
expanded inside an expanded parent yields the infinite in-place nesting of §4.3. The renderer
needs viewport virtualization (render only visible/expanded nodes) and layout that stays stable
as nodes expand, collapse, and zoom. This is a constraint on the `CanvasHost` (whatever frontend
is chosen, §4.5), not a design fork.

**Implementation note:** the per-node expanded/collapsed state is **persisted** (keyed by node
id) so a user's expansion layout is restored on reopen — view state alongside the `@Diagram`
layout (§2.7); whether it is shared in the document or kept per-user is left to the host.

---

## 6. End-to-end example: plan & hypotheses

A worked example that carries one function from diagram to generated code and back, built on a
**synthetic specimen** (not the `sms-virtual-core` app) so it is unconstrained by an existing
codebase. The specimen is designed to *grow with the work*: rung 0 uses only the node types the
plugin already implements, and each later rung adds exactly one capability.

### 6.1 Reuse of the existing plugin

The `plugin` module already implements a working per-node round-trip for a subset of the model
(see the mapping in the project notes): `ActionLayout` = a self-rendering / self-generating /
self-parsing node; `Action` = port-call leaf; `Passing` = Sequence (a linear `.let{}` pipe);
`RepeatWhileActive` = Loop; `RetryUntilResult` = decorator; `ActionDefinition` = the function
file, already auto-deriving dependency ports from the called `Action`s (§4.2). Parsing is UAST
(K2), generation is string + `CodeStyleManager` reformat. **Gaps:** assembly generation is
stubbed; no real types, annotations/checksum, value-plumbing nodes, Branch/Parallel/`Try`, or
T-function; UAST leaks into the node parsers (the `KotlinAnalysis` boundary of D12 is not yet
interposed).

### 6.2 The specimen: guess-the-number

Domain types: `Guess`, `Secret`, and `sealed interface Comparison { TooLow; TooHigh; Correct }`.

**Rung 0 (round-trips today):** a loop over a two-step pipeline of leaf calls —
`RepeatWhileActive(Passing[readGuess, checkGuess])`:

```kotlin
class GuessLoop(
    val readGuess: Action<Unit, Unit>,   // leaf port (types loose on purpose)
    val checkGuess: Action<Unit, Unit>,
) : Action<Unit, Nothing>() {
    override suspend fun invoke(input: Unit): Nothing =
        repeatWhileActive { input.let { readGuess(it) }.let { checkGuess(it) } }
}
```

**Target (rung 5):** a real round, looped until `Correct`:

```kotlin
askGuess  : Action<Secret, Guess>                 // T-function (Show)
compare   : Action<Pair<Secret, Guess>, Comparison>  // leaf; input via a Tuple node
showResult: Action<Comparison, Unit>              // T-function (Show)
// PlayRound: ask → compare(secret to guess) → showResult(result) → result
// Game: loop PlayRound, Branch on Comparison (TooLow/TooHigh → loop, Correct → exit)
```

### 6.3 The ladder

0. **Harness + `GuessLoop`** — round-trip on the current engine (Loop + Passing + leaves).
1. **Real types** — replace the placeholder `<Input,Output>` with inferred per-port types
   (`Unit`/`Guess`/`Comparison`/`Secret`); compile the generated function.
2. **Second file** — generate `…Assembly` (default wiring + decoration) and the derived
   `UiStateFlow` (the two `Show`s become projection leaves); compile.
3. **Integrity** — emit `@Node`/`@Diagram` + checksum; verify stability + drift detection.
4. **Decouple** — interpose the engine's own IR between UAST and the nodes (validate D12).
5. **Climb** — add the **Tuple** node (`secret to guess`), the **Branch** node (sealed
   `Comparison`), and the **T-function** (`Show`-bound `askGuess`/`showResult`); optional
   **Parallel** rung (a timeout racing the guess).

### 6.4 Hypotheses (each rung falsifies one)

- **H1 — Round-trip idempotence:** parse→generate→parse reaches a fixed point; generate is
  byte-stable after normalization. *(Rung 0)*
- **H2 — Port derivation + typing:** auto-collected ports yield a *compilable* function.
  *Prediction: generic `<Input,Output>` fails for real multi-typed ports → type inference (D10)
  is required.* *(Rung 1)*
- **H3 — Two-file derivability:** assembly + `UiStateFlow` projection are mechanically generable
  from the same diagram (§3.2–§3.3). *(Rung 2)*
- **H4 — Canonical form/checksum stability:** a normal form exists whose checksum is stable
  across regenerate cycles. *(Rung 3)*
- **H5 — Host decoupling:** an engine IR can sit between UAST and the model without breaking
  round-trip. *(Rung 4)*
- **H6 — No-opaque-text value plumbing:** a real body with Tuple/Construct/Branch round-trips
  with named `val`s. *Prediction: `Passing`-as-pipe is insufficient — named SSA `val`s (§2.7) are
  needed.* *(Rung 5)*
- **H7 — T-function + projection:** a `Show`-bound port renders as a T and derives a
  `UiStateFlow` leaf. *(Rung 5)*

### 6.5 Harness

A platform test (`TestFrameworkType.Platform`, already wired) that adds the specimen `.kt` to a
fixture, runs `ActionDefinition.parse` → `generate` → re-parse, and asserts a fixed point. The
fixture must make `com.genovich.components` **resolvable** (UAST parsing resolves FqNames), so the
specimen bundles **minimal `components` stubs** rather than depending on the published library.

---

## Decisions log

- **D1 — Multiple inputs:** A block keeps a single `Input`. Several inputs are modeled as one
  product type (`data class`/tuple) with explicit pack/unpack nodes. Faithful to
  `Action<I,O>`; simplest codegen and round-trip.
- **D2 — Branch arity:** Domain branching routes over named `sealed` types (one labeled
  out-port per case, exhaustiveness enforced). `OneOf` is retained only as the built-in 2-way
  structural sum. No `OneOfN` family is generated.
- **D3 — Two-file split:** Each function generates a *function file* (data-plane composition
  body + abstract `Action` dependency ports) and an *assembly file* (dependency-plane factory:
  wiring + decoration + derived state projection).
- **D4 — Module layout:** Two Gradle modules per function (`fn:Foo`, `asm:Foo`) plus a shared
  **contracts** module. The compiler enforces function independence.
- **D5 — Override seams:** Status quo — every internal node is an exposed defaulted parameter,
  overridable at the call site. Accepted costs (ordering coupling, wide surface, double-override
  ambiguity); to revisit after §2 and §4, when diagram-based re-wiring becomes an alternative.
- **D6 — Source of truth:** Diagram-primary. Deterministic `generate`, structural `parse`,
  round-trip lossless up to normalization; conforming hand-written code is importable.
- **D7 — Granularity:** Fine-grained — everything is a node. Composite bodies contain no opaque
  text; leaves are stdlib nodes, the pure value-plumbing set (§2.5), or custom leaf nodes.
- **D8 — Node model & annotations:** Open/extensible palette. Every node is a function with a
  typed interface + visual representation, consumed as a black box. `@Node` (face + interface)
  on every node; `@Diagram` (checksum + optional layout) on composite bodies only.
- **D9 — Descriptor authoring:** Fully inferred from the Kotlin signature for now (`@Node`
  carries cosmetics only); revisit if inference proves insufficient.
- **D10 — Canvas model:** Single data-plane body canvas; the dependency plane is derived from
  placement and edited via a per-node inspector (overrides, shared singletons, on-demand only).
- **D11 — Nesting navigation:** In-place expansion with infinite (semantic) zoom into nested
  bodies as the primary model; open a function in a separate view for focused work. Applies to
  composite bodies and branch/loop/`Try` slots.
- **D12 — Form factor:** IntelliJ plugin first, behind a host-abstraction boundary (host-agnostic
  engine + narrow `KotlinAnalysis` / `ProjectFileSystem` / `EditorBridge` / `CanvasHost` ports;
  canvas frontend TBD — a web frontend is one portability-friendly option). Reversible later at
  bounded adapter-rewrite cost, provided no host types leak into the engine.
- **D13 — `parallel` rendering:** Race node — input fans into N lanes converging to one output
  via a first-wins junction; no result-selection (lanes share `Output`). `repeatUntilFirst` is
  the same node with exit/loop-typed lanes; `Try` reuses the race primitive internally.
- **D14 — `Try` rendering:** Container node over a body slot with a catch rail on the inner
  boundary; routing `OneOf.Second(error)` short-circuits to the error output; output is
  `OneOf<Output, Error>`.
- **D15 — Scoped singletons:** Modeled explicitly — a *shared* dependency-plane node fanning out
  to consumers; scope = the declaring assembly; generated as an assembly parameter referenced by
  name in child defaults. Set via the inspector.
- **D16 — Canvas rendering:** Per-node, user-controlled expand/collapse (expand what you need,
  keep the rest collapsed) plus semantic-zoom / level-of-detail (face → ports → body → nested),
  with viewport virtualization and expand/zoom-stable layout behind the `CanvasHost` port
  (frontend TBD, §4.5). Per-node expansion state is persisted (keyed by node id).
- **D17 — "T-function" naming:** Confirmed — the name is the glyph's shape: input on the left
  arm, output on the right arm, vertical stem = the channel carrying the
  `UiState(input, callback)` package to the external system. Rendering is prescriptive.

---

## Remaining open items & next steps

Sections §1–§6 are drafted. Outstanding items:

- **Canvas frontend** (§4.5): undecided; a portable web frontend is one option, not yet pinned.
- **D5 revisit** — override seams, once diagram-based re-wiring exists (§3.7).
- **Execute the §6 ladder** — build rung 0 (harness + `GuessLoop` round-trip) and climb, testing
  H1–H7. Findings feed back into the decisions above.
```

