package gregc.gregchess.fabric.chess

import gregc.gregchess.chess.Pos
import gregc.gregchess.chess.move.*
import gregc.gregchess.chess.variant.ChessVariant
import gregc.gregchess.fabric.FabricRegistry
import gregc.gregchess.fabric.chess.component.ChessFloorRenderer
import gregc.gregchess.fabric.chess.player.FabricPlayer

fun simpleFloorRenderer(specialSquares: Collection<Pos> = emptyList()) = ChessFloorRenderer { p ->
    val heldPiece = (currentPlayer as? FabricPlayer)?.held
    fun Move.getFloor(): Floor {
        if (getTrait<CastlesTrait>() != null || getTrait<PromotionTrait>() != null)
            return Floor.SPECIAL
        getTrait<CaptureTrait>()?.let {
            if (board[it.capture]?.piece != null)
                return Floor.CAPTURE
        }
        return Floor.MOVE
    }
    when(p) {
        heldPiece?.pos -> Floor.NOTHING
        in heldPiece?.getLegalMoves(board).orEmpty().map { it.display } ->
            heldPiece?.getLegalMoves(board).orEmpty().first { it.display == p }.getFloor()
        board.lastMove?.origin -> Floor.LAST_START
        board.lastMove?.display -> Floor.LAST_END
        in specialSquares -> Floor.OTHER
        else -> if ((p.file + p.rank) % 2 == 0) Floor.DARK else Floor.LIGHT
    }
}

val ChessVariant.floorRenderer: ChessFloorRenderer
    get() = FabricRegistry.VARIANT_FLOOR_RENDERER[this]