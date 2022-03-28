package gregc.gregchess.bukkit

import gregc.gregchess.game.ChessEnvironment
import gregc.gregchess.registry.*
import gregc.gregchess.registry.Registry
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import org.bukkit.*
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.event.Listener
import java.util.*

internal data class Loc(val x: Int, val y: Int, val z: Int) {
    operator fun plus(offset: Loc) = Loc(x + offset.x, y + offset.y, z + offset.z)
}

internal fun Loc.toLocation(w: World) = Location(w, x.toDouble(), y.toDouble(), z.toDouble())
internal fun Location.toLoc() = Loc(blockX, blockY, blockZ)

internal fun Listener.registerEvents() = Bukkit.getPluginManager().registerEvents(this, GregChessPlugin.plugin)

internal val config: ConfigurationSection get() = GregChessPlugin.plugin.config

internal fun String.pascalToSnake(): String {
    val camelRegex = "(?<=[a-zA-Z])[A-Z]".toRegex()
    return camelRegex.replace(this) { "_${it.value}" }.lowercase()
}

internal object UUIDAsStringSerializer : KSerializer<UUID> {
    override val descriptor = PrimitiveSerialDescriptor("UUIDAsString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
}

@Suppress("UNCHECKED_CAST")
internal fun defaultModule() = SerializersModule {
    contextual(UUID::class, UUIDAsStringSerializer)
    contextual(ChessEnvironment::class, BukkitChessEnvironment.serializer() as KSerializer<ChessEnvironment>)
    contextual(RegistryKey::class) { ser ->
        when(ser) {
            listOf(String.serializer()) -> StringKeySerializer(BukkitChessModule.modules)
            else -> throw UnsupportedOperationException()
        }
    }
}

fun String.toKey(): RegistryKey<String> {
    val sections = split(":")
    return when (sections.size) {
        1 -> RegistryKey(GregChess, this)
        2 -> RegistryKey(BukkitChessModule[sections[0]], sections[1])
        else -> throw IllegalArgumentException("Bad registry key: $this")
    }
}

fun <T> ConfigurationSection.getFromRegistry(reg: Registry<String, T, *>, path: String): T? =
    getString(path)?.toKey()?.let { reg[it] }