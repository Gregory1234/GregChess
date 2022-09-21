package gregc.gregchess.move.connector

import gregc.gregchess.RegisterAll
import gregc.gregchess.event.ChessEvent
import gregc.gregchess.event.ChessEventType
import gregc.gregchess.move.MoveEnvironment
import gregc.gregchess.piece.PlacedPieceType
import kotlin.reflect.KClass

interface MoveConnector {
    val holders: Map<PlacedPieceType<*>, PieceHolder<*>>
}

class MoveConnectorType<T : MoveConnector>(val cl: KClass<T>) { // TODO: remove or use the class reference

    @RegisterAll(MoveConnectorType::class)
    companion object {
        @JvmField
        val CHESSBOARD = MoveConnectorType(ChessboardFacadeConnector::class)
    }
}

val MoveEnvironment.board get() = get(MoveConnectorType.CHESSBOARD)

class AddMoveConnectorsEvent(private val connectors: MutableMap<MoveConnectorType<*>, MoveConnector>): ChessEvent {
    operator fun <T: MoveConnector> set(type: MoveConnectorType<T>, connector: T) {
        connectors[type] = connector
    }

    override val type get() = ChessEventType.ADD_MOVE_CONNECTORS
}

class AddFakeMoveConnectorsEvent(private val connectors: MutableMap<MoveConnectorType<*>, MoveConnector>): ChessEvent {
    operator fun <T: MoveConnector> set(type: MoveConnectorType<T>, connector: T) {
        connectors[type] = connector
    }

    override val type get() = ChessEventType.ADD_FAKE_MOVE_CONNECTORS
}