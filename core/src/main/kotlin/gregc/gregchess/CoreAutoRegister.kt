package gregc.gregchess

import gregc.gregchess.component.ComponentType
import gregc.gregchess.move.trait.MoveTraitType
import gregc.gregchess.piece.PieceType
import gregc.gregchess.piece.PlacedPieceType
import gregc.gregchess.player.ChessSideType
import gregc.gregchess.registry.AutoRegister
import gregc.gregchess.registry.ChessModule
import gregc.gregchess.results.EndReason
import gregc.gregchess.stats.ChessStat
import gregc.gregchess.variant.ChessVariants

interface Registering {
    fun registerAll(module: ChessModule) {
        CoreAutoRegister(module).registerAll(this::class)
    }
}

object CoreAutoRegister {
    fun registerAll(module: ChessModule) {
        PieceType.registerCore(module)
        EndReason.registerCore(module)
        ChessFlag.registerCore(module)
        ComponentType.registerCore(module)
        ChessVariants.registerCore(module)
        MoveTraitType.registerCore(module)
        PlacedPieceType.registerCore(module)
        ChessStat.registerCore(module)
    }

    @JvmField
    val TYPES = listOf(
        PieceType.AUTO_REGISTER, EndReason.AUTO_REGISTER, ChessFlag.AUTO_REGISTER, ComponentType.AUTO_REGISTER,
        ChessVariants.AUTO_REGISTER, MoveTraitType.AUTO_REGISTER, ChessStat.AUTO_REGISTER,
        ChessSideType.AUTO_REGISTER, PlacedPieceType.AUTO_REGISTER,
    )

    operator fun invoke(module: ChessModule) = AutoRegister(module, TYPES)
}