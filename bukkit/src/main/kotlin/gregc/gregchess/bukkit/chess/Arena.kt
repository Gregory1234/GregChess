package gregc.gregchess.bukkit.chess

import gregc.gregchess.asIdent
import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.chess.component.BukkitRenderer
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Component
import gregc.gregchess.chess.component.SpectatorEvent
import gregc.gregchess.glog
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

private data class PlayerData(
    val location: Location? = null,
    val inventory: List<ItemStack?> = List(41) { null },
    val gameMode: GameMode = GameMode.SURVIVAL,
    val health: Double = 20.0,
    val foodLevel: Int = 20, val saturation: Float = 20.0F,
    val level: Int = 0, val exp: Float = 0.0F,
    val allowFlight: Boolean = false, val isFlying: Boolean = false,
    val effects: List<PotionEffect> = emptyList(),
    val scoreboard: Scoreboard = Bukkit.getScoreboardManager()!!.mainScoreboard
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
    private val ARENA_REMOVED = DrawEndReason("arena_removed".asIdent(), EndReason.Type.EMERGENCY, quick = true)

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


data class Arena(val name: String, var game: ChessGame? = null): Component.Settings<Arena.Usage> {
    companion object {
        private val defData = PlayerData(allowFlight = true, isFlying = true)

        private val spectatorData = defData.copy(gameMode = GameMode.SPECTATOR)
        private val adminData = defData.copy(gameMode = GameMode.CREATIVE)
    }

    class Usage(val arena: Arena, private val game: ChessGame): Component {

        private val data = mutableMapOf<UUID, PlayerData>()

        private fun BukkitPlayer.join(d: PlayerData = defData) {
            if (uuid in data)
                throw IllegalStateException("player already teleported")
            data[uuid] = player.playerData
            reset(d)
        }

        private fun BukkitPlayer.leave() {
            if (uuid !in data)
                throw IllegalStateException("player data not found")
            player.playerData = data[uuid]!!
            data.remove(uuid)
        }

        private fun BukkitPlayer.reset(d: PlayerData = defData) {
            player.teleport(game.requireComponent<BukkitRenderer>().spawnLocation.toLocation(arena.world))
            player.playerData = d
            game[this]?.held?.let { setItem(0, it.piece) }
        }

        @ChessEventHandler
        fun handleEvents(e: GameBaseEvent) = when (e) {
            GameBaseEvent.PRE_INIT -> addGame()
            GameBaseEvent.VERY_END -> removeGame()
            GameBaseEvent.PANIC -> evacuate()
            else -> {}
        }

        private fun addGame() {
            game.requireComponent<BukkitRenderer>()
            arena.game = game
        }

        private fun removeGame() {
            arena.game = null
        }

        private fun evacuate() {
            game.players.forEachReal { (it as? BukkitPlayer)?.leave() }
        }

        @ChessEventHandler
        fun handleSpectator(p: SpectatorEvent) = (p.human as? BukkitPlayer)?.run{
            when(p.dir) {
                PlayerDirection.JOIN -> join(spectatorData)
                PlayerDirection.LEAVE -> leave()
            }
        }

        @ChessEventHandler
        fun handlePlayer(p: HumanPlayerEvent) {
            (p.human as? BukkitPlayer)?.let {
                when (p.dir) {
                    PlayerDirection.JOIN -> it.join(if (it.isAdmin) adminData else defData)
                    PlayerDirection.LEAVE -> it.leave()
                }
            }
        }

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

val ChessGame.arena get() = arenaUsage.arena
val ChessGame.arenaUsage get() = requireComponent<Arena.Usage>()