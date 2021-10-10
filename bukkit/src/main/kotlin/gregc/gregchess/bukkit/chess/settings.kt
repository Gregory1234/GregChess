package gregc.gregchess.bukkit.chess

import gregc.gregchess.ChessModule
import gregc.gregchess.bukkit.*
import gregc.gregchess.chess.GameSettings
import gregc.gregchess.chess.component.componentModule
import gregc.gregchess.chess.variant.ChessVariant
import gregc.gregchess.registry.RegistryType
import gregc.gregchess.registry.toKey
import org.bukkit.Material
import org.bukkit.entity.Player

object SettingsManager {

    inline fun <T, R> chooseOrParse(opts: Map<T, R>, v: T?, parse: (T) -> R?): R? = opts[v] ?: v?.let(parse)

    fun getSettings(): List<GameSettings> =
        config.getConfigurationSection("Settings.Presets")?.getKeys(false).orEmpty().map { name ->
            val section = config.getConfigurationSection("Settings.Presets.$name")!!
            val simpleCastling = section.getBoolean("SimpleCastling", false)
            val variant = section.getString("Variant")?.toKey()
                ?.let { RegistryType.VARIANT.getOrNull(it) } ?: ChessVariant.Normal
            val requestedComponents = variant.requiredComponents + variant.optionalComponents +
                    ChessModule.modules.flatMap { it.bukkit.hookedComponents }
            val context = SettingsParserContext(variant, section)
            val components = requestedComponents.mapNotNull { req ->
                val f = BukkitRegistryTypes.SETTINGS_PARSER[req.componentModule, req]
                context.f()
            }
            GameSettings(name, simpleCastling, variant, components)
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