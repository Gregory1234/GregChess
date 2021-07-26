package gregc.gregchess.chess.variant

import gregc.gregchess.asIdent
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Chessboard

object HordeChess : ChessVariant("HORDE") {

    object HordePawnConfig : PawnMovementConfig {
        override fun canDouble(piece: PieceInfo): Boolean = when (piece.side) {
            Side.WHITE -> piece.pos.rank <= 1
            Side.BLACK -> piece.pos.rank >= 6
        }
    }

    override fun chessboardSetup(board: Chessboard) {
        board.piecesOf(Side.WHITE, PieceType.PAWN).forEach {
            it.force(false)
        }
    }

    override fun getPieceMoves(piece: BoardPiece): List<MoveCandidate> = when(piece.type) {
        PieceType.PAWN -> pawnMovement(HordePawnConfig)(piece)
        else -> Normal.getPieceMoves(piece)
    }

    override fun getLegality(move: MoveCandidate): MoveLegality = when {
        move.piece.side == Side.BLACK -> Normal.getLegality(move)
        Normal.isValid(move) -> MoveLegality.LEGAL
        else -> MoveLegality.INVALID
    }

    override fun isInCheck(king: BoardPiece) = king.side == Side.BLACK && Normal.isInCheck(king)

    override fun checkForGameEnd(game: ChessGame) = with(game.board) {
        if (piecesOf(Side.BLACK).all { getMoves(it.pos).none(game.variant::isLegal) }) {
            if (isInCheck(game, Side.BLACK))
                game.stop(Side.WHITE.wonBy(EndReason.CHECKMATE))
            else
                game.stop(drawBy(EndReason.STALEMATE))
        }
        if (piecesOf(Side.WHITE).isEmpty())
            game.stop(Side.BLACK.wonBy(EndReason.ALL_PIECES_LOST))
        checkForRepetition()
        checkForFiftyMoveRule()
    }

    override fun timeout(game: ChessGame, side: Side) = game.stop(side.lostBy(EndReason.TIMEOUT))

    override fun genFEN(chess960: Boolean): FEN {
        val base = Normal.genFEN(chess960)
        val replacement = "///1PP2PP1/PPPPPPPP/PPPPPPPP/PPPPPPPP/PPPPPPPP".split("/")
        val state = base.boardState.mapIndexed { i, r -> replacement[i].ifEmpty { r } }
        return base.copy(boardState = state, castlingRights = base.castlingRights.copy(black = emptyList()))
    }
}