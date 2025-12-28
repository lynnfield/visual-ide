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
import com.genovich.visualide.ui.AddNewLayoutButton
import com.genovich.visualide.ui.RemoveButton
import com.intellij.execution.processTools.mapFlat
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.tryResolveNamed

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

    companion object : ActionLayout.UExpressionParser<Passing> {
        override fun parse(expression: UExpression): Result<Passing> = runCatching {
            checkNotNull(expression as? UQualifiedReferenceExpression) { "not a qualified reference expression" }
                .also {
                    checkNotNull(it.tryResolveNamed()) { "failed to resolve named element" }
                        .let { checkNotNull(it.kotlinFqName) { "expression should have a kotlin fully qualified name" } }
                        .also { check(FqName("kotlin.StandardKt.let") == it) { "name should be kotlin.StandardKt.let" } }
                }
                .let { generateSequence(it) { it.receiver as? UQualifiedReferenceExpression } }
                .map { expression ->
                    runCatching {
                        expression
                            .let { checkNotNull(it.selector as? UCallExpression) { "selector should be a call expression" } }
                            .valueArguments
                            .also { check(it.size == 1) { "selector should have only one argument" } }
                            .single()
                            .let { checkNotNull(it as? ULambdaExpression) { "the single argument should be a lambda" } }
                            .let { checkNotNull(it.body as? UBlockExpression) { "lambda body should be a block expression" } }
                            .expressions
                            .also { check(it.size == 1) { "lambda body should contain single expression" } }
                            .single()
                            .let { checkNotNull(it as? UReturnExpression) { "lambda body should be a single return expression" } }
                            .let { checkNotNull(it.returnExpression) { "return expression in lambda should exists" } }
                    }
                        .mapFlat { ActionLayout.parse(it) }
                        .mapCatching { checkNotNull(it) { "failed to convert to ActionLayout" } }
                        .recoverCatching {
                            throw Exception(
                                "failed to parse ${expression.asSourceString()}",
                                it
                            )
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