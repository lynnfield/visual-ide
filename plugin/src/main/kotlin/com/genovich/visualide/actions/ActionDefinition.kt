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

    @OptIn(ExperimentalUuidApi::class)
    fun generate(): String {
        val actions =
            body.value
                ?.filterIsInstance<Action>()
                ?.distinctBy { it.name.value }
                ?.joinToString(separator = "", prefix = "\n") {
                    "val `${it.name.value}`: $COM_GENOVICH_COMPONENTS_ACTION<Input, Output>,\n"
                }
                .orEmpty()

        val input = "input"
        return """
            class `${name.value}`<Input, Output>($actions) : $COM_GENOVICH_COMPONENTS_ACTION<Input, Output>() {
                override suspend operator fun $INVOKE_METHOD_NAME($input: Input): Output =
                    ${(body.value?.generate(input) ?: TodoStub.generate())}
            }
        """.trimIndent()
    }

    companion object {
        const val INVOKE_METHOD_NAME = "invoke"
        const val COM_GENOVICH_COMPONENTS_ACTION = "com.genovich.components.Action"

        fun parse(uClass: UClass) =
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