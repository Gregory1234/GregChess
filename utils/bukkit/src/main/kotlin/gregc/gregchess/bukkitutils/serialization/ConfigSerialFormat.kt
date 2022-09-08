package gregc.gregchess.bukkitutils.serialization

import kotlinx.serialization.*
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import org.bukkit.configuration.ConfigurationSection

interface BukkitConfigFormat : SerialFormat {
    fun <T> encodeOnPath(serializer: SerializationStrategy<T>, path: String, value: T)
    fun <T> decodeFromPath(deserializer: DeserializationStrategy<T>, path: String): T
}

class BukkitConfig(
    val section: ConfigurationSection,
    override val serializersModule: SerializersModule = EmptySerializersModule()
) : BukkitConfigFormat {

    override fun <T> encodeOnPath(serializer: SerializationStrategy<T>, path: String, value: T) {
        BukkitConfigEncoder(serializersModule) {
            section.set(path, it)
        }.encodeSerializableValue(serializer, value)
    }

    override fun <T> decodeFromPath(deserializer: DeserializationStrategy<T>, path: String): T {
        return BukkitConfigDecoder(serializersModule, section.get(path)).decodeSerializableValue(deserializer)
    }
}

inline fun <reified T> BukkitConfig.encodeOnPath(path: String, value: T) = encodeOnPath(serializersModule.serializer(), path, value)
inline fun <reified T> BukkitConfig.decodeFromPath(path: String): T =
    decodeFromPath(serializersModule.serializer(), path)