package gregc.gregchess.fabric.coroutines

import gregc.gregchess.chess.ChessEnvironment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.Serializable

@Serializable
object FabricChessEnvironment : ChessEnvironment {
    override val coroutineDispatcher: CoroutineDispatcher
        get() = throw UnsupportedOperationException()
}