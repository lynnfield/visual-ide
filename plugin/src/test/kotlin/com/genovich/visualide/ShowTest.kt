package com.genovich.visualide

import com.genovich.visualide.actions.Action
import com.genovich.visualide.actions.ActionDefinition
import com.genovich.visualide.actions.Passing
import com.genovich.visualide.actions.RepeatWhileActive
import com.genovich.visualide.actions.Show
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UFile
import org.jetbrains.uast.toUElementOfType
import kotlin.uuid.ExperimentalUuidApi

/**
 * [Show] recognizes `com.genovich.components.Show(flow)` — the assembly-plane expression that
 * marks a port as a T-function (design.md §1.6, §5.1; `docs/example-rung3.md`). Scaffolding for a
 * future `parseAssembly` (see [Show]'s KDoc) — exercised here against the real text
 * `ActionDefinition.generateAssembly()` produces for the `GuessLoop` specimen, not a hand-written
 * stand-in.
 */
@OptIn(ExperimentalUuidApi::class)
class ShowTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    // Attach the real kotlin-stdlib.jar, same as GuessLoopRoundTripTest.
    override fun getProjectDescriptor(): LightProjectDescriptor = STDLIB_DESCRIPTOR

    private fun guessLoopAssemblyCode() = ActionDefinition(
        name = "GuessLoop",
        body = RepeatWhileActive(
            Passing(listOf(Action("readGuess"), Action("checkGuess"))),
        ),
        portDefaults = mapOf("readGuess" to Show),
    ).generateAssembly()

    fun testRecognizesGeneratedShowDefault() {
        myFixture.copyFileToProject("specimen/Components.kt", "com/genovich/components/Components.kt")

        val readGuessDefault = parameterDefaultValue(
            source = "package specimen\n\n${guessLoopAssemblyCode()}\n",
            fileName = "GuessLoopAssembly.kt",
            parameterName = "readGuess",
        )

        assertThat(Show.parse(readGuessDefault).isSuccess)
            .describedAs("Show(...) should be recognized")
            .isTrue()
    }

    fun testRejectsNonShowDefault() {
        myFixture.copyFileToProject("specimen/Components.kt", "com/genovich/components/Components.kt")

        val uiStateFlowDefault = parameterDefaultValue(
            source = "package specimen\n\n${guessLoopAssemblyCode()}\n",
            fileName = "GuessLoopAssembly2.kt",
            parameterName = "uiStateFlow",
        )

        assertThat(Show.parse(uiStateFlowDefault).isFailure)
            .describedAs("a non-Show default value should not be recognized")
            .isTrue()
    }

    private fun parameterDefaultValue(source: String, fileName: String, parameterName: String): UExpression {
        val psiFile = myFixture.addFileToProject(fileName, source)
        return runBlocking {
            smartReadAction(project) {
                // Kotlin top-level functions surface via UAST as methods of a synthetic
                // "<FileName>Kt" facade class, not directly off UFile (which only exposes real
                // classes) — mirrors how ActionDefinition.parse reaches into a real class's method.
                checkNotNull(
                    psiFile.toUElementOfType<UFile>()
                        ?.classes
                        ?.firstOrNull()
                        ?.methods
                        ?.firstOrNull()
                        ?.uastParameters
                        ?.firstOrNull { it.name == parameterName }
                        ?.uastInitializer,
                ) { "could not find default value for parameter `$parameterName`" }
            }
        }
    }

    companion object {
        private val STDLIB_DESCRIPTOR = object : LightProjectDescriptor() {
            override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
                val stdlibJar = checkNotNull(PathManager.getJarPathForClass(Unit::class.java)) {
                    "could not locate kotlin-stdlib.jar on the runtime classpath"
                }
                PsiTestUtil.addLibrary(module, stdlibJar)
            }
        }
    }
}
