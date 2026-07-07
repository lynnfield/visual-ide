package com.genovich.visualide

import com.genovich.visualide.actions.Action
import com.genovich.visualide.actions.ActionDefinition
import com.genovich.visualide.actions.Passing
import com.genovich.visualide.actions.RepeatWhileActive
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.assertj.core.api.Assertions.assertThat
import kotlin.uuid.ExperimentalUuidApi

/**
 * Rung 2 step 2 (design doc §6, `docs/implementation-plan.md` Step 2) — the state-projection half
 * of H3, plus H7 (a `Show`-bound port derives a `UiStateFlow` leaf).
 *
 * `ActionDefinition.generateUiStateFlow()` produces `GuessLoopUiStateFlow`: one
 * `MutableStateFlow<UiState<in, out>?>` per T-function port, `combine`d into a sealed "which
 * screen is live" `Screen` with one case per T-function. `null` when there are no T-function
 * ports — nothing to project. [testUiStateFlowTypeChecks] proves the generated class type-checks,
 * the same way [GuessLoopAssemblyTest.testAssemblyTypeChecks] proves the assembly does.
 */
@OptIn(ExperimentalUuidApi::class)
class GuessLoopUiStateFlowTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    // Attach the real kotlin-stdlib.jar, same as GuessLoopRoundTripTest.
    override fun getProjectDescriptor(): LightProjectDescriptor = STDLIB_DESCRIPTOR

    private fun guessLoopDefinition() = ActionDefinition(
        name = "GuessLoop",
        body = RepeatWhileActive(
            Passing(listOf(Action("readGuess", isTFunction = true), Action("checkGuess"))),
        ),
    )

    fun testGeneratesUiStateFlow() {
        val code = checkNotNull(guessLoopDefinition().generateUiStateFlow())

        assertThat(code)
            .describedAs("declares the projection class, typed only to the ports it covers")
            .contains("class `GuessLoopUiStateFlow`<Input, T1>")
        assertThat(code)
            .describedAs("one MutableStateFlow per T-function port")
            .contains(
                "val `readGuessFlow`: com.genovich.components.MutableStateFlow<com.genovich.components.UiState<Input, T1>?>",
            )
        assertThat(code)
            .describedAs("no flow for checkGuess — it isn't a T-function")
            .doesNotContain("checkGuessFlow")
        assertThat(code)
            .describedAs("sealed Screen has one case per T-function")
            .contains("sealed interface Screen<Input, T1>")
            .contains(
                "data class `ReadGuess`<Input, T1>(val state: com.genovich.components.UiState<Input, T1>) : Screen<Input, T1>",
            )
        assertThat(code)
            .describedAs("combines every T-function's flow into the screen projection")
            .contains("com.genovich.components.combine(")
            .contains("com.genovich.components.emitSelfWhenHaveValue(`readGuessFlow`) { Screen.`ReadGuess`(it) }")
    }

    fun testNoTFunctionPortsProducesNoProjection() {
        val definition = ActionDefinition(
            name = "GuessLoop",
            body = RepeatWhileActive(
                Passing(listOf(Action("readGuess"), Action("checkGuess"))),
            ),
        )

        assertThat(definition.generateUiStateFlow())
            .describedAs("nothing to project when no port is a T-function")
            .isNull()
    }

    fun testUiStateFlowTypeChecks() {
        myFixture.copyFileToProject("specimen/Components.kt", "com/genovich/components/Components.kt")

        val code = checkNotNull(guessLoopDefinition().generateUiStateFlow())
        myFixture.configureByText("GuessLoopUiStateFlow.kt", "package specimen\n\n$code\n")
        myFixture.checkHighlighting(false, false, false)
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
