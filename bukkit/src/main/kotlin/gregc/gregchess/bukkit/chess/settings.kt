package gregc.gregchess.bukkit.chess

import gregc.gregchess.GregChessModule
import gregc.gregchess.bukkit.*
import gregc.gregchess.chess.GameSettings
import gregc.gregchess.chess.variant.ChessVariant
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

object SettingsManager {

    fun <T, R> chooseOrParse(opts: Map<T, R>, v: T?, parse: (T) -> R?): R? = opts[v] ?: v?.let(parse)

    private fun getChessVariant(key: NamespacedKey): ChessVariant? =
        GregChessModule.getModuleOrNull(key.namespace)?.variants?.firstOrNull { it.name.lowercase() == key.key }

    fun getSettings(): List<GameSettings> =
        config.getConfigurationSection("Settings.Presets")?.getKeys(false).orEmpty().map { name ->
            val section = config.getConfigurationSection("Settings.Presets.$name")!!
            val simpleCastling = section.getBoolean("SimpleCastling", false)
            val variant = section.getString("Variant")?.toKey()?.let(::getChessVariant) ?: ChessVariant.Normal
            val components = GregChessModule.modules.flatMap { it.bukkit.getSettings(variant.requiredComponents + variant.optionalComponents, section) }
            GameSettings(name, simpleCastling, variant, components)
        }

}

private val CHOOSE_SETTINGS = message("ChooseSettings")

suspend fun Player.openSettingsMenu() =
    openMenu(CHOOSE_SETTINGS, SettingsManager.getSettings().mapIndexed { index, s ->
        val item = ItemStack(Material.IRON_BLOCK)
        val meta = item.itemMeta!!
        meta.setDisplayName(s.name)
        item.itemMeta = meta
        ScreenOption(item, s, index.toInvPos())
    })