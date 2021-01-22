package gregc.gregchess.chess

import gregc.gregchess.Loc
import gregc.gregchess.getBlockAt
import gregc.gregchess.playSound
import gregc.gregchess.star
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.World

data class ChessSquare(val pos: ChessPosition, private val world: World) {
    var piece: ChessPiece? = null
        set(v) {
            hide()
            field = v
            render()
        }
    var bakedMoves: List<ChessMove>? = null

    private val baseFloor = if ((pos.file + pos.rank) % 2 == 0) Material.SPRUCE_PLANKS else Material.BIRCH_PLANKS
    var previousMoveMarker: Material? = null
    var moveMarker: Material? = null
    val floor
        get() = moveMarker ?: previousMoveMarker ?: baseFloor
    private val loc = Loc(4 * 8 - 2 - pos.file * 3, 102, pos.rank * 3 + 8 + 1)
    fun render() {
        val (x,y,z) = loc
        (-1..1).star(-1..1) { i, j ->
            world.getBlockAt(x + i, y - 1, z + j).type = floor
        }
        piece?.render(world.getBlockAt(loc))
    }
    fun clear() {
        hide()
        piece = null
        bakedMoves = null
        previousMoveMarker = null
        moveMarker = null
    }

    fun hide() {
        piece?.hide(world.getBlockAt(loc))
    }

    private fun playSound(s: Sound) {
        loc.toLocation(world).playSound(s)
    }

    fun playPickUpSound() = piece?.let {playSound(it.type.pickUpSound)}
    fun playMoveSound() = piece?.let {playSound(it.type.moveSound)}
    fun playCaptureSound() = piece?.let {playSound(it.type.captureSound)}
}
