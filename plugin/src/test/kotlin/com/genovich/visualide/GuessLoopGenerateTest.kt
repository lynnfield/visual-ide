package com.genovich.visualide

import com.genovich.visualide.actions.Action
import com.genovich.visualide.actions.ActionDefinition
import com.genovich.visualide.actions.Passing
import com.genovich.visualide.actions.RepeatWhileActive
import com.genovich.visualide.actions.TFunction
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import kotlin.uuid.ExperimentalUuidApi

/**
 * Rung 0 (design doc §6) — lightweight check of the *generate* half, without an IDE fixture.
 *
 * Builds the GuessLoop diagram model in memory and asserts the generated Kotlin has the expected
 * shape: a class with auto-derived dependency ports (§4.2), the loop, and the two piped leaf
 * calls. Whitespace-tolerant on purpose; exact byte-stability is asserted by the round-trip test.
 */
@OptIn(ExperimentalUuidApi::class)
class GuessLoopGenerateTest {

    @Test
    fun generatesLoopOverPipeline() {
        // readGuess is a T-function (rung 2 step 2) — invisible here on purpose: it generates the
        // exact same shape as Action, never the function file (design.md §5.1).
        val definition = ActionDefinition(
            name = "GuessLoop",
            body = RepeatWhileActive(
                Passing(listOf(TFunction("readGuess"), Action("checkGuess"))),
            ),
        )

        val code = definition.generate()

        assertThat(code)
            .describedAs("declares threaded type parameters")
            .contains("class `GuessLoop`<Input, T1, T2>")
        assertThat(code)
            .describedAs("types the readGuess port")
            .contains("val `readGuess`: com.genovich.components.Action<Input, T1>")
        assertThat(code)
            .describedAs("types the checkGuess port")
            .contains("val `checkGuess`: com.genovich.components.Action<T1, T2>")
        assertThat(code)
            .describedAs("the loop's output type is Nothing")
            .contains(": com.genovich.components.Action<Input, Nothing>")
        assertThat(code).describedAs("emits the loop").contains(RepeatWhileActive.REPEAT_WHILE_ACTIVE_FQN)
        assertThat(code).describedAs("pipes readGuess").contains(".let { `readGuess`(it) }")
        assertThat(code).describedAs("pipes checkGuess").contains(".let { `checkGuess`(it) }")
    }
}
