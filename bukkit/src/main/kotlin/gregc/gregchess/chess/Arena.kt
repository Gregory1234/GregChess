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


data class Arena(val name: String, var game: ChessGame? = null): Component.Settings<Arena.Usage> {
    companion object {
        private val defData = PlayerData(allowFlight = true, isFlying = true)

        private val spectatorData = defData.copy(gameMode = GameMode.SPECTATOR)
        private val adminData = defData.copy(gameMode = GameMode.CREATIVE)
    }

    class Usage(val arena: Arena, private val game: ChessGame): Component {

        private val data = mutableMapOf<UUID, PlayerData>()

        private fun BukkitPlayer.join(d: PlayerData = defData) {
            if (uniqueId in data)
                throw IllegalStateException("player already teleported")
            data[uniqueId] = player.playerData
            reset(d)
        }

        private fun BukkitPlayer.leave() {
            if (uniqueId !in data)
                throw IllegalStateException("player data not found")
            player.playerData = data[uniqueId]!!
            data.remove(uniqueId)
        }

        private fun BukkitPlayer.reset(d: PlayerData = defData) {
            player.teleport(game.requireComponent<BukkitRenderer>().spawnLocation.toLocation(arena.world))
            player.playerData = d
            game[this]?.held?.let { setItem(0, it.piece) }
        }

        @GameEvent(GameBaseEvent.PRE_INIT, mod = TimeModifier.EARLY)
        fun addGame() {
            game.requireComponent<BukkitRenderer>()
            arena.game = game
        }

        @GameEvent(GameBaseEvent.VERY_END, mod = TimeModifier.LATE)
        fun removeGame() {
            arena.game = null
        }

        @GameEvent(GameBaseEvent.PANIC)
        fun evacuate() {
            game.players.forEachReal { (it as? BukkitPlayer)?.leave() }
        }

        @ChessEventHandler
        fun spectatorJoin(p: SpectatorJoinEvent) {
            p.player.join(spectatorData)
        }

        @ChessEventHandler
        fun spectatorLeave(p: SpectatorLeaveEvent) {
            p.player.leave()
        }

        @GameEvent(GameBaseEvent.REMOVE_PLAYER, relaxed = true)
        fun removePlayer(p: BukkitPlayer) {
            p.leave()
        }

        @GameEvent(GameBaseEvent.ADD_PLAYER, relaxed = true)
        fun addPlayer(p: BukkitPlayer) {
            p.join(if (p.isAdmin) adminData else defData)
        }

        @GameEvent(GameBaseEvent.RESET_PLAYER, relaxed = true)
        fun resetPlayer(p: BukkitPlayer) {
            p.reset(if (p.isAdmin) adminData else defData)
        }
    }

    override fun getComponent(game: ChessGame): Usage = Usage(this, game)

    object WorldGen : ChunkGenerator() {
        override fun generateChunkData(world: World, random: Random, chunkX: Int, chunkZ: Int, biome: BiomeGrid) =
            createChunkData(world)

        override fun shouldGenerateCaves() = false
        override fun shouldGenerateMobs() = false
        override fun shouldGenerateDecorations() = false
        override fun shouldGenerateStructures() = false
    }

    val world: World = run {
        val world = Bukkit.getWorld(name)

        (if (world != null) {
            glog.low("World already exists", name)
            world
        } else {
            val ret = Bukkit.createWorld(WorldCreator(name).generator(WorldGen))!!
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