package gregc.gregchess.chess.player

import gregc.gregchess.chess.ChessGame
import gregc.gregchess.chess.Color
import gregc.gregchess.registry.ClassRegisteredSerializer
import gregc.gregchess.registry.Registry
import kotlinx.serialization.Serializable

@Serializable(with = ChessPlayerSerializer::class)
interface ChessPlayer {
    val name: String
    fun initSide(color: Color, game: ChessGame): ChessSide<*>
}

@Suppress("UNCHECKED_CAST")
object ChessPlayerSerializer : ClassRegisteredSerializer<ChessPlayer>("ChessPlayer", Registry.PLAYER_CLASS)

abstract class ChessSide<T : ChessPlayer>(val player: T, val color: Color, val game: ChessGame) {

    val name: String
        get() = player.name

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