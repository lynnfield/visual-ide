package guessgame.cli

import guessgame.Correct
import guessgame.GaveUp
import guessgame.GuessGameAssembly
import guessgame.GuessGameScreens
import guessgame.Outcome
import guessgame.TooHigh
import guessgame.TooLow
import guessgame.render
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import uihost.Request
import uihost.UiHost
import kotlin.random.Random
import kotlin.system.exitProcess

/** A console "UI": a prompt and what to do with the typed line. */
class Prompt(val text: String, val onLine: (String) -> Unit)

/** The console client's implementation of the generated screens. */
object ConsoleScreens : GuessGameScreens<Prompt> {
    override fun guessing(askGuess: Request<Unit, Int>, giveUp: Request<Unit, Unit>) =
        Prompt("Guess a number from 1 to 100, or type 'give up':") { line ->
            when (val text = line.trim()) {
                "give up" -> giveUp.answer(Unit)
                else -> text.toIntOrNull()?.let(askGuess::answer) ?: println("That's not a number.")
            }
        }

    override fun result(showResult: Request<Outcome, Unit>) =
        Prompt("${describe(showResult.input)} Press Enter to continue.") { showResult.answer(Unit) }

    private fun describe(outcome: Outcome) = when (outcome) {
        TooLow -> "Too low."
        TooHigh -> "Too high."
        Correct -> "Correct!"
        is GaveUp -> "The number was ${outcome.secret}."
    }
}

fun main(): Unit = runBlocking {
    val host = UiHost()
    launch { GuessGameAssembly(host)(Random.nextInt(1, 101)) }
    while (true) {
        val prompt = host.live.first { ConsoleScreens.render(it) != null }.let { ConsoleScreens.render(it)!! }
        println(prompt.text)
        val line = withContext(Dispatchers.IO) { readlnOrNull() } ?: exitProcess(0)
        prompt.onLine(line)
    }
}
