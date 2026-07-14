package com.genovich.visualide

import com.genovich.visualide.actions.Action
import com.genovich.visualide.actions.ActionDefinition
import com.genovich.visualide.actions.Passing
import com.genovich.visualide.actions.RepeatWhileActive
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
 * Rung 0 of the guess-the-number end-to-end example (design doc §6).
 *
 * Specimen `GuessLoop` — a loop over a two-step pipeline of leaf port calls,
 * `RepeatWhileActive(Passing[readGuess, checkGuess])`, using only node types the engine already
 * implements. Generated form (canonical, fully qualified):
 *
 *     class `GuessLoop`<Input, Output>(
 *         val `readGuess`: com.genovich.components.Action<Input, Output>,
 *         val `checkGuess`: com.genovich.components.Action<Input, Output>,
 *     ) : com.genovich.components.Action<Input, Output>() {
 *         override suspend operator fun invoke(input: Input): Output =
 *             com.genovich.components.repeatWhileActive {
 *                 input
 *                 .let { `readGuess`(it) }
 *                 .let { `checkGuess`(it) }
 *             }
 *     }
 *
 * Validates H1 (`generate ∘ parse ∘ generate` is a fixed point) and, after rung 1, H2 (the typed
 * generation type-checks — see [testGuessLoopTypeChecks]). Per-port types are threaded
 * structurally: `GuessLoop<Input, T1, T2>`, ports `Action<Input, T1>` / `Action<T1, T2>`, output
 * `Nothing`.
 */
@OptIn(ExperimentalUuidApi::class)
class GuessLoopRoundTripTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    // Attach the real kotlin-stdlib.jar so `.let` resolves the same way it does in a real project
    // (BasePlatformTestCase's default light project has no JDK/Kotlin runtime attached at all).
    override fun getProjectDescriptor(): LightProjectDescriptor = STDLIB_DESCRIPTOR

    fun testGuessLoopRoundTrips() {
        // Make `com.genovich.components` resolvable for UAST (parsers match on resolved FqNames).
        myFixture.copyFileToProject("specimen/Components.kt", "com/genovich/components/Components.kt")

        // The diagram model for GuessLoop, built directly.
        val original = ActionDefinition(
            name = "GuessLoop",
            body = RepeatWhileActive(
                Passing(listOf(Action("readGuess"), Action("checkGuess"))),
            ),
        )

        val code1 = fileFor(original.generate())
        val reparsed = parseDefinition(code1, "GuessLoop1.kt")
        assertThat(reparsed).describedAs("GuessLoop should parse back into a definition").isNotNull()

        // H1: regenerating from the reparsed model reproduces the same code byte-for-byte.
        val code2 = fileFor(reparsed!!.generate())
        assertThat(code2).describedAs("generate ∘ parse ∘ generate must be a fixed point").isEqualTo(code1)

        // Structural sanity: the recovered body is Loop(Passing[readGuess, checkGuess]).
        val loop = reparsed.body.value as? RepeatWhileActive
        assertThat(loop).describedAs("top-level body should be a loop").isNotNull()
        val passing = loop!!.body.value as? Passing
        assertThat(passing).describedAs("loop body should be a passing pipeline").isNotNull()
        assertThat(passing!!.body.filterIsInstance<Action>().map { it.name.value })
            .isEqualTo(listOf("readGuess", "checkGuess"))
    }

    fun testGuessLoopTypeChecks() {
        // H2: typed generation must produce code that actually type-checks (no error highlights),
        // not merely parse. Rung 0's shared <Input, Output> would fail here (Output fed where Input
        // is expected); the threaded per-port types fix it.
        myFixture.copyFileToProject("specimen/Components.kt", "com/genovich/components/Components.kt")

        val definition = ActionDefinition(
            name = "GuessLoop",
            body = RepeatWhileActive(
                Passing(listOf(Action("readGuess"), Action("checkGuess"))),
            ),
        )

        myFixture.configureByText("GuessLoop.kt", "package specimen\n\n${definition.generate()}\n")
        // errors only (no warnings/infos); fails listing any type error.
        myFixture.checkHighlighting(false, false, false)
    }

    private fun fileFor(generatedClass: String): String =
        "package specimen\n\n$generatedClass\n"

    private fun parseDefinition(source: String, fileName: String): ActionDefinition? {
        val psiFile = myFixture.addFileToProject(fileName, source)
        // Mirror the tool window: parse inside a smart read action.
        return runBlocking {
            smartReadAction(project) {
                psiFile.toUElementOfType<UFile>()
                    ?.classes
                    ?.firstOrNull()
                    ?.let { ActionDefinition.parse(KotlinAnalysis.parseClass(it)) }
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
