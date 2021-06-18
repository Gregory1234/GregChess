package gregc.gregchess.chess

import gregc.gregchess.*
import gregc.gregchess.chess.component.*
import gregc.gregchess.chess.variant.ChessVariant
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

object SettingsManager {

    fun getSettings(): List<GameSettings> =
        Config.settings.presets.map { (key, child) ->
            val simpleCastling = child.getDefaultBoolean("SimpleCastling", false)
            val variant = ChessVariant[child.getOptionalString("Variant")]
            val components = buildList {
                this += Chessboard.Settings[child.getOptionalString("Board")]
                ChessClock.Settings[child.getOptionalString("Clock")]?.let { this += it }
                val tileSize = child.getDefaultInt("TileSize", 3)
                this += BukkitRenderer.Settings(tileSize)
                this += BukkitScoreboardManager.Settings
                this += BukkitEventRelay.Settings
            }
            GameSettings(key, simpleCastling, variant, components)
        }

}

suspend fun Player.openSettingsMenu() =
    openMenu(Config.message.chooseSettings, SettingsManager.getSettings().toList().mapIndexed { index, s ->
        ScreenOption(ItemStack(Material.IRON_BLOCK), s, InventoryPosition.fromIndex(index))
    })