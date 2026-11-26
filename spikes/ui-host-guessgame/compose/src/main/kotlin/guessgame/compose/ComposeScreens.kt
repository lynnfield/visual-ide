package guessgame.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import guessgame.Correct
import guessgame.GaveUp
import guessgame.GuessGameScreens
import guessgame.Outcome
import guessgame.TooHigh
import guessgame.TooLow
import guessgame.render
import kotlinx.coroutines.flow.mapNotNull
import uihost.Request
import uihost.UiHost

/**
 * The Compose client's implementation of the generated screens. `R` is a composable lambda:
 * composables as values, but on the UI side of the boundary, not in the logic's flows.
 */
object ComposeScreens : GuessGameScreens<@Composable () -> Unit> {
    override fun guessing(askGuess: Request<Unit, Int>, giveUp: Request<Unit, Unit>) =
        @Composable { GuessingScreen(onGuess = askGuess::answer, onGiveUp = { giveUp.answer(Unit) }) }

    override fun result(showResult: Request<Outcome, Unit>) =
        @Composable { ResultScreen(showResult.input, onContinue = { showResult.answer(Unit) }) }
}

/** The root: render whatever is waiting; between screens keep the last one. */
@Composable
fun GuessGameUi(host: UiHost) {
    val screen by remember(host) { host.live.mapNotNull { ComposeScreens.render(it) } }
        .collectAsState(initial = ComposeScreens.render(host.live.value))
    Surface(Modifier.fillMaxSize()) { screen?.invoke() }
}

@Composable
fun GuessingScreen(onGuess: (Int) -> Unit, onGiveUp: () -> Unit) {
    var text by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Guess a number from 1 to 100", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(text, { text = it }, label = { Text("Your guess") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onGiveUp) { Text("Give up") }      // answers giveUp
            Button(onClick = { text.toIntOrNull()?.let(onGuess) }) { Text("Guess") }  // answers askGuess
        }
    }
}

@Composable
fun ResultScreen(outcome: Outcome, onContinue: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            when (outcome) {
                TooLow -> "Too low"
                TooHigh -> "Too high"
                Correct -> "Correct!"
                is GaveUp -> "The number was ${outcome.secret}"
            },
            style = MaterialTheme.typography.headlineMedium,
        )
        Button(onClick = onContinue) { Text("Continue") }  // answers showResult
    }
}
