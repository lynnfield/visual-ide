package arcade

import guessgame.GuessGamePorts
import guessgame.GuessGameScope
import guessgame.GuessGameScreens
import guessgame.Outcome
import guessgame.register
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import player.PlayerPorts
import player.PlayerScope
import player.PlayerScreens
import player.register
import uihost.Request
import uihost.Screens
import uihost.UiHost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A text client: each scope registers its own screens by typed key. */
private fun textScreens() = Screens<String>().apply {
    register(ArcadeScope, object : ArcadeScreens<String> {
        override fun lobby(openGame: Request<Unit, Unit>, player: String?) = "lobby | $player"
        override fun game(game: String, closeGame: Request<Unit, Unit>, player: String?) = "game($game) | $player"
    })
    register(GuessGameScope, object : GuessGameScreens<String> {
        override fun guessing(askGuess: Request<Unit, Int>, giveUp: Request<Unit, Unit>) = "guessing"
        override fun result(showResult: Request<Outcome, Unit>) = "result ${showResult.input}"
    })
    register(PlayerScope, object : PlayerScreens<String> {
        override fun player(toggle: Request<Boolean, Boolean>) = if (toggle.input) "playing" else "paused"
    })
}

class ArcadeHostTest {
    private val screens = textScreens()

    private suspend fun UiHost.awaitScreen(expected: String) {
        version.first { screens.render(this) == expected }
    }

    @Test
    fun `game covers the lobby, player sits beside both`() = runTest {
        val root = UiHost(ArcadeScope)
        val app = launch { ArcadeAssembly(root, newSecret = { 42 })(Unit) }

        root.awaitScreen("lobby | paused")
        root.awaitRequest(ArcadePorts.openGame).answer(Unit)
        root.awaitScreen("game(guessing) | paused")

        val game = root.awaitChild(GuessGameScope)
        game.awaitRequest(GuessGamePorts.askGuess).answer(10)
        root.awaitScreen("game(result TooLow) | paused")

        app.cancel()
    }

    @Test
    fun `player keeps working while the game is open`() = runTest {
        val root = UiHost(ArcadeScope)
        val app = launch { ArcadeAssembly(root, newSecret = { 42 })(Unit) }

        root.awaitRequest(ArcadePorts.openGame).answer(Unit)
        root.awaitScreen("game(guessing) | paused")
        root.awaitChild(PlayerScope).awaitRequest(PlayerPorts.toggle).answer(true)
        root.awaitScreen("game(guessing) | playing")

        app.cancel()
    }

    @Test
    fun `back closes the deepest covering scope and skips the player`() = runTest {
        val root = UiHost(ArcadeScope)
        val app = launch { ArcadeAssembly(root, newSecret = { 42 })(Unit) }

        root.awaitScreen("lobby | paused")
        assertFalse(root.back(), "nothing to close: the player is Beside, so Back goes to the platform")

        root.awaitRequest(ArcadePorts.openGame).answer(Unit)
        root.awaitScreen("game(guessing) | paused")
        assertTrue(root.back())
        root.awaitScreen("lobby | paused")
        assertEquals(null, root.child(GuessGameScope), "the game's host is gone with its scope")

        app.cancel()
    }

    @Test
    fun `a reopened game is a fresh scope`() = runTest {
        val root = UiHost(ArcadeScope)
        val secrets = ArrayDeque(listOf(1, 2))
        val app = launch { ArcadeAssembly(root, newSecret = { secrets.removeFirst() })(Unit) }

        root.awaitRequest(ArcadePorts.openGame).answer(Unit)
        root.awaitChild(GuessGameScope).awaitRequest(GuessGamePorts.giveUp).answer(Unit)
        root.awaitScreen("game(result GaveUp(secret=1)) | paused")
        root.back()
        root.awaitRequest(ArcadePorts.openGame).answer(Unit)
        root.awaitChild(GuessGameScope).awaitRequest(GuessGamePorts.giveUp).answer(Unit)
        root.awaitScreen("game(result GaveUp(secret=2)) | paused")

        app.cancel()
    }

    @Test
    fun `a missing registration names the scope`() = runTest {
        val root = UiHost(ArcadeScope)
        val app = launch { ArcadeAssembly(root)(Unit) }
        root.awaitChild(PlayerScope)
        val error = assertFailsWith<IllegalStateException> { Screens<String>().render(root) }
        assertEquals("No screens registered for scope 'arcade'", error.message)
        app.cancel()
    }
}
