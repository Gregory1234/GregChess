package gregc.gregchess.bukkit

import gregc.gregchess.*

val ChessModule.plugin get() = BukkitRegistry.BUKKIT_PLUGIN[this].get()
val ChessModule.config get() = plugin.config

val Color.configName get() = name.snakeToPascal()