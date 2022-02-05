package gregc.gregchess.bukkit.chess

import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkitutils.*
import gregc.gregchess.chess.GameSettings
import gregc.gregchess.chess.variant.ChessVariant
import gregc.gregchess.registry.Registry
import org.bukkit.Material
import org.bukkit.entity.Player

object SettingsManager {

    inline fun <T, R> chooseOrParse(opts: Map<T, R>, v: T?, parse: (T) -> R?): R? = opts[v] ?: v?.let(parse)

    fun getSettings(): List<GameSettings> =
        config.getConfigurationSection("Settings.Presets")?.getKeys(false).orEmpty().map { name ->
            val section = config.getConfigurationSection("Settings.Presets.$name")!!
            val variant = section.getFromRegistry(Registry.VARIANT, "Variant") ?: ChessVariant.Normal
            val requestedComponents = variant.requiredComponents + variant.optionalComponents +
                    BukkitRegistry.HOOKED_COMPONENTS.elements
            val context = SettingsParserContext(variant, section)
            val components = requestedComponents.mapNotNull { req ->
                val f = BukkitRegistry.SETTINGS_PARSER[req]
                context.f()
            }
            GameSettings(name, variant, components)
        }

}

private val CHOOSE_SETTINGS = message("ChooseSettings")

suspend fun Player.openSettingsMenu() =
    openMenu(CHOOSE_SETTINGS, SettingsManager.getSettings().mapIndexed { index, s ->
        val item = itemStack(Material.IRON_BLOCK) {
            meta {
                name = s.name
            }
        }
        ScreenOption(item, s, index.toInvPos())
    })