package gregc.gregchess.move

import gregc.gregchess.event.ChessEventCaller
import gregc.gregchess.move.connector.MoveConnector
import gregc.gregchess.move.connector.MoveConnectorType
import gregc.gregchess.variant.ChessVariant

interface MoveEnvironment : ChessEventCaller, MoveConnector {
    val variant: ChessVariant
    val variantOptions: Long
    operator fun <T: MoveConnector> get(type: MoveConnectorType<T>): T
}