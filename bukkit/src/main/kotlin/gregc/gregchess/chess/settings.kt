package gregc.gregchess.chess

import gregc.gregchess.*
import gregc.gregchess.chess.component.*
import gregc.gregchess.chess.variant.ChessVariant
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

object SettingsManager {

    fun getClockSettings(): Map<String, ChessClock.Settings> =
        config["Settings.Clock"].childrenViews.orEmpty().mapValues { (_, it) ->
            val t = it.getEnum("Type", ChessClock.Type.INCREMENT, false)
            val initial = it.getDuration("Initial")
            val increment = if (t.usesIncrement) it.getDuration("increment") else 0.seconds
            ChessClock.Settings(t, initial, increment)
        }

    fun getSettings(): List<GameSettings> =
        config["Settings.Presets"].childrenViews.orEmpty().map { (key, child) ->
            val simpleCastling = child.getDefaultBoolean("SimpleCastling", false)
            val variant = ChessVariant[child.getOptionalString("Variant")]
            val components = buildList {
                this += Chessboard.Settings[child.getOptionalString("Board")]
                ChessClock.Settings.get(getClockSettings(), child.getOptionalString("Clock"))?.let { this += it }
                val tileSize = child.getDefaultInt("TileSize", 3)
                this += BukkitRenderer.Settings(tileSize)
                this += BukkitScoreboardManager.Settings
                this += BukkitEventRelay.Settings
            }
            GameSettings(key, simpleCastling, variant, components)
        }

}

val MessageConfig.chooseSettings by MessageConfig

suspend fun Player.openSettingsMenu() =
    openMenu(Config.message.chooseSettings, SettingsManager.getSettings().mapIndexed { index, s ->
        val item = ItemStack(Material.IRON_BLOCK)
        val meta = item.itemMeta!!
        meta.setDisplayName(s.name)
        item.itemMeta = meta
        ScreenOption(item, s, InventoryPosition.fromIndex(index))
    })