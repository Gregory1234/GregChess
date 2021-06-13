package gregc.gregchess.chess

import gregc.gregchess.*
import gregc.gregchess.chess.component.*
import gregc.gregchess.chess.variant.ChessVariant

object SettingsManager {

    fun getSettings(config: Configurator): List<GameSettings> =
        Config.Settings.presets.get(config).map { (key, child) ->
            val simpleCastling = child.get { simpleCastling }
            val variant = ChessVariant[child.get { variant }]
            val components = buildList {
                this += Chessboard.Settings[child.get { board }]
                ChessClock.Settings.get(config, child.get { clock })?.let { this += it }
                val tileSize = child.get { tileSize }
                this += BukkitRenderer.Settings(tileSize)
                this += BukkitScoreboardManager.Settings
                this += BukkitEventRelay.Settings
            }
            GameSettings(key, simpleCastling, variant, components)
        }

}

class SettingsScreen(private inline val startGame: (GameSettings) -> Unit) :
    Screen<GameSettings>(GameSettings::class, Config.Message.chooseSettings) {
    override fun getContent(config: Configurator) =
        SettingsManager.getSettings(config).toList().mapIndexed { index, s ->
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