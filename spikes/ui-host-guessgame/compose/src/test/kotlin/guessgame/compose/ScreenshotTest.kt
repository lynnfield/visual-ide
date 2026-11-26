package guessgame.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import guessgame.GuessGameAssembly
import guessgame.GuessGameScope
import guessgame.GuessGamePorts.askGuess
import guessgame.GuessGamePorts.giveUp
import guessgame.GuessGamePorts.showResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import uihost.UiHost
import java.io.File
import kotlin.test.Test

/** Plays a game headlessly and saves what Compose draws at each step. */
class ScreenshotTest {
    private val out = File(System.getProperty("screenshotDir", "build/screenshots")).apply { mkdirs() }

    @Test
    fun `play a round and capture each screen`() = runBlocking {
        val host = UiHost(GuessGameScope)
        val game = launch(Dispatchers.Default) { GuessGameAssembly(host)(42) }
        val scene = ImageComposeScene(840, 640, Density(2f)) { MaterialTheme { GuessGameUi(host) } }
        var frame = 0L
        fun shot(name: String) {
            repeat(5) { scene.render(frame); frame += 16_000_000L; Thread.sleep(20) }
            File(out, "$name.png").writeBytes(scene.render(frame).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }

        host.awaitRequest(giveUp); host.awaitRequest(askGuess)
        shot("1-guessing")
        host.live.value[askGuess].answer(10)
        host.awaitRequest(showResult)
        shot("2-too-low")
        host.live.value[showResult].answer(Unit)
        host.awaitRequest(giveUp).answer(Unit)
        host.awaitRequest(showResult)
        shot("3-gave-up")

        scene.close()
        game.cancel()
    }
}
