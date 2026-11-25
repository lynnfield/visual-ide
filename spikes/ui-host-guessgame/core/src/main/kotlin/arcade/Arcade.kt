package arcade

import com.genovich.components.Action
import com.genovich.components.parallel
import com.genovich.components.whileActive
import guessgame.GuessGameAssembly
import guessgame.GuessGameScope
import player.PlayerAssembly
import player.PlayerScope
import uihost.Port
import uihost.Request
import uihost.ScopeKey
import uihost.Screens
import uihost.UiHost
import uihost.onDemandFeature
import uihost.scoped
import kotlin.random.Random

/**
 * Logic: the root. The game can be opened and closed any number of times; next to it, the
 * player runs forever. Plain Actions only.
 */
class Arcade(
    val game: Action<Unit, Unit>,
    val player: Action<Unit, Nothing>,
) : Action<Unit, Nothing>() {
    override suspend fun invoke(input: Unit): Nothing =
        parallel<Unit, Nothing>(input, { whileActive { game(it) } }, { player(it) })
}

// ---- generated from the Arcade diagram ------------------------------------------------------

object ArcadePorts {
    val openGame = Port<Unit, Unit>("openGame")
    val closeGame = Port<Unit, Unit>("closeGame")
}

/**
 * Root screens. Child scopes arrive already rendered as `R`: the root names their keys
 * (GuessGameScope, PlayerScope) but never their screens interfaces.
 * - GuessGame is Cover, so it gets its own screen method that replaces the lobby.
 * - Player is Beside, so it is an optional slot in every screen it can sit next to.
 */
interface ArcadeScreens<R> {
    fun lobby(openGame: Request<Unit, Unit>, player: R?): R
    fun game(game: R, closeGame: Request<Unit, Unit>, player: R?): R
}

fun <R> ArcadeScreens<R>.render(host: UiHost, screens: Screens<R>): R? {
    val live = host.live.value
    val player = host.child(PlayerScope)?.let(screens::render)
    val game = host.child(GuessGameScope)
    return when {
        game != null && ArcadePorts.closeGame in live ->
            game(screens.render(game) ?: return null, live[ArcadePorts.closeGame], player)
        ArcadePorts.openGame in live -> lobby(live[ArcadePorts.openGame], player)
        else -> null
    }
}

object ArcadeScope : ScopeKey("arcade") {
    @Suppress("UNCHECKED_CAST")
    override fun <R> render(screens: Screens<R>, host: UiHost): R? =
        (screens.implementationFor(this) as ArcadeScreens<R>).render(host, screens)
}

fun <R> Screens<R>.register(key: ArcadeScope, screens: ArcadeScreens<R>) = registerUnchecked(key, screens)

fun ArcadeAssembly(
    host: UiHost,
    newSecret: () -> Int = { Random.nextInt(1, 101) },
    game: Action<Unit, Unit> = onDemandFeature(
        host, ArcadePorts.openGame, ArcadePorts.closeGame, GuessGameScope,
    ) { child -> Action { GuessGameAssembly(child)(newSecret()) } },
    player: Action<Unit, Nothing> = scoped(host, PlayerScope) { child -> PlayerAssembly(child) },
): Arcade = Arcade(game, player)
