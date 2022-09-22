package gregc.gregchess.bukkit.renderer

import gregc.gregchess.*
import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.event.BukkitChessEventType
import gregc.gregchess.bukkit.event.PlayerDirection
import gregc.gregchess.bukkit.piece.item
import gregc.gregchess.bukkit.player.BukkitPlayer
import gregc.gregchess.bukkitutils.CommandException
import gregc.gregchess.bukkitutils.serialization.BukkitConfig
import gregc.gregchess.bukkitutils.serialization.decodeFromPath
import gregc.gregchess.event.*
import gregc.gregchess.match.ChessMatch
import gregc.gregchess.results.*
import kotlinx.serialization.*
import org.bukkit.*
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

class NoFreeArenasException : NoSuchElementException("No free arenas")

class ArenaMetadata(val name: String, var match: ChessMatch? = null)

// TODO: make arena manager replaceable again
object ArenaManager : BukkitRegistering {

    private val NO_ARENAS = err("NoArenas")

    fun validateFreeArenas() {
        if (!ArenaManager.hasFreeArenas())
            throw CommandException(NO_ARENAS)
    }

    @JvmField
    @Register(data = ["quick"])
    val ARENA_REMOVED = DrawEndReason(EndReason.Type.EMERGENCY)

    private val arenas = mutableMapOf<Arena, ArenaMetadata>()

    fun reloadArenas() {
        val configFormat = BukkitConfig(config, defaultModule())
        val newArenas = config.getConfigurationSection("ChessArenas")
            ?.getKeys(false)
            ?.mapNotNull { name ->
                try {
                    val newArena = configFormat.decodeFromPath<Arena>("ChessArenas.$name")
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

    fun hasFreeArenas(): Boolean = arenas.toList().any { (_, meta) -> meta.match == null }

    fun reserveArenaOrNull(match: ChessMatch): Arena? = arenas.toList()
        .firstOrNull { (_, meta) -> meta.match == null }
        ?.also { (_, meta) -> meta.match = match }?.first

    fun reserveArena(match: ChessMatch): Arena = reserveArenaOrNull(match) ?: throw NoFreeArenasException()

    internal val returnWorld: World
        get() = config.getString("ReturnWorld")
            ?.let { requireNotNull(Bukkit.getWorld(it)) { "Return world not found: $it" } }
            ?: Bukkit.getWorlds().first()

    internal fun freeArena(arena: Arena) {
        arenas[arena]?.match = null
    }

    internal fun currentArenaName(arena: Arena) = requireNotNull(arenas[arena]).name
}

class ResetPlayerEvent(val player: BukkitPlayer) : ChessEvent {
    override val type get() = BukkitChessEventType.RESET_PLAYER
}

@Serializable
class Arena internal constructor(
    @Contextual private val world: World = Bukkit.getWorlds().first(),
    val tileSize: Int = 3,
    private val offset: Loc = Loc(0, 101, 0)
) {
    val name: String get() = ArenaManager.currentArenaName(this)
    @Transient
    private val boardStartLoc: Loc = offset + Loc(8, 0, 8)
    val boardStart: Location get() = boardStartLoc.toLocation(world)
    @Transient
    private val capturedStartLoc = byColor(
        boardStartLoc + Loc(8 * tileSize - 1, 0, -3),
        boardStartLoc + Loc(0, 0, 8 * tileSize + 2)
    )
    val capturedStart: ByColor<Location> get() = byColor { capturedStartLoc[it].toLocation(world) }
    @Transient
    private val spawn: Loc = offset + Loc(4, 0, 4)
    private val spawnLocation: Location get() = spawn.toLocation(world)

    fun equivalent(other: Arena) = world == other.world && tileSize == other.tileSize && offset == other.offset

    fun registerEvents(events: EventListenerRegistry) {
        events.register(ChessEventType.BASE) {
            if (it == ChessBaseEvent.CLEAR || it == ChessBaseEvent.PANIC) ArenaManager.freeArena(this)
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
            teleport(ArenaManager.returnWorld.spawnLocation)
            inventory.clear()
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