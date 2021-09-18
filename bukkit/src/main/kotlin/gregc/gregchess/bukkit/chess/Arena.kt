package gregc.gregchess.bukkit.chess

import gregc.gregchess.*
import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.chess.component.*
import gregc.gregchess.chess.*
import org.bukkit.*
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player

abstract class Arena(val name: String): ChessListener {

    companion object {
        @JvmField
        val ARENA_REMOVED = GregChessModule.register("arena_removed", DrawEndReason(EndReason.Type.EMERGENCY, true))

        @JvmField
        val NO_ARENAS = err("NoArenas")

        private val arenas = mutableListOf<Arena>()

        private fun parseArena(section: ConfigurationSection, name: String): Arena? {
            GregChess.logger.info("Loading arena $name")

            val start = Loc(
                section.getInt("Start.x", 0),
                section.getInt("Start.y", 101),
                section.getInt("Start.z", 0)
            )

            val world = Bukkit.getWorld(section.getString("World") ?: return null) ?: return null

            val tileSize = section.getInt("TileSize", 3)

            GregChess.logger.info("Loaded arena $name")

            return SimpleArena(name, world, tileSize, start)
        }

        fun reloadArenas() {
            val newArenas = BukkitGregChessModule.config.getConfigurationSection("ChessArenas")?.getKeys(false)?.mapNotNull { name ->
                BukkitGregChessModule.config.getConfigurationSection("ChessArenas.$name")?.let { parseArena(it, name) }
            }.orEmpty()
            arenas.forEach {
                it.game?.stop(drawBy(ARENA_REMOVED))
            }
            arenas.clear()
            arenas.addAll(newArenas)
        }

        fun nextArena(): Arena? = arenas.toList().firstOrNull { it.game == null }

        fun cNextArena(): Arena = nextArena().cNotNull(NO_ARENAS)

        @JvmStatic
        protected val returnWorld: World
            get() = BukkitGregChessModule.config.getString("ReturnWorld")?.let { Bukkit.getWorld(it)!! }
                ?: Bukkit.getWorlds().first()
    }

    var game: ChessGame? = null

    abstract val world: World
    abstract val boardStart: Loc
    abstract val tileSize: Int
    abstract val capturedStart: BySides<Loc>

}

class ResetPlayerEvent(val player: Player): ChessEvent

class SimpleArena(
    name: String,
    override val world: World,
    override val tileSize: Int,
    offset: Loc
) : Arena(name) {
    override val boardStart: Loc = offset + Loc(8,0,8)
    override val capturedStart = bySides(
        boardStart + Loc(8 * tileSize - 1, 0, -3),
        boardStart + Loc(0, 0, 8 * tileSize + 2)
    )
    private val spawn: Loc = offset + Loc(4, 0, 4)
    private val spawnLocation: Location get() = spawn.toLocation(world)

    @ChessEventHandler
    fun onStop(e: GameStopStageEvent) {
        if (e == GameStopStageEvent.VERY_END) game = null
        else if (e == GameStopStageEvent.PANIC)
            for (p in game?.players?.toList().orEmpty())
                if (p is BukkitPlayer)
                    p.player.leave()
    }

    private fun Player.leave() {
        teleport(returnWorld.spawnLocation)
        gameMode = GameMode.SURVIVAL
        allowFlight = false
        isFlying = false
    }

    @ChessEventHandler
    fun spectatorEvent(e: SpectatorEvent) {
        when(e.dir){
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
    fun playerEvent(e: PlayerEvent) = when(e.dir) {
        PlayerDirection.JOIN -> e.player.reset()
        PlayerDirection.LEAVE -> e.player.leave()
    }

    @ChessEventHandler
    fun resetPlayer(e: ResetPlayerEvent) = e.player.reset()

}