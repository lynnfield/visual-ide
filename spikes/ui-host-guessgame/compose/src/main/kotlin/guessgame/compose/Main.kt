package guessgame.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import guessgame.GuessGameAssembly
import uihost.UiHost
import kotlin.random.Random

fun main() = application {
    val host = remember { UiHost() }
    LaunchedEffect(host) { GuessGameAssembly(host)(Random.nextInt(1, 101)) }
    Window(onCloseRequest = ::exitApplication, title = "GuessGame", state = rememberWindowState(width = 420.dp, height = 320.dp)) {
        MaterialTheme { GuessGameUi(host) }
    }
}
