package gregc.gregchess.fabric.match

import gregc.gregchess.match.ChessMatch
import gregc.gregchess.match.MatchInfo
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
class FabricMatchInfo(@Contextual val uuid: UUID = UUID.randomUUID()) : MatchInfo {
    override val pgnSite: String get() = "GregChess Fabric mod"
    override val pgnEventName: String get() = "Casual game"
    override val pgnRound: Int get() = 1
    override fun matchCoroutineName(): String = uuid.toString()
    override fun matchToString(): String = "uuid=$uuid"
}

val ChessMatch.fabricInfo get() = info as FabricMatchInfo
val ChessMatch.uuid get() = fabricInfo.uuid