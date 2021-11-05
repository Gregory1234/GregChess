package gregc.gregchess.chess.player

import gregc.gregchess.chess.*
import gregc.gregchess.chess.move.PromotionTrait
import gregc.gregchess.chess.piece.PieceType
import gregc.gregchess.chess.piece.of
import gregc.gregchess.passExceptions
import kotlinx.coroutines.launch

interface ChessEngine : ChessPlayerInfo {
    fun stop()
    fun setOption(name: String, value: String)
    fun sendCommand(command: String)
    suspend fun getMove(fen: FEN): String
    override fun getPlayer(color: Color, game: ChessGame) = EnginePlayer(this, color, game)
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
                game.stop(drawBy(EndReason.ERROR))
                throw e
            }
        }.passExceptions()
    }
}

class NoEngineMoveException(fen: FEN) : Exception(fen.toString())