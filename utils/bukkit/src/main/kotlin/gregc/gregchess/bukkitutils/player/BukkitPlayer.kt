package gregc.gregchess.bukkitutils.player

import gregc.gregchess.bukkitutils.*
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.*

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
    fun openMenu(menu: Menu<*>) = onNull(entity) { openMenu(menu) }
}

object ConsoleBukkitCommandSender : BukkitCommandSender {
    override fun hasPermission(name: String): Boolean = Bukkit.getConsoleSender().hasPermission(name)
    override fun sendMessage(msg: String) = Bukkit.getConsoleSender().sendMessage(msg)
    override fun sendMessage(msg: TextComponent) = Bukkit.getConsoleSender().spigot().sendMessage(msg)
}

interface BukkitPlayerProvider<out Offline : BukkitPlayer, out Online : BukkitPlayer> {
    fun getPlayer(name: String): Offline?
    fun getOnlinePlayer(name: String): Online?
    fun getPlayer(uuid: UUID): Offline?
    fun getOnlinePlayer(uuid: UUID): Online?
}