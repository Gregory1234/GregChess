package gregc.gregchess.chess.player

import gregc.gregchess.chess.ChessGame
import gregc.gregchess.chess.Color
import gregc.gregchess.register
import gregc.gregchess.registry.*
import kotlinx.serialization.*
import kotlin.reflect.KClass

@Serializable(with = ChessPlayerType.Serializer::class)
class ChessPlayerType<T : ChessPlayer>(val cl: KClass<T>) : NameRegistered {
    object Serializer : NameRegisteredSerializer<ChessPlayerType<*>>("ChessPlayerType", Registry.PLAYER_TYPE)

    override val key get() = Registry.PLAYER_TYPE[this]

    override fun toString(): String = Registry.PLAYER_TYPE.simpleElementToString(this)

    companion object {
        internal val AUTO_REGISTER = AutoRegisterType(ChessPlayerType::class) { m, n, _ -> register(m, n) }
    }
}

// TODO: combine with ChessSide
@Serializable(with = ChessPlayerSerializer::class)
interface ChessPlayer {
    val type: ChessPlayerType<*>
    val name: String
    fun initSide(color: Color, game: ChessGame): ChessSide<*>
}


@OptIn(InternalSerializationApi::class)
object ChessPlayerSerializer : KeyRegisteredSerializer<ChessPlayerType<*>, ChessPlayer>("ChessPlayer", ChessPlayerType.Serializer) {
    @Suppress("UNCHECKED_CAST")
    override val ChessPlayerType<*>.serializer get() = cl.serializer() as KSerializer<ChessPlayer>
    override val ChessPlayer.key: ChessPlayerType<*> get() = type
}

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

    open fun start() {}
    open fun stop() {}
    open fun clear() {}
    open fun startTurn() {}

}