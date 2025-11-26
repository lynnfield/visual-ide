package com.genovich.visualide.toolWindow

import com.genovich.visualide.ui.ActionDefinition
import com.genovich.visualide.ui.ActionLayout
import com.genovich.visualide.ui.App
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.findDirectory
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.jewel.bridge.addComposeTab
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtPsiFactory
import kotlin.uuid.ExperimentalUuidApi

class VisualIdeToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow
    ) {
        toolWindow.addComposeTab("Visual IDE") {
            App(
                onSave = {
                    save(it, project)
                },
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
    psiFile.add(actionDefinition.generate(psiElementFactory))

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

@OptIn(ExperimentalUuidApi::class)
fun ActionDefinition.generate(psiElementFactory: KtPsiFactory): PsiElement {
    // todo imports
    // todo return???
    // todo propagate used Action names up to the top level
    // todo inputs in sequential calls
    return psiElementFactory.createClass(
        """
            // $id
            class `${name.value}`<Input, Output> : com.genovich.components.Action<Input, Output>() {
                override suspend fun invoke(input: Input): Output {
                    ${(body.value?.generate(psiElementFactory) ?: todoStub(psiElementFactory)).text}
                }
            }
        """.trimIndent()
    )
}

fun ActionLayout.Action.generate(psiElementFactory: KtPsiFactory): PsiElement {
    return psiElementFactory.createExpression("`${name.value}`()")
}

fun ActionLayout.RetryUntilResult.generate(psiElementFactory: KtPsiFactory): PsiElement {
    return psiElementFactory.createExpression(
        """
            retryUntilResult {
                ${body.value?.generate(psiElementFactory) ?: todoStub(psiElementFactory)}
            }
        """.trimIndent()
    )
}

fun ActionLayout.RepeatWhileActive.generate(psiElementFactory: KtPsiFactory): PsiElement {
    return psiElementFactory.createExpression(
        """
            repeatWhileActive {
                ${body.value?.generate(psiElementFactory) ?: todoStub(psiElementFactory)}
            }
        """.trimIndent()
    )
}

fun ActionLayout.Sequential.generate(psiElementFactory: KtPsiFactory): PsiElement {
    return body
        .takeIf { it.isNotEmpty() }
        ?.fold(psiElementFactory.createBlockCodeFragment("", null) as PsiElement) { acc, action ->
            acc.add(action.generate(psiElementFactory))
        }
        ?: todoStub(psiElementFactory)
}

fun ActionLayout.generate(psiElementFactory: KtPsiFactory): PsiElement {
    return when (this) {
        is ActionLayout.Action -> generate(psiElementFactory)
        is ActionLayout.RepeatWhileActive -> generate(psiElementFactory)
        is ActionLayout.RetryUntilResult -> generate(psiElementFactory)
        is ActionLayout.Sequential -> generate(psiElementFactory)
    }
}

fun todoStub(psiElementFactory: KtPsiFactory): PsiElement =
    psiElementFactory.createExpression("TODO(\"implement body\")")
