package gregc.gregchess.chess

import gregc.gregchess.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private fun victoryPgn(winner: Side) = when(winner) {
    Side.WHITE -> "1-0"
    Side.BLACK -> "0-1"
}

@Serializable
sealed class GameScore(val pgn: String) {
    @Serializable
    @SerialName("Victory")
    class Victory(val winner: Side): GameScore(victoryPgn(winner))
    @Serializable
    @SerialName("Draw")
    object Draw: GameScore("1/2-1/2")
}

typealias DetEndReason = EndReason<GameScore.Victory>
typealias DrawEndReason = EndReason<GameScore.Draw>

@Serializable(with = EndReason.Serializer::class)
class EndReason<R : GameScore>(val type: Type, val quick: Boolean = false): NameRegistered {

    object Serializer: NameRegisteredSerializer<EndReason<*>>("EndReason", RegistryType.END_REASON)

    enum class Type(val pgn: String) {
        NORMAL("normal"), ABANDONED("abandoned"), TIME_FORFEIT("time forfeit"), EMERGENCY("emergency")
    }

    override val module get() = RegistryType.END_REASON.getModule(this)
    override val name get() = RegistryType.END_REASON[this]

    override fun toString(): String = "${module.namespace}:$name@${hashCode().toString(16)}"

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

fun Side.wonBy(reason: DetEndReason, vararg args: Any?) = GameResults(reason, GameScore.Victory(this), args.toList())
fun Side.lostBy(reason: DetEndReason, vararg args: Any?) = (!this).wonBy(reason, *args)
fun drawBy(reason: DrawEndReason, vararg args: Any?) = GameResults(reason, GameScore.Draw, args.toList())
fun DrawEndReason.of(vararg args: Any?) = GameResults(this, GameScore.Draw, args.toList())

data class GameResults<R : GameScore>(val endReason: EndReason<R>, val score: R, val args: List<Any?>)