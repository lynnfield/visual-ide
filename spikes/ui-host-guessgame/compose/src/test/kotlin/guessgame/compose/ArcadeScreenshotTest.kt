package guessgame.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import arcade.ArcadeAssembly
import arcade.ArcadePorts
import arcade.ArcadeScope
import guessgame.GuessGamePorts
import guessgame.GuessGameScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import player.PlayerPorts
import player.PlayerScope
import uihost.UiHost
import java.io.File
import kotlin.test.Test

/** Plays the nested example headlessly and saves each screen. */
class ArcadeScreenshotTest {
    private val out = File(System.getProperty("screenshotDir", "build/screenshots")).apply { mkdirs() }

    @Test
    fun `lobby, game with player, back`() = runBlocking {
        val root = UiHost(ArcadeScope)
        val app = launch(Dispatchers.Default) { ArcadeAssembly(root, newSecret = { 42 })(Unit) }
        val scene = ImageComposeScene(840, 720, Density(2f)) { MaterialTheme { HostUi(root, composeScreens()) } }
        var frame = 0L
        fun shot(name: String) {
            repeat(5) { scene.render(frame); frame += 16_000_000L; Thread.sleep(30) }
            File(out, "$name.png").writeBytes(scene.render(frame).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }

        root.awaitRequest(ArcadePorts.openGame); root.awaitChild(PlayerScope).awaitRequest(PlayerPorts.toggle)
        shot("arcade-1-lobby")

        root.awaitRequest(ArcadePorts.openGame).answer(Unit)
        val game = root.awaitChild(GuessGameScope)
        game.awaitRequest(GuessGamePorts.askGuess); game.awaitRequest(GuessGamePorts.giveUp)
        root.awaitRequest(ArcadePorts.closeGame)
        root.awaitChild(PlayerScope).awaitRequest(PlayerPorts.toggle).answer(true)
        kotlinx.coroutines.delay(50)
        shot("arcade-2-game-with-player")

        root.back()
        root.awaitRequest(ArcadePorts.openGame)
        shot("arcade-3-after-back")

        scene.close()
        app.cancel()
    }
}
