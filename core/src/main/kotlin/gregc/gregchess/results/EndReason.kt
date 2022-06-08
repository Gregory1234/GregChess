package gregc.gregchess.results

import gregc.gregchess.*
import gregc.gregchess.registry.*
import kotlinx.serialization.Serializable

typealias DetEndReason = EndReason<MatchScore.Victory>
typealias DrawEndReason = EndReason<MatchScore.Draw>

@Serializable(with = EndReason.Serializer::class)
class EndReason<@Suppress("UNUSED") R : MatchScore>(val type: Type) : NameRegistered {

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