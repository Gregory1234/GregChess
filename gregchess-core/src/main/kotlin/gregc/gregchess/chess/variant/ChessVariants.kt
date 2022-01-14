package gregc.gregchess.chess.variant

import gregc.gregchess.ChessModule
import gregc.gregchess.registerVariant
import gregc.gregchess.registry.*

object ChessVariants {
    internal val AUTO_REGISTER = AutoRegisterType(ChessVariant::class) { v, m, n -> m.registerVariant(n, v); v.registerAll(m) }

    @JvmField
    @Register
    val NORMAL = ChessVariant.Normal
    @JvmField
    @Register
    val ANTICHESS = Antichess
    @JvmField
    @Register
    val ATOMIC = AtomicChess
    @JvmField
    @Register
    val CAPTURE_ALL = CaptureAll
    @JvmField
    @Register
    val HORDE = HordeChess
    @JvmField
    @Register
    val KING_OF_THE_HILL = KingOfTheHill
    @JvmField
    @Register
    val THREE_CHECKS = ThreeChecks

    fun registerCore(module: ChessModule) = AutoRegister(module, listOf(AUTO_REGISTER)).registerAll<ChessVariants>()
}