package com.genovich.visualide.actions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.genovich.visualide.analysis.Expr
import com.genovich.visualide.analysis.Reference
import com.genovich.visualide.ui.TextBlock

/**
 * Ref (design.md §2.5): names a value already in scope — a local `val`, the composite's own
 * `input`, a port result — rather than transforming its own pipeline input. [generate] ignores
 * the `input` it's given and emits [name] verbatim; the diagram author is trusted to type a name
 * that's actually in scope at that point, the same trust [Action]'s free-text port name already
 * relies on.
 *
 * [inferType] looks [name] up in [ActionLayout.inferType]'s `scope` map — seeded with the
 * composite's own `input` (by [ActionDefinition.signature]) and grown with each `Passing` step's
 * `stepN` (by [Passing.inferType]) — so it recovers a real type rather than guessing, e.g.
 * reaching back past a T-function call to recover a value a later SSA `val` shadowed (see the
 * `GuessGame` specimen's `Tuple(Ref("input"), Ref("step1"))`, `docs/example-rung6.md`). A name
 * outside that scope (never declared, or declared later) falls back to [input] unchanged — best
 * effort, matching this whole type-inference pass never throwing.
 */
data class Ref(
    val name: MutableState<String> = mutableStateOf(ActionDefinition.INPUT_PARAM_NAME)
) : ActionLayout {

    constructor(name: String) : this(mutableStateOf(name))

    override fun iterator(): Iterator<ActionLayout> = iterator {
        yield(this@Ref)
    }

    @Composable
    override fun Render(onRemove: () -> Unit, modifier: Modifier) {
        TextBlock(name, onRemove = onRemove, modifier = modifier)
    }

    override fun generate(input: String): String = name.value

    override fun inferType(
        input: String,
        fresh: () -> String,
        ports: MutableMap<String, Pair<String, String>>,
        scope: MutableMap<String, String>,
    ): String = scope[name.value] ?: input

    companion object : ActionLayout.ExpressionParser<Ref> {
        override fun parse(expression: Expr): Result<Ref> = runCatching {
            checkNotNull(expression as? Reference) { "not a bare reference expression" }
                .let { checkNotNull(it.resolvedName) { "failed to resolve named element" } }
                .let { Ref(it) }
        }.recoverCatching {
            throw Exception("failed to parse a ${Ref::class.qualifiedName}", it)
        }
    }
}
