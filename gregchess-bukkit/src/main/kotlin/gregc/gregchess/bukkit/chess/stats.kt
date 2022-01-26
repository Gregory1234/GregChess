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
    fun total(): PartialChessStats
    fun addWin(name: String)
    fun addLoss(name: String)
    fun addDraw(name: String)
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

    override fun total(): PartialChessStats =
        perSettings.values.reduceOrNull { acc, partialChessStats -> acc + partialChessStats }
            ?: PartialChessStats(0, 0, 0)

    override fun addWin(name: String) {
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

    override fun addLoss(name: String) {
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

    override fun addDraw(name: String) {
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
