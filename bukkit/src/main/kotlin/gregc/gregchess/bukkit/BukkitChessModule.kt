package gregc.gregchess.bukkit

import gregc.gregchess.GregChessCore
import gregc.gregchess.Registering
import gregc.gregchess.bukkit.component.ComponentAlternative
import gregc.gregchess.bukkit.match.defaultVariantOptionsParser
import gregc.gregchess.bukkit.move.defaultLocalMoveFormatter
import gregc.gregchess.bukkit.properties.PropertyType
import gregc.gregchess.bukkit.renderer.simpleFloorRenderer
import gregc.gregchess.registry.AutoRegister
import gregc.gregchess.registry.ChessModule
import gregc.gregchess.registry.registry.Registry
import gregc.gregchess.results.EndReason
import org.bukkit.plugin.Plugin

// TODO: rename this
object BukkitGregChessCore {
    val AUTO_REGISTER = listOf(
        EndReason.BUKKIT_AUTO_REGISTER, PropertyType.AUTO_REGISTER, ComponentAlternative.AUTO_REGISTER
    ) + GregChessCore.AUTO_REGISTER

    fun autoRegister(module: ChessModule) = AutoRegister(module, AUTO_REGISTER)
}

interface BukkitChessPlugin {
    fun onInitialize()
}

interface BukkitRegistering : Registering {
    override fun registerAll(module: ChessModule) {
        BukkitGregChessCore.autoRegister(module).registerAll(this::class)
    }
}

abstract class BukkitChessModule(val plugin: Plugin) : ChessModule(plugin.name, plugin.name.lowercase()) {
    companion object {
        internal val modules = mutableSetOf<ChessModule>()
        operator fun get(namespace: String) = modules.first { it.namespace == namespace }
    }

    final override fun postLoad() {
        BukkitRegistry.BUKKIT_PLUGIN[this] = plugin
        BukkitRegistry.SETTINGS_PARSER[this].completeWith { type -> { type.cl.objectInstance ?: type.cl.constructors.first { it.parameters.isEmpty() }.call() } }
        BukkitRegistry.LOCAL_MOVE_FORMATTER[this].completeWith { defaultLocalMoveFormatter }
        BukkitRegistry.FLOOR_RENDERER[this].completeWith { simpleFloorRenderer() }
        BukkitRegistry.VARIANT_OPTIONS_PARSER[this].completeWith { defaultVariantOptionsParser }
    }

    final override fun finish() {
        modules += this
    }

    final override fun validate() {
        Registry.REGISTRIES.forEach { it[this].validate() }
    }
}