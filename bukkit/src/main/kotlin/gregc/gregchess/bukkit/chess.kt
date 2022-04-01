package gregc.gregchess.bukkit

import gregc.gregchess.*
import gregc.gregchess.bukkit.registry.BukkitRegistry

val ChessModule.plugin get() = BukkitRegistry.BUKKIT_PLUGIN[this].get()
val ChessModule.config get() = plugin.config

val Color.configName get() = name.snakeToPascal()