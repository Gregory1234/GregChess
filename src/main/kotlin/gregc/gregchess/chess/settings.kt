package gregc.gregchess.chess

import gregc.gregchess.*
import gregc.gregchess.chess.component.*
import gregc.gregchess.chess.variant.ChessVariant

object SettingsManager {

    fun getSettings(): List<GameSettings> =
        Config.settings.presets.map { (key, child) ->
            val simpleCastling = child.getDefaultBoolean("SimpleCastling", false)
            val variant = ChessVariant[child.getString("Variant")]
            val components = buildList {
                this += Chessboard.Settings[child.getString("Board")]
                ChessClock.Settings[child.getString("Clock")]?.let { this += it }
                val tileSize = child.getDefaultInt("TileSize", 3)
                this += BukkitRenderer.Settings(tileSize)
                this += BukkitScoreboardManager.Settings
                this += BukkitEventRelay.Settings
            }
            GameSettings(key, simpleCastling, variant, components)
        }

}

class SettingsScreen(private inline val startGame: (GameSettings) -> Unit) :
    Screen<GameSettings>(GameSettings::class, MessageConfig::chooseSettings.path) {
    override fun getContent() =
        SettingsManager.getSettings().toList().mapIndexed { index, s ->
            ScreenOption(s, InventoryPosition.fromIndex(index))
        }

    override fun onClick(v: GameSettings) {
        startGame(v)
    }

    override fun onCancel() {
    }
}

data class GameSettings(
    val name: String,
    val simpleCastling: Boolean,
    val variant: ChessVariant,
    val components: Collection<Component.Settings<*>>
) {
    inline fun <reified T : Component.Settings<*>> getComponent(): T? = components.filterIsInstance<T>().firstOrNull()
}