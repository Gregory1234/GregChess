package gregc.gregchess

import gregc.gregchess.chess.ChessFlag
import gregc.gregchess.chess.EndReason
import gregc.gregchess.chess.component.*
import gregc.gregchess.chess.move.*
import gregc.gregchess.chess.piece.*
import gregc.gregchess.chess.variant.*

object GregChess : ChessModule("GregChess", "gregchess") {

    private fun registerComponents() {
        registerComponent<Chessboard, ChessboardState>("chessboard")
        registerComponent<ChessClock, ChessClockData>("clock")
        registerComponent<ThreeChecks.CheckCounter, ThreeChecks.CheckCounterData>("check_counter")
    }

    private fun registerMoveTraits() {
        registerMoveTrait<DefaultHalfmoveClockTrait>("halfmove_clock")
        registerMoveTrait<CastlesTrait>("castles")
        registerMoveTrait<PromotionTrait>("promotion")
        registerMoveTrait<NameTrait>("name")
        registerMoveTrait<FlagTrait>("flag")
        registerMoveTrait<CheckTrait>("check")
        registerMoveTrait<CaptureTrait>("capture")
        registerMoveTrait<PawnOriginTrait>("pawn_move")
        registerMoveTrait<PieceOriginTrait>("piece_move")
        registerMoveTrait<TargetTrait>("target")
        registerMoveTrait<AtomicChess.ExplosionTrait>("explosion")
        registerMoveTrait<ThreeChecks.CheckCounterTrait>("check_counter")
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
        registerMoveTraits()
        registerPlacedPieceClasses()
    }
}