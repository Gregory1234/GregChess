package gregc.gregchess.fabric.chess

import gregc.gregchess.fabric.FabricRegistry
import gregc.gregchess.fabric.chess.component.ChessFloorRenderer
import gregc.gregchess.fabric.chess.player.FabricChessSide
import gregc.gregchess.move.Move
import gregc.gregchess.move.trait.*
import gregc.gregchess.piece.Piece
import gregc.gregchess.registry.*
import gregc.gregchess.results.GameResults
import gregc.gregchess.util.Pos
import gregc.gregchess.variant.ChessVariant
import net.minecraft.item.Item
import net.minecraft.text.Text
import net.minecraft.text.TranslatableText
import net.minecraft.util.Identifier

val NameRegistered.id get() = Identifier(module.namespace, name)

val Piece.block get() = FabricRegistry.PIECE_BLOCK[this]
val Piece.item: Item get() = block.asItem()

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

val GameResults.text: Text get() = TranslatableText("end_reason.${endReason.module.namespace}.${endReason.name}", *args.toTypedArray())

fun Pos.toLong() = (file.toLong() shl 32) or rank.toLong()

fun Pos.Companion.fromLong(v: Long) = Pos((v shr 32).toInt(), v.toInt())
