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
 * Rung 2 (design doc §6) — assembly file generation, the wiring half of H3.
 *
 * `ActionDefinition.generateAssembly()` produces the dependency-plane factory for `GuessLoop`:
 * a function taking every leaf port as a parameter and constructing the `GuessLoop` class
 * (design.md §3.2). `readGuess` is a T-function (rung 2 step 2, design.md §1.6/§5.1), so it's
 * defaulted to `Show(uiStateFlow.readGuessFlow)` rather than required; `checkGuess` stays a plain
 * required port. [testAssemblyTypeChecks] proves the three generated files — the function class,
 * its assembly, and its state projection — type-check together, the same way
 * [GuessLoopRoundTripTest] proves the function file alone type-checks (H2).
 */
@OptIn(ExperimentalUuidApi::class)
class GuessLoopAssemblyTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    // Attach the real kotlin-stdlib.jar, same as GuessLoopRoundTripTest.
    override fun getProjectDescriptor(): LightProjectDescriptor = STDLIB_DESCRIPTOR

    private fun guessLoopDefinition() = ActionDefinition(
        name = "GuessLoop",
        body = RepeatWhileActive(
            Passing(listOf(Action("readGuess", isTFunction = true), Action("checkGuess"))),
        ),
    )

    fun testGeneratesTypedFactory() {
        val code = guessLoopDefinition().generateAssembly()

        assertThat(code)
            .describedAs("declares the assembly function with threaded type parameters")
            .contains("fun <Input, T1, T2> `GuessLoopAssembly`")
        assertThat(code)
            .describedAs("takes a defaulted uiStateFlow parameter, typed to the T-function ports it covers")
            .contains("`uiStateFlow`: `GuessLoopUiStateFlow`<Input, T1> = `GuessLoopUiStateFlow`()")
        assertThat(code)
            .describedAs("defaults the readGuess T-function port to Show(uiStateFlow.readGuessFlow)")
            .contains("`readGuess`: com.genovich.components.Action<Input, T1> = com.genovich.components.Show(`uiStateFlow`.`readGuessFlow`)")
        assertThat(code)
            .describedAs("takes checkGuess as a required (non-defaulted) parameter")
            .contains("`checkGuess`: com.genovich.components.Action<T1, T2>")
        assertThat(code)
            .describedAs("checkGuess has no default — it's not a T-function")
            .doesNotContain("`checkGuess`: com.genovich.components.Action<T1, T2> =")
        assertThat(code)
            .describedAs("returns the GuessLoop type")
            .contains(": `GuessLoop`<Input, T1, T2>")
        assertThat(code).describedAs("constructs GuessLoop").contains("`GuessLoop`(")
        assertThat(code).describedAs("wires readGuess").contains("`readGuess` = `readGuess`")
        assertThat(code).describedAs("wires checkGuess").contains("`checkGuess` = `checkGuess`")
    }

    fun testAssemblyTypeChecks() {
        myFixture.copyFileToProject("specimen/Components.kt", "com/genovich/components/Components.kt")

        val definition = guessLoopDefinition()

        // The assembly references both the GuessLoop class and its UiStateFlow projection; add
        // both as dependencies, but check the assembly file itself (configureByText makes it the
        // file checkHighlighting inspects).
        myFixture.addFileToProject("GuessLoop.kt", "package specimen\n\n${definition.generate()}\n")
        myFixture.addFileToProject(
            "GuessLoopUiStateFlow.kt",
            "package specimen\n\n${checkNotNull(definition.generateUiStateFlow())}\n",
        )
        myFixture.configureByText("GuessLoopAssembly.kt", "package specimen\n\n${definition.generateAssembly()}\n")
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
