package gregc.gregchess.bukkitutils.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModuleBuilder
import org.bukkit.Bukkit
import org.bukkit.World

object WorldSerializer : KSerializer<World> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("World", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: World) = encoder.encodeString(value.name)

    override fun deserialize(decoder: Decoder): World = Bukkit.getWorld(decoder.decodeString())!!
}

fun SerializersModuleBuilder.addBukkitSerializers() {
    contextual(World::class, WorldSerializer)
}