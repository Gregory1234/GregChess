package gregc.gregchess.fabric.coroutines

import gregc.gregchess.match.ChessEnvironment
import gregc.gregchess.match.ChessMatch
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.Clock
import java.util.*

@Serializable
class FabricChessEnvironment(@Contextual val uuid: UUID = UUID.randomUUID()) : ChessEnvironment {
    override val pgnSite: String get() = "GregChess Fabric mod"
    override val pgnEventName: String get() = "Casual game"
    override val pgnRound: Int get() = 1
    override val coroutineDispatcher: CoroutineDispatcher get() = FabricDispatcher()
    override val clock: Clock get() = Clock.systemDefaultZone()
    override fun matchCoroutineName(): String = uuid.toString()
    override fun matchToString(): String = "uuid=$uuid"
}

val ChessMatch.fabricEnvironment get() = environment as FabricChessEnvironment
val ChessMatch.uuid get() = fabricEnvironment.uuid