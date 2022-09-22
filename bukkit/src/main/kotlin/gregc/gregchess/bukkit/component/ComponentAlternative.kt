package gregc.gregchess.bukkit.component

import gregc.gregchess.*
import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.renderer.Renderer
import gregc.gregchess.component.*
import gregc.gregchess.registry.*
import org.bukkit.configuration.ConfigurationSection

interface SelectableComponentIdentifier<T : Component> : ComponentIdentifier<T> {
    fun getGlobalSelected(): ComponentType<out T>
    fun getLocalSelected(section: ConfigurationSection): ComponentType<out T>
}

class ComponentAlternative<T : Component>(private val default: ComponentType<out T>) : NameRegistered, SelectableComponentIdentifier<T> {
    override val key get() = BukkitRegistry.COMPONENT_ALTERNATIVE[this]

    override fun toString(): String = BukkitRegistry.COMPONENT_ALTERNATIVE.simpleElementToString(this)

    override fun getGlobalSelected() = getSelected()
    override fun getLocalSelected(section: ConfigurationSection) = getSelectedOrNull(config, name.snakeToPascal()) ?: getSelected()

    private fun getSelectedOrNull(config: ConfigurationSection = module.config, path: String = "Component.${name.snakeToPascal()}"): ComponentType<out T>? {
        @Suppress("UNCHECKED_CAST")
        val ret = config.getFromRegistry(CoreRegistry.COMPONENT_TYPE, path) as ComponentType<out T>?
        return when (ret) {
            null -> null
            !in this -> {
                module.logger.warn("Invalid $this on path ${config.currentPath}.$path")
                null
            }
            else -> ret
        }
    }

    private fun getSelected(config: ConfigurationSection = module.config, path: String = "Component.${name.snakeToPascal()}"): ComponentType<out T> =
        getSelectedOrNull(config, path) ?: default

    private val values = mutableSetOf(default)

    operator fun contains(type: ComponentType<out T>) = type in values
    operator fun plusAssign(type: ComponentType<out T>) {
        require(type !in values)
        values += type
    }

    override val matchedTypes: Set<ComponentType<out T>> get() = values

    @RegisterAll(ComponentAlternative::class)
    companion object {

        internal val AUTO_REGISTER = AutoRegisterType(ComponentAlternative::class) { m, n, _ -> BukkitRegistry.COMPONENT_ALTERNATIVE[m, n] = this }

        @JvmField
        val RENDERER = ComponentAlternative<Renderer>(BukkitComponentType.RENDERER)

    }
}