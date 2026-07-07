package com.genovich.visualide.actions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.genovich.visualide.types.TYPE_NOTHING
import com.genovich.visualide.ui.AddNewLayoutButton
import com.genovich.visualide.ui.TextBlock
import com.genovich.visualide.ui.step
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.util.asSafely
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
    val id: Uuid = Uuid.random(),
) {
    constructor(
        name: String,
        body: ActionLayout? = null,
        id: Uuid = Uuid.random(),
    ) : this(
        name = mutableStateOf(name),
        body = mutableStateOf(body),
        id = id,
    )

    @Composable
    fun Render(modifier: Modifier = Modifier.Companion) {
        Column(modifier = modifier.width(IntrinsicSize.Max)) {
            TextBlock(name) // todo should I allow self-delete???
            val paddings = Modifier.padding(horizontal = step)
            body.value?.Render(onRemove = { body.value = null }, modifier = paddings)
                ?: AddNewLayoutButton(modifier = paddings, onAdd = body::value::set)
        }
    }

    /**
     * Structural type inference (rung 1 / H2), run once and shared by [generate] and
     * [generateAssembly]: threads type variables through the body so each port gets its own
     * `Action<in, out>` types instead of a shared placeholder `<Input, Output>`.
     */
    fun signature(): Signature {
        val ports = mutableMapOf<String, ActionLayout.PortSignature>()
        val typeParameters = mutableListOf(INPUT_TYPE)
        val fresh = { "T${typeParameters.size}".also(typeParameters::add) }
        val outputType = body.value?.inferType(INPUT_TYPE, fresh, ports) ?: TYPE_NOTHING
        return Signature(typeParameters, ports, outputType)
    }

    /** Type parameters (in [Signature.typeParameters] order) referenced by [tFunctionPorts]. */
    private fun uiStateFlowTypeParameters(
        signature: Signature,
        tFunctionPorts: Map<String, ActionLayout.PortSignature>,
    ): String {
        val used = tFunctionPorts.values.flatMapTo(mutableSetOf()) { listOf(it.inputType, it.outputType) }
        return signature.typeParameters.filter { it in used }.joinToString(separator = ", ")
    }

    private fun screenCaseName(portName: String): String = portName.replaceFirstChar(Char::uppercaseChar)

    @OptIn(ExperimentalUuidApi::class)
    fun generate(): String {
        val signature = signature()
        val typeParameters = signature.typeParameters.joinToString(separator = ", ")

        val portDeclarations =
            if (signature.ports.isEmpty()) {
                ""
            } else {
                signature.ports.entries.joinToString(separator = ",\n", prefix = "\n") { (portName, port) ->
                    "val `$portName`: $COM_GENOVICH_COMPONENTS_ACTION<${port.inputType}, ${port.outputType}>"
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
     * required parameter and constructing this definition's class. A T-function port
     * (design.md §1.6, §5.1) instead gets a `Show(flow)` default sourced from a
     * `<Name>UiStateFlow` instance (design.md §3.3, [generateUiStateFlow]) — itself a defaulted
     * first parameter (D5 override seam), so callers can supply their own instance to observe the
     * boundary state, or rely on the default.
     */
    fun generateAssembly(): String {
        val signature = signature()
        val typeParameters = signature.typeParameters.joinToString(separator = ", ")
        val tFunctionPorts = signature.ports.filterValues { it.isTFunction }
        val uiStateFlowName = "${name.value}$UI_STATE_FLOW_SUFFIX"

        val parameterLines = buildList {
            if (tFunctionPorts.isNotEmpty()) {
                val uiStateFlowTypeParameters = uiStateFlowTypeParameters(signature, tFunctionPorts)
                add("    `$UI_STATE_FLOW_PARAM_NAME`: `$uiStateFlowName`<$uiStateFlowTypeParameters> = `$uiStateFlowName`()")
            }
            signature.ports.forEach { (portName, port) ->
                val default = if (port.isTFunction) {
                    " = $COM_GENOVICH_COMPONENTS_SHOW(`$UI_STATE_FLOW_PARAM_NAME`.`$portName$FLOW_SUFFIX`)"
                } else {
                    ""
                }
                add("    `$portName`: $COM_GENOVICH_COMPONENTS_ACTION<${port.inputType}, ${port.outputType}>$default")
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
     * The state projection (design.md §3.3): a class mirroring the T-function ports, one
     * `MutableStateFlow<UiState<in, out>?>` per port, `combine`d into a sealed "which screen is
     * live" `Screen` (one case per T-function). `null` when there are no T-function ports —
     * nothing to project, so no file is generated (see [com.genovich.visualide.toolWindow]'s
     * `save()`, which skips writing the third file in that case).
     */
    fun generateUiStateFlow(): String? {
        val signature = signature()
        val tFunctionPorts = signature.ports.filterValues { it.isTFunction }
        if (tFunctionPorts.isEmpty()) return null

        val typeParameters = uiStateFlowTypeParameters(signature, tFunctionPorts)

        val flowDeclarations = tFunctionPorts.entries.joinToString(separator = "\n\n") { (portName, port) ->
            """    val `$portName$FLOW_SUFFIX`: $COM_GENOVICH_COMPONENTS_MUTABLE_STATE_FLOW<$COM_GENOVICH_COMPONENTS_UI_STATE<${port.inputType}, ${port.outputType}>?> =
            |        $COM_GENOVICH_COMPONENTS_MUTABLE_STATE_FLOW(null)"""
                .trimMargin()
        }

        // Screen is nested for readability, but Kotlin's nested (non-inner; sealed interfaces
        // can't be `inner`) types don't inherit the enclosing class's type parameters — it must
        // redeclare its own, matching the outer class's list so callers pass the same types.
        val screenCases = tFunctionPorts.entries.joinToString(separator = "\n") { (portName, port) ->
            "        data class `${screenCaseName(portName)}`<$typeParameters>(val state: $COM_GENOVICH_COMPONENTS_UI_STATE<${port.inputType}, ${port.outputType}>) : Screen<$typeParameters>"
        }

        val combineArguments = tFunctionPorts.keys.joinToString(separator = ",\n            ") { portName ->
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

    /** [typeParameters] in declaration order (`Input, T1..Tn`); [ports] preserve first-use order. */
    data class Signature(
        val typeParameters: List<String>,
        val ports: Map<String, ActionLayout.PortSignature>,
        val outputType: String,
    )

    companion object {
        const val INVOKE_METHOD_NAME = "invoke"
        const val COM_GENOVICH_COMPONENTS_ACTION = "com.genovich.components.Action"
        const val COM_GENOVICH_COMPONENTS_SHOW = "com.genovich.components.Show"
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