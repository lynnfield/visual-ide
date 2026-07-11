package com.genovich.visualide.actions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.genovich.visualide.types.TYPE_NOTHING
import com.genovich.visualide.ui.AddNewLayoutButton
import com.genovich.visualide.ui.TextBlock
import com.genovich.visualide.ui.step
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.util.asSafely
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.utils.findIsInstanceAnd
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class ActionDefinition(
    val name: MutableState<String>,
    val body: MutableState<ActionLayout?> = mutableStateOf(null),
    /**
     * Names of derived ports (see [signature]) wired via [Show] (design.md §1.6, §5.1) instead of
     * a required parameter. Attached here, at the definition level, rather than on whichever leaf
     * node happens to produce the port — the body tree stays 100% unaware of T-functions (matches
     * "invisible at the function level"), and this set is the sole "known list of T-functions" for
     * this definition. See docs/example-rung3.md for why this replaced two earlier designs (a
     * boolean flag on the leaf, then a distinct leaf node type).
     */
    val tFunctionPorts: MutableState<Set<String>> = mutableStateOf(emptySet()),
    val id: Uuid = Uuid.random(),
) {
    constructor(
        name: String,
        body: ActionLayout? = null,
        tFunctionPorts: Set<String> = emptySet(),
        id: Uuid = Uuid.random(),
    ) : this(
        name = mutableStateOf(name),
        body = mutableStateOf(body),
        tFunctionPorts = mutableStateOf(tFunctionPorts),
        id = id,
    )

    @Composable
    fun Render(modifier: Modifier = Modifier.Companion) {
        Column(modifier = modifier.width(IntrinsicSize.Max)) {
            TextBlock(name) // todo should I allow self-delete???
            val paddings = Modifier.padding(horizontal = step)
            val ports = signature().ports.keys
            if (ports.isNotEmpty()) {
                Column(modifier = paddings) {
                    ports.forEach { portName ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = portName in tFunctionPorts.value,
                                onCheckedChange = { checked ->
                                    tFunctionPorts.value = if (checked) {
                                        tFunctionPorts.value + portName
                                    } else {
                                        tFunctionPorts.value - portName
                                    }
                                },
                            )
                            Text("`$portName` is a T-function")
                        }
                    }
                }
            }
            body.value?.Render(onRemove = { body.value = null }, modifier = paddings)
                ?: AddNewLayoutButton(modifier = paddings, onAdd = body::value::set)
        }
    }

    /**
     * Structural type inference (rung 1 / H2), run once and shared by [generate], [generateAssembly],
     * and [generateUiStateFlow]: threads type variables through the body so each port gets its own
     * `Action<in, out>` types instead of a shared placeholder `<Input, Output>`.
     */
    fun signature(): Signature {
        val ports = mutableMapOf<String, Pair<String, String>>()
        val typeParameters = mutableListOf(INPUT_TYPE)
        val fresh = { "T${typeParameters.size}".also(typeParameters::add) }
        val outputType = body.value?.inferType(INPUT_TYPE, fresh, ports) ?: TYPE_NOTHING
        return Signature(typeParameters, ports, outputType)
    }

    @OptIn(ExperimentalUuidApi::class)
    fun generate(): String {
        val signature = signature()
        val typeParameters = signature.typeParameters.joinToString(separator = ", ")

        val portDeclarations =
            if (signature.ports.isEmpty()) {
                ""
            } else {
                signature.ports.entries.joinToString(separator = ",\n", prefix = "\n") { (portName, io) ->
                    "val `$portName`: $COM_GENOVICH_COMPONENTS_ACTION<${io.first}, ${io.second}>"
                }
            }

        val input = "input"
        return """
            class `${name.value}`<$typeParameters>($portDeclarations) : $COM_GENOVICH_COMPONENTS_ACTION<$INPUT_TYPE, ${signature.outputType}>() {
                override suspend operator fun $INVOKE_METHOD_NAME($input: $INPUT_TYPE): ${signature.outputType} =
                    ${(body.value?.generate(input) ?: TodoStub.generate())}
            }
        """.trimIndent()
    }

    /**
     * The dependency-plane factory (design.md §3.2): a function taking every leaf port as a
     * parameter and constructing this definition's class. A port named in [tFunctionPorts] gets a
     * `Show(flow)` default instead of a required parameter, sourced from a [generateUiStateFlow]
     * instance threaded in as this function's own defaulted *first* parameter (a Kotlin default
     * expression can only reference earlier parameters, never a body-local `val` — and the class
     * can't be a singleton `object` since ports are still abstract type variables at this rung),
     * which also gives it a D5 override seam for free.
     */
    fun generateAssembly(): String {
        val signature = signature()
        val typeParameters = signature.typeParameters.joinToString(separator = ", ")
        val tFunctionPortNames = signature.ports.keys.filter { it in tFunctionPorts.value }
        val uiStateFlowName = "${name.value}$UI_STATE_FLOW_SUFFIX"

        val parameterLines = buildList {
            if (tFunctionPortNames.isNotEmpty()) {
                val uiStateFlowTypeParameters = uiStateFlowTypeParameters(signature, tFunctionPortNames)
                add("    `$UI_STATE_FLOW_PARAM_NAME`: `$uiStateFlowName`<$uiStateFlowTypeParameters> = `$uiStateFlowName`()")
            }
            signature.ports.forEach { (portName, io) ->
                val default = if (portName in tFunctionPorts.value) {
                    " = ${Show.SHOW_FQN}(`$UI_STATE_FLOW_PARAM_NAME`.`$portName$FLOW_SUFFIX`)"
                } else {
                    ""
                }
                add("    `$portName`: $COM_GENOVICH_COMPONENTS_ACTION<${io.first}, ${io.second}>$default")
            }
        }
        val parameterDeclarations =
            if (parameterLines.isEmpty()) "" else parameterLines.joinToString(separator = ",\n", prefix = "\n", postfix = ",\n")

        val constructorArguments =
            if (signature.ports.isEmpty()) {
                ""
            } else {
                signature.ports.keys.joinToString(separator = ",\n", prefix = "\n", postfix = ",\n") { portName ->
                    "    `$portName` = `$portName`"
                }
            }

        return """
            fun <$typeParameters> `${name.value}$ASSEMBLY_SUFFIX`($parameterDeclarations): `${name.value}`<$typeParameters> =
                `${name.value}`($constructorArguments)
        """.trimIndent()
    }

    /**
     * The state projection (design.md §3.3): a class mirroring the [tFunctionPorts], one
     * `MutableStateFlow<UiState<in, out>?>` per T-function port, `combine`d into a sealed "which
     * screen is live" `Screen` (one case per T-function). `null` when [tFunctionPorts] is empty —
     * nothing to project, so no file is generated (see [com.genovich.visualide.toolWindow]'s
     * `save()`, which skips writing the third file in that case).
     */
    fun generateUiStateFlow(): String? {
        val signature = signature()
        val tFunctionPortNames = signature.ports.keys.filter { it in tFunctionPorts.value }
        if (tFunctionPortNames.isEmpty()) return null

        val typeParameters = uiStateFlowTypeParameters(signature, tFunctionPortNames)

        val flowDeclarations = tFunctionPortNames.joinToString(separator = "\n\n") { portName ->
            val (inputType, outputType) = signature.ports.getValue(portName)
            """    val `$portName$FLOW_SUFFIX`: $COM_GENOVICH_COMPONENTS_MUTABLE_STATE_FLOW<$COM_GENOVICH_COMPONENTS_UI_STATE<$inputType, $outputType>?> =
            |        $COM_GENOVICH_COMPONENTS_MUTABLE_STATE_FLOW(null)"""
                .trimMargin()
        }

        // Screen is nested for readability, but Kotlin's nested (non-inner; sealed interfaces
        // can't be `inner`) types don't inherit the enclosing class's type parameters — it must
        // redeclare its own, matching the outer class's list so callers pass the same types.
        val screenCases = tFunctionPortNames.joinToString(separator = "\n") { portName ->
            val (inputType, outputType) = signature.ports.getValue(portName)
            "        data class `${screenCaseName(portName)}`<$typeParameters>(val state: $COM_GENOVICH_COMPONENTS_UI_STATE<$inputType, $outputType>) : Screen<$typeParameters>"
        }

        val combineArguments = tFunctionPortNames.joinToString(separator = ",\n            ") { portName ->
            "$COM_GENOVICH_COMPONENTS_EMIT_SELF_WHEN_HAVE_VALUE(`$portName$FLOW_SUFFIX`) { Screen.`${screenCaseName(portName)}`(it) }"
        }

        return """
            class `${name.value}$UI_STATE_FLOW_SUFFIX`<$typeParameters> {
            $flowDeclarations

                sealed interface Screen<$typeParameters> {
            $screenCases
                }

                val screen: $COM_GENOVICH_COMPONENTS_STATE_FLOW<Screen<$typeParameters>?> =
                    $COM_GENOVICH_COMPONENTS_COMBINE(
                        $combineArguments
                    ) { cases -> cases.firstOrNull { it != null } }
            }
        """.trimIndent()
    }

    /** Type parameters (in [Signature.typeParameters] order) referenced by [tFunctionPortNames]. */
    private fun uiStateFlowTypeParameters(signature: Signature, tFunctionPortNames: Collection<String>): String {
        val used = tFunctionPortNames.flatMapTo(mutableSetOf()) { signature.ports.getValue(it).toList() }
        return signature.typeParameters.filter { it in used }.joinToString(separator = ", ")
    }

    private fun screenCaseName(portName: String): String = portName.replaceFirstChar(Char::uppercaseChar)

    /** [typeParameters] in declaration order (`Input, T1..Tn`); [ports] preserve first-use order. */
    data class Signature(
        val typeParameters: List<String>,
        val ports: Map<String, Pair<String, String>>,
        val outputType: String,
    )

    companion object {
        const val INVOKE_METHOD_NAME = "invoke"
        const val COM_GENOVICH_COMPONENTS_ACTION = "com.genovich.components.Action"
        const val COM_GENOVICH_COMPONENTS_MUTABLE_STATE_FLOW = "com.genovich.components.MutableStateFlow"
        const val COM_GENOVICH_COMPONENTS_STATE_FLOW = "com.genovich.components.StateFlow"
        const val COM_GENOVICH_COMPONENTS_UI_STATE = "com.genovich.components.UiState"
        const val COM_GENOVICH_COMPONENTS_COMBINE = "com.genovich.components.combine"
        const val COM_GENOVICH_COMPONENTS_EMIT_SELF_WHEN_HAVE_VALUE = "com.genovich.components.emitSelfWhenHaveValue"
        const val INPUT_TYPE = "Input"
        const val ASSEMBLY_SUFFIX = "Assembly"
        const val UI_STATE_FLOW_SUFFIX = "UiStateFlow"
        const val UI_STATE_FLOW_PARAM_NAME = "uiStateFlow"
        const val FLOW_SUFFIX = "Flow"

        fun parse(uClass: UClass): ActionDefinition? =
            uClass
                .takeIf {
                    it.uastSuperTypes.any { it.getQualifiedName() == COM_GENOVICH_COMPONENTS_ACTION }
                }
                ?.let {
                    ActionDefinition(
                        name = it.name ?: "Unknown",
                        body = it.uastDeclarations
                            .findIsInstanceAnd<UMethod> { it.name == INVOKE_METHOD_NAME }
                            ?.uastBody
                            ?.asSafely<UBlockExpression>()
                            ?.expressions
                            ?.firstIsInstanceOrNull<UReturnExpression>()
                            ?.returnExpression
                            ?.let { ActionLayout.parse(it) }
                            ?.getOrLogException { it.printStackTrace() },
                    )
                }
    }
}
