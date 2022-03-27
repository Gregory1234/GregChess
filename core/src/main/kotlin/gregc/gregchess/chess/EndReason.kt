package gregc.gregchess.chess

import gregc.gregchess.ChessModule
import gregc.gregchess.registry.*
import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

private fun victoryPgn(winner: Color) = when (winner) {
    Color.WHITE -> "1-0"
    Color.BLACK -> "0-1"
}

@Serializable(with = GameScore.Serializer::class)
sealed class GameScore(val pgn: String) {
    override fun toString(): String = pgn

    object Serializer : KSerializer<GameScore> {
        override val descriptor: SerialDescriptor
            get() = PrimitiveSerialDescriptor("GameScore", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: GameScore) {
            encoder.encodeString(value.pgn)
        }

        override fun deserialize(decoder: Decoder): GameScore = when (decoder.decodeString()) {
            Draw.pgn -> Draw
            victoryPgn(Color.WHITE) -> Victory(Color.WHITE)
            victoryPgn(Color.BLACK) -> Victory(Color.BLACK)
            else -> error("${decoder.decodeString()} is not a valid GameScore")
        }

    }

    class Victory(val winner: Color) : GameScore(victoryPgn(winner)) {
        override fun equals(other: Any?): Boolean = this === other || other is Victory && winner == other.winner
        override fun hashCode(): Int = winner.hashCode()
    }

    object Draw : GameScore("1/2-1/2")
}

typealias DetEndReason = EndReason<GameScore.Victory>
typealias DrawEndReason = EndReason<GameScore.Draw>


@Serializable(with = EndReason.Serializer::class)
class EndReason<@Suppress("UNUSED") R : GameScore>(val type: Type) : NameRegistered {

    object Serializer : NameRegisteredSerializer<EndReason<*>>("EndReason", Registry.END_REASON)

    enum class Type(val pgn: String) {
        NORMAL("normal"), ABANDONED("abandoned"), TIME_FORFEIT("time forfeit"), EMERGENCY("emergency")
    }

    override val key get() = Registry.END_REASON[this]

    override fun toString(): String = Registry.END_REASON.simpleElementToString(this)

    @RegisterAll(EndReason::class)
    companion object {

        internal val AUTO_REGISTER = AutoRegisterType(EndReason::class) { m, n, _ -> Registry.END_REASON[m, n] = this }

        @JvmField
        val CHECKMATE = DetEndReason(Type.NORMAL)
        @JvmField
        val RESIGNATION = DetEndReason(Type.ABANDONED)
        @JvmField
        val WALKOVER = DetEndReason(Type.ABANDONED)
        @JvmField
        val WALKOVER_DRAW = DrawEndReason(Type.ABANDONED)
        @JvmField
        val STALEMATE = DrawEndReason(Type.NORMAL)
        @JvmField
        val INSUFFICIENT_MATERIAL = DrawEndReason(Type.NORMAL)
        @JvmField
        val FIFTY_MOVES = DrawEndReason(Type.NORMAL)
        @JvmField
        val REPETITION = DrawEndReason(Type.NORMAL)
        @JvmField
        val DRAW_AGREEMENT = DrawEndReason(Type.NORMAL)
        @JvmField
        val TIMEOUT = DetEndReason(Type.TIME_FORFEIT)
        @JvmField
        val DRAW_TIMEOUT = DrawEndReason(Type.TIME_FORFEIT)
        @JvmField
        val ALL_PIECES_LOST = DetEndReason(Type.NORMAL)
        @JvmField
        val ERROR = DrawEndReason(Type.EMERGENCY)

        fun registerCore(module: ChessModule) = AutoRegister(module, listOf(AUTO_REGISTER)).registerAll<EndReason<*>>()
    }

    val pgn get() = type.pgn
}

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