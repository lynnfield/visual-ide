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
import com.genovich.visualide.analysis.ClassInfo
import com.genovich.visualide.types.TYPE_NOTHING
import com.genovich.visualide.ui.AddNewLayoutButton
import com.genovich.visualide.ui.TextBlock
import com.genovich.visualide.ui.step
import com.intellij.openapi.diagnostic.getOrLogException
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.Text
import java.security.MessageDigest
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class ActionDefinition(
    val name: MutableState<String>,
    val body: MutableState<ActionLayout?> = mutableStateOf(null),
    /**
     * Derived ports (see [signature]) mapped to their assembly-plane default, when they have one
     * other than "required". Attached here, at the definition level, rather than on whichever leaf
     * node happens to produce the port — the body tree stays 100% unaware of T-functions (matches
     * "invisible at the function level"). A port is a T-function exactly when its entry here is
     * [Show] (`portDefaults.value[portName] == Show`) — see docs/example-rung3.md for why this
     * replaced two earlier designs (a boolean flag on the leaf, then a distinct leaf node type) and
     * a plain `Set<String>` before this.
     */
    val portDefaults: MutableState<Map<String, PortDefault>> = mutableStateOf(emptyMap()),
    val id: Uuid = Uuid.random(),
    /**
     * Set by [parse] when the source's `@Diagram(checksum = …)` doesn't match the checksum of the
     * parsed-and-regenerated body (design.md §2.4, §4.4) — the body was hand-edited out of band
     * instead of through the diagram tool. `false` for definitions built in memory (nothing to
     * drift from) and always `false` when the source carries no `@Diagram` (a leaf/custom node).
     */
    val isDrifted: Boolean = false,
) {
    constructor(
        name: String,
        body: ActionLayout? = null,
        portDefaults: Map<String, PortDefault> = emptyMap(),
        id: Uuid = Uuid.random(),
        isDrifted: Boolean = false,
    ) : this(
        name = mutableStateOf(name),
        body = mutableStateOf(body),
        portDefaults = mutableStateOf(portDefaults),
        id = id,
        isDrifted = isDrifted,
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
                                checked = portDefaults.value[portName] == Show,
                                onCheckedChange = { checked ->
                                    portDefaults.value = if (checked) {
                                        portDefaults.value + (portName to Show)
                                    } else {
                                        portDefaults.value - portName
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
        // Seeded with the composite's own input so a top-level Ref(INPUT_PARAM_NAME) resolves to
        // a real type (design.md §2.5) — see ActionLayout.inferType's `scope` parameter.
        val scope = mutableMapOf(INPUT_PARAM_NAME to INPUT_TYPE)
        val outputType = body.value?.inferType(INPUT_TYPE, fresh, ports, scope) ?: TYPE_NOTHING
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
                signature.ports.entries.joinToString(
                    separator = ",\n",
                    prefix = "\n"
                ) { (portName, io) ->
                    "val `$portName`: $COM_GENOVICH_COMPONENTS_ACTION<${io.first}, ${io.second}>"
                }
            }

        val inputParamName = INPUT_PARAM_NAME
        val bodyCode = bodyCode(inputParamName)
        return """
            @$COM_GENOVICH_COMPONENTS_NODE
            @$COM_GENOVICH_COMPONENTS_DIAGRAM(version = 1, checksum = "${checksumOf(bodyCode)}")
            class `${name.value}`<$typeParameters>($portDeclarations) : $COM_GENOVICH_COMPONENTS_ACTION<$INPUT_TYPE, ${signature.outputType}>() {
                override suspend operator fun $INVOKE_METHOD_NAME($inputParamName: $INPUT_TYPE): ${signature.outputType} =
                    $bodyCode
            }
        """.trimIndent()
    }

    /** The normalized body text (design.md §2.7) the [checksumOf] hash is taken over. */
    private fun bodyCode(inputParamName: String): String =
        body.value?.generate(inputParamName) ?: TodoStub.generate()

    /**
     * The dependency-plane factory (design.md §3.2): a function taking every leaf port as a
     * parameter and constructing this definition's class. A port whose [portDefaults] entry is
     * [Show] gets a `Show(flow)` default instead of a required parameter, sourced from a
     * [generateUiStateFlow] instance threaded in as this function's own defaulted *first* parameter
     * (a Kotlin default expression can only reference earlier parameters, never a body-local `val`
     * — and the class can't be a singleton `object` since ports are still abstract type variables
     * at this rung), which also gives it a D5 override seam for free.
     */
    fun generateAssembly(): String {
        val signature = signature()
        val typeParameters = signature.typeParameters.joinToString(separator = ", ")
        val tFunctionPortNames = signature.ports.keys.filter { portDefaults.value[it] == Show }
        val uiStateFlowName = "${name.value}$UI_STATE_FLOW_SUFFIX"

        val parameterLines = buildList {
            if (tFunctionPortNames.isNotEmpty()) {
                val uiStateFlowTypeParameters =
                    uiStateFlowTypeParameters(signature, tFunctionPortNames)
                add("    `$UI_STATE_FLOW_PARAM_NAME`: `$uiStateFlowName`<$uiStateFlowTypeParameters> = `$uiStateFlowName`()")
            }
            signature.ports.forEach { (portName, io) ->
                val default = if (portDefaults.value[portName] == Show) {
                    " = ${Show.SHOW_FQN}(`$UI_STATE_FLOW_PARAM_NAME`.`$portName$FLOW_SUFFIX`)"
                } else {
                    ""
                }
                add("    `$portName`: $COM_GENOVICH_COMPONENTS_ACTION<${io.first}, ${io.second}>$default")
            }
        }
        val parameterDeclarations =
            if (parameterLines.isEmpty()) {
                ""
            } else {
                parameterLines.joinToString(
                    separator = ",\n",
                    prefix = "\n",
                    postfix = ",\n",
                )
            }

        val constructorArguments =
            if (signature.ports.isEmpty()) {
                ""
            } else {
                signature.ports.keys.joinToString(
                    separator = ",\n",
                    prefix = "\n",
                    postfix = ",\n",
                ) { portName ->
                    "    `$portName` = `$portName`"
                }
            }

        return """
            fun <$typeParameters> `${name.value}$ASSEMBLY_SUFFIX`($parameterDeclarations): `${name.value}`<$typeParameters> =
                `${name.value}`($constructorArguments)
        """.trimIndent()
    }

    /**
     * The state projection (design.md §3.3): a class mirroring the T-function ports (those whose
     * [portDefaults] entry is [Show]), one `MutableStateFlow<UiState<in, out>?>` per port,
     * `combine`d into a sealed "which screen is live" `Screen` (one case per T-function). `null`
     * when there are no T-function ports — nothing to project, so no file is generated (see
     * [com.genovich.visualide.toolWindow]'s `save()`, which skips writing the third file in that
     * case).
     */
    fun generateUiStateFlow(): String? {
        val signature = signature()
        val tFunctionPortNames = signature.ports.keys.filter { portDefaults.value[it] == Show }
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

        val combineArguments =
            tFunctionPortNames.joinToString(separator = ",\n            ") { portName ->
                val screenCaseName = screenCaseName(portName)
                "$COM_GENOVICH_COMPONENTS_EMIT_SELF_WHEN_HAVE_VALUE(`$portName$FLOW_SUFFIX`) { Screen.`$screenCaseName`(it) }"
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
    private fun uiStateFlowTypeParameters(
        signature: Signature,
        tFunctionPortNames: Collection<String>,
    ): String {
        val used =
            tFunctionPortNames.flatMapTo(mutableSetOf()) { signature.ports.getValue(it).toList() }
        return signature.typeParameters.filter { it in used }.joinToString(separator = ", ")
    }

    private fun screenCaseName(portName: String): String =
        portName.replaceFirstChar(Char::uppercaseChar)

    /** [typeParameters] in declaration order (`Input, T1..Tn`); [ports] preserve first-use order. */
    data class Signature(
        val typeParameters: List<String>,
        val ports: Map<String, Pair<String, String>>,
        val outputType: String,
    )

    /**
     * A port's assembly-plane default value (design.md §3.2 point 1, "Wiring"). [Show] is the only
     * implementation today — see its KDoc — but this is a closed set a future binding kind (e.g.
     * wiring a port to a child `*Assembly(...)` call) would extend, rather than a bespoke
     * boolean/name-set concept per binding kind.
     */
    sealed interface PortDefault

    companion object {
        const val INVOKE_METHOD_NAME = "invoke"
        const val COM_GENOVICH_COMPONENTS_ACTION = "com.genovich.components.Action"
        const val COM_GENOVICH_COMPONENTS_MUTABLE_STATE_FLOW =
            "com.genovich.components.MutableStateFlow"
        const val COM_GENOVICH_COMPONENTS_STATE_FLOW = "com.genovich.components.StateFlow"
        const val COM_GENOVICH_COMPONENTS_UI_STATE = "com.genovich.components.UiState"
        const val COM_GENOVICH_COMPONENTS_COMBINE = "com.genovich.components.combine"
        const val COM_GENOVICH_COMPONENTS_EMIT_SELF_WHEN_HAVE_VALUE =
            "com.genovich.components.emitSelfWhenHaveValue"
        const val COM_GENOVICH_COMPONENTS_NODE = "com.genovich.components.Node"
        const val COM_GENOVICH_COMPONENTS_DIAGRAM = "com.genovich.components.Diagram"
        const val INPUT_TYPE = "Input"
        const val INPUT_PARAM_NAME = "input"
        const val ASSEMBLY_SUFFIX = "Assembly"
        const val UI_STATE_FLOW_SUFFIX = "UiStateFlow"
        const val UI_STATE_FLOW_PARAM_NAME = "uiStateFlow"
        const val FLOW_SUFFIX = "Flow"

        /**
         * Consumes [ClassInfo] — the engine IR (design.md §4.5, D12) — never UAST/PSI directly;
         * [com.genovich.visualide.analysis.KotlinAnalysis] is the sole adapter that builds one from
         * a UAST `UClass`. Callers (the tool window, tests) convert first.
         */
        fun parse(classInfo: ClassInfo): ActionDefinition? =
            classInfo
                .takeIf { COM_GENOVICH_COMPONENTS_ACTION in it.superTypeQualifiedNames }
                ?.let {
                    val definition = ActionDefinition(
                        name = it.name ?: "Unknown",
                        body = it.invokeBody
                            ?.let { expr -> ActionLayout.parse(expr) }
                            ?.getOrLogException { it.printStackTrace() },
                    )
                    val storedChecksum = it.diagramChecksum
                    if (storedChecksum == null) {
                        definition
                    } else {
                        definition.copy(
                            isDrifted = storedChecksum != checksumOf(definition.bodyCode(INPUT_PARAM_NAME)),
                        )
                    }
                }

        /** SHA-256 of [bodyCode], the checksum stored in `@Diagram` and checked for drift on [parse]. */
        private fun checksumOf(bodyCode: String): String {
            val digest =
                MessageDigest.getInstance("SHA-256").digest(bodyCode.toByteArray(Charsets.UTF_8))
            return "sha256:" + digest.joinToString(separator = "") { "%02x".format(it) }
        }
    }
}
