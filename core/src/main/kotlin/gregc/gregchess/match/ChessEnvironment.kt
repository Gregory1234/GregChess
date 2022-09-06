package gregc.gregchess.match

import kotlinx.coroutines.CoroutineDispatcher
import java.time.Clock

// TODO: choose a new better name?
interface ChessEnvironment {
    val pgnSite: String
    val pgnEventName: String
    val pgnRound: Int

    val coroutineDispatcher: CoroutineDispatcher
    val clock: Clock
    fun matchToString(): String
    fun matchCoroutineName(): String
}