package gregc.gregchess.chess.move

import gregc.gregchess.chess.Color
import gregc.gregchess.chess.Pos
import gregc.gregchess.chess.piece.BoardPiece

fun phantomSpawn(piece: BoardPiece): Move = Move(
    PieceTracker(), piece.pos, emptySet(), setOf(piece.pos), setOf(piece.pos), setOf(piece.pos), true,
    listOf(CaptureTrait(piece.pos, false, piece.color), SpawnTrait(piece))
)

fun phantomMove(piece: BoardPiece, target: Pos): Move = Move(
    PieceTracker(piece), target, setOf(piece.pos), setOf(target), setOf(target), setOf(piece.pos, target), true,
    listOf(CaptureTrait(target), TargetTrait(target))
)

fun phantomCapture(piece: BoardPiece, by: Color): Move = Move(
    PieceTracker(piece), piece.pos, setOf(piece.pos), setOf(), setOf(), setOf(), true,
    listOf(CaptureTrait(piece.pos, true, by))
)

fun phantomClear(piece: BoardPiece): Move = Move(
    PieceTracker(piece), piece.pos, setOf(piece.pos), setOf(), setOf(), setOf(), true,
    listOf(ClearTrait(piece))
)