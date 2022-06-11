package gregc.gregchess.move.connector

import gregc.gregchess.RegisterAll
import gregc.gregchess.match.ChessEvent
import gregc.gregchess.move.MoveEnvironment
import gregc.gregchess.piece.PlacedPieceType
import kotlin.reflect.KClass

interface MoveConnector {
    val holders: Map<PlacedPieceType<*>, PieceHolder<*>>
}

class MoveConnectorType<T : MoveConnector>(val cl: KClass<T>) {

    @RegisterAll(MoveConnectorType::class)
    companion object {
        @JvmField
        val CHESSBOARD = MoveConnectorType(ChessboardConnector::class)
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