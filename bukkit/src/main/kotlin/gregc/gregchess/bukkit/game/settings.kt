package gregc.gregchess.bukkit.game

import gregc.gregchess.bukkit.config
import gregc.gregchess.bukkit.message
import gregc.gregchess.bukkit.registry.BukkitRegistry
import gregc.gregchess.bukkit.registry.getFromRegistry
import gregc.gregchess.bukkitutils.*
import gregc.gregchess.game.Component
import gregc.gregchess.registry.Registry
import gregc.gregchess.variant.ChessVariant
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player

class SettingsParserContext(val variant: ChessVariant, val section: ConfigurationSection, val presetName: String)

typealias SettingsParser<T> = SettingsParserContext.() -> T?

class GameSettings(
    val name: String,
    val variant: ChessVariant,
    val components: Collection<Component>
)

object SettingsManager {

    inline fun <T, R> chooseOrParse(opts: Map<T, R>, v: T?, parse: (T) -> R?): R? = opts[v] ?: v?.let(parse)

    fun getSettings(): List<GameSettings> =
        config.getConfigurationSection("Settings.Presets")?.getKeys(false).orEmpty().map { name ->
            val section = config.getConfigurationSection("Settings.Presets.$name")!!
            val variant = section.getFromRegistry(Registry.VARIANT, "Variant") ?: ChessVariant.Normal
            val requestedComponents = variant.requiredComponents + variant.optionalComponents +
                    BukkitRegistry.HOOKED_COMPONENTS.elements
            val context = SettingsParserContext(variant, section, name)
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