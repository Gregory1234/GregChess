package gregc.gregchess

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.ComponentType
import gregc.gregchess.chess.move.ChessVariantOption
import gregc.gregchess.chess.move.MoveTraitType
import gregc.gregchess.chess.piece.PieceType
import gregc.gregchess.chess.piece.PlacedPieceType
import gregc.gregchess.chess.variant.ChessVariants

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
}