package guessgame

import com.genovich.components.Action
import com.genovich.components.parallel
import com.genovich.components.whileActive

sealed interface Outcome
data object TooLow : Outcome
data object TooHigh : Outcome
data object Correct : Outcome
data class GaveUp(val secret: Int) : Outcome

/**
 * The logic. Nothing here knows about the UI host, flows or Compose: four ports, all plain
 * `Action`s. The input is the secret number.
 *
 * Each round asks for a guess while, side by side, offering to give up. Whichever answers first
 * wins, the other is cancelled, and the result is shown.
 */
class GuessGame(
    val askGuess: Action<Unit, Int>,
    val giveUp: Action<Unit, Unit>,
    val compare: Action<Pair<Int, Int>, Outcome>,
    val showResult: Action<Outcome, Unit>,
) : Action<Int, Nothing>() {

    override suspend fun invoke(input: Int): Nothing =
        whileActive {
            val outcome = parallel<Int, Outcome>(
                input,
                { secret -> compare(secret to askGuess(Unit)) },
                { secret -> giveUp(Unit); GaveUp(secret) },
            )
            showResult(outcome)
        }
}

/** A pure step: compares (secret, guess). */
class Compare : Action<Pair<Int, Int>, Outcome>() {
    override suspend fun invoke(input: Pair<Int, Int>): Outcome {
        val (secret, guess) = input
        return when {
            guess < secret -> TooLow
            guess > secret -> TooHigh
            else -> Correct
        }
    }
}
