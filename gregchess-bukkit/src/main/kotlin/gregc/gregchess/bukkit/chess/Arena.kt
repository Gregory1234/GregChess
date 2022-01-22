package gregc.gregchess.bukkit.chess

import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.chess.component.*
import gregc.gregchess.bukkit.chess.player.*
import gregc.gregchess.chess.*
import gregc.gregchess.registry.Register
import org.bukkit.*
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

class NoFreeArenasException : NoSuchElementException("No free arenas")

abstract class Arena(val name: String) : ChessListener {

    companion object {
        @JvmField
        @Register(data = ["quick"])
        val ARENA_REMOVED = DrawEndReason(EndReason.Type.EMERGENCY)

        private val arenas = mutableListOf<Arena>()

        private fun ConfigurationSection.getLoc(path: String, def: Loc): Loc = getConfigurationSection(path)?.let {
            Loc(getInt("x", def.x), getInt("y", def.y), getInt("z", def.z))
        } ?: def

        private fun ConfigurationSection.parseArena(name: String): Arena? {
            GregChess.logger.info("Loading arena $name")
            val start = getLoc("Start", Loc(0, 101, 0))

            val world = Bukkit.getWorld(getString("World") ?: return null) ?: return null

            val tileSize = getInt("TileSize", 3)

            GregChess.logger.info("Loaded arena $name")

            return SimpleArena(name, world, tileSize, start)
        }

        fun reloadArenas() {
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
                it.game?.stop(drawBy(ARENA_REMOVED))
            }
            arenas.clear()
            arenas.addAll(newArenas)
        }

        fun nextArenaOrNull(): Arena? = arenas.toList().firstOrNull { it.game == null }

        fun nextArena(): Arena = nextArenaOrNull() ?: throw NoFreeArenasException()

        @JvmStatic
        protected val returnWorld: World
            get() = config.getString("ReturnWorld")
                ?.let { requireNotNull(Bukkit.getWorld(it)) { "Return world not found: $it" } }
                ?: Bukkit.getWorlds().first()
    }

    var game: ChessGame? = null

    abstract val boardStart: Location
    abstract val tileSize: Int
    abstract val capturedStart: ByColor<Location>

}

class ResetPlayerEvent(val player: Player) : ChessEvent

class SimpleArena internal constructor(
    name: String,
    private val world: World,
    override val tileSize: Int,
    offset: Loc
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
    fun onStop(e: GameStopStageEvent) {
        if (e == GameStopStageEvent.VERY_END || e == GameStopStageEvent.PANIC) game = null
        if (e == GameStopStageEvent.PANIC)
            for (p in game?.sides?.toList().orEmpty())
                if (p is BukkitChessSide)
                    p.bukkit?.leave()
    }

    private fun Player.leave() {
        for (e in activePotionEffects)
            removePotionEffect(e.type)
        teleport(returnWorld.spawnLocation)
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