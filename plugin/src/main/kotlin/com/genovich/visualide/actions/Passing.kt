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
import com.genovich.visualide.analysis.Lambda
import com.genovich.visualide.analysis.QualifiedCall
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

    override fun generate(input: String): String = body
        .takeIf { it.isNotEmpty() }
        ?.joinToString(separator = "\n", prefix = "$input\n", postfix = "\n") { layout ->
            """.let { ${layout.generate("it")} }"""
        }
        ?: TodoStub.generate()

    override fun inferType(
        input: String,
        fresh: () -> String,
        ports: MutableMap<String, Pair<String, String>>,
    ): String =
        body.fold(input) { previousType, layout -> layout.inferType(previousType, fresh, ports) }

    companion object : ActionLayout.ExpressionParser<Passing> {
        const val LET_FQN = "kotlin.StandardKt.let"

        override fun parse(expression: Expr): Result<Passing> = runCatching {
            checkNotNull(expression as? QualifiedCall) { "not a qualified call expression" }
                .also { check(it.resolvedQualifiedName == LET_FQN) { "name should be $LET_FQN" } }
                .let { generateSequence(it) { it.receiver as? QualifiedCall } }
                .map { chained ->
                    runCatching {
                        chained
                            .let { checkNotNull(it.selector as? Call) { "selector should be a call expression" } }
                            .arguments
                            .also { check(it.size == 1) { "selector should have only one argument" } }
                            .single()
                            .let { checkNotNull(it as? Lambda) { "the single argument should be a lambda" } }
                            .let { checkNotNull(it.singleReturnExpression) { "lambda body should be a single return expression" } }
                            .let { checkNotNull(ActionLayout.parse(it).getOrThrow()) { "failed to convert to ActionLayout" } }
                    }
                        .recoverCatching {
                            throw Exception("failed to parse ${chained.sourceText}", it)
                        }
                        .getOrThrow()
                }
                .toList()
                .reversed()
                .let { Passing(it) }
        }.recoverCatching {
            throw Exception("failed to parse ${Passing::class.qualifiedName}", it)
        }
    }
}