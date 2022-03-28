package gregc.gregchess.game

import kotlinx.coroutines.CoroutineDispatcher
import java.time.Clock


interface ChessEnvironment {
    val pgnSite: String
    val coroutineDispatcher: CoroutineDispatcher
    val clock: Clock
}