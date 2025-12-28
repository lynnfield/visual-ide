package com.genovich.visualide.actions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.genovich.visualide.ui.TextBlock
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.tryResolveNamed

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

    companion object : ActionLayout.UExpressionParser<Action> {
        override fun parse(expression: UExpression): Result<Action> = runCatching {
            checkNotNull(expression as? UCallExpression) { "is not a call expression" }
                .also { check(it.methodName == "invoke") { "should have `invoke` method" } }
                .let { checkNotNull(it.receiver) { "`invoke` expression should have a receiver" } }
                .let { checkNotNull(it.tryResolveNamed()) { "failed to resolve named element" } }
                .let { checkNotNull(it.name) { "receiver should have name" } }
                .let { Action(it) }
        }.recoverCatching {
            throw Exception("failed to parse an ${Action::class.qualifiedName}", it)
        }
    }
}