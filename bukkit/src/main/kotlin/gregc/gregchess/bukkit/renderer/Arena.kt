package gregc.gregchess.bukkit.renderer

import gregc.gregchess.*
import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.match.BukkitChessEventType
import gregc.gregchess.bukkit.match.PlayerDirection
import gregc.gregchess.bukkit.piece.item
import gregc.gregchess.bukkit.player.*
import gregc.gregchess.bukkit.registry.BukkitRegistry
import gregc.gregchess.bukkit.registry.getFromRegistry
import gregc.gregchess.match.*
import gregc.gregchess.results.*
import org.bukkit.*
import org.bukkit.configuration.ConfigurationSection
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
    val name: String

    fun registerEvents(match: ChessMatch, eventManager: ChessEventManager)
    val boardStart: Location
    val tileSize: Int
    val capturedStart: ByColor<Location>
}

class ArenaMetadata(var match: ChessMatch? = null)

object SimpleArenaManager : ArenaManager<SimpleArena>, BukkitRegistering {
    @JvmField
    @Register(data = ["quick"])
    val ARENA_REMOVED = DrawEndReason(EndReason.Type.EMERGENCY)

    private val arenas = mutableMapOf<SimpleArena, ArenaMetadata>()

    private fun ConfigurationSection.getLoc(path: String, def: Loc): Loc = getConfigurationSection(path)?.let {
        Loc(getInt("x", def.x), getInt("y", def.y), getInt("z", def.z))
    } ?: def

    private fun ConfigurationSection.parseArena(name: String): SimpleArena? {
        GregChess.logger.info("Loading arena $name")
        val start = getLoc("Start", Loc(0, 101, 0))

        val world = Bukkit.getWorld(getString("World") ?: Bukkit.getWorlds().first().name) ?: run {
            GregChess.logger.warn("World not found: ${getString("World")}!")
            return null
        }

        val tileSize = getInt("TileSize", 3)

        val thisArena = arenas.keys.firstOrNull {
            it.name == name && it.offset == start && it.boardStart.world == world && it.tileSize == tileSize
        }

        return if (thisArena != null) {
            GregChess.logger.info("Arena $name did not change")

            thisArena
        } else {
            GregChess.logger.info("Loaded arena $name")

            SimpleArena(name, world, tileSize, start)
        }
    }

    override fun unloadArenas() {
        arenas.forEach { (_, meta) ->
            meta.match?.stop(drawBy(ARENA_REMOVED))
        }
        arenas.clear()
    }

    override fun reloadArenas() {
        val newArenas = config.getConfigurationSection("ChessArenas")
            ?.getKeys(false)
            ?.mapNotNull { name ->
                val section = config.getConfigurationSection("ChessArenas.$name")
                if (section != null)
                    section.parseArena(name)
                else {
                    GregChess.logger.warn("Arena $name has a wrong format")
                    null
                }
            }.orEmpty().associateWith { arenas[it] ?: ArenaMetadata() }
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

    internal fun currentArenaMatch(arena: SimpleArena) = arenas[arena]?.match
}

class ResetPlayerEvent(val player: BukkitPlayer) : ChessEvent {
    override val type get() = BukkitChessEventType.RESET_PLAYER
}

class SimpleArena internal constructor(
    override val name: String,
    private val world: World,
    override val tileSize: Int,
    internal val offset: Loc
) : Arena {
    private val boardStartLoc: Loc = offset + Loc(8, 0, 8)
    override val boardStart: Location get() = boardStartLoc.toLocation(world)
    private val capturedStartLoc = byColor(
        boardStartLoc + Loc(8 * tileSize - 1, 0, -3),
        boardStartLoc + Loc(0, 0, 8 * tileSize + 2)
    )
    override val capturedStart: ByColor<Location> get() = byColor { capturedStartLoc[it].toLocation(world) }
    private val spawn: Loc = offset + Loc(4, 0, 4)
    private val spawnLocation: Location get() = spawn.toLocation(world)

    override fun registerEvents(match: ChessMatch, eventManager: ChessEventManager) {
        eventManager.registerEvent(ChessEventType.BASE) {
            if (it == ChessBaseEvent.CLEAR || it == ChessBaseEvent.PANIC) SimpleArenaManager.freeArena(this)
            if (it == ChessBaseEvent.PANIC)
                for (p in match.sides.toList())
                    if (p is BukkitChessSide)
                        p.player.leave()
        }
        eventManager.registerEventR(BukkitChessEventType.SPECTATOR) {
            when (dir) {
                PlayerDirection.JOIN -> player.resetSpectator()
                PlayerDirection.LEAVE -> player.leave()
            }
        }
        eventManager.registerEventR(BukkitChessEventType.PLAYER) {
            when (dir) {
                PlayerDirection.JOIN -> player.reset()
                PlayerDirection.LEAVE -> player.leave()
            }
        }
        eventManager.registerEventR(BukkitChessEventType.RESET_PLAYER) { player.reset() }
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
            inventory.setItem(0, currentChessSide?.held?.piece?.item)
            gameMode = GameMode.SURVIVAL
            allowFlight = true
            isFlying = true
        }
    }

}