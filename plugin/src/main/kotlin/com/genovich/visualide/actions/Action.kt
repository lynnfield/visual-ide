package com.genovich.visualide.actions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.genovich.visualide.ui.TextBlock
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.tryResolveNamed

data class Action(
    val name: MutableState<String> = mutableStateOf("New Action"),
    val isTFunction: MutableState<Boolean> = mutableStateOf(false),
) : ActionLayout {

    constructor(name: String, isTFunction: Boolean = false) : this(mutableStateOf(name), mutableStateOf(isTFunction))

    override fun iterator(): Iterator<ActionLayout> = iterator {
        yield(this@Action)
    }

    @Composable
    override fun Render(onRemove: () -> Unit, modifier: Modifier) {
        Column(modifier = modifier) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isTFunction.value, onCheckedChange = { isTFunction.value = it })
                Text("T-function")
            }
            TextBlock(name, onRemove = onRemove)
        }
    }

    override fun generate(input: String): String = "`${name.value}`($input)"

    // T-ness is invisible at the function level (design.md §5.1) — it only affects the assembly's
    // default wiring, so it is not part of the generated function file and does not round-trip
    // via parse() (parseAssembly, which would recover it, is not implemented yet — see rung 2).
    override fun inferType(
        input: String,
        fresh: () -> String,
        ports: MutableMap<String, ActionLayout.PortSignature>,
    ): String = ports.getOrPut(name.value) {
        ActionLayout.PortSignature(input, fresh(), isTFunction.value)
    }.outputType

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