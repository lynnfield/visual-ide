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
 * Rung 2 / Step 3 (design doc §6, `docs/implementation-plan.md` Step 3) — H4: a normal form whose
 * checksum is stable across regenerate cycles, plus drift detection.
 *
 * `ActionDefinition.generate()` now emits `@com.genovich.components.Node` (face, D8/D9) and
 * `@com.genovich.components.Diagram(version, checksum, layout)` (identity, design.md §2.4/§2.7)
 * above the class. The checksum is a SHA-256 of the normalized body text. `ActionDefinition.parse`
 * recomputes it from the parsed-and-regenerated body and compares against the source's stored
 * value, flagging [ActionDefinition.isDrifted] when the class body was hand-edited out of band.
 */
@OptIn(ExperimentalUuidApi::class)
class GuessLoopChecksumTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    // Attach the real kotlin-stdlib.jar, same as GuessLoopRoundTripTest.
    override fun getProjectDescriptor(): LightProjectDescriptor = STDLIB_DESCRIPTOR

    private fun guessLoopDefinition() = ActionDefinition(
        name = "GuessLoop",
        body = RepeatWhileActive(
            Passing(listOf(Action("readGuess"), Action("checkGuess"))),
        ),
    )

    fun testGeneratesNodeAndDiagramAnnotations() {
        val code = guessLoopDefinition().generate()

        assertThat(code).describedAs("every node carries @Node").contains("@com.genovich.components.Node")
        assertThat(code)
            .describedAs("a composite body carries @Diagram with a checksum")
            .contains("@com.genovich.components.Diagram(version = 1, checksum = \"sha256:")
    }

    fun testChecksumStableAcrossRegenerateCycles() {
        myFixture.copyFileToProject("specimen/Components.kt", "com/genovich/components/Components.kt")

        val original = guessLoopDefinition()
        val code1 = fileFor(original.generate())
        val reparsed = checkNotNull(parseDefinition(code1, "GuessLoop1.kt"))
        val code2 = fileFor(reparsed.generate())

        assertThat(checksumOf(code2))
            .describedAs("the checksum is a stable function of the normalized body across a regenerate cycle")
            .isEqualTo(checksumOf(code1))
        assertThat(reparsed.isDrifted)
            .describedAs("unmodified round-trip: the recomputed checksum matches the one stored in @Diagram")
            .isFalse()
    }

    fun testHandEditedBodyIsDetectedAsDrifted() {
        myFixture.copyFileToProject("specimen/Components.kt", "com/genovich/components/Components.kt")

        val code1 = fileFor(guessLoopDefinition().generate())
        // Simulate an out-of-band hand-edit: swap the pipeline order in the body text while
        // leaving the @Diagram checksum (still the original) untouched. Swap via a marker string
        // that won't otherwise occur in the generated code, so unrelated text is untouched.
        val marker = "@@SWAP@@"
        val tampered = code1
            .replace("`readGuess`(it)", marker)
            .replace("`checkGuess`(it)", "`readGuess`(it)")
            .replace(marker, "`checkGuess`(it)")

        val reparsed = checkNotNull(parseDefinition(tampered, "GuessLoopTampered.kt"))

        assertThat(reparsed.isDrifted)
            .describedAs("the body no longer matches the checksum recorded in @Diagram")
            .isTrue()
    }

    private fun checksumOf(code: String): String =
        Regex("checksum = \"(sha256:[0-9a-f]+)\"").find(code)?.groupValues?.get(1)
            ?: error("no checksum found in generated code:\n$code")

    private fun fileFor(generatedClass: String): String = "package specimen\n\n$generatedClass\n"

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
