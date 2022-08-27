package gregc.gregchess.bukkitutils.player

import gregc.gregchess.bukkitutils.Message
import gregc.gregchess.bukkitutils.sendTitleFull
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import java.util.UUID

interface BukkitCommandSender {
    fun sendMessage(msg: String)
    fun sendMessage(msg: Message) = sendMessage(msg.get())
    fun sendMessage(msg: TextComponent)
    fun hasPermission(name: String): Boolean
}

private inline fun <T : Any> onNull(value: T?, callback: T.() -> Unit) {
    value?.callback()
}

interface BukkitPlayer : BukkitCommandSender {
    val uuid: UUID
    val name: String
    val entity: Player?
    override fun sendMessage(msg: String) = onNull(entity) { sendMessage(msg) }
    override fun sendMessage(msg: TextComponent) = onNull(entity) { spigot().sendMessage(msg) }
    override fun hasPermission(name: String): Boolean = entity?.hasPermission(name) ?: false
    fun sendTitle(title: String?, subtitle: String?) = onNull(entity) { sendTitleFull(title, subtitle) }
}

object ConsoleBukkitCommandSender : BukkitCommandSender {
    override fun hasPermission(name: String): Boolean = Bukkit.getConsoleSender().hasPermission(name)
    override fun sendMessage(msg: String) = Bukkit.getConsoleSender().sendMessage(msg)
    override fun sendMessage(msg: TextComponent) = Bukkit.getConsoleSender().spigot().sendMessage(msg)
}

data class DefaultBukkitPlayer(val bukkit: OfflinePlayer) : BukkitPlayer {
    override val entity: Player? get() = bukkit.player
    override val name: String get() = bukkit.name ?: ""
    override val uuid: UUID get() = bukkit.uniqueId
}