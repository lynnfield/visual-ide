package guessgame.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import arcade.ArcadeScope
import arcade.ArcadeScreens
import arcade.register
import guessgame.GuessGameScope
import guessgame.register
import kotlinx.coroutines.flow.mapNotNull
import player.PlayerScope
import player.PlayerScreens
import player.register
import uihost.Request
import uihost.Screens
import uihost.UiHost

typealias Ui = @Composable () -> Unit

/** Root scope. `game` and `player` arrive already drawn by their own scopes' screens. */
object ArcadeComposeScreens : ArcadeScreens<Ui> {
    override fun lobby(openGame: Request<Unit, Unit>, player: Ui?): Ui = @Composable {
        WithPlayer(player) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Arcade", style = MaterialTheme.typography.headlineMedium)
                Button(onClick = { openGame.answer(Unit) }) { Text("Play GuessGame") }
            }
        }
    }

    override fun game(game: Ui, closeGame: Request<Unit, Unit>, player: Ui?): Ui = @Composable {
        WithPlayer(player) {
            Column(Modifier.fillMaxSize()) {
                TextButton(onClick = { closeGame.answer(Unit) }) { Text("← Back to lobby") }
                Box(Modifier.weight(1f)) { game() }
            }
        }
    }
}

/** Beside scope: a bar, wherever the parent puts the slot. */
object PlayerComposeScreens : PlayerScreens<Ui> {
    override fun player(toggle: Request<Boolean, Boolean>): Ui = @Composable {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(if (toggle.input) "♪ Playing: Lo-fi beats" else "Paused: Lo-fi beats")
            TextButton(onClick = { toggle.answer(!toggle.input) }) { Text(if (toggle.input) "Pause" else "Play") }
        }
    }
}

@Composable
private fun WithPlayer(player: Ui?, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { content() }
        player?.invoke()
    }
}

/** Every scope's screens, registered by typed key. A dynamically loaded feature would add its own. */
fun composeScreens() = Screens<Ui>().apply {
    register(ArcadeScope, ArcadeComposeScreens)
    register(GuessGameScope, ComposeScreens)
    register(PlayerScope, PlayerComposeScreens)
}

/** Root: redraw from the top on any change in the host tree; keep the last screen in the gaps. */
@Composable
fun HostUi(root: UiHost, screens: Screens<Ui>) {
    val ui by remember(root, screens) { root.version.mapNotNull { screens.render(root) } }
        .collectAsState(initial = screens.render(root))
    Surface(Modifier.fillMaxSize()) { ui?.invoke() }
}
