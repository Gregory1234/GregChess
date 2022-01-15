package gregc.gregchess

import gregc.gregchess.chess.ChessFlag
import gregc.gregchess.chess.EndReason
import gregc.gregchess.chess.component.*
import gregc.gregchess.chess.move.MoveNameTokenType
import gregc.gregchess.chess.move.MoveTraitType
import gregc.gregchess.chess.piece.*
import gregc.gregchess.chess.variant.ChessVariants
import gregc.gregchess.chess.variant.ThreeChecks

object GregChess : ChessModule("GregChess", "gregchess") {

    private fun registerComponents() {
        registerComponent<Chessboard, ChessboardState>("chessboard")
        registerComponent<ChessClock, ChessClockData>("clock")
        registerComponent<ThreeChecks.CheckCounter, ThreeChecks.CheckCounterData>("check_counter")
    }

    private fun registerPlacedPieceClasses() {
        registerPlacedPieceClass<BoardPiece>("board")
        registerPlacedPieceClass<CapturedPiece>("captured")
    }

    override fun load() {
        PieceType.registerCore(this)
        EndReason.registerCore(this)
        MoveNameTokenType.registerCore(this)
        ChessFlag.registerCore(this)
        registerComponents()
        ChessVariants.registerCore(this)
        MoveTraitType.registerCore(this)
        registerPlacedPieceClasses()
    }
}