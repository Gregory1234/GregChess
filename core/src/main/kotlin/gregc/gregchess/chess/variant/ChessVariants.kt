package gregc.gregchess.chess.variant

import gregc.gregchess.ChessModule
import gregc.gregchess.registry.Registry
import gregc.gregchess.util.*

object ChessVariants {
    internal val AUTO_REGISTER = AutoRegisterType(ChessVariant::class) { m, n, _ ->
        Registry.VARIANT[m, n] = this
        (this as? Registering)?.registerAll(m)
    }

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