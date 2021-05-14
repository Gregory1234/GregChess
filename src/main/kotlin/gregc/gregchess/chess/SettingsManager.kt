package gregc.gregchess.chess

import gregc.gregchess.ConfigManager
import gregc.gregchess.Loc
import gregc.gregchess.PlayerData
import gregc.gregchess.View
import gregc.gregchess.chess.component.ChessClock
import gregc.gregchess.chess.component.Chessboard
import gregc.gregchess.chess.component.Renderer
import gregc.gregchess.chess.component.ScoreboardManager
import gregc.gregchess.chess.variant.ChessVariant

object SettingsManager {

    inline fun <T> parseSettings(name: String, parser: (View) -> T) =
        ConfigManager.getView("Settings.$name").children.mapValues { (_, child) -> parser(child) }

    val settingsChoice: Map<String, ChessGame.Settings>
        get() {
            val presets = ConfigManager.getView("Settings.Presets")
            return presets.children.mapValues { (key, child) ->
                val simpleCastling = child.getBool("SimpleCastling", false)
                val variant = ChessVariant[child.getOptionalString("Variant")]
                val board = Chessboard.Settings[child.getOptionalString("Board")]
                val clock = ChessClock.Settings[child.getOptionalString("Clock")]
                val tileSize = child.getInt("TileSize", 3)
                val gameModeInfo = GameModeInfo(Loc(4, 101, 4), PlayerData(allowFlight = true, isFlying = true), false)
                ChessGame.Settings(
                    key, simpleCastling, variant,
                    board, clock, Renderer.Settings(tileSize, gameModeInfo), ScoreboardManager.Settings()
                )
            }
        }

}
