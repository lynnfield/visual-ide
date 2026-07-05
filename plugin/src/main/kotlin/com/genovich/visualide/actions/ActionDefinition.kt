package com.genovich.visualide.actions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
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
        val ports = mutableMapOf<String, Pair<String, String>>()
        var typeVarCount = 0
        val fresh = { "T${++typeVarCount}" }
        val outputType = body.value?.inferType(INPUT_TYPE, fresh, ports) ?: NOTHING_TYPE
        val typeParameters = listOf(INPUT_TYPE) + (1..typeVarCount).map { "T$it" }
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
     * required parameter and constructing this definition's class. No default wiring yet (that
     * arrives with child assemblies / T-functions in later rungs), so every port is required.
     */
    fun generateAssembly(): String {
        val signature = signature()
        val typeParameters = signature.typeParameters.joinToString(separator = ", ")

        val parameterDeclarations =
            if (signature.ports.isEmpty()) {
                ""
            } else {
                signature.ports.entries.joinToString(separator = ",\n", prefix = "\n", postfix = ",\n") { (portName, io) ->
                    "    `$portName`: $COM_GENOVICH_COMPONENTS_ACTION<${io.first}, ${io.second}>"
                }
            }

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

    /** [typeParameters] in declaration order (`Input, T1..Tn`); [ports] preserve first-use order. */
    data class Signature(
        val typeParameters: List<String>,
        val ports: Map<String, Pair<String, String>>,
        val outputType: String,
    )

    companion object {
        const val INVOKE_METHOD_NAME = "invoke"
        const val COM_GENOVICH_COMPONENTS_ACTION = "com.genovich.components.Action"
        const val INPUT_TYPE = "Input"
        const val NOTHING_TYPE = "Nothing"
        const val ASSEMBLY_SUFFIX = "Assembly"

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