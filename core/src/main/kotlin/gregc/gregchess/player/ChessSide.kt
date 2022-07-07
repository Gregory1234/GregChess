package gregc.gregchess.player

import gregc.gregchess.AutoRegisterType
import gregc.gregchess.Color
import gregc.gregchess.match.ChessMatch
import gregc.gregchess.registry.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable(with = ChessPlayerType.Serializer::class)
class ChessPlayerType<P: Any>(
    val serializer: KSerializer<P>,
    val nameOf: (P) -> String,
    val initSide: (P, Color, ChessMatch) -> ChessSide<P>
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
abstract class ChessSide<P : Any>(private val playerType: ChessPlayerType<P>, private val playerValue: P, val color: Color, val match: ChessMatch) {

    val player: ChessPlayer get() = playerType.of(playerValue)

    val name: String get() = playerType.nameOf(playerValue)

    val opponent
        get() = match[!color]

    val hasTurn
        get() = match.board.currentTurn == color

    val pieces
        get() = match.board.piecesOf(color)

    val king
        get() = match.board.kingOf(color)

    open fun stop() {}
    open fun clear() {}
    open fun startTurn() {}

}