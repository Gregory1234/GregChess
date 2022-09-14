package gregc.gregchess.bukkit.match

import gregc.gregchess.ByColor
import gregc.gregchess.bukkit.config
import gregc.gregchess.bukkit.message
import gregc.gregchess.bukkit.player.BukkitPlayer
import gregc.gregchess.bukkit.registry.BukkitRegistry
import gregc.gregchess.bukkit.registry.getFromRegistry
import gregc.gregchess.bukkitutils.*
import gregc.gregchess.component.ComponentList
import gregc.gregchess.match.ChessMatch
import gregc.gregchess.player.ChessPlayer
import gregc.gregchess.player.ChessSideManager
import gregc.gregchess.registry.Registry
import gregc.gregchess.registry.name
import gregc.gregchess.snakeToPascal
import gregc.gregchess.variant.ChessVariant
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection

class SettingsParserContext(val variant: ChessVariant, val variantOptions: Long, val section: ConfigurationSection)

typealias SettingsParser<T> = SettingsParserContext.() -> T?

typealias VariantOptionsParser = ConfigurationSection.() -> Long

val defaultVariantOptionsParser: VariantOptionsParser = {
    val simpleCastling = getBoolean("SimpleCastling", false)
    val chess960 = getBoolean("Chess960", false)
    if (chess960 && simpleCastling) 2L else if (chess960) 1L else 0L
}

class MatchSettings(
    val environment: BukkitChessEnvironment,
    val name: String,
    val variant: ChessVariant,
    val variantOptions: Long,
    val components: ComponentList
) {
    fun createMatch(players: ByColor<ChessPlayer<*>>) = ChessMatch(environment, variant, listOf(ChessSideManager(players.white, players.black)) + components, variantOptions)
}

object SettingsManager {

    inline fun <T, R> chooseOrParse(opts: Map<T, R>, v: T?, parse: (T) -> R?): R? = opts[v] ?: v?.let(parse)

    fun getSettings(name: String, round: Int = 1): MatchSettings? {
        val section = config.getConfigurationSection("Settings.Presets.$name") ?: return null
        val variant = section.getFromRegistry(Registry.VARIANT, "Variant") ?: ChessVariant.Normal

        val variantOptionsParser = BukkitRegistry.VARIANT_OPTIONS_PARSER[variant]
        val variantOptions = section.variantOptionsParser()

        val context = SettingsParserContext(variant, variantOptions, section)
        val requestedComponents = variant.requiredComponents + variant.optionalComponents +
                BukkitRegistry.HOOKED_COMPONENTS.elements + BukkitRegistry.COMPONENT_ALTERNATIVE.values.map { it.getSelectedOrNull(section, it.name.snakeToPascal()) ?: it.getSelected() }
        val components = requestedComponents.mapNotNull { req ->
            val f = BukkitRegistry.SETTINGS_PARSER[req]
            context.f()
        }

        val environment = BukkitChessEnvironment(name, round)

        return MatchSettings(environment, name, variant, variantOptions, ComponentList(components))
    }

    fun getSettings(round: Int = 1): List<MatchSettings> =
        config.getConfigurationSection("Settings.Presets")?.getKeys(false).orEmpty().map { name ->
            getSettings(name, round)!!
        }

}

private val CHOOSE_SETTINGS = message("ChooseSettings")

suspend fun BukkitPlayer.openSettingsMenu() =
    openMenu(CHOOSE_SETTINGS, SettingsManager.getSettings().mapIndexed { index, s ->
        val item = itemStack(Material.IRON_BLOCK) {
            meta {
                name = s.name
            }
        }
        ScreenOption(item, s, index.toInvPos())
    })