package com.genovich.visualide.actions

import com.genovich.visualide.analysis.Call
import com.genovich.visualide.analysis.Expr
import com.genovich.visualide.analysis.QualifiedCall

/**
 * Recognizes `com.genovich.components.Show(flow)` — the assembly-plane expression
 * ([ActionDefinition.generateAssembly]) that marks a port as a T-function (design.md §1.6, §5.1).
 * `Show` is currently the *only* recognized T-function binding (`docs/example-rung3.md` records
 * "customizable T-function recognition" and "multiple T-functions per project" as open hypotheses
 * still to validate, not yet supported) — the sole implementation of [ActionDefinition.PortDefault].
 *
 * Not an [ActionLayout.ExpressionParser]: `Show` binds a *dependency-plane* default-parameter
 * value, not a function-body node, so it has no place in [ActionLayout.parse]'s dispatcher. This
 * is scaffolding for a future `parseAssembly` (rung 2 step 1's still-open stretch goal, see
 * `docs/example-rung2.md`) — nothing calls [parse] yet.
 */
object Show : ActionDefinition.PortDefault {
    const val SHOW_FQN = "com.genovich.components.Show"

    /** On success, the single `flow` argument expression. */
    fun parse(expression: Expr): Result<Expr> = runCatching {
        checkNotNull(expression as? QualifiedCall) { "not a qualified call expression" }
            .also { check(it.resolvedQualifiedName == SHOW_FQN) { "name should be $SHOW_FQN" } }
            .let { checkNotNull(it.selector as? Call) { "selector should be a call expression" } }
            .arguments
            .also { check(it.size == 1) { "Show should have exactly one argument" } }
            .single()
    }.recoverCatching {
        throw Exception("failed to parse a Show(...) expression", it)
    }
}
