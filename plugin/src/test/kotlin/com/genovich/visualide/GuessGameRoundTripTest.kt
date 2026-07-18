package com.genovich.visualide

import com.genovich.visualide.actions.Action
import com.genovich.visualide.actions.ActionDefinition
import com.genovich.visualide.actions.Branch
import com.genovich.visualide.actions.Passing
import com.genovich.visualide.actions.Ref
import com.genovich.visualide.actions.RepeatWhileActive
import com.genovich.visualide.actions.Show
import com.genovich.visualide.actions.Tuple
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
 * Rung 5 (design doc §6.2's "target") — the full guess-the-number game, completing the ladder:
 *
 *     askGuess  : Action<Secret, Guess>                    // T-function (Show)
 *     compare   : Action<Pair<Secret, Guess>, Comparison>   // leaf; input via a Tuple node
 *     showResult: Action<Comparison, Unit>                  // T-function (Show)
 *     // ask → compare(secret to guess) → branch on Comparison, each case shows the result
 *
 * Body: `RepeatWhileActive(Passing[askGuess, Tuple(Ref(input), Ref(step1)), compare, Branch[...]])`.
 * [Ref] recovers the original `secret` (the loop's own `input`) after `askGuess` has already
 * consumed it and produced `step1` (`guess`) — named SSA `val`s (rung 5 / H6) are exactly what
 * makes this possible: `step1` stays in lexical scope for the rest of the `run { }` block, unlike
 * the old anonymous `.let { it }` chain. [Branch] then dispatches on the sealed `Comparison`
 * (design.md §1.5/D2); every case is wired to the same `showResult` T-function port, exercising
 * the "repeated port name reuses the first inferred typing" caveat
 * (`docs/implementation-plan.md`'s cross-cutting risks) deliberately, not accidentally — see
 * `docs/example-rung6.md`.
 *
 * Loop exit is out of scope: `repeatWhileActive` (rung 0's primitive) has no break/return
 * mechanism in the current node catalog — design.md §1.4's `updateLoop(initial){ step }` "Loop"
 * operator (state-threading, naturally exitable) is a different, unimplemented primitive. This
 * specimen demonstrates Tuple/Ref/Branch's *structural* round-trip, not literal "stop asking once
 * correct" play behavior.
 */
@OptIn(ExperimentalUuidApi::class)
class GuessGameRoundTripTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    override fun getProjectDescriptor(): LightProjectDescriptor = STDLIB_DESCRIPTOR

    private fun guessGameDefinition() = ActionDefinition(
        name = "GuessGame",
        body = RepeatWhileActive(
            Passing(
                listOf(
                    Action("askGuess"),
                    Tuple(Ref("input"), Ref("step1")),
                    Action("compare"),
                    Branch(
                        listOf(
                            Branch.Case("specimen.TooLow", Action("showResult")),
                            Branch.Case("specimen.TooHigh", Action("showResult")),
                            Branch.Case("specimen.Correct", Action("showResult")),
                        ),
                    ),
                ),
            ),
        ),
        portDefaults = mapOf("askGuess" to Show, "showResult" to Show),
    )

    fun testGuessGameRoundTrips() {
        addFixtures()

        val original = guessGameDefinition()
        val code1 = fileFor(original.generate())
        val reparsed = parseDefinition(code1, "GuessGame1.kt")
        assertThat(reparsed).describedAs("GuessGame should parse back into a definition").isNotNull()

        // H1/H6: regenerating from the reparsed model reproduces the same code byte-for-byte, even
        // with named SSA vals, Tuple, and Branch in the mix.
        val code2 = fileFor(reparsed!!.generate())
        assertThat(code2).describedAs("generate ∘ parse ∘ generate must be a fixed point").isEqualTo(code1)

        // Structural sanity: the recovered body has the same shape (loop / passing / 4 steps).
        val loop = reparsed.body.value as? RepeatWhileActive
        val passing = loop?.body?.value as? Passing
        assertThat(passing).describedAs("loop body should be a passing pipeline").isNotNull()
        assertThat(passing!!.body).hasSize(4)
        assertThat(passing.body[0]).isInstanceOf(Action::class.java)
        assertThat(passing.body[1]).isInstanceOf(Tuple::class.java)
        assertThat(passing.body[2]).isInstanceOf(Action::class.java)
        val branch = passing.body[3] as? Branch
        assertThat(branch).describedAs("fourth step should be a Branch").isNotNull()
        assertThat(branch!!.cases.map { it.caseTypeQualifiedName.value })
            .isEqualTo(listOf("specimen.TooLow", "specimen.TooHigh", "specimen.Correct"))
    }

    fun testGuessGameTypeChecks() {
        addFixtures()

        val definition = guessGameDefinition()
        myFixture.configureByText("GuessGame.kt", fileFor(definition.generate()))
        myFixture.checkHighlighting(false, false, false)
    }

    fun testGuessGameAssemblyAndUiStateFlowTypeCheck() {
        addFixtures()

        val definition = guessGameDefinition()
        myFixture.addFileToProject("GuessGame.kt", fileFor(definition.generate()))
        myFixture.addFileToProject(
            "GuessGameUiStateFlow.kt",
            fileFor(checkNotNull(definition.generateUiStateFlow())),
        )
        myFixture.configureByText("GuessGameAssembly.kt", fileFor(definition.generateAssembly()))
        myFixture.checkHighlighting(false, false, false)
    }

    private fun addFixtures() {
        myFixture.copyFileToProject("specimen/Components.kt", "com/genovich/components/Components.kt")
        myFixture.copyFileToProject("specimen/Comparison.kt", "specimen/Comparison.kt")
    }

    private fun fileFor(generatedClass: String): String = "package specimen\n\n$generatedClass\n"

    private fun parseDefinition(source: String, fileName: String): ActionDefinition? {
        val psiFile = myFixture.addFileToProject(fileName, source)
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
