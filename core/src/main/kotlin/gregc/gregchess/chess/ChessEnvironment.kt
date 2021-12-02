package gregc.gregchess.chess

import kotlinx.coroutines.CoroutineDispatcher

interface ChessEnvironment {
    val coroutineDispatcher: CoroutineDispatcher
}