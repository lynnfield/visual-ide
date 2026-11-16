package guessgame

import com.genovich.components.Action
import uihost.Live
import uihost.Port
import uihost.Request
import uihost.UiHost

// ---------------------------------------------------------------------------------------------
// Everything in this file is what visual-ide (or KSP) would GENERATE from the GuessGame diagram.
// It replaces today's GuessGameUiStateFlow (sealed Screen + combine + emitSelfWhenHaveValue).
// ---------------------------------------------------------------------------------------------

/** One port per T-function in the diagram. */
object GuessGamePorts {
    val askGuess = Port<Unit, Int>("askGuess")
    val giveUp = Port<Unit, Unit>("giveUp")
    val showResult = Port<Outcome, Unit>("showResult")
}

/**
 * The screens a client must provide, one method per screen. Grouping is inferred from the
 * diagram: askGuess and giveUp are siblings under one `parallel`, so they are one screen.
 * showResult runs alone in the sequence, so it is its own screen.
 *
 * [R] is whatever the client draws with: a composable lambda, a console prompt, a bot move.
 * The interface itself is framework-free, and a client that forgets a screen does not compile.
 */
interface GuessGameScreens<R> {
    fun guessing(askGuess: Request<Unit, Int>, giveUp: Request<Unit, Unit>): R
    fun result(showResult: Request<Outcome, Unit>): R
}

/**
 * Generated dispatch: picks the screen whose ports are all waiting. Returns `null` in the brief
 * moments between screens (for example, after a guess is answered and before `giveUp` is
 * cancelled); clients keep showing the last screen then.
 */
fun <R> GuessGameScreens<R>.render(live: Live): R? = when {
    GuessGamePorts.askGuess in live && GuessGamePorts.giveUp in live ->
        guessing(live[GuessGamePorts.askGuess], live[GuessGamePorts.giveUp])
    GuessGamePorts.showResult in live ->
        result(live[GuessGamePorts.showResult])
    else -> null
}

/** Generated assembly: every T-function port defaults to the host. */
fun GuessGameAssembly(
    host: UiHost,
    askGuess: Action<Unit, Int> = host.show(GuessGamePorts.askGuess),
    giveUp: Action<Unit, Unit> = host.show(GuessGamePorts.giveUp),
    compare: Action<Pair<Int, Int>, Outcome> = Compare(),
    showResult: Action<Outcome, Unit> = host.show(GuessGamePorts.showResult),
): GuessGame = GuessGame(askGuess, giveUp, compare, showResult)
