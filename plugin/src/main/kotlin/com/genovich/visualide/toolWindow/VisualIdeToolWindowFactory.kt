@file:OptIn(ExperimentalUuidApi::class)

package com.genovich.visualide.toolWindow

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.genovich.visualide.actions.ActionDefinition
import com.genovich.visualide.ui.App
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.command.WriteCommandAction
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
import com.intellij.util.messages.impl.subscribeAsFlow
import kotlinx.coroutines.flow.map
import org.jetbrains.jewel.bridge.addComposeTab
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.uast.UFile
import org.jetbrains.uast.toUElementOfType
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
                        .mapNotNull { ActionDefinition.parse(it) }
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
