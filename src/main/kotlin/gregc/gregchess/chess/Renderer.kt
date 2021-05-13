package gregc.gregchess.chess

import gregc.gregchess.*
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.entity.Player

class Renderer(private val game: ChessGame): ChessGame.Component {

    private lateinit var arena: ChessArena

    private val world
        get() = arena.world

    val arenaName
        get() = arena.name

    val spawnLocation
        get() = world.spawnLocation

    fun getPos(loc: Loc) =
        ChessPosition(Math.floorDiv(4 * 8 - 1 - loc.x, 3), Math.floorDiv(loc.z - 8, 3))

    fun getPieceLoc(pos: ChessPosition) =
        Loc(4 * 8 - 2 - pos.file * 3, 102, pos.rank * 3 + 8 + 1)

    fun getCapturedLoc(pos: Pair<Int, Int>, by: ChessSide): Loc {
        return when (by) {
            ChessSide.WHITE -> Loc(4 * 8 - 1 - 2 * pos.first, 101, 8 - 3 - 2 * pos.second)
            ChessSide.BLACK -> Loc(8 + 2 * pos.first, 101, 8 * 4 + 2 + 2 * pos.second)
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
        (Pair(-1, -1)..Pair(1, 1)).forEach { (i, j) ->
            world.getBlockAt(x + i, y - 1, z + j).type = floor
        }
    }

    fun addPlayer(p: Player) {
        arena.teleport(p)
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
        for (i in 0 until 8 * 5) {
            for (j in 0 until 8 * 5) {
                world.getBlockAt(i, 100, j).type = Material.DARK_OAK_PLANKS
                if (i in 8 - 1..8 * 4 && j in 8 - 1..8 * 4) {
                    world.getBlockAt(i, 101, j).type = Material.DARK_OAK_PLANKS
                }
            }
        }
    }

    fun removeBoard() {
        for (i in 0 until 8 * 5) {
            for (j in 0 until 8 * 5) {
                for (k in 100..105)
                    world.getBlockAt(i, k, j).type = Material.AIR
            }
        }
    }

    fun evacuate() {
        world.players.forEach(arena::safeExit)
    }

    override fun spectatorJoin(p: Player) {
        arena.teleportSpectator(p)
    }

    override fun spectatorLeave(p: Player) {
        arena.exit(p)
    }

    fun removePlayer(p: Player) {
        arena.exit(p)
    }

    fun clearArena() {
        arena.clear()
    }

    fun isOn(a: ChessArena) = a == arena
    fun resetPlayer(p: Player) {
        p.playerData = arena.defaultData
        game[p]?.held?.let { p.inventory.setItem(0, it.item )}
    }
}