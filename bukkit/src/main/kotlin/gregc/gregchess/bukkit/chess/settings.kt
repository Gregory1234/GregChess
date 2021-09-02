package gregc.gregchess.bukkit.chess

import gregc.gregchess.ChessModule
import gregc.gregchess.RegistryType
import gregc.gregchess.bukkit.*
import gregc.gregchess.chess.GameSettings
import gregc.gregchess.chess.component.componentDataClass
import gregc.gregchess.chess.component.componentModule
import gregc.gregchess.chess.variant.ChessVariant
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

object SettingsManager {

    inline fun <T, R> chooseOrParse(opts: Map<T, R>, v: T?, parse: (T) -> R?): R? = opts[v] ?: v?.let(parse)

    private fun getChessVariant(key: NamespacedKey): ChessVariant? =
        RegistryType.VARIANT.getOrNull(key.namespace, key.key)

    fun getSettings(): List<GameSettings> =
        config.getConfigurationSection("Settings.Presets")?.getKeys(false).orEmpty().map { name ->
            val section = config.getConfigurationSection("Settings.Presets.$name")!!
            val simpleCastling = section.getBoolean("SimpleCastling", false)
            val variant = section.getString("Variant")?.toKey()?.let(::getChessVariant) ?: ChessVariant.Normal
            val requestedComponents = variant.requiredComponents + variant.optionalComponents +
                    ChessModule.modules.flatMap { it.bukkit.hookedComponents }
            val context = SettingsParserContext(variant, section)
            val components = requestedComponents.mapNotNull { req ->
                val f = req.componentModule[BukkitRegistryTypes.SETTINGS_PARSER][req.componentDataClass]
                context.f()
            }
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