@file:OptIn(ExperimentalUuidApi::class)

package com.genovich.visualide.toolWindow

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.genovich.visualide.ui.ActionDefinition
import com.genovich.visualide.ui.ActionLayout
import com.genovich.visualide.ui.App
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.findDirectory
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.util.asSafely
import com.intellij.util.messages.impl.subscribeAsFlow
import kotlinx.coroutines.flow.map
import org.jetbrains.jewel.bridge.addComposeTab
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.utils.findIsInstanceAnd
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UFile
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.tryResolveNamed
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class VisualIdeToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow
    ) {
        val fileEditorManagerEvents =
            project.messageBus.subscribeAsFlow(FileEditorManagerListener.FILE_EDITOR_MANAGER) {
                object : FileEditorManagerListener {
                    override fun selectionChanged(event: FileEditorManagerEvent) {
                        trySend(event)
                    }
                }
            }

        toolWindow.addComposeTab("Visual IDE") {
            val currentFile by fileEditorManagerEvents
                .map { it.newFile }
                .collectAsState(FileEditorManager.getInstance(project).selectedFiles.firstOrNull())

            val actions = remember { mutableStateMapOf<Uuid, ActionDefinition>() }
            val currentActionId = remember(actions) { mutableStateOf(actions.keys.firstOrNull()) }

            LaunchedEffect(currentFile) {
                val definitions = smartReadAction(project) {
                    currentFile
                        ?.toPsiFile(project)
                        ?.toUElementOfType<UFile>()
                        ?.classes
                        .orEmpty()
                        .mapNotNull(UClass::asActionDefinition)
                }

                actions.clear()
                definitions.forEach { actions[it.id] = it }
                currentActionId.value = definitions.firstOrNull()?.id
            }


            App(
                actions = actions,
                currentActionId = currentActionId,
                onSave = { save(it, project) },
            )
        }
    }
}

private fun save(actionDefinition: ActionDefinition, project: Project) {
    val psiElementFactory = KtPsiFactory(project)

    // todo make configurable
    val dir = "src/main/kotlin"
    val actionsPackage = FqName("com.example")

    val fileName = "${actionDefinition.name.value}.kt"
    val psiFile = psiElementFactory.createFile(fileName, "")

    psiElementFactory.createPackageDirectiveIfNeeded(actionsPackage)
    psiFile.add(psiElementFactory.createNewLine())
    psiFile.add(psiElementFactory.createClass(actionDefinition.generate()))

    // 1. EXECUTE WRITE ACTION (Required for modifying the project)
    WriteCommandAction.runWriteCommandAction(project) {
        val targetVirtualFile = project.guessProjectDir()?.findDirectory(dir) ?: run {
            println("$dir not found")
            return@runWriteCommandAction
        }

        val targetDirectory =
            PsiManager.getInstance(project).findDirectory(targetVirtualFile)?.let {
                actionsPackage.pathSegments().fold(it) { dir, name ->
                    dir.findSubdirectory(name.identifier) ?: dir.createSubdirectory(name.identifier)
                }
            } ?: run {
                println("$targetVirtualFile not found")
                return@runWriteCommandAction
            }

        // find and rewrite or create a new file
        targetDirectory.findFile(psiFile.name)?.delete()
        val file = targetDirectory.add(psiFile) as PsiFile

        // have to commit the document before reformatting
        PsiDocumentManager.getInstance(project).commitDocument(file.fileDocument)
        CodeStyleManager.getInstance(project).reformat(file)

        // 4. SHOW (Open in Editor)
        // We use the virtualFile from the *created* file, not the in-memory one
        file.virtualFile?.let { vFile ->
            FileEditorManager.getInstance(project).openFile(vFile, true)
        } ?: println("File not found $file")
    }
}

private const val INVOKE_METHOD_NAME = "invoke"
private const val COM_GENOVICH_COMPONENTS_ACTION = "com.genovich.components.Action"

