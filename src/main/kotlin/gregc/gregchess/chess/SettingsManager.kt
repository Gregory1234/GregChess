package gregc.gregchess.chess

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.plugin.java.JavaPlugin

class SettingsManager(val plugin: JavaPlugin) {

    private val componentChoice: MutableMap<String, Map<String, ChessGame.ComponentSettings>> = mutableMapOf()

    fun <T : ChessGame.ComponentSettings> registerComponent(name: String, presets: Map<String, T>) {
        componentChoice[name] = presets
    }

    inline fun <T : ChessGame.ComponentSettings> registerComponent(
        name: String,
        path: String,
        parser: (ConfigurationSection) -> T
    ) {
        val section = plugin.config.getConfigurationSection(path) ?: return
        registerComponent(name, section.getValues(false).mapNotNull { (key, value) ->
            if (value !is ConfigurationSection) return@mapNotNull null
            Pair(key, parser(value))
        }.toMap())
    }

    val settingsChoice: Map<String, ChessGame.Settings>
        get() {
            val presets = plugin.config.getConfigurationSection("Settings.Presets") ?: return emptyMap()
            return presets.getValues(false).mapNotNull { (key, value) ->
                if (value !is ConfigurationSection) return@mapNotNull null
                val relaxedInsufficientMaterial = value.getBoolean("Relaxed")
                val components = value.getValues(false)
                    .mapNotNull { (k, v) -> componentChoice[k]?.get(v.toString()) }
                Pair(key, ChessGame.Settings(key, relaxedInsufficientMaterial, components))
            }.toMap()
        }

}
