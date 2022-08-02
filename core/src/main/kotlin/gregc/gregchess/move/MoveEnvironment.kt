package gregc.gregchess.move

import gregc.gregchess.match.ChessEventCaller
import gregc.gregchess.move.connector.MoveConnector
import gregc.gregchess.move.connector.MoveConnectorType
import gregc.gregchess.variant.ChessVariant

@Suppress("UNCHECKED_CAST")
interface MoveEnvironment : ChessEventCaller, MoveConnector {
    val variant: ChessVariant
    val variantOptions: Long
    operator fun <T: MoveConnector> get(type: MoveConnectorType<T>): T
}