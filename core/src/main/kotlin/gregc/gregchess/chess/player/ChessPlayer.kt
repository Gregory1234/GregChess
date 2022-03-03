package gregc.gregchess.chess.player

import gregc.gregchess.chess.ChessGame
import gregc.gregchess.chess.Color
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable(with = ChessPlayer.Serializer::class)
class ChessPlayer internal constructor(val type: ChessPlayerType<*>, val value: Any) {
    @OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
    object Serializer : KSerializer<ChessPlayer> {
        override val descriptor = buildClassSerialDescriptor("ChessPlayer") {
            element("type", ChessPlayerType.Serializer.descriptor)
            element("value", buildSerialDescriptor("ChessPlayerValue", SerialKind.CONTEXTUAL))
        }

        override fun serialize(encoder: Encoder, value: ChessPlayer) = encoder.encodeStructure(descriptor) {
            encodeSerializableElement(descriptor, 0, ChessPlayerType.Serializer, value.type)
            encodeSerializableElement(descriptor, 1, value.unsafeType.serializer, value.value)
        }

        override fun deserialize(decoder: Decoder): ChessPlayer = decoder.decodeStructure(descriptor) {
            var type: ChessPlayerType<*>? = null
            var data: Any? = null
            if (decodeSequentially()) { // sequential decoding protocol
                type = decodeSerializableElement(descriptor, 0, ChessPlayerType.Serializer)
                data = decodeSerializableElement(descriptor, 1, type.serializer)
            } else {
                while (true) {
                    when (val index = decodeElementIndex(descriptor)) {
                        0 -> type = decodeSerializableElement(descriptor, index, ChessPlayerType.Serializer)
                        1 -> data = decodeSerializableElement(descriptor, index, type!!.serializer)
                        CompositeDecoder.DECODE_DONE -> break
                        else -> error("Unexpected index: $index")
                    }
                }
            }
            ChessPlayer(type!!, data!!)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private val unsafeType get() = type as ChessPlayerType<Any>
    val name: String get() = unsafeType.nameOf(value)
    fun initSide(c: Color, g: ChessGame): ChessSide<*> = unsafeType.initSide(value, c, g)

    override fun equals(other: Any?): Boolean =
        this === other || other is ChessPlayer && type == other.type && value == other.value

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + value.hashCode()
        return result
    }

    override fun toString(): String = "ChessPlayer(type=$type, value=$value)"

}