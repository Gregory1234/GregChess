package gregc.gregchess.bukkit.results

import gregc.gregchess.bukkit.config
import gregc.gregchess.bukkit.configName
import gregc.gregchess.bukkit.registry.BukkitRegistry
import gregc.gregchess.bukkitutils.getPathString
import gregc.gregchess.registry.module
import gregc.gregchess.registry.name
import gregc.gregchess.results.*
import gregc.gregchess.snakeToPascal

val EndReason<*>.quick
    get() = this in BukkitRegistry.QUICK_END_REASONS

val GameResults.name
    get() = endReason.module.config
        .getPathString("Chess.EndReason.${endReason.name.snakeToPascal()}", *args.toTypedArray())

val GameResults.message
    get() = score.let {
        when (it) {
            is GameScore.Draw -> config.getPathString("Message.GameFinished.ItWasADraw", name)
            is GameScore.Victory -> config.getPathString("Message.GameFinished." + it.winner.configName + "Won", name)
        }
    }