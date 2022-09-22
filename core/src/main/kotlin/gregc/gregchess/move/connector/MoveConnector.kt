package gregc.gregchess.move.connector

import gregc.gregchess.event.ChessEvent
import gregc.gregchess.move.MoveEnvironment
import gregc.gregchess.piece.PlacedPieceType

interface MoveConnector {
    val holders: Map<PlacedPieceType<*>, PieceHolder<*>>
}

class MoveConnectorType<T : MoveConnector> {
    companion object {
        @JvmField
        val CHESSBOARD = MoveConnectorType<ChessboardFacadeConnector>()
    }
}

val MoveEnvironment.board get() = get(MoveConnectorType.CHESSBOARD)

class AddMoveConnectorsEvent(private val connectors: MutableMap<MoveConnectorType<*>, MoveConnector>): ChessEvent {
    operator fun <T: MoveConnector> set(type: MoveConnectorType<T>, connector: T) {
        connectors[type] = connector
    }
}

class AddFakeMoveConnectorsEvent(private val connectors: MutableMap<MoveConnectorType<*>, MoveConnector>): ChessEvent {
    operator fun <T: MoveConnector> set(type: MoveConnectorType<T>, connector: T) {
        connectors[type] = connector
    }
}