package gregc.gregchess.results

import gregc.gregchess.util.Color
import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.*

fun Color.wonBy(reason: DetEndReason, vararg args: String): GameResults =
    GameResultsWith(reason, GameScore.Victory(this), args.toList())

fun Color.lostBy(reason: DetEndReason, vararg args: String): GameResults = (!this).wonBy(reason, *args)

fun whiteWonBy(reason: DetEndReason, vararg args: String): GameResults = Color.WHITE.wonBy(reason, *args)
fun blackWonBy(reason: DetEndReason, vararg args: String): GameResults = Color.BLACK.wonBy(reason, *args)

fun drawBy(reason: DrawEndReason, vararg args: String): GameResults =
    GameResultsWith(reason, GameScore.Draw, args.toList())

@Serializable(with = GameResultsSerializer::class)
data class GameResultsWith<out R : GameScore> internal constructor(
    val endReason: EndReason<out R>,
    val score: R,
    val args: List<String>
)

object GameResultsSerializer : KSerializer<GameResults> {
    override val descriptor = buildClassSerialDescriptor("GameResults") {
        element("endReason", EndReason.Serializer.descriptor)
        element("score", GameScore.Serializer.descriptor)
        element<List<String>>("args")
    }

    override fun serialize(encoder: Encoder, value: GameResults) = encoder.encodeStructure(descriptor) {
        encodeSerializableElement(descriptor, 0, EndReason.Serializer, value.endReason)
        encodeSerializableElement(descriptor, 1, GameScore.Serializer, value.score)
        encodeSerializableElement(descriptor, 2, ListSerializer(String.serializer()), value.args)
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder): GameResults = decoder.decodeStructure(descriptor) {
        var endReason: EndReason<*>? = null
        var score: GameScore? = null
        var args: List<String>? = null
        if (decodeSequentially()) { // sequential decoding protocol
            endReason = decodeSerializableElement(descriptor, 0, EndReason.Serializer)
            score = decodeSerializableElement(descriptor, 1, GameScore.Serializer)
            args = decodeSerializableElement(descriptor, 2, ListSerializer(String.serializer()))
        } else {
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> endReason = decodeSerializableElement(descriptor, 0, EndReason.Serializer)
                    1 -> score = decodeSerializableElement(descriptor, 1, GameScore.Serializer)
                    2 -> args = decodeSerializableElement(descriptor, 2, ListSerializer(String.serializer()))
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
        }
        GameResultsWith(endReason!!, score!!, args!!)
    }
}

typealias GameResults = GameResultsWith<*>