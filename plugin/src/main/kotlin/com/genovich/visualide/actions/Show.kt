package com.genovich.visualide.actions

import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.tryResolveNamed

/**
 * Recognizes `com.genovich.components.Show(flow)` — the assembly-plane expression
 * ([ActionDefinition.generateAssembly]) that marks a port as a T-function (design.md §1.6, §5.1).
 * `Show` is currently the *only* recognized T-function binding (`docs/example-rung3.md` records
 * "customizable T-function recognition" and "multiple T-functions per project" as open hypotheses
 * still to validate, not yet supported) — the sole implementation of [ActionDefinition.PortDefault].
 *
 * Not an [ActionLayout.UExpressionParser]: `Show` binds a *dependency-plane* default-parameter
 * value, not a function-body node, so it has no place in [ActionLayout.parse]'s dispatcher. This
 * is scaffolding for a future `parseAssembly` (rung 2 step 1's still-open stretch goal, see
 * `docs/example-rung2.md`) — nothing calls [parse] yet.
 */
object Show : ActionDefinition.PortDefault {
    const val SHOW_FQN = "com.genovich.components.Show"

    /** On success, the single `flow` argument expression. */
    fun parse(expression: UExpression): Result<UExpression> = runCatching {
        checkNotNull(expression as? UQualifiedReferenceExpression) { "not a qualified reference expression" }
            .also {
                checkNotNull(it.tryResolveNamed()) { "failed to resolve named element" }
                    .let { checkNotNull(it.kotlinFqName) { "expression should have a kotlin fully qualified name" } }
                    .also { check(FqName(SHOW_FQN) == it) { "name should be $SHOW_FQN" } }
            }
            .let { checkNotNull(it.selector as? UCallExpression) { "selector should be a call expression" } }
            .valueArguments
            .also { check(it.size == 1) { "Show should have exactly one argument" } }
            .single()
    }.recoverCatching {
        throw Exception("failed to parse a Show(...) expression", it)
    }
}
