package gregc.gregchess.player

import gregc.gregchess.AutoRegisterType
import gregc.gregchess.Color
import gregc.gregchess.game.ChessGame
import gregc.gregchess.registry.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable(with = ChessPlayerType.Serializer::class)
class ChessPlayerType<P: Any>(
    val serializer: KSerializer<P>,
    val nameOf: (P) -> String,
    val initSide: (P, Color, ChessGame) -> ChessSide<P>
) : NameRegistered {
    object Serializer : NameRegisteredSerializer<ChessPlayerType<*>>("ChessPlayerType", Registry.PLAYER_TYPE)

    override val key get() = Registry.PLAYER_TYPE[this]

    override fun toString(): String = Registry.PLAYER_TYPE.simpleElementToString(this)

    fun of(data: P) = ChessPlayer(this, data)

    companion object {
        internal val AUTO_REGISTER = AutoRegisterType(ChessPlayerType::class) { m, n, _ -> Registry.PLAYER_TYPE[m, n] = this }
    }
}

// TODO: add an interface for human players
abstract class ChessSide<P : Any>(private val playerType: ChessPlayerType<P>, private val playerValue: P, val color: Color, val game: ChessGame) {

    val player: ChessPlayer get() = playerType.of(playerValue)

    val name: String get() = playerType.nameOf(playerValue)

    val opponent
        get() = game[!color]

    val hasTurn
        get() = game.currentTurn == color

    val pieces
        get() = game.board.piecesOf(color)

    val king
        get() = game.board.kingOf(color)

    open fun start() {}
    open fun stop() {}
    open fun clear() {}
    open fun startTurn() {}

}