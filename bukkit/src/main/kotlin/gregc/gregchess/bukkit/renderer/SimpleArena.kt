package gregc.gregchess.bukkit.renderer

import gregc.gregchess.*
import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.match.MatchInfoEvent
import gregc.gregchess.bukkit.player.*
import gregc.gregchess.bukkitutils.serialization.BukkitConfig
import gregc.gregchess.bukkitutils.serialization.decodeFromPath
import gregc.gregchess.event.ChessBaseEvent
import gregc.gregchess.event.EventListenerRegistry
import gregc.gregchess.match.ChessMatch
import gregc.gregchess.piece.PieceType
import gregc.gregchess.registry.Register
import gregc.gregchess.results.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

class SimpleArena private constructor(
    val name: String,
    private val arenaData: Data
) : Arena {
    companion object : BukkitRegistering {
        @JvmField
        @Register(data = ["quick"])
        val ARENA_REMOVED = DrawEndReason(EndReason.Type.EMERGENCY)

        private val arenas = mutableListOf<SimpleArena>()

        fun reloadArenas() {
            val configFormat = BukkitConfig(config, defaultModule())
            val newArenas = config.getConfigurationSection("ChessArenas")
                ?.getKeys(false)
                ?.mapNotNull { name ->
                    try {
                        val newArenaData = configFormat.decodeFromPath<Data>("ChessArenas.$name")
                        val equivalent = arenas.toList().firstOrNull { it.name == name && it.arenaData == newArenaData }
                        if (equivalent != null) {
                            GregChess.logger.info("Arena $name did not change")
                        } else {
                            GregChess.logger.info("Loaded arena $name")
                        }
                        equivalent ?: SimpleArena(name, newArenaData)
                    } catch (e: IllegalArgumentException) {
                        GregChess.logger.warn("Arena $name has a wrong format")
                        null
                    }
                }.orEmpty()
            arenas.forEach {
                if (it !in newArenas) {
                    it.currentMatch?.stop(drawBy(ARENA_REMOVED))
                }
            }
            arenas.clear()
            arenas += newArenas
        }

        fun reserveOrNull(match: ChessMatch): SimpleArena? = arenas
            .firstOrNull { it.currentMatch == null }
            ?.also { it.currentMatch = match }

        private val returnWorld: World
            get() = config.getString("ReturnWorld")
                ?.let { requireNotNull(Bukkit.getWorld(it)) { "Return world not found: $it" } }
                ?: Bukkit.getWorlds().first()
    }


    @Serializable
    private data class Data(
        @Contextual val world: World = Bukkit.getWorlds().first(),
        val tileSize: Int = 3,
        val offset: Loc = Loc(0, 101, 0),
        val pieceRows: Map<PieceType, Int> = mapOf(PieceType.PAWN to 1)
    )

    var currentMatch: ChessMatch? = null
        private set

    override fun toString(): String = "SimpleArena(name=$name)"

    private val world: World = arenaData.world
    val tileSize: Int = arenaData.tileSize
    val boardSize get() = 8 * tileSize
    private val offset: Loc = arenaData.offset
    val pieceRows = arenaData.pieceRows

    private val boardStartLoc: Loc = offset + Loc(8, 0, 8)
    val boardStart: Location get() = boardStartLoc.toLocation(world)

    internal val capturedStartLoc = byColor(
        boardStartLoc + Loc(8 * tileSize - 1, 0, -3),
        boardStartLoc + Loc(0, 0, 8 * tileSize + 2)
    )
    val capturedStart: ByColor<Location> get() = byColor { capturedStartLoc[it].toLocation(world) }

    private val spawn: Loc = offset + Loc(4, 0, 4)
    val spawnLocation: Location get() = spawn.toLocation(world)

    fun registerEventListeners(events: EventListenerRegistry) {
        events.register<ChessBaseEvent> {
            if (it == ChessBaseEvent.CLEAR || it == ChessBaseEvent.PANIC) currentMatch = null
        }
        events.registerR<MatchInfoEvent> {
            text("Arena: $name\n")
        }
        events.registerR<SpectatorEvent> {
            when (dir) {
                PlayerDirection.JOIN -> player.entity?.let(::resetSpectator)
                PlayerDirection.LEAVE -> player.entity?.let(::leave)
            }
        }
        events.registerR<PlayerEvent> {
            when (dir) {
                PlayerDirection.JOIN -> player.entity?.let(::reset)
                PlayerDirection.LEAVE -> player.entity?.let(::leave)
            }
        }
        events.registerR<ResetPlayerEvent> {
            player.entity?.let(::reset)
        }
    }

    private fun leave(player: Player) = with(player) {
        for (e in activePotionEffects)
            removePotionEffect(e.type)
        teleport(returnWorld.spawnLocation)
        inventory.clear()
        gameMode = GameMode.SURVIVAL
        allowFlight = false
        isFlying = false
    }

    private fun resetSpectator(player: Player) = with(player) {
        teleport(spawnLocation)
        inventory.clear()
        gameMode = GameMode.SPECTATOR
        allowFlight = true
        isFlying = true
    }

    private fun reset(player: Player) = with(player) {
        health = 20.0
        foodLevel = 20
        for (e in activePotionEffects)
            removePotionEffect(e.type)
        addPotionEffect(PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 10, false, false, false))
        teleport(spawnLocation)
        inventory.clear()
        gameMode = GameMode.SURVIVAL
        allowFlight = true
        isFlying = true
    }

    private fun getPos(loc: Loc) = Pos(
        file = 7 - (loc.x - boardStartLoc.x).floorDiv(tileSize),
        rank = (loc.z - boardStartLoc.z).floorDiv(tileSize)
    ).takeIf { it.file in 0..7 && it.rank in 0..7 }

    internal fun getLoc(pos: Pos) = Loc(boardSize - 2 - pos.file * tileSize, 1, 1 + pos.rank * tileSize) + boardStartLoc

    override fun getPos(location: Location) = getPos(location.toLoc())

}