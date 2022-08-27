package gregc.gregchess.bukkit.player

import gregc.gregchess.bukkitutils.getOfflinePlayerByName
import gregc.gregchess.bukkitutils.player.BukkitPlayer
import gregc.gregchess.bukkitutils.player.BukkitPlayerProvider
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import java.util.*

data class GregChessBukkitPlayer(val bukkit: OfflinePlayer) : BukkitPlayer {
    override val entity: Player? get() = bukkit.player
    override val name: String get() = bukkit.name ?: ""
    override val uuid: UUID get() = bukkit.uniqueId
}

object GregChessBukkitPlayerProvider : BukkitPlayerProvider {
    override fun getOnlinePlayer(uuid: UUID): BukkitPlayer? = Bukkit.getPlayer(uuid)?.let(::GregChessBukkitPlayer)
    override fun getOnlinePlayer(name: String): BukkitPlayer? = Bukkit.getPlayer(name)?.let(::GregChessBukkitPlayer)
    override fun getPlayer(uuid: UUID): BukkitPlayer = Bukkit.getOfflinePlayer(uuid).let(::GregChessBukkitPlayer)
    override fun getPlayer(name: String): BukkitPlayer? = getOfflinePlayerByName(name)?.let(::GregChessBukkitPlayer)
}