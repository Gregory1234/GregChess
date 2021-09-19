package gregc.gregchess.chess

import gregc.gregchess.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

private fun victoryPgn(winner: Side) = when(winner) {
    Side.WHITE -> "1-0"
    Side.BLACK -> "0-1"
}

@Serializable(with = GameScore.Serializer::class)
sealed class GameScore(val pgn: String) {
    override fun toString(): String = pgn

    object Serializer: KSerializer<GameScore> {
        override val descriptor: SerialDescriptor
            get() = PrimitiveSerialDescriptor("GameScore", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: GameScore) {
            encoder.encodeString(value.pgn)
        }

        override fun deserialize(decoder: Decoder): GameScore = when(decoder.decodeString()) {
            Draw.pgn -> Draw
            victoryPgn(Side.WHITE) -> Victory(Side.WHITE)
            victoryPgn(Side.BLACK) -> Victory(Side.BLACK)
            else -> throw IllegalStateException()
        }

    }

    @Serializable(with = Victory.Serializer::class)
    class Victory(val winner: Side): GameScore(victoryPgn(winner)) {
        object Serializer: KSerializer<Victory> {
            override val descriptor: SerialDescriptor
                get() = PrimitiveSerialDescriptor("GameScore.Victory", PrimitiveKind.STRING)

            override fun serialize(encoder: Encoder, value: Victory) {
                encoder.encodeString(value.pgn)
            }

            override fun deserialize(decoder: Decoder): Victory = when(decoder.decodeString()) {
                victoryPgn(Side.WHITE) -> Victory(Side.WHITE)
                victoryPgn(Side.BLACK) -> Victory(Side.BLACK)
                else -> throw IllegalStateException()
            }
        }
    }
    @Serializable(with = Draw.Serializer::class)
    object Draw: GameScore("1/2-1/2") {
        object Serializer: KSerializer<Draw> {
            override val descriptor: SerialDescriptor
                get() = PrimitiveSerialDescriptor("GameScore.Draw", PrimitiveKind.STRING)

            override fun serialize(encoder: Encoder, value: Draw) {
                encoder.encodeString(value.pgn)
            }

            override fun deserialize(decoder: Decoder): Draw = when(decoder.decodeString()) {
                Draw.pgn -> Draw
                else -> throw IllegalStateException()
            }

        }
    }
}

typealias DetEndReason = EndReason<GameScore.Victory>
typealias DrawEndReason = EndReason<GameScore.Draw>

@Serializable(with = EndReason.Serializer::class)
class EndReason<R : GameScore>(val type: Type, val quick: Boolean = false): NameRegistered {

    object Serializer: NameRegisteredSerializer<EndReason<*>>("EndReason", RegistryType.END_REASON)

    enum class Type(val pgn: String) {
        NORMAL("normal"), ABANDONED("abandoned"), TIME_FORFEIT("time forfeit"), EMERGENCY("emergency")
    }

    override val key get() = RegistryType.END_REASON[this]

    override fun toString(): String = "$key@${hashCode().toString(16)}"

    companion object {
        @JvmField
        val CHECKMATE = GregChessModule.register("checkmate", DetEndReason(Type.NORMAL))
        @JvmField
        val RESIGNATION = GregChessModule.register("resignation", DetEndReason(Type.ABANDONED))
        @JvmField
        val WALKOVER = GregChessModule.register("walkover", DetEndReason(Type.ABANDONED))
        @JvmField
        val STALEMATE = GregChessModule.register("stalemate", DrawEndReason(Type.NORMAL))
        @JvmField
        val INSUFFICIENT_MATERIAL = GregChessModule.register("insufficient_material", DrawEndReason(Type.NORMAL))
        @JvmField
        val FIFTY_MOVES = GregChessModule.register("fifty_moves", DrawEndReason(Type.NORMAL))
        @JvmField
        val REPETITION = GregChessModule.register("repetition", DrawEndReason(Type.NORMAL))
        @JvmField
        val DRAW_AGREEMENT = GregChessModule.register("draw_agreement", DrawEndReason(Type.NORMAL))
        @JvmField
        val TIMEOUT = GregChessModule.register("timeout", DetEndReason(Type.TIME_FORFEIT))
        @JvmField
        val DRAW_TIMEOUT = GregChessModule.register("draw_timeout", DrawEndReason(Type.TIME_FORFEIT))
        @JvmField
        val ALL_PIECES_LOST = GregChessModule.register("all_pieces_lost", DetEndReason(Type.NORMAL))
        @JvmField
        val ERROR = GregChessModule.register("error", DrawEndReason(Type.EMERGENCY))
    }

    val pgn get() = type.pgn
}

fun Side.wonBy(reason: DetEndReason, vararg args: String): GameResults = GameResultsWith(reason, GameScore.Victory(this), args.toList())
fun Side.lostBy(reason: DetEndReason, vararg args: String): GameResults = (!this).wonBy(reason, *args)
fun whiteWonBy(reason: DetEndReason, vararg args: String): GameResults = Side.WHITE.wonBy(reason, *args)
fun blackWonBy(reason: DetEndReason, vararg args: String): GameResults = Side.BLACK.wonBy(reason, *args)
fun drawBy(reason: DrawEndReason, vararg args: String): GameResults = GameResultsWith(reason, GameScore.Draw, args.toList())
fun DrawEndReason.of(vararg args: String): GameResults = GameResultsWith(this, GameScore.Draw, args.toList())

@Serializable
data class GameResultsWith<out R : GameScore> internal constructor(val endReason: EndReason<out R>, val score: R, val args: List<String>)

typealias GameResults = GameResultsWith<GameScore>