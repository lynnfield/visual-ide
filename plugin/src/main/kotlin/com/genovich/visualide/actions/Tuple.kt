package com.genovich.visualide.actions

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.genovich.visualide.analysis.Call
import com.genovich.visualide.analysis.Expr
import com.genovich.visualide.analysis.QualifiedCall
import com.genovich.visualide.types.TYPE_NOTHING
import com.genovich.visualide.ui.AddNewLayoutButton
import com.genovich.visualide.ui.RemoveButton

/**
 * Tuple (design.md §2.5): `first to second` — a product of two values, both computed from the
 * *same* incoming value (a fan-out, not a chain — matches [RepeatWhileActive]/[RetryUntilResult]
 * already passing their own `input` down into a nested slot unchanged).
 *
 * **Canonical form uses `.to(...)` dot-call syntax, not the infix `a to b` operator form.** The
 * engine's IR (`analysis.Expr`) only recognizes named dot-calls/qualified-calls
 * ([QualifiedCall]/[Call]) reliably (design.md D12); infix-operator UAST recognition is a
 * separate, more involved shape (`UBinaryExpression`) not covered by this rung's IR and is
 * deferred. `a.to(b)` is 100% semantically equivalent Kotlin — `to` is simply an infix-declared
 * extension function, callable either way.
 */
data class Tuple(
    val first: MutableState<ActionLayout?> = mutableStateOf(null),
    val second: MutableState<ActionLayout?> = mutableStateOf(null),
) : ActionLayout {

    constructor(first: ActionLayout?, second: ActionLayout?) : this(mutableStateOf(first), mutableStateOf(second))

    override fun iterator(): Iterator<ActionLayout> = iterator {
        yield(this@Tuple)
        first.value?.also { yieldAll(it) }
        second.value?.also { yieldAll(it) }
    }

    @Composable
    override fun Render(onRemove: () -> Unit, modifier: Modifier) {
        Row(modifier) {
            first.value?.Render(onRemove = { first.value = null }, modifier = Modifier)
                ?: AddNewLayoutButton(onAdd = first::value::set)
            second.value?.Render(onRemove = { second.value = null }, modifier = Modifier)
                ?: AddNewLayoutButton(onAdd = second::value::set)
            RemoveButton("Tuple", onRemove)
        }
    }

    override fun generate(input: String): String =
        "${first.value?.generate(input) ?: TodoStub.generate()}.to(${second.value?.generate(input) ?: TodoStub.generate()})"

    override fun inferType(
        input: String,
        fresh: () -> String,
        ports: MutableMap<String, Pair<String, String>>,
        scope: MutableMap<String, String>,
    ): String {
        val firstType = first.value?.inferType(input, fresh, ports, scope) ?: TYPE_NOTHING
        val secondType = second.value?.inferType(input, fresh, ports, scope) ?: TYPE_NOTHING
        return "kotlin.Pair<$firstType, $secondType>"
    }

    companion object : ActionLayout.ExpressionParser<Tuple> {
        const val TO_FQN = "kotlin.to"

        override fun parse(expression: Expr): Result<Tuple> = runCatching {
            checkNotNull(expression as? QualifiedCall) { "not a qualified call expression" }
                .also { check(it.resolvedQualifiedName == TO_FQN) { "name should be $TO_FQN" } }
                .let { qualifiedCall ->
                    val secondArgument = checkNotNull(qualifiedCall.selector as? Call) { "selector should be a call expression" }
                        .arguments
                        .also { check(it.size == 1) { "`to` should have exactly one argument" } }
                        .single()
                    Tuple(
                        first = checkNotNull(ActionLayout.parse(qualifiedCall.receiver).getOrThrow()) { "failed to convert the first element to an ActionLayout" },
                        second = checkNotNull(ActionLayout.parse(secondArgument).getOrThrow()) { "failed to convert the second element to an ActionLayout" },
                    )
                }
        }.recoverCatching {
            throw Exception("failed to parse ${Tuple::class.qualifiedName}", it)
        }
    }
}
