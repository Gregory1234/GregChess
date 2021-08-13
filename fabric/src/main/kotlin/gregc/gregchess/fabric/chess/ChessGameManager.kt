package gregc.gregchess.fabric.chess

import gregc.gregchess.chess.ChessGame
import gregc.gregchess.chess.GameSettings
import gregc.gregchess.chess.component.ChessboardState
import gregc.gregchess.chess.variant.ChessVariant
import gregc.gregchess.fabric.chess.component.FabricRendererSettings
import gregc.gregchess.fabric.chess.component.PlayerManagerData
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

    fun settings(v: ChessVariant, r: FabricRendererSettings): GameSettings {
        val components = buildList {
            this += ChessboardState[v, null]
            this += PlayerManagerData
            this += r
        }
        return GameSettings("", false, ChessVariant.Normal, components)
    }

}