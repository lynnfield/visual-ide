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
 * A lambda argument, collapsed to the one shape every node parser needs: a block containing a
 * single (possibly implicit — Kotlin/UAST synthesizes a `return` for a lambda's last expression)
 * `return` statement. [singleReturnExpression] is null when the lambda doesn't have that shape.
 */
data class Lambda(
    override val sourceText: String,
    val singleReturnExpression: Expr?,
    override val resolvedName: String? = null,
    override val resolvedQualifiedName: String? = null,
) : Expr
