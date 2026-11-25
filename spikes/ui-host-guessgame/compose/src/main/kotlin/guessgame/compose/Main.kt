package guessgame.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import arcade.ArcadeAssembly
import arcade.ArcadeScope
import uihost.UiHost

fun main() = application {
    val root = remember { UiHost(ArcadeScope) }
    val screens = remember { composeScreens() }
    LaunchedEffect(root) { ArcadeAssembly(root)(Unit) }
    Window(
        onCloseRequest = ::exitApplication,
        title = "Arcade",
        state = rememberWindowState(width = 420.dp, height = 360.dp),
        // Escape plays the role of system Back: the host closes the deepest covering scope.
        onPreviewKeyEvent = { it.type == KeyEventType.KeyUp && it.key == Key.Escape && root.back() },
    ) {
        MaterialTheme { HostUi(root, screens) }
    }
}
