package gregc.gregchess.results

import gregc.gregchess.Color
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = MatchScore.Serializer::class)
sealed class MatchScore(val pgn: String) {
    companion object {
        private fun victoryPgn(winner: Color) = when (winner) {
            Color.WHITE -> "1-0"
            Color.BLACK -> "0-1"
        }
    }

    override fun toString(): String = pgn

    @PublishedApi
    internal object Serializer : KSerializer<MatchScore> {
        override val descriptor: SerialDescriptor
            get() = PrimitiveSerialDescriptor("MatchScore", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: MatchScore) {
            encoder.encodeString(value.pgn)
        }

        override fun deserialize(decoder: Decoder): MatchScore = when (decoder.decodeString()) {
            Draw.pgn -> Draw
            victoryPgn(Color.WHITE) -> Victory(Color.WHITE)
            victoryPgn(Color.BLACK) -> Victory(Color.BLACK)
            else -> error("${decoder.decodeString()} is not a valid MatchScore")
        }

    }

    class Victory(val winner: Color) : MatchScore(victoryPgn(winner)) {
        override fun equals(other: Any?): Boolean = this === other || other is Victory && winner == other.winner
        override fun hashCode(): Int = winner.hashCode()
    }

    object Draw : MatchScore("1/2-1/2")
}