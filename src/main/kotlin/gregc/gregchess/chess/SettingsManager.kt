package gregc.gregchess.chess

import gregc.gregchess.ConfigManager
import gregc.gregchess.View

object SettingsManager {

    private val componentChoice: MutableMap<String, Map<String, ChessGame.ComponentSettings>> =
        mutableMapOf()

    fun <T : ChessGame.ComponentSettings> registerComponent(name: String, presets: Map<String, T>) {
        componentChoice[name] = presets
    }

    inline fun <T : ChessGame.ComponentSettings>
            registerComponent(name: String, parser: (View) -> T) {
        val view = ConfigManager.getView("Settings.$name")
        registerComponent(name, view.children.mapValues { (_, child) -> parser(child) })
    }

    val settingsChoice: Map<String, ChessGame.Settings>
        get() {
            val presets = ConfigManager.getView("Settings.Presets")
            return presets.children.mapValues { (key, child) ->
                val relaxedInsufficientMaterial = child.getBool("Relaxed", true)
                val simpleCastling = child.getBool("SimpleCastling", false)
                val components = child.toMap().mapNotNull { (k, v) -> componentChoice[k]?.get(v) }
                ChessGame.Settings(key, relaxedInsufficientMaterial, simpleCastling, components)
            }
        }

}
