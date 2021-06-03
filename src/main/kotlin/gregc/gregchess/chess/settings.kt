package gregc.gregchess.chess

import gregc.gregchess.*
import gregc.gregchess.chess.component.*
import gregc.gregchess.chess.variant.ChessVariant
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

object SettingsManager {

    fun getSettings(config: Configurator): Map<String, GameSettings> =
        Config.settings.presets.get(config).mapValues { (key, child) ->
            val simpleCastling = child.get { simpleCastling }
            val variant = ChessVariant[child.get { variant }]
            val board = Chessboard.Settings[child.get { board }]
            val clock = ChessClock.Settings.get(config, child.get { clock })
            val tileSize = child.get { tileSize }
            GameSettings(
                key, simpleCastling, variant,
                listOfNotNull(board, clock, BukkitRenderer.Settings(tileSize), ScoreboardManager.Settings())
            )
        }

}

class SettingsScreen(
    private inline val startGame: (GameSettings) -> Unit
) : Screen<GameSettings>(Config.message.chooseSettings) {
    override fun getContent(config: Configurator) =
        SettingsManager.getSettings(config).toList().mapIndexed { index, (name, s) ->
            val item = ItemStack(Material.IRON_BLOCK)
            val meta = item.itemMeta
            meta?.setDisplayName(name)
            item.itemMeta = meta
            ScreenOption(item, s, InventoryPosition.fromIndex(index))
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