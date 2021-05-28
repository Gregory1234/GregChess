package gregc.gregchess.chess

import gregc.gregchess.ConfigManager
import gregc.gregchess.GregInfo
import gregc.gregchess.cNotNull
import gregc.gregchess.glog
import org.bukkit.Difficulty
import org.bukkit.GameRule
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.weather.WeatherChangeEvent
import org.bukkit.generator.ChunkGenerator
import java.util.*

@JvmInline
value class Arena(val name: String){

    companion object: Listener {
        private val arenas = mutableMapOf<Arena, ChessGame?>()

        fun next(): Arena? = arenas.toList().firstOrNull { (_, game) -> game == null }?.first

        fun cNext() = cNotNull(next(), "NoArenas")

        fun World.isArena(): Boolean = arenas.any {it.key.name == name}

        fun reload() {
            val newArenas = ConfigManager.getStringList("ChessArenas")
            arenas.forEach { (arena, game) ->
                if (arena.name in newArenas){
                    game?.quickStop(ChessGame.EndReason.ArenaRemoved())
                    arenas.remove(arena)
                }
            }
            arenas.putAll((newArenas- arenas.map {it.key.name}).associate { Arena(it) to null })
        }

        fun start() {
            GregInfo.server.pluginManager.registerEvents(this, GregInfo.plugin)
            arenas.putAll(ConfigManager.getStringList("ChessArenas").associate { Arena(it) to null })
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

    object WorldGen : ChunkGenerator() {
        override fun generateChunkData(world: World, random: Random, chunkX: Int, chunkZ: Int, biome: BiomeGrid) =
            createChunkData(world)

        override fun shouldGenerateCaves() = false
        override fun shouldGenerateMobs() = false
        override fun shouldGenerateDecorations() = false
        override fun shouldGenerateStructures() = false
    }
    fun register(g: ChessGame) {
        arenas[this] = g
    }
    fun unregister() {
        arenas[this] = null
    }
}

val Arena.world: World
    get() {
        val world = GregInfo.server.getWorld(name)

        return (if (world != null) {
            glog.low("World already exists", name)
            world
        } else {
            val ret = GregInfo.server.createWorld(WorldCreator(name).generator(Arena.WorldGen))!!
            glog.io("Created arena", name)
            ret
        }).apply {
            pvp = false
            setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
            difficulty = Difficulty.PEACEFUL
        }
    }