package gregc.gregchess.chess

import gregc.gregchess.*
import gregc.gregchess.chess.component.*

interface ArenaManager {
    companion object {
        val NO_ARENAS = ErrorMsg("NoArenas")
    }

    fun next(): Arena?

    fun cNext() = cNotNull(next(), NO_ARENAS)
}

interface ArenasConfig : ConfigBlock {
    val chessArenas: List<String>
}

val Config.arenas: ArenasConfig by Config

data class Arena(val name: String, var game: ChessGame? = null) : Component {

    @GameEvent(GameBaseEvent.VERY_END, GameBaseEvent.PANIC, mod = TimeModifier.LATE)
    fun veryEnd() {
        game = null
    }
}