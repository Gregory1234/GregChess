package gregc.gregchess.bukkit

import gregc.gregchess.*
import gregc.gregchess.Color
import gregc.gregchess.bukkit.registry.BukkitRegistry
import gregc.gregchess.bukkitutils.serialization.BukkitConfigLowercase
import kotlinx.serialization.Serializable
import org.bukkit.*
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.event.Listener

@Serializable
internal data class Loc(@BukkitConfigLowercase val x: Int, @BukkitConfigLowercase val y: Int, @BukkitConfigLowercase val z: Int) {
    operator fun plus(offset: Loc) = Loc(x + offset.x, y + offset.y, z + offset.z)
}

internal fun Loc.toLocation(w: World) = Location(w, x.toDouble(), y.toDouble(), z.toDouble())
internal fun Location.toLoc() = Loc(blockX, blockY, blockZ)

internal fun Listener.registerEvents() = Bukkit.getPluginManager().registerEvents(this, GregChessPlugin.plugin)

internal val config: ConfigurationSection get() = GregChessPlugin.plugin.config

internal fun String.pascalToSnake(): String {
    val camelRegex = "(?<=[a-zA-Z])[A-Z]".toRegex()
    return camelRegex.replace(this) { "_${it.value}" }.lowercase()
}

val ChessModule.plugin get() = BukkitRegistry.BUKKIT_PLUGIN[this].get()
val ChessModule.config get() = plugin.config

val Color.configName get() = name.snakeToPascal()