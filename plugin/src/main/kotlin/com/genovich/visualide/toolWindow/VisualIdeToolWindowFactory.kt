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
    val assembliesPackage = actionsPackage

    val actionPsiFile = psiElementFactory.createFile("${actionDefinition.name.value}.kt", "")
    actionPsiFile.add(psiElementFactory.createPackageDirective(actionsPackage))
    actionPsiFile.add(psiElementFactory.createNewLine())
    actionPsiFile.add(psiElementFactory.createClass(actionDefinition.generate()))

    val assemblyPsiFile =
        psiElementFactory.createFile("${actionDefinition.name.value}${ActionDefinition.ASSEMBLY_SUFFIX}.kt", "")
    assemblyPsiFile.add(psiElementFactory.createPackageDirective(assembliesPackage))
    assemblyPsiFile.add(psiElementFactory.createNewLine())
    assemblyPsiFile.add(psiElementFactory.createFunction(actionDefinition.generateAssembly()))

    // only present when the definition has at least one T-function port (design.md §3.3)
    val uiStateFlowPsiFile = actionDefinition.generateUiStateFlow()?.let { uiStateFlowCode ->
        psiElementFactory
            .createFile("${actionDefinition.name.value}${ActionDefinition.UI_STATE_FLOW_SUFFIX}.kt", "")
            .apply {
                add(psiElementFactory.createPackageDirective(assembliesPackage))
                add(psiElementFactory.createNewLine())
                add(psiElementFactory.createClass(uiStateFlowCode))
            }
    }

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

        // find and rewrite or create a new file, for the function, its assembly, and (if the
        // definition has any T-function ports) its state projection
        listOfNotNull(actionPsiFile, assemblyPsiFile, uiStateFlowPsiFile).forEach { psiFile ->
            targetDirectory.findFile(psiFile.name)?.delete()
            val file = targetDirectory.add(psiFile) as PsiFile

            // have to commit the document before reformatting
            PsiDocumentManager.getInstance(project).commitDocument(file.fileDocument)
            CodeStyleManager.getInstance(project).reformat(file)

            // 4. SHOW (Open in Editor) — only the function file, to keep the same UX as before
            // We use the virtualFile from the *created* file, not the in-memory one
            if (psiFile === actionPsiFile) {
                file.virtualFile?.let { vFile ->
                    FileEditorManager.getInstance(project).openFile(vFile, true)
                } ?: println("File not found $file")
            }
        }
    }
}
