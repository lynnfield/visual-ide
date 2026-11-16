package guessgame

import guessgame.GuessGamePorts.askGuess
import guessgame.GuessGamePorts.giveUp
import guessgame.GuessGamePorts.showResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import uihost.Request
import uihost.UiHost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A scripted client: no UI at all, it answers requests on the host directly. */
class GuessGameHostTest {

    @Test
    fun `a guess is compared and the result shown`() = runTest {
        val host = UiHost()
        val game = launch { GuessGameAssembly(host)(42) }

        host.awaitRequest(askGuess).answer(10)
        assertEquals(TooLow, host.awaitRequest(showResult).input)
        host.awaitRequest(showResult).answer(Unit)

        host.awaitRequest(askGuess).answer(42)
        assertEquals(Correct, host.awaitRequest(showResult).input)

        game.cancel()
    }

    @Test
    fun `giving up reveals the secret`() = runTest {
        val host = UiHost()
        val game = launch { GuessGameAssembly(host)(42) }

        host.awaitRequest(giveUp).answer(Unit)
        assertEquals(GaveUp(42), host.awaitRequest(showResult).input)

        game.cancel()
    }

    @Test
    fun `answering one parallel sibling withdraws the other`() = runTest {
        val host = UiHost()
        val game = launch { GuessGameAssembly(host)(42) }

        host.awaitRequest(giveUp)
        host.awaitRequest(askGuess).answer(99)
        val afterAnswer = host.live.first { showResult in it }

        assertTrue(giveUp !in afterAnswer, "giveUp is cancelled once askGuess wins")
        assertTrue(askGuess !in afterAnswer)
        game.cancel()
    }

    @Test
    fun `parallel siblings render as one screen`() = runTest {
        val names = object : GuessGameScreens<String> {
            override fun guessing(askGuess: Request<Unit, Int>, giveUp: Request<Unit, Unit>) = "guessing"
            override fun result(showResult: Request<Outcome, Unit>) = "result"
        }
        val host = UiHost()
        val game = launch { GuessGameAssembly(host)(42) }

        assertEquals("guessing", names.render(host.live.first { names.render(it) != null }))
        host.awaitRequest(askGuess).answer(1)
        assertEquals("result", names.render(host.live.first { showResult in it }))

        game.cancel()
    }

    @Test
    fun `logic tests with fakes still work without any host`() = runTest {
        val shown = mutableListOf<Outcome>()
        val guesses = ArrayDeque(listOf(50, 25, 42))
        runCatching {
            GuessGame(
                askGuess = com.genovich.components.Action { guesses.removeFirst() },
                giveUp = com.genovich.components.Action { kotlinx.coroutines.awaitCancellation() },
                compare = Compare(),
                showResult = com.genovich.components.Action { shown += it; if (it == Correct) throw Done() },
            )(42)
        }

        assertEquals(listOf(TooHigh, TooLow, Correct), shown)
    }

    private class Done : RuntimeException()
}
