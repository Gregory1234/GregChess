package gregc.gregchess.bukkit

import gregc.gregchess.Identifier

data class IdentifierAlreadyUsedException(val id: Identifier, val original: Any?, val duplicate: Any?) :
    Exception("$id - original: $original, duplicate: $duplicate")

data class AlreadyRegisteredException(val o: Any, val original: Identifier, val duplicate: Identifier) :
    Exception("$o - original: $original, duplicate: $duplicate")

open class Registry<T: Any> {
    private val values = mutableMapOf<Identifier, T>()


    fun register(id: Identifier, v: T) {
        if (id in values)
            throw IdentifierAlreadyUsedException(id, values[id], v)
        if (values.containsValue(v))
            throw AlreadyRegisteredException(v, getId(v)!!, id)
        values[id] = v
    }

    fun getId(v: T) = values.filterValues { it == v }.keys.firstOrNull()
    operator fun get(id: Identifier) = values[id]

    val ids get() = values.keys
}