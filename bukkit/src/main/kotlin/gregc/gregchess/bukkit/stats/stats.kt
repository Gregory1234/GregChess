package gregc.gregchess.bukkit.stats

import gregc.gregchess.*
import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.match.SettingsManager
import gregc.gregchess.bukkit.player.BukkitPlayer
import gregc.gregchess.bukkitutils.*
import gregc.gregchess.bukkitutils.serialization.BukkitConfig
import gregc.gregchess.registry.module
import gregc.gregchess.registry.name
import gregc.gregchess.stats.*
import kotlinx.serialization.builtins.nullable
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.*

interface BukkitPlayerStats {
    val uuid: UUID
    operator fun get(color: Color, name: String): PlayerStatsSink
    operator fun set(color: Color, name: String, stats: PlayerStatsView)
    fun clear(color: Color, name: String)
    fun clear(name: String)
    fun total(): PlayerStatsView
    operator fun get(color: Color): PlayerStatsView
    operator fun get(name: String): PlayerStatsView
    companion object {
        fun of(uuid: UUID) = config.getFromRegistry(BukkitRegistry.CHESS_STATS_PROVIDER, "StatsProvider")!!(uuid)
    }
}

class YamlChessStats(override val uuid: UUID) : BukkitPlayerStats {
    private inner class YamlPlayerStats(val config: ConfigurationSection, val saveFile: (() -> Unit)? = null) : PlayerStatsView, PlayerStatsSink {

        private fun pathOf(stat: ChessStat<*>): String =
            "${stat.module.namespace.snakeToPascal()}.${stat.name.snakeToPascal()}"

        override fun <T : Any> add(stat: ChessStat<T>, vararg values: T) {
            if (values.isEmpty())
                return
            val path = pathOf(stat)
            val bukkitConfig = BukkitConfig(config, defaultModule())
            val oldValue = bukkitConfig.decodeFromPath(stat.serializer.nullable, path)
            val newValue = stat.aggregate(listOfNotNull(oldValue, *values))
            bukkitConfig.encodeOnPath(stat.serializer, path, newValue)
            commit()
        }

        override fun <T : Any> get(stat: ChessStat<T>): T {
            return stat.aggregate(listOfNotNull(BukkitConfig(config, defaultModule()).decodeFromPath(stat.serializer.nullable, pathOf(stat))))
        }

        override val stored: Set<ChessStat<*>>
            get() = config.getKeys(false).flatMap { module ->
                val trueModule = module.pascalToSnake()
                config.getOrCreateSection(module).getKeys(false).map {
                    CoreRegistry.STAT["$trueModule:${it.pascalToSnake()}".toKey()]
                }
            }.toSet()

        override fun commit() {
            saveFile?.invoke()
        }
    }

    override fun get(color: Color, name: String): PlayerStatsSink {
        val config = getConfig(uuid)
        return YamlPlayerStats(config.getOrCreateSection ("$name.${color.toString().snakeToPascal()}")) {
            config.save(getFile(uuid))
        }
    }

    override fun get(color: Color): PlayerStatsView {
        val config = getConfig(uuid)
        return CombinedPlayerStatsView(config.getKeys(false).map { name ->
            YamlPlayerStats(config.getOrCreateSection("$name.${color.toString().snakeToPascal()}"))
        })
    }

    override fun get(name: String): PlayerStatsView {
        val config = getConfig(uuid)
        return CombinedPlayerStatsView(byColor { color ->
            YamlPlayerStats(config.getOrCreateSection("$name.${color.toString().snakeToPascal()}"))
        }.toList())
    }

    override fun total(): PlayerStatsView {
        val config = getConfig(uuid)
        return CombinedPlayerStatsView(config.getKeys(false).flatMap { name ->
            byColor { color ->
                YamlPlayerStats(config.getOrCreateSection("$name.${color.toString().snakeToPascal()}"))
            }.toList()
        })
    }

    @Suppress("UNCHECKED_CAST")
    override fun set(color: Color, name: String, stats: PlayerStatsView) {
        val config = getConfig(uuid)
        config.set("$name.${color.toString().snakeToPascal()}", null)
        val yamlStats = YamlPlayerStats(config.getOrCreateSection("$name.${color.toString().snakeToPascal()}"))
        stats.stored.forEach {
            yamlStats.add(it as ChessStat<Any>, stats[it])
        }
        config.save(getFile(uuid))
    }

    override fun clear(color: Color, name: String) {
        val config = getConfig(uuid)
        config.set("$name.${color.toString().snakeToPascal()}", null)
        config.save(getFile(uuid))
    }

    override fun clear(name: String) {
        val config = getConfig(uuid)
        config.set(name, null)
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
    }
}

private fun PlayerStatsView.toItemStack(name: String) = itemStack(Material.IRON_BLOCK) {
    meta {
        this.name = name
        lore = listOf(
            config.getPathString("Message.Stats.Wins", get(ChessStat.WINS).toString()),
            config.getPathString("Message.Stats.Losses", get(ChessStat.LOSSES).toString()),
            config.getPathString("Message.Stats.Draws", get(ChessStat.DRAWS).toString())
        )
    }
}

suspend fun BukkitPlayer.openStatsMenu(playerName: String, stats: BukkitPlayerStats) =
    openMenu(config.getPathString("Message.StatsOf", playerName), SettingsManager.getSettings().let { settings ->
        settings.mapIndexed { index, s ->
            val item = stats[s.name].toItemStack(s.name)
            ScreenOption(item, Unit, index.toInvPos())
        } + ScreenOption(stats.total().toItemStack(config.getPathString("Message.Stats.Total")), Unit, settings.size.toInvPos())
    })
