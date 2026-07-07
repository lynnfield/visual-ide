package com.genovich.visualide.actions

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.genovich.visualide.ui.TextBlock
import org.jetbrains.jewel.ui.component.Text

/**
 * A T-function (design.md §1.6, §5.1): a leaf port bound to a UI boundary handshake — same
 * `(Input) -> Output` shape as [Action], but its assembly default is `Show(flow)` instead of a
 * required parameter ([ActionDefinition.generateAssembly]), and it contributes a flow to
 * [ActionDefinition.generateUiStateFlow]'s projection.
 *
 * At the function level it is indistinguishable from [Action] — [generate] emits the exact same
 * shape (design.md §5.1: "at the function level it is indistinguishable from any other port").
 * Because of that, parsing generated code can never recover a [TFunction] from a leaf call: it
 * always reconstructs a plain [Action] instead (see [Action.parse]). [TFunction] therefore has no
 * [ActionLayout.UExpressionParser] and is not registered in [ActionLayout.parse]'s dispatcher —
 * this is intentional (the same round-trip limitation the previous marker-based design had; see
 * docs/example-rung3.md), not an oversight of the "every node type has a parser" convention.
 */
data class TFunction(
    val name: MutableState<String> = mutableStateOf("New T-function")
) : ActionLayout {

    constructor(name: String) : this(mutableStateOf(name))

    override fun iterator(): Iterator<ActionLayout> = iterator {
        yield(this@TFunction)
    }

    @Composable
    override fun Render(onRemove: () -> Unit, modifier: Modifier) {
        Column(modifier = modifier) {
            Text("T-function")
            TextBlock(name, onRemove = onRemove)
        }
    }

    override fun generate(input: String): String = "`${name.value}`($input)"

    override fun inferType(
        input: String,
        fresh: () -> String,
        ports: MutableMap<String, ActionLayout.PortSignature>,
    ): String = ports.getOrPut(name.value) {
        ActionLayout.PortSignature(input, fresh(), isTFunction = true)
    }.outputType
}
