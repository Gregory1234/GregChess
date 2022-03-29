package gregc.gregchess.player

import gregc.gregchess.*
import gregc.gregchess.board.FEN
import gregc.gregchess.game.ChessGame
import gregc.gregchess.move.trait.promotionTrait
import gregc.gregchess.piece.PieceType
import gregc.gregchess.piece.of
import gregc.gregchess.results.EndReason
import gregc.gregchess.results.drawBy
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer

interface ChessEngine {
    val name: String
    val type: ChessPlayerType<out @SelfType ChessEngine>
    fun stop()
    suspend fun setOption(name: String, value: String)
    suspend fun sendCommand(command: String)
    suspend fun getMove(fen: FEN): String
}

fun ChessEngine.toPlayer() = ChessPlayer(type, this)

fun <T : ChessEngine> enginePlayerType(serializer: KSerializer<T>) : ChessPlayerType<T> = ChessPlayerType(serializer, { it.name }, ::EngineChessSide)

@Suppress("UNCHECKED_CAST")
class EngineChessSide<T : ChessEngine>(val engine: T, color: Color, game: ChessGame)
    : ChessSide<T>(engine.type as ChessPlayerType<T>, engine, color, game) {

    override fun toString() = "EngineChessSide(engine=$engine, color=$color)"

    override fun clear() = engine.stop()

    override fun startTurn() {
        game.coroutineScope.launch {
            try {
                val str = engine.getMove(game.board.getFEN())
                val origin = Pos.parseFromString(str.take(2))
                val target = Pos.parseFromString(str.drop(2).take(2))
                val promotion = str.drop(4).firstOrNull()?.let { PieceType.chooseByChar(game.variant.pieceTypes, it) }
                val move = game.board.getMoves(origin).first { it.display == target }
                move.promotionTrait?.promotion = promotion?.of(move.main.color)
                game.finishMove(move)
            } catch (e: Exception) {
                game.stop(drawBy(EndReason.ERROR))
                throw e
            }
        }
    }
}

class NoEngineMoveException(fen: FEN) : Exception(fen.toString())