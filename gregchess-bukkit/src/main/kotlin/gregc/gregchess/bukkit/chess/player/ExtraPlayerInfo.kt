package gregc.gregchess.bukkit.chess.player

import gregc.gregchess.bukkit.chess.ResetPlayerEvent
import gregc.gregchess.bukkit.chess.component.spectators
import gregc.gregchess.chess.ChessGame
import org.bukkit.entity.Player

private class ExtraPlayerInfo(val player: Player) {
    companion object {
        private val infos = mutableMapOf<Player, ExtraPlayerInfo>()
        fun of(p: Player) = infos.getOrPut(p) { ExtraPlayerInfo(p) }
    }

    var currentGame: ChessGame? = null
    val games = mutableListOf<ChessGame>()

    var isAdmin = false
        set(value) {
            field = value
            val loc = player.location
            currentGame?.callEvent(ResetPlayerEvent(player))
            player.teleport(loc)
        }

    var spectatedGame: ChessGame? = null
        set(v) {
            field?.let { it.spectators -= player }
            field = v
            field?.let { it.spectators += player }
        }
}

private val Player.extra get() = ExtraPlayerInfo.of(this)
val Player.chess: BukkitChessSide?
    get() {
        val players = currentGame?.sides?.toList().orEmpty()
            .filterIsInstance<BukkitChessSide>().filter { it.player == gregchess }
        return if (players.size == 2)
            players.firstOrNull { it.hasTurn }
        else
            players.singleOrNull()
    }
var Player.currentGame
    get() = extra.currentGame
    set(v) {
        extra.currentGame = v
    }
val Player.games get() = extra.games
val Player.isInGame get() = currentGame != null
var Player.isAdmin
    get() = extra.isAdmin
    set(v) {
        extra.isAdmin = v
    }
var Player.spectatedGame
    get() = extra.spectatedGame
    set(v) {
        extra.spectatedGame = v
    }
val Player.isSpectating get() = spectatedGame != null