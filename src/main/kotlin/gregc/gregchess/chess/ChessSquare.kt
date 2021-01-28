package gregc.gregchess.chess

import gregc.gregchess.chess.component.Chessboard
import org.bukkit.Material

data class ChessSquare(val pos: ChessPosition, val board: Chessboard) {
    var piece: ChessPiece? = null
    var bakedMoves: List<ChessMove>? = null

    private val baseFloor = if ((pos.file + pos.rank) % 2 == 0) Material.SPRUCE_PLANKS else Material.BIRCH_PLANKS
    var previousMoveMarker: Material? = null
    var moveMarker: Material? = null
    val floor
        get() = moveMarker ?: previousMoveMarker ?: baseFloor
    private val loc = board.renderer.getPieceLoc(pos)
    fun render() {
        board.renderer.fillFloor(pos, floor)
    }
    fun clear() {
        piece = null
        bakedMoves = null
        previousMoveMarker = null
        moveMarker = null
        board.renderer.fillFloor(pos, floor)
        board.renderer.clearPiece(pos)
    }
}
