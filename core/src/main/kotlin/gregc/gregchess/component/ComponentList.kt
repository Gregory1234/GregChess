package gregc.gregchess.component

import gregc.gregchess.utils.ClassMapSerializer
import gregc.gregchess.match.AnyFacade
import gregc.gregchess.match.ChessMatch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

// TODO: remove this?
interface ComponentIdentifier<T : Component> {
    val matchedTypes: Set<ComponentType<out T>>? get() = null
    @Suppress("UNCHECKED_CAST")
    fun matchesOrNull(c: Component): T? = matchedTypes?.let { if (c.type in it) c as T else null }
}

class ComponentNotFoundException(type: ComponentIdentifier<*>) : NoSuchElementException(type.toString())

interface ComponentCollection : Collection<Component> {
    operator fun <T : Component> get(type: ComponentIdentifier<T>): T?
    fun <T : Component> require(type: ComponentIdentifier<T>): T = get(type) ?: throw ComponentNotFoundException(type)
    operator fun contains(type: ComponentIdentifier<*>) = get(type) != null
}

@Serializable(ComponentList.Serializer::class)
class ComponentList private constructor(private val componentMap: Map<ComponentType<*>, Component>) : ComponentCollection {
    constructor(components: Iterable<Component>) : this(createComponentMap(components))
    constructor(vararg components: Component) : this(components.toList())

    companion object {
        private fun createComponentMap(components: Iterable<Component>): Map<ComponentType<*>, Component> =
            if (components is ComponentList)
                components.componentMap
            else
                buildMap {
                    for (c in components) {
                        check(!containsKey(c.type))
                        put(c.type, c)
                    }
                }
        private fun validateComponentMap(components: Map<ComponentType<*>, Component>) {
            components.forEach { (k, v) ->
                check(v.type == k)
            }
        }
    }

    @PublishedApi
    internal object Serializer : ClassMapSerializer<ComponentList, ComponentType<*>, Component>("ComponentList", ComponentType.Serializer) {
        override fun ComponentList.asMap(): Map<ComponentType<*>, Component> = componentMap
        override fun fromMap(m: Map<ComponentType<*>, Component>): ComponentList {
            validateComponentMap(m)
            return ComponentList(m)
        }

        @Suppress("UNCHECKED_CAST")
        override fun ComponentType<*>.valueSerializer(module: SerializersModule): KSerializer<Component> = serializer as KSerializer<Component>
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Component> getFromType(type: ComponentType<T>): T? =
        componentMap[type] as T?

    override operator fun <T : Component> get(type: ComponentIdentifier<T>): T? {
        if (type is ComponentType<T>)
            return getFromType(type)
        val matches = type.matchedTypes
        return if (matches != null && matches.size < size)
            matches.firstOrNull { it in componentMap }?.let { getFromType(it) }
        else
            componentMap.values.firstNotNullOfOrNull { type.matchesOrNull(it) }
    }

    override val size: Int get() = componentMap.size
    override fun isEmpty(): Boolean = componentMap.isEmpty()
    override fun iterator(): Iterator<Component> = componentMap.values.iterator()
    override fun containsAll(elements: Collection<Component>): Boolean = componentMap.values.containsAll(elements)
    override fun contains(element: Component): Boolean = componentMap.containsValue(element)
}

// TODO: implied components should be accessible?
class ComponentListFacade(override val match: ChessMatch, val components: ComponentList) : AnyFacade, ComponentCollection by components {
    private val facadeCache = mutableMapOf<Component, ComponentFacade<*>>()
    @Suppress("UNCHECKED_CAST")
    fun <T : Component, F : ComponentFacade<T>> makeCachedFacade(mk: (ChessMatch, T) -> F, component: T): F = facadeCache.getOrPut(component) { mk(match, component) } as F
}