package gregc.gregchess.chess.player

import gregc.gregchess.chess.*
import gregc.gregchess.chess.move.PromotionTrait
import gregc.gregchess.chess.piece.PieceType
import gregc.gregchess.chess.piece.of
import kotlinx.coroutines.launch

interface ChessEngine : ChessPlayer {
    fun stop()
    suspend fun setOption(name: String, value: String)
    suspend fun sendCommand(command: String)
    suspend fun getMove(fen: FEN): String
    override fun initSide(color: Color, game: ChessGame): EngineChessSide<*> = EngineChessSide(this, color, game)
}

class EngineChessSide<T : ChessEngine>(val engine: T, color: Color, game: ChessGame) : ChessSide<T>(engine, color, game) {

    override fun toString() = "EngineChessSide(engine=$engine, color=$color)"

    override fun stop() = engine.stop()

    override fun startTurn() {
        game.coroutineScope.launch {
            try {
                val str = engine.getMove(game.board.getFEN(game))
                val origin = Pos.parseFromString(str.take(2))
                val target = Pos.parseFromString(str.drop(2).take(2))
                val promotion = str.drop(4).firstOrNull()?.let { PieceType.chooseByChar(game.variant.pieceTypes, it) }
                val move = game.board.getMoves(origin).first { it.display == target }
                move.getTrait<PromotionTrait>()?.promotion = promotion?.of(move.main.color)
                game.finishMove(move)
            } catch (e: Exception) {
                game.stop(drawBy(EndReason.ERROR))
                throw e
            }
        }
    }
}

class NoEngineMoveException(fen: FEN) : Exception(fen.toString())