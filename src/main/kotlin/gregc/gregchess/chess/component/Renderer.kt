package gregc.gregchess.chess.component

import gregc.gregchess.*
import gregc.gregchess.chess.*
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.entity.Player
import kotlin.math.floor

class Renderer(private val game: ChessGame, private val settings: Settings): Component {

    class Settings(val tileSize: Int) {
        fun getComponent(game: ChessGame) = Renderer(game, this)

        internal val highHalfTile
            get() = floor(tileSize.toDouble()/2).toInt()
        internal val lowHalfTile
            get() = floor((tileSize.toDouble()-1)/2).toInt()
    }

    private lateinit var arena: ChessArena

    private val world
        get() = arena.world

    val arenaName
        get() = arena.name

    val spawnLocation
        get() = world.spawnLocation

    fun getPos(loc: Loc) =
        ChessPosition(Math.floorDiv((settings.tileSize+1) * 8 - 1 - loc.x, settings.tileSize), Math.floorDiv(loc.z - 8, settings.tileSize))

    fun getPieceLoc(pos: ChessPosition) =
        Loc((settings.tileSize+1) * 8 - 1 - settings.highHalfTile - pos.file * settings.tileSize, 102, pos.rank * settings.tileSize + 8 + settings.lowHalfTile)

    fun getCapturedLoc(pos: Pair<Int, Int>, by: ChessSide): Loc {
        return when (by) {
            ChessSide.WHITE -> Loc((settings.tileSize+1) * 8 - 1 - 2 * pos.first, 101, 8 - 3 - 2 * pos.second)
            ChessSide.BLACK -> Loc(8 + 2 * pos.first, 101, 8 * (settings.tileSize+1) + 2 + 2 * pos.second)
        }
    }

    fun renderPiece(loc: Loc, structure: List<Material>) {
        structure.forEachIndexed { i, m ->
            world.getBlockAt(loc.copy(y = loc.y + i)).type = m
        }
    }

    fun <R> doAt(pos: ChessPosition, f: (World, Location) -> R) = getPieceLoc(pos).doIn(world, f)

    fun playPieceSound(pos: ChessPosition, sound: Sound) = doAt(pos) { world, l -> world.playSound(l, sound) }

    fun fillFloor(pos: ChessPosition, floor: Material) {
        val (x, y, z) = getPieceLoc(pos)
        val mi = -settings.lowHalfTile
        val ma = settings.highHalfTile
        (Pair(mi, mi)..Pair(ma, ma)).forEach { (i, j) ->
            world.getBlockAt(x + i, y - 1, z + j).type = floor
        }
    }

    @GameEvent(GameBaseEvent.INIT, TimeModifier.EARLY)
    fun init() {
        game.forEachPlayer(arena::teleport)
    }

    fun checkForFreeArenas(): Boolean {
        val a = ChessManager.nextArena()
        if (a != null) {
            a.reserve()
            arena = a
        }
        return a != null
    }

    fun renderBoardBase() {
        for (i in 0 until 8 * (settings.tileSize+2)) {
            for (j in 0 until 8 * (settings.tileSize+2)) {
                world.getBlockAt(i, 100, j).type = Material.DARK_OAK_PLANKS
                if (i in 8 - 1..8 * (settings.tileSize+1) && j in 8 - 1..8 * (settings.tileSize+1)) {
                    world.getBlockAt(i, 101, j).type = Material.DARK_OAK_PLANKS
                }
            }
        }
    }

    fun removeBoard() {
        for (i in 0 until 8 * (settings.tileSize+2)) {
            for (j in 0 until 8 * (settings.tileSize+2)) {
                for (k in 100..105)
                    world.getBlockAt(i, k, j).type = Material.AIR
            }
        }
    }

    fun evacuate() {
        world.players.forEach(arena::safeExit)
    }

    @GameEvent(GameBaseEvent.SPECTATOR_JOIN, TimeModifier.EARLY)
    fun spectatorJoin(p: Player) {
        arena.teleportSpectator(p)
    }

    @GameEvent(GameBaseEvent.SPECTATOR_JOIN, TimeModifier.LATE)
    fun spectatorLeave(p: Player) {
        arena.exit(p)
    }

    fun removePlayer(p: Player) {
        arena.exit(p)
    }

    @GameEvent(GameBaseEvent.VERY_END, TimeModifier.LATE)
    fun clearArena() {
        arena.clear()
    }

    fun isOn(a: ChessArena) = a == arena
    fun resetPlayer(p: Player) {
        p.playerData = arena.defaultData
        game[p]?.held?.let { p.inventory.setItem(0, it.item )}
    }
}