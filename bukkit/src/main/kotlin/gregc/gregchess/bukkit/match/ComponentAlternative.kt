package gregc.gregchess.bukkit.match

import gregc.gregchess.*
import gregc.gregchess.bukkit.config
import gregc.gregchess.bukkit.registry.BukkitRegistry
import gregc.gregchess.bukkit.registry.getFromRegistry
import gregc.gregchess.bukkit.renderer.Renderer
import gregc.gregchess.match.*
import gregc.gregchess.registry.*
import org.bukkit.configuration.ConfigurationSection

class ComponentAlternative<T : Component>(private val default: ComponentType<out T>) : NameRegistered, ComponentIdentifier<T> {
    override val key get() = BukkitRegistry.COMPONENT_ALTERNATIVE[this]

    override fun toString(): String = BukkitRegistry.COMPONENT_ALTERNATIVE.simpleElementToString(this)

    fun getSelectedOrNull(config: ConfigurationSection = module.config, path: String = "Component.${name.snakeToPascal()}"): ComponentType<out T>? {
        @Suppress("UNCHECKED_CAST")
        val ret = config.getFromRegistry(Registry.COMPONENT_TYPE, path) as ComponentType<out T>?
        return when (ret) {
            null -> null
            !in this -> {
                module.logger.warn("Invalid $this on path ${config.currentPath}.$path")
                null
            }
            else -> ret
        }
    }

    fun getSelected(config: ConfigurationSection = module.config, path: String = "Component.${name.snakeToPascal()}"): ComponentType<out T> =
        getSelectedOrNull(config, path) ?: default

    private val values = mutableSetOf(default)

    operator fun contains(type: ComponentType<out T>) = type in values
    operator fun plusAssign(type: ComponentType<out T>) {
        require(type !in values)
        values += type
    }

    @Suppress("UNCHECKED_CAST")
    override fun matchesOrNull(c: Component): T? = if (c.type in values) c as T else null

    @RegisterAll(ComponentAlternative::class)
    companion object {

        internal val AUTO_REGISTER = AutoRegisterType(ComponentAlternative::class) { m, n, _ -> BukkitRegistry.COMPONENT_ALTERNATIVE[m, n] = this }

        @JvmField
        val RENDERER = ComponentAlternative<Renderer>(BukkitComponentType.RENDERER)

    }
}