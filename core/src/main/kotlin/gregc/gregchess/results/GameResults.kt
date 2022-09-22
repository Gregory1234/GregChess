package gregc.gregchess.results

import gregc.gregchess.Color
import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.*

fun Color.wonBy(reason: DetEndReason, vararg args: String): MatchResults =
    MatchResultsWith(reason, MatchScore.Victory(this), args.toList())

fun Color.lostBy(reason: DetEndReason, vararg args: String): MatchResults = (!this).wonBy(reason, *args)

fun whiteWonBy(reason: DetEndReason, vararg args: String): MatchResults = Color.WHITE.wonBy(reason, *args)
fun blackWonBy(reason: DetEndReason, vararg args: String): MatchResults = Color.BLACK.wonBy(reason, *args)

fun drawBy(reason: DrawEndReason, vararg args: String): MatchResults =
    MatchResultsWith(reason, MatchScore.Draw, args.toList())

@Serializable(with = MatchResultsSerializer::class)
data class MatchResultsWith<out R : MatchScore> internal constructor(
    val endReason: EndReason<out R>,
    val score: R,
    val args: List<String>
)

@PublishedApi
internal object MatchResultsSerializer : KSerializer<MatchResults> {
    override val descriptor = buildClassSerialDescriptor("MatchResults") {
        element("endReason", EndReason.Serializer.descriptor)
        element("score", MatchScore.Serializer.descriptor)
        element<List<String>>("args")
    }

    override fun serialize(encoder: Encoder, value: MatchResults) = encoder.encodeStructure(descriptor) {
        encodeSerializableElement(descriptor, 0, EndReason.Serializer, value.endReason)
        encodeSerializableElement(descriptor, 1, MatchScore.Serializer, value.score)
        encodeSerializableElement(descriptor, 2, ListSerializer(String.serializer()), value.args)
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder): MatchResults = decoder.decodeStructure(descriptor) {
        var endReason: EndReason<*>? = null
        var score: MatchScore? = null
        var args: List<String>? = null
        if (decodeSequentially()) { // sequential decoding protocol
            endReason = decodeSerializableElement(descriptor, 0, EndReason.Serializer)
            score = decodeSerializableElement(descriptor, 1, MatchScore.Serializer)
            args = decodeSerializableElement(descriptor, 2, ListSerializer(String.serializer()))
        } else {
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> endReason = decodeSerializableElement(descriptor, 0, EndReason.Serializer)
                    1 -> score = decodeSerializableElement(descriptor, 1, MatchScore.Serializer)
                    2 -> args = decodeSerializableElement(descriptor, 2, ListSerializer(String.serializer()))
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
        }
        MatchResultsWith(endReason!!, score!!, args!!)
    }
}

typealias MatchResults = MatchResultsWith<*>