@OptIn(ExperimentalUuidApi::class)
fun ActionDefinition.generate(): String {
    // todo return???
    // todo inputs in sequential calls
    val actions =
        body.value
            ?.filterIsInstance<ActionLayout.Action>()
            ?.distinctBy { it.name.value }
            ?.joinToString(separator = "", prefix = "\n") {
                "val `${it.name.value}`: $COM_GENOVICH_COMPONENTS_ACTION<Input, Output>,\n"
            }
            .orEmpty()

    val input = "input"
    return """
        class `${name.value}`<Input, Output>($actions) : $COM_GENOVICH_COMPONENTS_ACTION<Input, Output>() {
            override suspend operator fun $INVOKE_METHOD_NAME($input: Input): Output =
                ${(body.value?.generate(input) ?: todoStub())}
        }
    """.trimIndent()
}

private fun UClass.asActionDefinition(): ActionDefinition? =
    takeIf {
        uastSuperTypes.any { it.getQualifiedName() == COM_GENOVICH_COMPONENTS_ACTION }
    }?.let {
        ActionDefinition(
            name = name ?: "Unknown",
            body = uastDeclarations
                .findIsInstanceAnd<UMethod> { it.name == INVOKE_METHOD_NAME }
                ?.uastBody
                ?.asSafely<UBlockExpression>()
                ?.expressions
                ?.firstIsInstanceOrNull<UReturnExpression>()
                ?.returnExpression
                ?.asActionLayout()
                ?.getOrLogException { it.printStackTrace() },
        )
    }

fun ActionLayout.Action.generate(input: String): String = "`${name.value}`($input)"

fun UCallExpression.asAction(): Result<ActionLayout.Action> = runCatching {
    ActionLayout.Action(checkNotNull(receiver?.tryResolveNamed()?.name) { "Missing action name" })
}

fun ActionLayout.RetryUntilResult.generate(input: String): String = """
    com.genovich.components.retryUntilResult {
        ${(body.value?.generate(input) ?: todoStub())}
    }
""".trimIndent()

fun UQualifiedReferenceExpression.asRetryUntilResult(): Result<ActionLayout.RetryUntilResult> =
    runCatching {
        ActionLayout.RetryUntilResult(
            ((((selector as UCallExpression)
                .valueArguments.single() as ULambdaExpression)
                .body as UBlockExpression)
                .expressions.single() as UReturnExpression)
                .returnExpression
                ?.asActionLayout()
                ?.getOrThrow()
        )
    }


fun ActionLayout.RepeatWhileActive.generate(input: String): String = """
    com.genovich.components.repeatWhileActive {
        ${(body.value?.generate(input) ?: todoStub())}
    }
""".trimIndent()

fun UQualifiedReferenceExpression.asRepeatWhileActive(): Result<ActionLayout.RepeatWhileActive> =
    runCatching {
        ActionLayout.RepeatWhileActive(
            ((((selector as UCallExpression)
                .valueArguments.single() as ULambdaExpression)
                .body as UBlockExpression)
                .expressions.single() as UReturnExpression)
                .returnExpression
                ?.asActionLayout()
                ?.getOrThrow()
        )
    }

fun ActionLayout.Sequential.generate(input: String): String = body
    .takeIf { it.isNotEmpty() }
    ?.fold(input) { exp, layout -> layout.generate(exp) }
    ?: todoStub()

fun ActionLayout.generate(inputParamName: String): String = when (this) {
    is ActionLayout.Action -> generate(inputParamName)
    is ActionLayout.RepeatWhileActive -> generate(inputParamName)
    is ActionLayout.RetryUntilResult -> generate(inputParamName)
    is ActionLayout.Sequential -> generate(inputParamName)
}

fun UExpression.asActionLayout(): Result<ActionLayout?> = runCatching {
    when (this) {
        is UCallExpression -> when (methodName) {
            "invoke" -> asAction().getOrThrow()
            "TODO" -> null
            else -> error("Unsupported call expression: ${this.asSourceString()}")
        }

        is UQualifiedReferenceExpression -> when (tryResolveNamed()?.kotlinFqName) {
            FqName("com.genovich.components.retryUntilResult") -> asRetryUntilResult().getOrThrow()
            FqName("com.genovich.components.repeatWhileActive") -> asRepeatWhileActive().getOrThrow()
            else -> error("Unsupported qualified reference: ${this.asSourceString()}")
        }

        else -> error("Unsupported expression type: ${this::class}")
    }
}.recoverCatching { throw Exception("while parsing ${asSourceString()}", it) }

fun todoStub(): String = """TODO("implement body")"""
