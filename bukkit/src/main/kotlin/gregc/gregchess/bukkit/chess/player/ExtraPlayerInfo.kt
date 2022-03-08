package gregc.gregchess.bukkit.chess.player

import gregc.gregchess.bukkit.chess.ChessGameManager
import gregc.gregchess.bukkit.chess.ResetPlayerEvent
import gregc.gregchess.bukkit.chess.component.spectators
import gregc.gregchess.chess.ChessGame
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.*

private class ExtraPlayerInfo(val uuid: UUID) {
    companion object {
        private val infos = mutableMapOf<UUID, ExtraPlayerInfo>()
        fun of(p: Player) = infos.getOrPut(p.uniqueId) { ExtraPlayerInfo(p.uniqueId) }
    }
    private val player: Player? get() = Bukkit.getPlayer(uuid)

    var lastLeftGame: UUID? = null
    // TODO: make currentGame more similar to spectatedGame
    var currentGame: ChessGame? = null
    val games = mutableListOf<ChessGame>()

    var isAdmin = false
        set(value) {
            field = value
            val loc = player!!.location
            currentGame?.callEvent(ResetPlayerEvent(player!!))
            player!!.teleport(loc)
        }

    var spectatedGame: ChessGame? = null
        set(v) {
            field?.let { it.spectators -= player!! }
            field = v
            field?.let { it.spectators += player!! }
        }
}

private val Player.extra get() = ExtraPlayerInfo.of(this)
val Player.chess: BukkitChessSide?
    get() {
        val players = currentGame?.sides?.toList().orEmpty()
            .filterIsInstance<BukkitChessSide>().filter { it.uuid == uniqueId }
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
var Player.lastLeftGame
    get() = extra.lastLeftGame?.let(ChessGameManager::get)
    set(v) {
        extra.lastLeftGame = v?.uuid
    }