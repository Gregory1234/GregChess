package gregc.gregchess.player

import gregc.gregchess.*
import gregc.gregchess.board.FEN
import gregc.gregchess.event.*
import gregc.gregchess.match.ChessMatch
import gregc.gregchess.move.trait.promotionTrait
import gregc.gregchess.piece.PieceType
import gregc.gregchess.piece.of
import gregc.gregchess.results.EndReason
import gregc.gregchess.results.drawBy
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

interface ChessEngine: ChessPlayer<EngineChessSide<out @SelfType ChessEngine>> {
    val name: String
    val type: ChessSideType<out EngineChessSide<out @SelfType ChessEngine>>
    fun stop()
    suspend fun setOption(name: String, value: String)
    suspend fun sendCommand(command: String)
    suspend fun getMove(fen: FEN): String
    override fun createChessSide(color: Color) = EngineChessSide(this, color)
}

@Serializable
class EngineChessSide<T : ChessEngine>(val engine: T, override val color: Color) : ChessSide {

    override fun toString() = "EngineChessSide(engine=$engine, color=$color)"

    override val name: String get() = engine.name

    @Suppress("UNCHECKED_CAST")
    override val type: ChessSideType<EngineChessSide<T>> get() = engine.type as ChessSideType<EngineChessSide<T>>

    override fun init(match: ChessMatch, events: ChessEventRegistry) {
        events.register(ChessEventType.BASE) {
            if (it == ChessBaseEvent.CLEAR || it == ChessBaseEvent.PANIC)
                engine.stop()
        }
        events.registerE(TurnEvent.START) {
            startTurn(match)
        }
    }

    private fun startTurn(match: ChessMatch) {
        match.coroutineScope.launch {
            try {
                val str = engine.getMove(match.board.getFEN())
                val origin = Pos.parseFromString(str.take(2))
                val target = Pos.parseFromString(str.drop(2).take(2))
                val promotion = str.drop(4).firstOrNull()?.let { PieceType.chooseByChar(match.variant.pieceTypes, it) }
                val move = match.board.getMoves(origin).first { it.display == target }
                move.promotionTrait?.promotion = promotion?.of(move.main.color)
                match.finishMove(move)
            } catch (e: Exception) {
                match.stop(drawBy(EndReason.ERROR))
                throw e
            }
        }
    }

    override fun createFacade(match: ChessMatch) = EngineChessSideFacade(match, this)
}

class EngineChessSideFacade<T : ChessEngine>(match: ChessMatch, side: EngineChessSide<T>) : ChessSideFacade<EngineChessSide<T>>(match, side) {
    val engine: T get() = side.engine
}

class NoEngineMoveException(fen: FEN) : Exception(fen.toString())