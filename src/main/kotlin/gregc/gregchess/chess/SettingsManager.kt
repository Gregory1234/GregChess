package gregc.gregchess.chess

import gregc.gregchess.ConfigManager
import gregc.gregchess.View
import gregc.gregchess.chess.component.ChessClock
import gregc.gregchess.chess.component.Chessboard

object SettingsManager {

    inline fun <T> parseSettings(name: String, parser: (View) -> T) =
        ConfigManager.getView("Settings.$name").children.mapValues { (_, child) -> parser(child) }

    val settingsChoice: Map<String, ChessGame.Settings>
        get() {
            val presets = ConfigManager.getView("Settings.Presets")
            return presets.children.mapValues { (key, child) ->
                val relaxedInsufficientMaterial = child.getBool("Relaxed", true)
                val simpleCastling = child.getBool("SimpleCastling", false)
                val board = Chessboard.Settings[child.getOptionalString("Board")]
                val clock = ChessClock.Settings[child.getOptionalString("Clock")]
                ChessGame.Settings(key, relaxedInsufficientMaterial, simpleCastling, board, clock)
            }
        }

}
