package gregc.gregchess.event

import gregc.gregchess.component.ComponentType

interface EventListener {
    fun isOf(parent: EventListener): Boolean
    operator fun div(subOwner: Any): EventListener
}

class ComplexEventListener private constructor(val component: ComponentType<*>, val path: List<Any>): EventListener {
    constructor(component: ComponentType<*>, path1: Any, vararg path: Any) : this(component, listOf(path1, *path))
    override fun isOf(parent: EventListener): Boolean = this == parent ||
            (parent is ComponentType<*> && component == parent) ||
            (parent is ComplexEventListener && component == parent.component && path.subList(0, parent.path.size) == parent.path)
    override fun div(subOwner: Any) = ComplexEventListener(component, path + subOwner)
    override fun equals(other: Any?): Boolean = other is ComplexEventListener && other.component == component && other.path == path
    override fun hashCode(): Int {
        var result = component.hashCode()
        result = 31 * result + path.hashCode()
        return result
    }
    override fun toString(): String = buildString {
        append(component)
        for (p in path) {
            append(" -> ")
            append(p)
        }
    }
}