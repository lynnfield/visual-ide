package player

import com.genovich.components.Action
import com.genovich.components.whileActive
import uihost.Live
import uihost.Placement
import uihost.Port
import uihost.Request
import uihost.ScopeKey
import uihost.Screens
import uihost.UiHost

/** Logic: a mini-player that toggles play/pause forever. Input of `toggle` is "is playing now". */
class Player(val toggle: Action<Boolean, Boolean>) : Action<Unit, Nothing>() {
    override suspend fun invoke(input: Unit): Nothing {
        var playing = false
        whileActive { playing = toggle(playing) }
    }
}

// ---- generated from the Player diagram ------------------------------------------------------

object PlayerPorts {
    val toggle = Port<Boolean, Boolean>("toggle")
}

interface PlayerScreens<R> {
    fun player(toggle: Request<Boolean, Boolean>): R
}

fun <R> PlayerScreens<R>.render(live: Live): R? = when {
    PlayerPorts.toggle in live -> player(live[PlayerPorts.toggle])
    else -> null
}

/** Beside: shown next to whatever its siblings show; Back skips it. */
object PlayerScope : ScopeKey("player", Placement.Beside) {
    @Suppress("UNCHECKED_CAST")
    override fun <R> render(screens: Screens<R>, host: UiHost): R? =
        (screens.implementationFor(this) as PlayerScreens<R>).render(host.live.value)
}

fun <R> Screens<R>.register(key: PlayerScope, screens: PlayerScreens<R>) = registerUnchecked(key, screens)

fun PlayerAssembly(host: UiHost): Player = Player(toggle = host.show(PlayerPorts.toggle))
