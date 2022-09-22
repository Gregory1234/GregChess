package gregc.gregchess.variant

import gregc.gregchess.CoreRegistry
import gregc.gregchess.Registering
import gregc.gregchess.registry.*

object ChessVariants {
    internal val AUTO_REGISTER = AutoRegisterType(ChessVariant::class) { m, n, _ ->
        CoreRegistry.VARIANT[m, n] = this
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