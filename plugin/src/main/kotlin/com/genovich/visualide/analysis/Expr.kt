package com.genovich.visualide.analysis

/**
 * The engine's own normalized expression IR (design.md §4.5, D12; `docs/implementation-plan.md`
 * Step 4, H5). Node parsers in `com.genovich.visualide.actions` match against these four shapes —
 * the ones the current grammar (leaf calls, `.let{}`/`repeatWhileActive{}`/`retryUntilResult{}`
 * chains) actually needs — instead of touching UAST/PSI directly. [KotlinAnalysis] is the only
 * adapter that builds an [Expr] tree, so `actions` stays host-agnostic: reusable unchanged if a
 * non-IntelliJ form factor is ever added.
 *
 * Resolution ([resolvedName]/[resolvedQualifiedName]) is computed eagerly by the adapter for every
 * node (mirroring UAST's `tryResolveNamed()`, which is defined for any expression) rather than
 * being scoped to one shape — different node parsers need it off different shapes (a bare
 * [Reference] for a port call's receiver, a [QualifiedCall] for a `.let{}`/FQN-call chain link).
 */
sealed interface Expr {
    /** The original source text, for error messages only (mirrors `UExpression.asSourceString()`). */
    val sourceText: String
    val resolvedName: String?
    val resolvedQualifiedName: String?
}

/** A name reference that doesn't decompose further (a local `val`, a bare property access, …). */
data class Reference(
    override val sourceText: String,
    override val resolvedName: String?,
    override val resolvedQualifiedName: String?,
) : Expr

/** `receiver.method(arguments)` or a receiver-less call, e.g. `readGuess(it)`, `TODO()`. */
data class Call(
    override val sourceText: String,
    override val resolvedName: String?,
    override val resolvedQualifiedName: String?,
    val methodName: String?,
    val receiver: Expr?,
    val arguments: List<Expr>,
) : Expr

/** `receiver.selector`, e.g. `input.let { … }` or a fully-qualified `com.genovich.components.repeatWhileActive { … }`. */
data class QualifiedCall(
    override val sourceText: String,
    override val resolvedName: String?,
    override val resolvedQualifiedName: String?,
    val receiver: Expr,
    val selector: Expr,
) : Expr

/**
 * A lambda argument's body, as an ordered list of statements (design.md §2.7's SSA-like
 * normal form: named `val` bindings in declaration order, ending in a trailing expression —
 * Kotlin/UAST synthesizes an implicit `return` for a lambda's last expression). Empty when the
 * lambda's body couldn't be read as a block at all.
 */
data class Lambda(
    override val sourceText: String,
    val statements: List<Stmt>,
    override val resolvedName: String? = null,
    override val resolvedQualifiedName: String? = null,
) : Expr

/** A single statement inside a [Lambda]'s (or a class body's) block. */
sealed interface Stmt {
    /** The original source text, for error messages only. */
    val sourceText: String
}

/** `val [name] = [initializer]` — a single-assignment SSA binding (design.md §2.7). */
data class ValStmt(
    override val sourceText: String,
    val name: String,
    val initializer: Expr,
) : Stmt

/**
 * A bare expression statement: a block's trailing expression (a lambda's implicit return, or a
 * function body's explicit `return`), or a side-effecting call with no result binding.
 */
data class ExprStmt(
    override val sourceText: String,
    val expr: Expr,
) : Stmt

/** The trailing return expression of a [Lambda] whose body is exactly one statement — the shape
 * every node parser that just needs "the lambda's one expression" (no named vals) wants. */
val Lambda.singleReturnExpression: Expr?
    get() = (statements.singleOrNull() as? ExprStmt)?.expr

/**
 * `when (val [subjectName] = [subjectInitializer]) { is Case1 -> …; is Case2 -> …; … }` —
 * design.md §1.5/D2's exhaustive domain Branch: one labeled arm per named-sealed-type case, in
 * declaration order. [subjectName]/[subjectInitializer] are null when the subject isn't bound to
 * a named `val` (Branch's own canonical form always binds one — see `actions/Branch.kt`).
 */
data class WhenExpr(
    override val sourceText: String,
    val subjectName: String?,
    val subjectInitializer: Expr?,
    val cases: List<WhenCase>,
    override val resolvedName: String? = null,
    override val resolvedQualifiedName: String? = null,
) : Expr

/**
 * One `is [caseTypeQualifiedName] -> …` arm of a [WhenExpr]. [statements] mirrors [Lambda]'s
 * block shape (named `val`s + a trailing expression) so a case body can grow the same SSA
 * structure a `Passing` pipeline can. [caseTypeQualifiedName] is null when the arm's condition
 * isn't a resolvable `is Type` check.
 */
data class WhenCase(
    val sourceText: String,
    val caseTypeQualifiedName: String?,
    val statements: List<Stmt>,
)

/** The trailing return expression of a [WhenCase] whose body is exactly one statement. */
val WhenCase.singleReturnExpression: Expr?
    get() = (statements.singleOrNull() as? ExprStmt)?.expr
