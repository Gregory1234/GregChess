package gregc.gregchess.move

import gregc.gregchess.match.ChessEvent
import gregc.gregchess.move.connector.*
import gregc.gregchess.piece.PlacedPieceType
import gregc.gregchess.variant.ChessVariant

@Suppress("UNCHECKED_CAST")
interface MoveEnvironment {
    fun updateMoves()
    fun callEvent(e: ChessEvent)
    val variant: ChessVariant
    val variantOptions: Long
    operator fun <T: MoveConnector> get(type: MoveConnectorType<T>): T
    val holders: Map<PlacedPieceType<*>, PieceHolder<*>>
}