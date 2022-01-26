package gregc.gregchess.bukkit.chess

import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkitutils.*
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.util.*

interface ChessStats {
    val uuid: UUID
    operator fun get(name: String): PartialChessStats
    operator fun set(name: String, stats: PartialChessStats)
    fun total(): PartialChessStats
    fun clear(name: String) = set(name, PartialChessStats(0, 0, 0))
    fun setWins(name: String, value: Int) = set(name, get(name).copy(wins = value))
    fun setLosses(name: String, value: Int) = set(name, get(name).copy(losses = value))
    fun setDraws(name: String, value: Int) = set(name, get(name).copy(draws = value))
    fun addWins(name: String, value: Int = 1) = set(name, get(name) + PartialChessStats(value, 0, 0))
    fun addLosses(name: String, value: Int = 1) = set(name, get(name) + PartialChessStats(0, value, 0))
    fun addDraws(name: String, value: Int = 1) = set(name, get(name) + PartialChessStats(0, 0, value))
    companion object {
        fun of(uuid: UUID) = BukkitRegistry.CHESS_STATS_PROVIDER[config.getString("StatsProvider")!!.toKey()](uuid)
    }
}

data class PartialChessStats(val wins: Int, val losses: Int, val draws: Int) {
    operator fun plus(other: PartialChessStats) =
        PartialChessStats(wins + other.wins, losses + other.losses, draws + other.draws)
}

class YamlChessStats private constructor(
    override val uuid: UUID, private val perSettings: MutableMap<String, PartialChessStats>
) : ChessStats {

    val player: Player? get() = Bukkit.getPlayer(uuid)

    override operator fun get(name: String) = perSettings[name] ?: PartialChessStats(0, 0, 0)

    override operator fun set(name: String, stats: PartialChessStats) {
        val config = getConfig(uuid)
        config.set("$name.Wins", stats.wins)
        config.set("$name.Losses", stats.losses)
        config.set("$name.Draws", stats.draws)
        perSettings[name] = stats
        config.save(getFile(uuid))
    }

    override fun total(): PartialChessStats =
        perSettings.values.reduceOrNull { acc, partialChessStats -> acc + partialChessStats }
            ?: PartialChessStats(0, 0, 0)

    override fun clear(name: String) {
        val config = getConfig(uuid)
        config.set(name, null)
        perSettings.remove(name)
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
            return YamlChessStats(uuid, stats)
        }
    }
}

private fun PartialChessStats.toItemStack(name: String) = itemStack(Material.IRON_BLOCK) {
    meta {
        this.name = name
        lore = listOf(
            config.getPathString("Message.Stats.Wins", wins.toString()),
            config.getPathString("Message.Stats.Losses", losses.toString()),
            config.getPathString("Message.Stats.Draws", draws.toString())
        )
    }
}

// TODO: add variant-specific stats
suspend fun Player.openStatsMenu(playerName: String, stats: ChessStats) =
    openMenu(config.getPathString("Message.StatsOf", playerName), SettingsManager.getSettings().let { settings ->
        settings.mapIndexed { index, s ->
            val item = stats[s.name].toItemStack(s.name)
            ScreenOption(item, Unit, index.toInvPos())
        } + ScreenOption(stats.total().toItemStack(config.getPathString("Message.Stats.Total")), Unit, settings.size.toInvPos())
    })
