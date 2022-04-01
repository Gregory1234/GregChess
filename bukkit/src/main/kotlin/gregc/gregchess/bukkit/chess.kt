package gregc.gregchess.bukkit

import gregc.gregchess.*
import gregc.gregchess.bukkit.piece.localChar
import gregc.gregchess.bukkitutils.getPathString
import gregc.gregchess.move.trait.*
import gregc.gregchess.piece.BoardPiece
import gregc.gregchess.piece.PieceType
import gregc.gregchess.variant.ChessVariant

val ChessModule.plugin get() = BukkitRegistry.BUKKIT_PLUGIN[this].get()
val ChessModule.config get() = plugin.config

val Color.configName get() = name.snakeToPascal()

val CheckType.localChar
    get() = config.getPathString("Chess.${name.lowercase().replaceFirstChar(Char::uppercase) }").single()

val defaultLocalMoveFormatter: MoveFormatter
    get() = MoveFormatter { move ->
        buildString { // TODO: remove repetition
            operator fun Any?.unaryPlus() { if (this != null) append(this) }

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
    }

val ChessVariant.localMoveFormatter: MoveFormatter
    get() = BukkitRegistry.LOCAL_MOVE_FORMATTER[this]