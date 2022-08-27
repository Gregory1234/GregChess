package gregc.gregchess.bukkit.player

import gregc.gregchess.Color
import gregc.gregchess.bukkitutils.getOfflinePlayerByName
import gregc.gregchess.bukkitutils.player.BukkitPlayer
import gregc.gregchess.bukkitutils.player.BukkitPlayerProvider
import gregc.gregchess.player.ChessPlayer
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import java.util.*

data class GregChessBukkitPlayer(val bukkit: OfflinePlayer) : BukkitPlayer, ChessPlayer<BukkitChessSide> {
    override val entity: Player? get() = bukkit.player
    override val name: String get() = bukkit.name ?: ""
    override val uuid: UUID get() = bukkit.uniqueId
    override fun createChessSide(color: Color): BukkitChessSide = BukkitChessSide(uuid, color)
}

object GregChessBukkitPlayerProvider : BukkitPlayerProvider<GregChessBukkitPlayer, GregChessBukkitPlayer> {
    override fun getOnlinePlayer(uuid: UUID): GregChessBukkitPlayer? = Bukkit.getPlayer(uuid)?.let(::GregChessBukkitPlayer)
    override fun getOnlinePlayer(name: String): GregChessBukkitPlayer? = Bukkit.getPlayer(name)?.let(::GregChessBukkitPlayer)
    override fun getPlayer(uuid: UUID): GregChessBukkitPlayer = Bukkit.getOfflinePlayer(uuid).let(::GregChessBukkitPlayer)
    override fun getPlayer(name: String): GregChessBukkitPlayer? = getOfflinePlayerByName(name)?.let(::GregChessBukkitPlayer)
}