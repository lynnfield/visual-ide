package com.genovich.visualide.actions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.genovich.visualide.analysis.Call
import com.genovich.visualide.analysis.Expr
import com.genovich.visualide.analysis.ExprStmt
import com.genovich.visualide.analysis.Lambda
import com.genovich.visualide.analysis.Reference
import com.genovich.visualide.analysis.Stmt
import com.genovich.visualide.analysis.ValStmt
import com.genovich.visualide.ui.AddNewLayoutButton
import com.genovich.visualide.ui.RemoveButton

data class Passing(
    val body: SnapshotStateList<ActionLayout> = mutableStateListOf()
) : ActionLayout {
    constructor(body: List<ActionLayout>) : this(mutableStateListOf(*body.toTypedArray()))

    override fun iterator(): Iterator<ActionLayout> = iterator {
        yield(this@Passing)
        body.forEach { yieldAll(it) }
    }

    @Composable
    override fun Render(onRemove: () -> Unit, modifier: Modifier) {
        Box(modifier.width(IntrinsicSize.Min)) {
            Row {
                body.forEachIndexed { index, actionLayout ->
                    AddNewLayoutButton { body.add(index, it) }
                    actionLayout.Render(
                        onRemove = { body.remove(actionLayout) },
                        modifier = Modifier.width(IntrinsicSize.Max),
                    )
                }
                AddNewLayoutButton { body.add(it) }
            }
            RemoveButton("Passing", onRemove, Modifier.align(Alignment.TopEnd))
        }
    }

    /**
     * Named SSA `val`s (design.md §2.7), not a `.let{}` chain — each step is bound to its own
     * `stepN` before the next runs, so intermediates can be reused (the H6 target this rung
     * validates). Wrapped in `run { }` so the whole pipeline stays the single expression
     * [ActionLayout.generate]'s contract expects. `stepN` is positional, not a user-authored node
     * id — per-node naming is a future UI feature (design.md §2.7 wants "node id = val name"),
     * out of scope here.
     */
    override fun generate(input: String): String = body
        .takeIf { it.isNotEmpty() }
        ?.let { steps ->
            val stepNames = steps.indices.map { "step${it + 1}" }
            val valDeclarations = steps.mapIndexed { index, layout ->
                val previousValue = if (index == 0) input else stepNames[index - 1]
                "    val ${stepNames[index]} = ${layout.generate(previousValue)}"
            }
            (valDeclarations + "    ${stepNames.last()}")
                .joinToString(separator = "\n", prefix = "run {\n", postfix = "\n}")
        }
        ?: TodoStub.generate()

    /**
     * Also records each step's type into [scope] under its `stepN` [generate] name — otherwise a
     * later [Ref] naming an earlier step (not just the immediately-preceding one, already carried
     * by the fold's own accumulator) could never recover a real type. See [ActionLayout.inferType].
     */
    override fun inferType(
        input: String,
        fresh: () -> String,
        ports: MutableMap<String, Pair<String, String>>,
        scope: MutableMap<String, String>,
    ): String = body.foldIndexed(input) { index, previousType, layout ->
        layout.inferType(previousType, fresh, ports, scope)
            .also { stepType -> scope["step${index + 1}"] = stepType }
    }

    companion object : ActionLayout.ExpressionParser<Passing> {
        const val RUN_FQN = "kotlin.StandardKt.run"

        override fun parse(expression: Expr): Result<Passing> = runCatching {
            checkNotNull(expression as? Call) { "not a call expression" }
                .also { check(it.methodName == "run") { "should be named `run`" } }
                .also { check(it.resolvedQualifiedName == RUN_FQN) { "name should be $RUN_FQN" } }
                .arguments
                .also { check(it.size == 1) { "run should have exactly one argument" } }
                .single()
                .let { checkNotNull(it as? Lambda) { "the single argument should be a lambda" } }
                .statements
                .let { parseStatements(it) }
                .let { Passing(it) }
        }.recoverCatching {
            throw Exception("failed to parse ${Passing::class.qualifiedName}", it)
        }

        /**
         * Recovers the step list from a `run { }` body's statements: every statement but the last
         * must be a named `val` binding, and the trailing statement must be a bare reference to the
         * last binding's name (design.md §2.7's normal form — see [generate]).
         */
        private fun parseStatements(statements: List<Stmt>): List<ActionLayout> {
            check(statements.isNotEmpty()) { "run block should not be empty" }
            val valStatements = statements.dropLast(1).map { statement ->
                checkNotNull(statement as? ValStmt) { "expected a val binding, got `${statement.sourceText}`" }
            }
            val trailing =
                checkNotNull(statements.last() as? ExprStmt) { "expected a trailing expression" }
            val lastStepName = checkNotNull(valStatements.lastOrNull()?.name) {
                "run block should declare at least one step"
            }
            check((trailing.expr as? Reference)?.resolvedName == lastStepName) {
                "trailing expression should reference the last step (`$lastStepName`)"
            }
            return valStatements.map { valStatement ->
                checkNotNull(ActionLayout.parse(valStatement.initializer).getOrThrow()) {
                    "failed to convert `${valStatement.sourceText}` to an ActionLayout"
                }
            }
        }
    }
}