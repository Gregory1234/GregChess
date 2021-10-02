package gregc.gregchess.chess

import gregc.gregchess.ClassRegisteredSerializer
import gregc.gregchess.RegistryType
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable(with = ChessPlayerInfoSerializer::class)
interface ChessPlayerInfo {
    val name: String
    fun getPlayer(color: Color, game: ChessGame): ChessPlayer
}

object ChessPlayerInfoSerializer :
    ClassRegisteredSerializer<ChessPlayerInfo>("ChessPlayerInfo", RegistryType.PLAYER_TYPE)

abstract class ChessPlayer(val info: ChessPlayerInfo, val color: Color, val game: ChessGame) {

    val name = info.name

    var held: BoardPiece? = null
        set(v) {
            v?.let {
                game.board[it.pos]?.moveMarker = Floor.NOTHING
                game.board[it.pos]?.bakedLegalMoves?.forEach { m -> m.show(game.board) }
                it.pickUp(game.board)
            }
            field?.let {
                game.board[it.pos]?.moveMarker = null
                game.board[it.pos]?.bakedLegalMoves?.forEach { m -> m.hide(game.board) }
                it.placeDown(game.board)
            }
            field = v
        }

    val opponent
        get() = game[!color]

    val hasTurn
        get() = game.currentTurn == color

    val pieces
        get() = game.board.piecesOf(color)

    val king
        get() = game.board.kingOf(color)

    open fun init() {}
    open fun stop() {}
    open fun startTurn() {}

}

class EnginePlayer(val engine: ChessEngine, color: Color, game: ChessGame) : ChessPlayer(engine, color, game) {

    override fun toString() = "EnginePlayer(engine=$engine, color=$color)"

    override fun stop() = engine.stop()

    override fun startTurn() {
        game.coroutineScope.launch {
            try {
                val str = engine.getMove(game.board.getFEN())
                val origin = Pos.parseFromString(str.take(2))
                val target = Pos.parseFromString(str.drop(2).take(2))
                val promotion = str.drop(4).firstOrNull()?.let { PieceType.chooseByChar(game.variant.pieceTypes, it) }
                val move = game.board.getMoves(origin).first { it.display == target }
                move.getTrait<PromotionTrait>()?.promotion = promotion?.of(move.piece.color)
                game.finishMove(move)
            } catch (e: Exception) {
                e.printStackTrace()
                game.stop(drawBy(EndReason.ERROR))
            }
        }
    }
}