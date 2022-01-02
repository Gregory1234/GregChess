package gregc.gregchess.bukkit

import gregc.gregchess.chess.ChessEnvironment
import kotlinx.serialization.KSerializer
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
}
