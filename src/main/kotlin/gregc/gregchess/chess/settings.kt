package gregc.gregchess.chess

import gregc.gregchess.*
import gregc.gregchess.chess.component.*
import gregc.gregchess.chess.variant.ChessVariant
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

object SettingsManager {

    inline fun <T> parseSettings(name: String, parser: (View) -> T) =
        ConfigManager.getView("Settings.$name").children.mapValues { (_, child) -> parser(child) }

    val settingsChoice: Map<String, GameSettings>
        get() {
            val presets = ConfigManager.getView("Settings.Presets")
            return presets.children.mapValues { (key, child) ->
                val simpleCastling = child.getBool("SimpleCastling", false)
                val variant = ChessVariant[child.getOptionalString("Variant")]
                val board = Chessboard.Settings[child.getOptionalString("Board")]
                val clock = ChessClock.Settings[child.getOptionalString("Clock")]
                val tileSize = child.getInt("TileSize", 3, false)
                GameSettings(
                    key, simpleCastling, variant,
                    listOfNotNull(board, clock, BukkitRenderer.Settings(tileSize), ScoreboardManager.Settings())
                )
            }
        }

}

class SettingsScreen(
    private inline val startGame: (GameSettings) -> Unit
) : Screen<GameSettings>("Message.ChooseSettings") {
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