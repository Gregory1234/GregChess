package gregc.gregchess.chess

import gregc.gregchess.ConfigManager
import gregc.gregchess.GregInfo
import gregc.gregchess.cNotNull
import gregc.gregchess.chess.component.Component
import gregc.gregchess.chess.component.GameBaseEvent
import gregc.gregchess.chess.component.GameEvent
import gregc.gregchess.chess.component.TimeModifier
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

data class Arena(val name: String, var game: ChessGame? = null): Component {

    companion object: Listener {
        private val arenas = mutableListOf<Arena>()

        fun next(): Arena? = arenas.firstOrNull { (_, game) -> game == null }

        fun cNext() = cNotNull(next(), "NoArenas")

        fun World.isArena(): Boolean = arenas.any {it.name == name}

        fun reload() {
            val newArenas = ConfigManager.getStringList("ChessArenas")
            arenas.forEach {
                if (it.name in newArenas){
                    it.game?.quickStop(ChessGame.EndReason.ArenaRemoved())
                    arenas.remove(it)
                }
            }
            arenas.addAll((newArenas - arenas.map {it.name}).map { Arena(it) })
        }

        fun start() {
            GregInfo.server.pluginManager.registerEvents(this, GregInfo.plugin)
            arenas.addAll(ConfigManager.getStringList("ChessArenas").map { Arena(it) })
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

    @GameEvent(GameBaseEvent.VERY_END, mod = TimeModifier.LATE)
    fun veryEnd() {
        game = null
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