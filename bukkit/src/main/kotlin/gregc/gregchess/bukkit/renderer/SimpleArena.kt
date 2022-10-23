package gregc.gregchess.bukkit.renderer

import gregc.gregchess.*
import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkitutils.serialization.BukkitConfig
import gregc.gregchess.bukkitutils.serialization.decodeFromPath
import gregc.gregchess.match.ChessMatch
import gregc.gregchess.registry.Register
import gregc.gregchess.results.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

object SimpleArenaManager : BukkitRegistering {

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
                    val newArenaData = configFormat.decodeFromPath<SimpleArenaData>("ChessArenas.$name")
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

    fun reserveArenaOrNull(match: ChessMatch): SimpleArena? = arenas
        .firstOrNull { it.currentMatch == null }
        ?.also { it.currentMatch = match }

    internal val returnWorld: World
        get() = config.getString("ReturnWorld")
            ?.let { requireNotNull(Bukkit.getWorld(it)) { "Return world not found: $it" } }
            ?: Bukkit.getWorlds().first()
}

@Serializable
internal data class SimpleArenaData internal constructor(
    @Contextual internal val world: World = Bukkit.getWorlds().first(),
    val tileSize: Int = 3,
    internal val offset: Loc = Loc(0, 101, 0)
)

class SimpleArena internal constructor(
    val name: String,
    internal val arenaData: SimpleArenaData
) : Arena {
    var currentMatch: ChessMatch? = null
        internal set

    override fun toString(): String = "SimpleArena(name=$name)"

    private val world: World = arenaData.world
    val tileSize: Int = arenaData.tileSize
    private val offset: Loc = arenaData.offset

    private val boardStartLoc: Loc = offset + Loc(8, 0, 8)
    val boardStart: Location get() = boardStartLoc.toLocation(world)

    private val capturedStartLoc = byColor(
        boardStartLoc + Loc(8 * tileSize - 1, 0, -3),
        boardStartLoc + Loc(0, 0, 8 * tileSize + 2)
    )
    val capturedStart: ByColor<Location> get() = byColor { capturedStartLoc[it].toLocation(world) }

    private val spawn: Loc = offset + Loc(4, 0, 4)
    val spawnLocation: Location get() = spawn.toLocation(world)

    internal fun leave(player: Player) = with(player) {
        for (e in activePotionEffects)
            removePotionEffect(e.type)
        teleport(SimpleArenaManager.returnWorld.spawnLocation)
        inventory.clear()
        gameMode = GameMode.SURVIVAL
        allowFlight = false
        isFlying = false
    }

    internal fun resetSpectator(player: Player) = with(player) {
        teleport(spawnLocation)
        inventory.clear()
        gameMode = GameMode.SPECTATOR
        allowFlight = true
        isFlying = true
    }

    internal fun reset(player: Player) = with(player) {
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

    override fun getPos(location: Location) = getPos(location.toLoc())

}