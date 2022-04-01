package gregc.gregchess.bukkit.renderer

import gregc.gregchess.*
import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.game.*
import gregc.gregchess.bukkit.piece.item
import gregc.gregchess.bukkit.player.*
import gregc.gregchess.game.*
import gregc.gregchess.results.*
import org.bukkit.*
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
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

    fun nextArenaOrNull(): A?

    fun nextArena(): A = nextArenaOrNull() ?: throw NoFreeArenasException()
}

abstract class Arena(val name: String) : ChessListener {

    var game: ChessGame? = null

    abstract val boardStart: Location
    abstract val tileSize: Int
    abstract val capturedStart: ByColor<Location>

}

object SimpleArenaManager : ArenaManager<SimpleArena>, BukkitRegistering {
    @JvmField
    @Register(data = ["quick"])
    val ARENA_REMOVED = DrawEndReason(EndReason.Type.EMERGENCY)

    private val arenas = mutableListOf<SimpleArena>()

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

        val thisArena = arenas.firstOrNull {
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
        arenas.forEach {
            it.game?.stop(drawBy(ARENA_REMOVED))
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
            }.orEmpty()
        arenas.forEach {
            if (it !in newArenas)
                it.game?.stop(drawBy(ARENA_REMOVED))
        }
        arenas.clear()
        arenas.addAll(newArenas)
    }

    override fun nextArenaOrNull(): SimpleArena? = arenas.toList().firstOrNull { it.game == null }

    internal val returnWorld: World
        get() = config.getString("ReturnWorld")
            ?.let { requireNotNull(Bukkit.getWorld(it)) { "Return world not found: $it" } }
            ?: Bukkit.getWorlds().first()
}

class ResetPlayerEvent(val player: Player) : ChessEvent

class SimpleArena internal constructor(
    name: String,
    private val world: World,
    override val tileSize: Int,
    internal val offset: Loc
) : Arena(name) {
    private val boardStartLoc: Loc = offset + Loc(8, 0, 8)
    override val boardStart: Location get() = boardStartLoc.toLocation(world)
    private val capturedStartLoc = byColor(
        boardStartLoc + Loc(8 * tileSize - 1, 0, -3),
        boardStartLoc + Loc(0, 0, 8 * tileSize + 2)
    )
    override val capturedStart: ByColor<Location> get() = byColor { capturedStartLoc[it].toLocation(world) }
    private val spawn: Loc = offset + Loc(4, 0, 4)
    private val spawnLocation: Location get() = spawn.toLocation(world)

    @ChessEventHandler
    fun onBaseEvent(e: GameBaseEvent) {
        if (e == GameBaseEvent.CLEAR || e == GameBaseEvent.PANIC) game = null
        if (e == GameBaseEvent.PANIC)
            for (p in game?.sides?.toList().orEmpty())
                if (p is BukkitChessSide)
                    p.bukkit?.leave()
    }

    private fun Player.leave() {
        for (e in activePotionEffects)
            removePotionEffect(e.type)
        teleport(SimpleArenaManager.returnWorld.spawnLocation)
        gameMode = GameMode.SURVIVAL
        allowFlight = false
        isFlying = false
    }

    @ChessEventHandler
    fun spectatorEvent(e: SpectatorEvent) {
        when (e.dir) {
            PlayerDirection.JOIN -> {
                e.player.teleport(spawnLocation)
                e.player.inventory.clear()
                e.player.gameMode = GameMode.SPECTATOR
                e.player.allowFlight = true
                e.player.isFlying = true
            }
            PlayerDirection.LEAVE -> {
                e.player.leave()
            }
        }
    }

    private fun Player.reset() {
        health = 20.0
        foodLevel = 20
        for (e in activePotionEffects)
            removePotionEffect(e.type)
        addPotionEffect(PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 10, false, false, false))
        teleport(spawnLocation)
        inventory.clear()
        inventory.setItem(0, chess?.held?.piece?.item)
        if (isAdmin)
            gameMode = GameMode.CREATIVE
        else {
            gameMode = GameMode.SURVIVAL
            allowFlight = true
            isFlying = true
        }
    }

    @ChessEventHandler
    fun playerEvent(e: PlayerEvent) = when (e.dir) {
        PlayerDirection.JOIN -> e.player.reset()
        PlayerDirection.LEAVE -> e.player.leave()
    }

    @ChessEventHandler
    fun resetPlayer(e: ResetPlayerEvent) = e.player.reset()

}