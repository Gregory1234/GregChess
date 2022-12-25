package gregc.gregchess.bukkit.match

import gregc.gregchess.match.ChessMatch
import gregc.gregchess.match.MatchInfo
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
class BukkitMatchInfo(
    val presetName: String,
    override val pgnRound: Int,
    @Contextual val uuid: UUID = UUID.randomUUID()
) : MatchInfo {
    override val pgnSite: String get() = "GregChess Bukkit plugin"
    override val pgnEventName: String get() = "Casual game" // TODO: add events

    override fun matchCoroutineName(): String = uuid.toString()
    override fun matchToString(): String = "uuid=$uuid"
}

val ChessMatch.bukkitInfo get() = info as BukkitMatchInfo
val ChessMatch.uuid get() = bukkitInfo.uuid
val ChessMatch.presetName get() = bukkitInfo.presetName