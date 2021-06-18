package gregc.gregchess.chess

import gregc.gregchess.*
import gregc.gregchess.chess.component.*

interface ArenaManager {
    fun next(): Arena?
}

interface ArenasConfig : ConfigBlock {
    val chessArenas: List<String>
}

val Config.arenas: ArenasConfig by Config

val ErrorConfig.noArenas by ErrorConfig

fun ArenaManager.cNext() = cNotNull(next(), Config.error.noArenas)

data class Arena(val name: String, var game: ChessGame? = null) : Component {

    @GameEvent(GameBaseEvent.VERY_END, GameBaseEvent.PANIC, mod = TimeModifier.LATE)
    fun veryEnd() {
        game = null
    }
}