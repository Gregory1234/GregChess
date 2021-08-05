package gregc.gregchess.fabric.chess

import gregc.gregchess.chess.ChessGame
import gregc.gregchess.chess.GameSettings
import gregc.gregchess.chess.component.Chessboard
import gregc.gregchess.chess.variant.ChessVariant
import gregc.gregchess.fabric.chess.component.FabricRenderer
import gregc.gregchess.fabric.chess.component.PlayerManager
import java.util.*

object ChessGameManager {

    private val loadedGames = mutableMapOf<UUID, ChessGame>()

    operator fun get(uuid: UUID): ChessGame? = loadedGames[uuid]

    operator fun plusAssign(game: ChessGame) {
        loadedGames[game.uuid] = game
    }

    operator fun minusAssign(game: ChessGame) {
        loadedGames.remove(game.uuid, game)
    }

    fun clear() = loadedGames.clear()

    fun settings(r: FabricRenderer.Settings): GameSettings {
        val components = buildList {
            this += Chessboard.Settings[null]
            this += PlayerManager.Settings
            this += r
        }
        return GameSettings("", false, ChessVariant.Normal, components)
    }

}