package gregc.gregchess.bukkit.results

import gregc.gregchess.*
import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkitutils.getPathString
import gregc.gregchess.bukkitutils.player.BukkitHuman
import gregc.gregchess.registry.module
import gregc.gregchess.registry.name
import gregc.gregchess.results.*

val EndReason<*>.quick
    get() = this in BukkitRegistry.QUICK_END_REASONS

val MatchResults.name
    get() = endReason.module.config
        .getPathString("Chess.EndReason.${endReason.name.snakeToPascal()}", *args.toTypedArray())

val MatchResults.message
    get() = score.let {
        when (it) {
            is MatchScore.Draw -> config.getPathString("Message.MatchFinished.ItWasADraw", name)
            is MatchScore.Victory -> config.getPathString("Message.MatchFinished." + it.winner.configName + "Won", name)
        }
    }

private val SPECTATOR_WINNER = byColor { title("Spectator.${it.configName}Won") }
private val SPECTATOR_DRAW = title("Spectator.ItWasADraw")

fun BukkitHuman.sendMatchResults(results: MatchResults) {
    val msg = when (val score = results.score) {
        is MatchScore.Victory -> SPECTATOR_WINNER[score.winner]
        is MatchScore.Draw -> SPECTATOR_DRAW
    }
    sendTitle(msg.get(), results.name)
    sendMessage(results.message)
}

private val YOU_WON = title("Player.YouWon")
private val YOU_LOST = title("Player.YouLost")
private val YOU_DREW = title("Player.YouDrew")

fun BukkitHuman.sendMatchResults(color: Color, results: MatchResults) {
    val wld = when (results.score) {
        MatchScore.Victory(color) -> YOU_WON
        MatchScore.Draw -> YOU_DREW
        else -> YOU_LOST
    }
    sendTitle(wld.get(), results.name)
    sendMessage(results.message)
}