package guessgame

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import uihost.Request
import uihost.UiHost
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A non-UI client implementing the same generated screens: a bot that plays by binary search.
 * It shows that "the UI" is just whoever answers the requests.
 */
class BinarySearchBot : GuessGameScreens<() -> Unit> {
    private var low = 1
    private var high = 100
    private var lastGuess = 0
    var guesses = 0
    var won = false

    override fun guessing(askGuess: Request<Unit, Int>, giveUp: Request<Unit, Unit>) = {
        lastGuess = (low + high) / 2
        guesses++
        askGuess.answer(lastGuess)
    }

    override fun result(showResult: Request<Outcome, Unit>) = {
        when (showResult.input) {
            TooLow -> low = lastGuess + 1
            TooHigh -> high = lastGuess - 1
            Correct -> won = true
            is GaveUp -> Unit
        }
        showResult.answer(Unit)
    }
}

class BotClientTest {
    @Test
    fun `bot finds any secret in at most 7 guesses`() = runTest {
        for (secret in 1..100) {
            val host = UiHost(GuessGameScope)
            val bot = BinarySearchBot()
            val game = launch { GuessGameAssembly(host)(secret) }
            while (!bot.won) {
                host.live.first { bot.render(it) != null }.let { bot.render(it)!!() }
            }
            game.cancel()
            assertTrue(bot.guesses <= 7, "secret $secret took ${bot.guesses} guesses")
        }
    }
}
