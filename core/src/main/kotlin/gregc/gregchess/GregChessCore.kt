package gregc.gregchess

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.ComponentType
import gregc.gregchess.chess.move.ChessVariantOption
import gregc.gregchess.chess.move.MoveTraitType
import gregc.gregchess.chess.piece.PieceType
import gregc.gregchess.chess.piece.PlacedPieceType
import gregc.gregchess.chess.player.ChessPlayerType
import gregc.gregchess.chess.variant.ChessVariants
import gregc.gregchess.util.AutoRegister

object GregChessCore {
    fun registerAll(module: ChessModule) {
        PieceType.registerCore(module)
        EndReason.registerCore(module)
        ChessFlag.registerCore(module)
        ComponentType.registerCore(module)
        ChessVariants.registerCore(module)
        MoveTraitType.registerCore(module)
        PlacedPieceType.registerCore(module)
        ChessVariantOption.registerCore(module)
        ChessStat.registerCore(module)
    }

    @JvmField
    val AUTO_REGISTER = listOf(
        PieceType.AUTO_REGISTER, EndReason.AUTO_REGISTER, ChessFlag.AUTO_REGISTER, ComponentType.AUTO_REGISTER,
        ChessVariants.AUTO_REGISTER, MoveTraitType.AUTO_REGISTER, ChessStat.AUTO_REGISTER,
        ChessPlayerType.AUTO_REGISTER, PlacedPieceType.AUTO_REGISTER, ChessVariantOption.AUTO_REGISTER,
    )

    fun autoRegister(module: ChessModule) = AutoRegister(module, AUTO_REGISTER)
}