package com.genovich.visualide.toolWindow

import com.genovich.visualide.ui.ActionDefinition
import com.genovich.visualide.ui.ActionLayout
import com.genovich.visualide.ui.App
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.jewel.bridge.addComposeTab
import org.jetbrains.kotlin.idea.KotlinLanguage

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
    // todo make configurable
    val dir = "src"
    val actionsPackage = "com.example"

    val fileName = "${actionDefinition.name.value}.kt"
    val fileContent = """
        package $actionsPackage
        
        ${actionDefinition.generate()}
    """.trimIndent()

    // 1. EXECUTE WRITE ACTION (Required for modifying the project)
    WriteCommandAction.runWriteCommandAction(project) {
        val targetVirtualFile = project.guessProjectDir()?.findChild(dir) ?: run {
            println("$dir not found")
            return@runWriteCommandAction
        }
        val targetDirectory =
            PsiManager.getInstance(project).findDirectory(targetVirtualFile) ?: run {
                println("$targetVirtualFile not found")
                return@runWriteCommandAction
            }

        // find and rewrite or create a new file
        val file = targetDirectory.findFile(fileName)?.also { it.fileDocument.setText(fileContent) }
            ?: PsiFileFactory.getInstance(project).createFileFromText(
                fileName,
                KotlinLanguage.INSTANCE,
                fileContent
            ).let { targetDirectory.add(it) as PsiFile }

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

fun ActionDefinition.generate(): String {
    // todo name escaping if needed
    // todo imports
    // todo return???
    // todo propagate used Action names up to the top level
    // todo inputs in sequential calls
    return """
        class ${name.value}<Input, Output> : Action<Input, Output>() {
            override suspend fun invoke(input: Input): Output {
                ${body.value?.generate() ?: stubBody()}
            }
        }
    """.trimIndent()
}

fun ActionLayout.Action.generate(): String {
    return """
        ${name.value}()
    """.trimIndent()
}

fun ActionLayout.RetryUntilResult.generate(): String {
    return """
        retryUntilResult {
            ${body.value?.generate() ?: stubBody()}
        }
    """.trimIndent()
}

fun ActionLayout.RepeatWhileActive.generate(): String {
    return """
        repeatWhileActive {
            ${body.value?.generate() ?: stubBody()}
        }
    """.trimIndent()
}

fun ActionLayout.Sequential.generate(): String {
    return body.joinToString("\n") { it.generate() }
}

fun ActionLayout.generate(): String {
    return when (this) {
        is ActionLayout.Action -> generate()
        is ActionLayout.RepeatWhileActive -> generate()
        is ActionLayout.RetryUntilResult -> generate()
        is ActionLayout.Sequential -> generate()
    }
}

fun stubBody(): String = "TODO(\"implement body\")"
