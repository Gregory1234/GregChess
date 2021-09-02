package gregc.gregchess.chess

import gregc.gregchess.*
import kotlinx.serialization.Serializable

@Serializable(with = ChessPlayerInfoSerializer::class)
interface ChessPlayerInfo {
    val name: String
    fun getPlayer(side: Side, game: ChessGame): ChessPlayer
}

object ChessPlayerInfoSerializer: ClassRegisteredSerializer<ChessPlayerInfo>("ChessPlayerInfo", RegistryType.PLAYER_TYPE)

abstract class ChessPlayer(val info: ChessPlayerInfo, val side: Side, val game: ChessGame) {

    val name = info.name

    var held: BoardPiece? = null
        set(v) {
            v?.let {
                it.square.moveMarker = Floor.NOTHING
                it.square.bakedLegalMoves?.forEach { m -> m.show(game.board) }
                it.pickUp()
            }
            field?.let {
                it.square.moveMarker = null
                it.square.bakedLegalMoves?.forEach { m -> m.hide(game.board) }
                it.placeDown()
            }
            field = v
        }

    val opponent
        get() = game[!side]

    val hasTurn
        get() = game.currentTurn == side

    val pieces
        get() = game.board.piecesOf(side)

    val king
        get() = game.board.kingOf(side)

    open fun init() {}
    open fun stop() {}
    open fun startTurn() {}

}

class EnginePlayer(val engine: ChessEngine, side: Side, game: ChessGame) : ChessPlayer(engine, side, game) {

    override fun toString() = "EnginePlayer(engine=$engine, side=$side)"

    override fun stop() = engine.stop()

    override fun startTurn() {
        interact {
            try {
                val str = engine.getMove(game.board.getFEN())
                val origin = Pos.parseFromString(str.take(2))
                val target = Pos.parseFromString(str.drop(2).take(2))
                val promotion = str.drop(4).firstOrNull()?.let { PieceType.chooseByChar(game.variant.pieceTypes, it) }
                val move = game.board.getMoves(origin).first { it.display == target }
                move.getTrait<PromotionTrait>()?.promotion = promotion?.of(move.piece.side)
                game.finishMove(move)
            } catch (e: Exception) {
                e.printStackTrace()
                game.stop(drawBy(EndReason.ERROR))
            }
        }
    }
}