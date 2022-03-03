package gregc.gregchess.fabric.coroutines

import gregc.gregchess.chess.ChessEnvironment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.Serializable
import java.time.Clock

@Serializable
object FabricChessEnvironment : ChessEnvironment {
    override val pgnSite: String get() = "GregChess Fabric mod"
    override val coroutineDispatcher: CoroutineDispatcher get() = FabricDispatcher()
    override val clock: Clock get() = Clock.systemDefaultZone()
}