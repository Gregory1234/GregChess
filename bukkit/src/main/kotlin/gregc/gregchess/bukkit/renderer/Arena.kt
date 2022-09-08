package gregc.gregchess.bukkit.renderer

import gregc.gregchess.*
import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.match.BukkitChessEventType
import gregc.gregchess.bukkit.match.PlayerDirection
import gregc.gregchess.bukkit.piece.item
import gregc.gregchess.bukkit.player.BukkitPlayer
import gregc.gregchess.bukkit.registry.BukkitRegistry
import gregc.gregchess.bukkit.registry.getFromRegistry
import gregc.gregchess.bukkitutils.serialization.BukkitConfig
import gregc.gregchess.bukkitutils.serialization.decodeFromPath
import gregc.gregchess.match.*
import gregc.gregchess.results.*
import kotlinx.serialization.*
import org.bukkit.*
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

class NoFreeArenasException : NoSuchElementException("No free arenas")

object ArenaManagers {
    internal val AUTO_REGISTER = AutoRegisterType(ArenaManager::class) { m, n, _ ->
        BukkitRegistry.ARENA_MANAGER[m, n] = this
        (this as? Registering)?.registerAll(m)
    }

    @JvmField
    @Register
    val SIMPLE = SimpleArenaManager
}

interface ArenaManager<out A : Arena> {
    companion object {
        fun fromConfig(): ArenaManager<*> = config.getFromRegistry(BukkitRegistry.ARENA_MANAGER, "ArenaManager")!!
    }

    fun unloadArenas()

    fun reloadArenas()

    fun hasFreeArenas(): Boolean

    fun reserveArenaOrNull(match: ChessMatch): A?

    fun reserveArena(match: ChessMatch): A = reserveArenaOrNull(match) ?: throw NoFreeArenasException()

}

interface Arena {
    fun registerEvents(match: ChessMatch, events: ChessEventRegistry)
    val boardStart: Location
    val tileSize: Int
    val capturedStart: ByColor<Location>
}

class ArenaMetadata(val name: String, var match: ChessMatch? = null)

object SimpleArenaManager : ArenaManager<SimpleArena>, BukkitRegistering {
    @JvmField
    @Register(data = ["quick"])
    val ARENA_REMOVED = DrawEndReason(EndReason.Type.EMERGENCY)

    private val arenas = mutableMapOf<SimpleArena, ArenaMetadata>()

    override fun unloadArenas() {
        arenas.forEach { (_, meta) ->
            meta.match?.stop(drawBy(ARENA_REMOVED))
        }
        arenas.clear()
    }

    override fun reloadArenas() {
        val configFormat = BukkitConfig(config, defaultModule())
        val newArenas = config.getConfigurationSection("ChessArenas")
            ?.getKeys(false)
            ?.mapNotNull { name ->
                try {
                    val newArena = configFormat.decodeFromPath<SimpleArena>("ChessArenas.$name")
                    val equivalent = arenas.toList().firstOrNull { (arena, meta) -> meta.name == name && arena.equivalent(newArena) }
                    if (equivalent != null) {
                        GregChess.logger.info("Arena $name did not change")
                    } else {
                        GregChess.logger.info("Loaded arena $name")
                    }
                    equivalent ?: (newArena to ArenaMetadata(name))
                } catch (e: IllegalArgumentException) {
                    GregChess.logger.warn("Arena $name has a wrong format")
                    null
                }
            }.orEmpty().toMap()
        arenas.forEach { (arena, meta) ->
            if (arena !in newArenas) {
                meta.match?.stop(drawBy(ARENA_REMOVED))
            }
        }
        arenas.clear()
        arenas.putAll(newArenas)
    }

    override fun hasFreeArenas(): Boolean = arenas.toList().any { (_, meta) -> meta.match == null }

    override fun reserveArenaOrNull(match: ChessMatch): SimpleArena? = arenas.toList()
        .firstOrNull { (_, meta) -> meta.match == null }
        ?.also { (_, meta) -> meta.match = match }?.first

    internal val returnWorld: World
        get() = config.getString("ReturnWorld")
            ?.let { requireNotNull(Bukkit.getWorld(it)) { "Return world not found: $it" } }
            ?: Bukkit.getWorlds().first()

    internal fun freeArena(arena: SimpleArena) {
        arenas[arena]?.match = null
    }

    internal fun currentArenaName(arena: SimpleArena) = requireNotNull(arenas[arena]).name
}

class ResetPlayerEvent(val player: BukkitPlayer) : ChessEvent {
    override val type get() = BukkitChessEventType.RESET_PLAYER
}

@Serializable
class SimpleArena internal constructor(
    @Contextual private val world: World = Bukkit.getWorlds().first(),
    override val tileSize: Int = 3,
    private val offset: Loc = Loc(0, 101, 0)
) : Arena {
    val name: String get() = SimpleArenaManager.currentArenaName(this)
    @Transient
    private val boardStartLoc: Loc = offset + Loc(8, 0, 8)
    override val boardStart: Location get() = boardStartLoc.toLocation(world)
    @Transient
    private val capturedStartLoc = byColor(
        boardStartLoc + Loc(8 * tileSize - 1, 0, -3),
        boardStartLoc + Loc(0, 0, 8 * tileSize + 2)
    )
    override val capturedStart: ByColor<Location> get() = byColor { capturedStartLoc[it].toLocation(world) }
    @Transient
    private val spawn: Loc = offset + Loc(4, 0, 4)
    private val spawnLocation: Location get() = spawn.toLocation(world)

    fun equivalent(other: SimpleArena) = world == other.world && tileSize == other.tileSize && offset == other.offset

    override fun registerEvents(match: ChessMatch, events: ChessEventRegistry) {
        events.register(ChessEventType.BASE) {
            if (it == ChessBaseEvent.CLEAR || it == ChessBaseEvent.PANIC) SimpleArenaManager.freeArena(this)
        }
        events.registerR(BukkitChessEventType.SPECTATOR) {
            when (dir) {
                PlayerDirection.JOIN -> player.resetSpectator()
                PlayerDirection.LEAVE -> player.leave()
            }
        }
        events.registerR(BukkitChessEventType.PLAYER) {
            when (dir) {
                PlayerDirection.JOIN -> player.reset()
                PlayerDirection.LEAVE -> player.leave()
            }
        }
        events.registerR(BukkitChessEventType.RESET_PLAYER) { player.reset() }
        events.registerR(BukkitChessEventType.MATCH_INFO) {
            text("Arena: $name\n")
        }
    }

    private fun BukkitPlayer.leave() {
        entity?.run {
            for (e in activePotionEffects)
                removePotionEffect(e.type)
            teleport(SimpleArenaManager.returnWorld.spawnLocation)
            gameMode = GameMode.SURVIVAL
            allowFlight = false
            isFlying = false
        }
    }

    private fun BukkitPlayer.resetSpectator() {
        entity?.run {
            teleport(spawnLocation)
            inventory.clear()
            gameMode = GameMode.SPECTATOR
            allowFlight = true
            isFlying = true
        }
    }

    private fun BukkitPlayer.reset() {
        entity?.run {
            health = 20.0
            foodLevel = 20
            for (e in activePotionEffects)
                removePotionEffect(e.type)
            addPotionEffect(PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 10, false, false, false))
            teleport(spawnLocation)
            inventory.clear()
            inventory.setItem(0, currentSide?.held?.piece?.item)
            gameMode = GameMode.SURVIVAL
            allowFlight = true
            isFlying = true
        }
    }

}