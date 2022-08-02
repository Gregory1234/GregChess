package gregc.gregchess

import gregc.gregchess.match.ComponentType
import gregc.gregchess.move.trait.MoveTraitType
import gregc.gregchess.piece.PieceType
import gregc.gregchess.piece.PlacedPieceType
import gregc.gregchess.player.ChessSideType
import gregc.gregchess.results.EndReason
import gregc.gregchess.stats.ChessStat
import gregc.gregchess.variant.ChessVariants

object GregChessCore {
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
    val AUTO_REGISTER = listOf(
        PieceType.AUTO_REGISTER, EndReason.AUTO_REGISTER, ChessFlag.AUTO_REGISTER, ComponentType.AUTO_REGISTER,
        ChessVariants.AUTO_REGISTER, MoveTraitType.AUTO_REGISTER, ChessStat.AUTO_REGISTER,
        ChessSideType.AUTO_REGISTER, PlacedPieceType.AUTO_REGISTER,
    )

    fun autoRegister(module: ChessModule) = AutoRegister(module, AUTO_REGISTER)
}