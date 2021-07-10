package gregc.gregchess.chess

import gregc.gregchess.*
import gregc.gregchess.chess.component.*
import gregc.gregchess.chess.component.GameEvent
import org.bukkit.*
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.weather.WeatherChangeEvent
import org.bukkit.generator.ChunkGenerator
import java.util.*

object ArenaManager : Listener {
    class ArenaRemovedEndReason : EndReason("ArenaRemoved", "emergency", quick = true)

    private val arenas = mutableListOf<Arena>()

    val freeAreas get() = arenas.filter { it.game == null }

    private fun World.isArena(): Boolean = arenas.any { it.name == name }

    fun reload() {
        val newArenas = config.getStringList("ChessArenas")
        arenas.removeIf {
            if (it.name !in newArenas) {
                it.game?.quickStop(ArenaRemovedEndReason())
                true
            } else false
        }
        arenas.addAll((newArenas - arenas.map { it.name }).map { Arena(it) })
    }

    fun start() {
        registerEvents()
        reload()
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

object BukkitArenaWorldGen : ChunkGenerator() {
    override fun generateChunkData(world: World, random: Random, chunkX: Int, chunkZ: Int, biome: BiomeGrid) =
        createChunkData(world)

    override fun shouldGenerateCaves() = false
    override fun shouldGenerateMobs() = false
    override fun shouldGenerateDecorations() = false
    override fun shouldGenerateStructures() = false
}

data class Arena(val name: String, var game: ChessGame? = null): Component.Settings<Arena.Usage> {
    class Usage(val arena: Arena, private val game: ChessGame): Component {
        @GameEvent(GameBaseEvent.PRE_INIT, mod = TimeModifier.EARLY)
        fun addGame() {
            arena.game = game
        }
        @GameEvent(GameBaseEvent.VERY_END, mod = TimeModifier.LATE)
        fun removeGame() {
            arena.game = null
        }
    }

    override fun getComponent(game: ChessGame): Usage = Usage(this, game)

    val world: World by lazy {
        val world = Bukkit.getWorld(name)

        (if (world != null) {
            glog.low("World already exists", name)
            world
        } else {
            val ret = Bukkit.createWorld(WorldCreator(name).generator(BukkitArenaWorldGen))!!
            glog.io("Created arena", name)
            ret
        }).apply {
            pvp = false
            setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
            difficulty = Difficulty.PEACEFUL
        }
    }
}

val ChessGame.arena get() = requireComponent<Arena.Usage>().arena