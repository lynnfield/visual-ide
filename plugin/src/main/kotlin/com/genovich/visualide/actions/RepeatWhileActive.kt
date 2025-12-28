package com.genovich.visualide.actions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.genovich.visualide.ui.AddNewLayoutButton
import com.genovich.visualide.ui.TextBlock
import com.genovich.visualide.ui.step
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.tryResolveNamed

data class RepeatWhileActive(
    val body: MutableState<ActionLayout?> = mutableStateOf(null)
) : ActionLayout {
    constructor(body: ActionLayout?) : this(mutableStateOf(body))

    override fun iterator(): Iterator<ActionLayout> = iterator {
        yield(this@RepeatWhileActive)
        body.value?.also { yieldAll(it) }
    }

    @Composable
    override fun Render(onRemove: () -> Unit, modifier: Modifier) {
        Column(modifier = modifier.width(IntrinsicSize.Max)) {
            TextBlock(
                text = "repeat while active",
                onRemove = onRemove,
            )
            val paddings = Modifier.padding(horizontal = step)
            body.value?.Render(
                onRemove = { body.value = null },
                modifier = paddings,
            ) ?: AddNewLayoutButton(modifier = paddings, onAdd = body::value::set)
        }
    }

    override fun generate(input: String): String = """
        com.genovich.components.repeatWhileActive {
            ${(body.value?.generate(input) ?: TodoStub.generate())}
        }
    """.trimIndent()

    companion object : ActionLayout.UExpressionParser<RepeatWhileActive> {
        override fun parse(expression: UExpression): Result<RepeatWhileActive> = runCatching {
            checkNotNull(expression as? UQualifiedReferenceExpression) { "not a qualified reference expression" }
                .also {
                    checkNotNull(it.tryResolveNamed()) { "failed to resolve named element" }
                        .let { checkNotNull(it.kotlinFqName) { "expression should have a kotlin fully qualified name" } }
                        .also { check(FqName("com.genovich.components.repeatWhileActive") == it) { "name should be com.genovich.components.retryUntilResult" } }
                }
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
                .let { ActionLayout.parse(it) }
                .getOrThrow()
                .let { RepeatWhileActive(it) }
        }.recoverCatching {
            throw Exception("failed to parse ${RepeatWhileActive::class.qualifiedName}", it)
        }
    }
}