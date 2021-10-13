package gregc.gregchess.chess

import gregc.gregchess.chess.piece.BoardPiece
import gregc.gregchess.registry.ClassRegisteredSerializer
import gregc.gregchess.registry.RegistryType
import kotlinx.serialization.Serializable

// TODO: replace ChessPlayerInfo with lambdas
@Serializable(with = ChessPlayerInfoSerializer::class)
interface ChessPlayerInfo {
    val name: String
    fun getPlayer(color: Color, game: ChessGame): ChessPlayer
}

object ChessPlayerInfoSerializer :
    ClassRegisteredSerializer<ChessPlayerInfo>("ChessPlayerInfo", RegistryType.PLAYER_TYPE)

abstract class ChessPlayer(val info: ChessPlayerInfo, val color: Color, val game: ChessGame) {

    val name = info.name

    // TODO: remove held
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