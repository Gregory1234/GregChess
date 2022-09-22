package gregc.gregchess.bukkit.move

import gregc.gregchess.ChessFlag
import gregc.gregchess.bukkit.BukkitRegistry
import gregc.gregchess.bukkit.config
import gregc.gregchess.bukkit.piece.localChar
import gregc.gregchess.bukkitutils.getPathString
import gregc.gregchess.move.*
import gregc.gregchess.move.trait.*
import gregc.gregchess.piece.BoardPiece
import gregc.gregchess.piece.PieceType
import gregc.gregchess.variant.ChessVariant


val CheckType.localChar
    get() = config.getPathString("Chess.${name.lowercase().replaceFirstChar(Char::uppercase) }").single()

val defaultLocalMoveFormatter: MoveFormatter
    get() = simpleMoveFormatter { move ->
        val main = move.pieceTracker.getOriginalOrNull("main")

        if (main?.piece?.type != PieceType.PAWN && move.castlesTrait == null) {
            +main?.piece?.localChar?.uppercase()
            +move.targetTrait?.uniquenessCoordinate
        }
        if (move.captureTrait?.captureSuccess == true) {
            if (main?.piece?.type == PieceType.PAWN)
                +(main as? BoardPiece)?.pos?.fileStr
            +config.getPathString("Chess.Capture")
        }
        +move.targetTrait?.target
        +move.castlesTrait?.side?.castles
        +move.promotionTrait?.promotion?.localChar?.uppercase()
        +move.checkTrait?.checkType?.localChar
        if (move.requireFlagTrait?.flags?.any { ChessFlag.EN_PASSANT in it.value } == true)
            +config.getPathString("Chess.EnPassant")
    }

val ChessVariant.localMoveFormatter: MoveFormatter
    get() = BukkitRegistry.LOCAL_MOVE_FORMATTER[this]

fun MoveFormatter.formatLastMoves(num: Int, wLast: Move?, bLast: Move?) = buildString {
    append(num - 1)
    append(". ")
    wLast?.let { append(format(it)) }
    append("  | ")
    bLast?.let { append(format(it)) }
}