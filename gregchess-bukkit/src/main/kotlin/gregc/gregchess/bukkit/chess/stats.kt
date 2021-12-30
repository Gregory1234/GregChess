package gregc.gregchess.bukkit.chess

import gregc.gregchess.bukkit.GregChessPlugin
import gregc.gregchess.bukkit.config
import gregc.gregchess.bukkitutils.*
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.util.*

data class PartialChessStats(val wins: Int, val losses: Int, val draws: Int) {
    operator fun plus(other: PartialChessStats) =
        PartialChessStats(wins + other.wins, losses + other.losses, draws + other.draws)
}

class ChessStats private constructor(val uuid: UUID, private val perSettings: MutableMap<String, PartialChessStats>) {
    val player: Player? get() = Bukkit.getPlayer(uuid)

    operator fun get(name: String) = perSettings[name] ?: PartialChessStats(0, 0, 0)

    fun addWin(name: String) {
        val config = getConfig(uuid)
        if (name in config) {
            config.set("$name.Wins", config.getInt("$name.Wins") + 1)
            perSettings[name] = this[name] + PartialChessStats(1, 0, 0)
        } else {
            config.set("$name.Wins", 1)
            config.set("$name.Losses", 0)
            config.set("$name.Draws", 0)
            perSettings[name] = PartialChessStats(1, 0, 0)
        }
        config.save(getFile(uuid))
    }

    fun addLoss(name: String) {
        val config = getConfig(uuid)
        if (name in config) {
            config.set("$name.Losses", config.getInt("$name.Losses") + 1)
            perSettings[name] = this[name] + PartialChessStats(0, 1, 0)
        } else {
            config.set("$name.Wins", 0)
            config.set("$name.Losses", 1)
            config.set("$name.Draws", 0)
            perSettings[name] = PartialChessStats(0, 1, 0)
        }
        config.save(getFile(uuid))
    }

    fun addDraw(name: String) {
        val config = getConfig(uuid)
        if (name in config) {
            config.set("$name.Draws", config.getInt("$name.Draws") + 1)
            perSettings[name] = this[name] + PartialChessStats(0, 0, 1)
        } else {
            config.set("$name.Wins", 0)
            config.set("$name.Losses", 0)
            config.set("$name.Draws", 1)
            perSettings[name] = PartialChessStats(0, 0, 1)
        }
        config.save(getFile(uuid))
    }

    companion object {

        private val dataDir = GregChessPlugin.plugin.dataFolder.resolve("players").also {
            if (!it.exists())
                it.mkdirs()
        }

        private fun getFile(uuid: UUID): File = dataDir.resolve("$uuid.yml").also {
            if (!it.exists())
                it.createNewFile()
        }

        private fun getConfig(uuid: UUID): YamlConfiguration = YamlConfiguration.loadConfiguration(getFile(uuid))

        fun of(uuid: UUID): ChessStats {
            val config = getConfig(uuid)
            val stats = mutableMapOf<String, PartialChessStats>()
            for (name in config.getKeys(false)) {
                stats[name] = PartialChessStats(config.getInt("$name.Wins"), config.getInt("$name.Losses"), config.getInt("$name.Draws"))
            }
            return ChessStats(uuid, stats)
        }
    }
}

// TODO: add combined stats
suspend fun Player.openStatsMenu(playerName: String, stats: ChessStats) =
    openMenu(config.getPathString("Message.StatsOf", playerName), SettingsManager.getSettings().mapIndexed { index, s ->
        val partialStats = stats[s.name]
        val item = itemStack(Material.IRON_BLOCK) {
            meta {
                name = s.name
                lore = listOf(
                    config.getPathString("Message.Stats.Wins", partialStats.wins.toString()),
                    config.getPathString("Message.Stats.Losses", partialStats.losses.toString()),
                    config.getPathString("Message.Stats.Draws", partialStats.draws.toString())
                )
            }
        }
        ScreenOption(item, Unit, index.toInvPos())
    })