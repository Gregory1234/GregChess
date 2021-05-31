package gregc.gregchess.chess

import gregc.gregchess.Config
import gregc.gregchess.InventoryPosition
import gregc.gregchess.Screen
import gregc.gregchess.ScreenOption
import gregc.gregchess.chess.component.*
import gregc.gregchess.chess.variant.ChessVariant
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

object SettingsManager {

    val settingsChoice: Map<String, GameSettings>
        get() {
            val presets = Config.settings.presets.presets
            return presets.mapValues { (key, child) ->
                val simpleCastling = child.simpleCastling
                val variant = ChessVariant[child.variant]
                val board = Chessboard.Settings[child.board]
                val clock = ChessClock.Settings[child.clock]
                val tileSize = child.tileSize
                GameSettings(
                    key, simpleCastling, variant,
                    listOfNotNull(board, clock, BukkitRenderer.Settings(tileSize), ScoreboardManager.Settings())
                )
            }
        }

}

class SettingsScreen(
    private inline val startGame: (GameSettings) -> Unit
) : Screen<GameSettings>(Config.message::chooseSettings) {
    override fun getContent() =
        SettingsManager.settingsChoice.toList().mapIndexed { index, (name, s) ->
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