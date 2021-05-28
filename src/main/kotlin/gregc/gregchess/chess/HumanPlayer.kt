package gregc.gregchess.chess

import gregc.gregchess.sendDefTitle
import org.bukkit.entity.Player
import java.util.*

abstract class HumanPlayer(val name: String) {
    abstract fun sendMessage(msg: String)
    abstract fun sendTitle(title: String, subtitle: String = "")
}

abstract class MinecraftPlayer(val uniqueId: UUID, name: String): HumanPlayer(name)

class BukkitPlayer private constructor(val player: Player): MinecraftPlayer(player.uniqueId, player.name) {
    companion object {
        private val bukkitPlayers = mutableMapOf<Player, HumanPlayer>()
        fun toHuman(p: Player) = bukkitPlayers.getOrPut(p){ BukkitPlayer(p) }
    }

    override fun sendMessage(msg: String) = player.sendMessage(msg)

    override fun sendTitle(title: String, subtitle: String) = player.sendDefTitle(title, subtitle)
}

val HumanPlayer.bukkit get() = (this as BukkitPlayer).player

val Player.human get() = BukkitPlayer.toHuman(this)