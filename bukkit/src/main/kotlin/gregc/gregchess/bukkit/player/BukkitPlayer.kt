package gregc.gregchess.bukkit.player

import gregc.gregchess.Color
import gregc.gregchess.bukkitutils.getOfflinePlayerByName
import gregc.gregchess.bukkitutils.player.BukkitHuman
import gregc.gregchess.bukkitutils.player.BukkitHumanProvider
import gregc.gregchess.player.ChessPlayer
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import java.util.*

data class BukkitPlayer(val bukkit: OfflinePlayer) : BukkitHuman, ChessPlayer<BukkitChessSide> {
    override val entity: Player? get() = bukkit.player
    override val name: String get() = bukkit.name ?: ""
    override val uuid: UUID get() = bukkit.uniqueId
    override fun createChessSide(color: Color): BukkitChessSide = BukkitChessSide(uuid, color)
}

object BukkitPlayerProvider : BukkitHumanProvider<BukkitPlayer, BukkitPlayer> {
    override fun getOnlinePlayer(uuid: UUID): BukkitPlayer? = Bukkit.getPlayer(uuid)?.let(::BukkitPlayer)
    override fun getOnlinePlayer(name: String): BukkitPlayer? = Bukkit.getPlayer(name)?.let(::BukkitPlayer)
    override fun getPlayer(uuid: UUID): BukkitPlayer = Bukkit.getOfflinePlayer(uuid).let(::BukkitPlayer)
    override fun getPlayer(name: String): BukkitPlayer? = getOfflinePlayerByName(name)?.let(::BukkitPlayer)
}