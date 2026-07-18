package com.genovich.visualide.actions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.genovich.visualide.analysis.Call
import com.genovich.visualide.analysis.Expr
import com.genovich.visualide.analysis.Lambda
import com.genovich.visualide.analysis.QualifiedCall
import com.genovich.visualide.analysis.singleReturnExpression
import com.genovich.visualide.types.TYPE_NOTHING
import com.genovich.visualide.ui.AddNewLayoutButton
import com.genovich.visualide.ui.TextBlock
import com.genovich.visualide.ui.step

data class RetryUntilResult(
    val body: MutableState<ActionLayout?> = mutableStateOf(null)
) : ActionLayout {
    constructor(body: ActionLayout?) : this(mutableStateOf(body))

    override fun iterator(): Iterator<ActionLayout> = iterator {
        yield(this@RetryUntilResult)
        body.value?.also { yieldAll(it) }
    }

    @Composable
    override fun Render(onRemove: () -> Unit, modifier: Modifier) {
        Column(modifier = modifier.width(IntrinsicSize.Max)) {
            TextBlock(
                text = "retry until result",
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
        $RETRY_UNTIL_RESULT_FQN {
            ${(body.value?.generate(input) ?: TodoStub.generate())}
        }
    """.trimIndent()

    override fun inferType(
        input: String,
        fresh: () -> String,
        ports: MutableMap<String, Pair<String, String>>,
        scope: MutableMap<String, String>,
    ): String = body.value?.inferType(input, fresh, ports, scope) ?: TYPE_NOTHING

    companion object : ActionLayout.ExpressionParser<RetryUntilResult> {
        const val RETRY_UNTIL_RESULT_FQN = "com.genovich.components.retryUntilResult"

        override fun parse(expression: Expr): Result<RetryUntilResult> = runCatching {
            checkNotNull(expression as? QualifiedCall) { "not a qualified call expression" }
                .also { check(it.resolvedQualifiedName == RETRY_UNTIL_RESULT_FQN) { "name should be $RETRY_UNTIL_RESULT_FQN" } }
                .let { checkNotNull(it.selector as? Call) { "selector should be a call expression" } }
                .arguments
                .also { check(it.size == 1) { "selector should have only one argument" } }
                .single()
                .let { checkNotNull(it as? Lambda) { "the single argument should be a lambda" } }
                .let { checkNotNull(it.singleReturnExpression) { "lambda body should be a single return expression" } }
                .let { ActionLayout.parse(it) }
                .getOrThrow()
                .let { RetryUntilResult(it) }
        }.recoverCatching {
            throw Exception("failed to parse ${RetryUntilResult::class.qualifiedName}", it)
        }
    }
}