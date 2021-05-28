package gregc.gregchess.chess

import gregc.gregchess.sendDefTitle
import org.bukkit.GameMode
import org.bukkit.entity.Player
import java.util.*

abstract class HumanPlayer(val name: String) {
    abstract var isAdmin: Boolean
    var currentGame: ChessGame? = null
    var spectatedGame: ChessGame? = null
    val games = mutableListOf<ChessGame>()

    abstract fun sendMessage(msg: String)
    abstract fun sendTitle(title: String, subtitle: String = "")
    fun isInGame(): Boolean = currentGame != null
    fun isSpectating(): Boolean = spectatedGame != null
}

abstract class MinecraftPlayer(val uniqueId: UUID, name: String): HumanPlayer(name)

class BukkitPlayer private constructor(val player: Player): MinecraftPlayer(player.uniqueId, player.name) {
    companion object {
        private val bukkitPlayers = mutableMapOf<Player, HumanPlayer>()
        fun toHuman(p: Player) = bukkitPlayers.getOrPut(p){ BukkitPlayer(p) }
    }

    override var isAdmin = false
        set(value) {
            if (!value) {
                val loc = player.location
                currentGame?.renderer?.resetPlayer(this)
                player.teleport(loc)
            } else {
                player.gameMode = GameMode.CREATIVE
            }
            field = value
        }

    override fun sendMessage(msg: String) = player.sendMessage(msg)

    override fun sendTitle(title: String, subtitle: String) = player.sendDefTitle(title, subtitle)
}

val HumanPlayer.bukkit get() = (this as BukkitPlayer).player

val Player.human get() = BukkitPlayer.toHuman(this)