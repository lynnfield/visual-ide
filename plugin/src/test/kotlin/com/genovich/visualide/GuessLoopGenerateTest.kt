package com.genovich.visualide

import com.genovich.visualide.actions.Action
import com.genovich.visualide.actions.ActionDefinition
import com.genovich.visualide.actions.Passing
import com.genovich.visualide.actions.RepeatWhileActive
import org.junit.Assert.assertTrue
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
        val definition = ActionDefinition(
            name = "GuessLoop",
            body = RepeatWhileActive(
                Passing(listOf(Action("readGuess"), Action("checkGuess"))),
            ),
        )

        val code = definition.generate()

        assertTrue("declares the class", code.contains("class `GuessLoop`<Input, Output>"))
        assertTrue(
            "derives the readGuess port",
            code.contains("val `readGuess`: com.genovich.components.Action<Input, Output>"),
        )
        assertTrue(
            "derives the checkGuess port",
            code.contains("val `checkGuess`: com.genovich.components.Action<Input, Output>"),
        )
        assertTrue("emits the loop", code.contains("com.genovich.components.repeatWhileActive"))
        assertTrue("pipes readGuess", code.contains(".let { `readGuess`(it) }"))
        assertTrue("pipes checkGuess", code.contains(".let { `checkGuess`(it) }"))
    }
}
