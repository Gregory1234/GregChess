package gregc.gregchess

import gregc.gregchess.chess.ChessFlag
import gregc.gregchess.chess.EndReason
import gregc.gregchess.chess.component.ComponentType
import gregc.gregchess.chess.move.MoveNameTokenType
import gregc.gregchess.chess.move.MoveTraitType
import gregc.gregchess.chess.piece.PieceType
import gregc.gregchess.chess.piece.PlacedPieceType
import gregc.gregchess.chess.variant.ChessVariants

object GregChess : ChessModule("GregChess", "gregchess") {

    override fun load() {
        PieceType.registerCore(this)
        EndReason.registerCore(this)
        MoveNameTokenType.registerCore(this)
        ChessFlag.registerCore(this)
        ComponentType.registerCore(this)
        ChessVariants.registerCore(this)
        MoveTraitType.registerCore(this)
        PlacedPieceType.registerCore(this)
    }
}