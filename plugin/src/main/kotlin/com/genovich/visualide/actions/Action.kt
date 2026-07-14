package com.genovich.visualide.actions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.genovich.visualide.analysis.Call
import com.genovich.visualide.analysis.Expr
import com.genovich.visualide.ui.TextBlock

data class Action(
    val name: MutableState<String> = mutableStateOf("New Action")
) : ActionLayout {

    constructor(name: String) : this(mutableStateOf(name))

    override fun iterator(): Iterator<ActionLayout> = iterator {
        yield(this@Action)
    }

    @Composable
    override fun Render(onRemove: () -> Unit, modifier: Modifier) {
        TextBlock(name, onRemove = onRemove, modifier = modifier)
    }

    override fun generate(input: String): String = "`${name.value}`($input)"

    override fun inferType(
        input: String,
        fresh: () -> String,
        ports: MutableMap<String, Pair<String, String>>,
    ): String = ports.getOrPut(name.value) { input to fresh() }.second

    companion object : ActionLayout.ExpressionParser<Action> {
        override fun parse(expression: Expr): Result<Action> = runCatching {
            checkNotNull(expression as? Call) { "is not a call expression" }
                .also { check(it.methodName == "invoke") { "should have `invoke` method" } }
                .let { checkNotNull(it.receiver) { "`invoke` expression should have a receiver" } }
                .let { checkNotNull(it.resolvedName) { "failed to resolve named element" } }
                .let { Action(it) }
        }.recoverCatching {
            throw Exception("failed to parse an ${Action::class.qualifiedName}", it)
        }
    }
}