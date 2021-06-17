package gregc.gregchess.chess

import gregc.gregchess.*
import gregc.gregchess.chess.component.*
import org.bukkit.*
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.weather.WeatherChangeEvent
import org.bukkit.generator.ChunkGenerator
import org.bukkit.plugin.Plugin
import java.util.*

interface ArenaManager {
    fun next(): Arena?
}

interface ArenasConfig : ConfigBlock {
    val chessArenas: List<String>
}

val Config.arenas: ArenasConfig by Config

fun ArenaManager.cNext() = cNotNull(next(), Config.error.noArenas)

class BukkitArenaManager(private val plugin: Plugin) : ArenaManager, Listener {
    private val arenas = mutableListOf<Arena>()

    override fun next(): Arena? = arenas.firstOrNull { (_, game) -> game == null }

    private fun World.isArena(): Boolean = arenas.any { it.name == name }

    fun reload() {
        val newArenas = Config.arenas.chessArenas
        arenas.removeIf {
            if (it.name !in newArenas) {
                it.game?.quickStop(EndReason.ArenaRemoved())
                true
            } else false
        }
        arenas.addAll((newArenas - arenas.map { it.name }).map { Arena(it) })
    }

    fun start() {
        Bukkit.getPluginManager().registerEvents(this, plugin)
        arenas.addAll(Config.arenas.chessArenas.map { Arena(it) })
    }

    @EventHandler
    fun onCreatureSpawn(e: CreatureSpawnEvent) {
        if (e.location.world?.isArena() == true) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onWeatherChange(e: WeatherChangeEvent) {
        if (e.toWeatherState()) {
            if (e.world.isArena()) {
                e.isCancelled = true
            }
        }
    }
}

data class Arena(val name: String, var game: ChessGame? = null) : Component {

    object WorldGen : ChunkGenerator() {
        override fun generateChunkData(world: World, random: Random, chunkX: Int, chunkZ: Int, biome: BiomeGrid) =
            createChunkData(world)

        override fun shouldGenerateCaves() = false
        override fun shouldGenerateMobs() = false
        override fun shouldGenerateDecorations() = false
        override fun shouldGenerateStructures() = false
    }

    @GameEvent(GameBaseEvent.VERY_END, GameBaseEvent.PANIC, mod = TimeModifier.LATE)
    fun veryEnd() {
        game = null
    }
}

val Arena.world: World
    get() {
        val world = Bukkit.getWorld(name)

        return (if (world != null) {
            glog.low("World already exists", name)
            world
        } else {
            val ret = Bukkit.createWorld(WorldCreator(name).generator(Arena.WorldGen))!!
            glog.io("Created arena", name)
            ret
        }).apply {
            pvp = false
            setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
            difficulty = Difficulty.PEACEFUL
        }
    }