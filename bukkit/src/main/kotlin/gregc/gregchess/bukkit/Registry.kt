package gregc.gregchess.bukkit

import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import org.bukkit.NamespacedKey

class IdentifierAlreadyUsedException(id: NamespacedKey, original: Any?, duplicate: Any?) :
    Exception("$id - original: $original, duplicate: $duplicate")

class AlreadyRegisteredException(o: Any, original: NamespacedKey, duplicate: NamespacedKey) :
    Exception("$o - original: $original, duplicate: $duplicate")

open class Registry<T: Any, D: Any> {
    private val values: BiMap<NamespacedKey, T> = HashBiMap.create()
    private val datas = mutableMapOf<NamespacedKey, D>()


    protected fun register(id: NamespacedKey, v: T, d: D) {
        if (id in values)
            throw IdentifierAlreadyUsedException(id, values[id], v)
        if (v in values.inverse())
            throw AlreadyRegisteredException(v, getId(v)!!, id)
        values[id] = v
        datas[id] = d
    }

    fun getId(v: T) = values.inverse()[v]
    fun getData(id: NamespacedKey): D? = datas[id]
    fun getData(v: T): D? = getId(v)?.let(::getData)
    operator fun get(id: NamespacedKey) = values[id]

    val ids get() = values.keys
}