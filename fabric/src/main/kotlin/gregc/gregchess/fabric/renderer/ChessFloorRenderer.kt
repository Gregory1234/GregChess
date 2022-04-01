package gregc.gregchess.fabric.renderer

import gregc.gregchess.Pos
import gregc.gregchess.fabric.FabricRegistry
import gregc.gregchess.fabric.block.Floor
import gregc.gregchess.fabric.player.FabricChessSide
import gregc.gregchess.game.ChessGame
import gregc.gregchess.move.Move
import gregc.gregchess.move.trait.*
import gregc.gregchess.variant.ChessVariant


fun interface ChessFloorRenderer {
    fun ChessGame.getFloorMaterial(p: Pos): Floor
}

fun simpleFloorRenderer(specialSquares: Collection<Pos> = emptyList()) = ChessFloorRenderer { p ->
    val heldPiece = (currentSide as? FabricChessSide)?.held
    fun Move.getFloor(): Floor {
        if (castlesTrait != null || promotionTrait != null)
            return Floor.SPECIAL
        captureTrait?.let {
            if (board[it.capture]?.piece != null)
                return Floor.CAPTURE
        }
        return Floor.MOVE
    }
    when(p) {
        heldPiece?.pos -> Floor.NOTHING
        in heldPiece?.getLegalMoves(board).orEmpty().map { it.display } ->
            heldPiece?.getLegalMoves(board).orEmpty().first { it.display == p }.getFloor()
        board.lastNormalMove?.origin -> Floor.LAST_START
        board.lastNormalMove?.display -> Floor.LAST_END
        in specialSquares -> Floor.OTHER
        else -> if ((p.file + p.rank) % 2 == 0) Floor.DARK else Floor.LIGHT
    }
}

val ChessVariant.floorRenderer: ChessFloorRenderer
    get() = FabricRegistry.FLOOR_RENDERER[this]