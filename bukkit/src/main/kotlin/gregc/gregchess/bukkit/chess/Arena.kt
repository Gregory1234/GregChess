package gregc.gregchess.bukkit.chess

import gregc.gregchess.GregChessModule
import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.chess.component.*
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Component
import gregc.gregchess.chess.component.ComponentData
import gregc.gregchess.register
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.weather.WeatherChangeEvent
import org.bukkit.generator.ChunkGenerator
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.scoreboard.Scoreboard
import java.util.*

internal data class PlayerData(
    @JvmField val location: Location? = null,
    @JvmField val inventory: List<ItemStack?> = List(41) { null },
    @JvmField val gameMode: GameMode = GameMode.SURVIVAL,
    @JvmField val health: Double = 20.0,
    @JvmField val foodLevel: Int = 20, @JvmField val saturation: Float = 20.0F,
    @JvmField val level: Int = 0, @JvmField val exp: Float = 0.0F,
    @JvmField val allowFlight: Boolean = false, @JvmField val isFlying: Boolean = false,
    @JvmField val effects: List<PotionEffect> = emptyList(),
    @JvmField val scoreboard: Scoreboard = Bukkit.getScoreboardManager()!!.mainScoreboard
)

private var Player.playerData: PlayerData
    get() = PlayerData(
        location.clone(),
        inventory.contents.toList(),
        gameMode,
        health,
        foodLevel, saturation,
        level, exp,
        allowFlight, isFlying,
        activePotionEffects.toList(),
        scoreboard
    )
    set(d) {
        inventory.contents = d.inventory.toTypedArray()
        gameMode = d.gameMode
        health = d.health
        foodLevel = d.foodLevel; saturation = d.saturation
        level = d.level; exp = d.exp
        allowFlight = d.allowFlight; isFlying = d.isFlying
        activePotionEffects.forEach { removePotionEffect(it.type) }
        d.effects.forEach(::addPotionEffect)
        d.location?.let(::teleport)
        scoreboard = d.scoreboard
    }

object ArenaManager : Listener {
    @JvmField
    val ARENA_REMOVED = GregChessModule.register("arena_removed", DrawEndReason(EndReason.Type.EMERGENCY, true))

    private val arenas = mutableListOf<Arena>()

    val freeAreas get() = arenas.filter { it.game == null }

    private fun World.isArena(): Boolean = arenas.any { it.name == name }

    fun reload() {
        val newArenas = config.getStringList("ChessArenas")
        arenas.removeIf {
            if (it.name !in newArenas) {
                it.game?.quickStop(drawBy(ARENA_REMOVED))
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

class ResetPlayerEvent(val player: Player): ChessEvent

@Serializable
class Arena(val name: String, @Transient var game: ChessGame? = null) : ComponentData<Arena.Usage> {
    companion object {
        private val defData = PlayerData(allowFlight = true, isFlying = true)

        private val spectatorData = defData.copy(gameMode = GameMode.SPECTATOR)
        private val adminData = defData.copy(gameMode = GameMode.CREATIVE)
    }

    class Usage(val arena: Arena, game: ChessGame) : Component(game) {

        override val data = arena

        private val dataMap = mutableMapOf<UUID, PlayerData>()

        private fun Player.join(d: PlayerData = defData) {
            if (uniqueId in dataMap)
                throw IllegalStateException("player already teleported")
            dataMap[uniqueId] = playerData
            reset(d)
        }

        private fun Player.leave() {
            if (uniqueId !in dataMap)
                throw IllegalStateException("player data not found")
            playerData = dataMap[uniqueId]!!
            dataMap.remove(uniqueId)
        }

        private fun Player.reset(d: PlayerData = defData) {
            teleport(game.requireComponent<BukkitRenderer>().spawnLocation.toLocation(arena.world))
            playerData = d
            chess?.held?.let { inventory.setItem(0, it.piece.item) }
        }

        @ChessEventHandler
        override fun validate() {
            game.requireComponent<BukkitRenderer>()
            arena.game = game
        }

        @ChessEventHandler
        fun onStop(e: GameStopStageEvent) {
            if (e == GameStopStageEvent.VERY_END) removeGame()
            else if (e == GameStopStageEvent.PANIC) evacuate()
        }

        private fun removeGame() {
            arena.game = null
        }

        private fun evacuate() {
            for (p in game.players.toList())
                if (p is BukkitPlayer)
                    p.player.leave()
        }

        @ChessEventHandler
        fun handleSpectator(p: SpectatorEvent)  {
            when (p.dir) {
                PlayerDirection.JOIN -> p.player.join(spectatorData)
                PlayerDirection.LEAVE -> p.player.leave()
            }
        }

        @ChessEventHandler
        fun handlePlayer(p: PlayerEvent) {
            when (p.dir) {
                PlayerDirection.JOIN -> p.player.join(if (p.player.isAdmin) adminData else defData)
                PlayerDirection.LEAVE -> p.player.leave()
            }
        }

        @ChessEventHandler
        fun resetPlayer(e: ResetPlayerEvent) {
            e.player.reset(if (e.player.isAdmin) adminData else defData)
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
            world
        } else {
            val ret = Bukkit.createWorld(WorldCreator(name).generator(WorldGen))!!
            println("Created arena $name")
            ret
        }).apply {
            pvp = false
            setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
            difficulty = Difficulty.PEACEFUL
        }
    }
}

val ChessGame.arena get() = arenaUsage.arena
val ChessGame.arenaUsage get() = requireComponent<Arena.Usage>()