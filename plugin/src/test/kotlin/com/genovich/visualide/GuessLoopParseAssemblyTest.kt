package com.genovich.visualide

import androidx.compose.runtime.mutableStateOf
import com.genovich.visualide.actions.Action
import com.genovich.visualide.actions.ActionDefinition
import com.genovich.visualide.actions.Passing
import com.genovich.visualide.actions.RepeatWhileActive
import com.genovich.visualide.actions.Show
import com.genovich.visualide.analysis.KotlinAnalysis
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
import org.jetbrains.uast.UFile
import org.jetbrains.uast.toUElementOfType
import kotlin.uuid.ExperimentalUuidApi

/**
 * `parseAssembly` (design.md §2.8) — the dependency-plane round-trip: recovers
 * [ActionDefinition.portDefaults] from a generated `<Name>Assembly.kt`, since the function file's
 * body tree stays 100% unaware of T-functions (`docs/example-rung2.md`'s "Limitations", finally
 * closed here). Mirrors exactly what [ActionDefinition.generateAssembly] emits — a required
 * parameter vs. one defaulted to `Show(uiStateFlow.<port>Flow)` — not the fuller dependency-plane
 * grammar (child-assembly wiring, decorators, shared singletons) that generator doesn't produce
 * yet either.
 */
@OptIn(ExperimentalUuidApi::class)
class GuessLoopParseAssemblyTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    override fun getProjectDescriptor(): LightProjectDescriptor = STDLIB_DESCRIPTOR

    private fun guessLoopDefinition(portDefaults: Map<String, ActionDefinition.PortDefault>) =
        ActionDefinition(
            name = "GuessLoop",
            body = RepeatWhileActive(
                Passing(listOf(Action("readGuess"), Action("checkGuess"))),
            ),
            portDefaults = portDefaults,
        )

    fun testRecoversShowDefaultedPort() {
        myFixture.copyFileToProject("specimen/Components.kt", "com/genovich/components/Components.kt")

        val original = guessLoopDefinition(mapOf("readGuess" to Show))
        val bodyOnlyDefinition = guessLoopDefinition(emptyMap())
        val functionInfo = parseAssemblyFunction(original)

        val recovered = ActionDefinition.parseAssembly(functionInfo, bodyOnlyDefinition)

        assertThat(recovered).describedAs("should recognize its own assembly").isNotNull()
        assertThat(recovered!!.portDefaults.value)
            .describedAs("readGuess should be recovered as a T-function; checkGuess stays required")
            .isEqualTo(mapOf("readGuess" to Show))
    }

    fun testNoTFunctionPortsRoundTrips() {
        myFixture.copyFileToProject("specimen/Components.kt", "com/genovich/components/Components.kt")

        val original = guessLoopDefinition(emptyMap())
        val functionInfo = parseAssemblyFunction(original)

        val recovered = ActionDefinition.parseAssembly(functionInfo, original)

        assertThat(recovered).describedAs("should recognize its own assembly").isNotNull()
        assertThat(recovered!!.portDefaults.value).isEmpty()
    }

    fun testRejectsAssemblyOfADifferentDefinition() {
        myFixture.copyFileToProject("specimen/Components.kt", "com/genovich/components/Components.kt")

        val original = guessLoopDefinition(mapOf("readGuess" to Show))
        val functionInfo = parseAssemblyFunction(original)
        val unrelatedDefinition = guessLoopDefinition(emptyMap()).copy(name = mutableStateOf("SomethingElse"))

        assertThat(ActionDefinition.parseAssembly(functionInfo, unrelatedDefinition))
            .describedAs("a function named `GuessLoopAssembly` isn't `SomethingElseAssembly`'s own assembly")
            .isNull()
    }

    /**
     * Adds [original]'s own class file (the constructor call `<Name>Assembly`'s body needs to
     * resolve against) plus its assembly file to the fixture, and returns the assembly's
     * top-level function converted to the IR.
     */
    private fun parseAssemblyFunction(original: ActionDefinition) = run {
        myFixture.addFileToProject("GuessLoop.kt", "package specimen\n\n${original.generate()}\n")
        val psiFile = myFixture.addFileToProject(
            "GuessLoopAssembly.kt",
            "package specimen\n\n${original.generateAssembly()}\n",
        )
        runBlocking {
            smartReadAction(project) {
                checkNotNull(
                    psiFile.toUElementOfType<UFile>()
                        ?.classes
                        ?.firstOrNull()
                        ?.methods
                        ?.firstOrNull()
                        ?.let { KotlinAnalysis.parseFunction(it) },
                ) { "could not find the assembly's top-level function" }
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
