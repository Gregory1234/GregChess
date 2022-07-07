package gregc.gregchess.variant

import gregc.gregchess.Color
import gregc.gregchess.board.FEN
import gregc.gregchess.match.ChessMatch
import gregc.gregchess.move.*
import gregc.gregchess.move.connector.ChessboardView
import gregc.gregchess.piece.BoardPiece
import gregc.gregchess.piece.PieceType
import gregc.gregchess.results.*

object HordeChess : ChessVariant() {

    private fun pawnCanDouble(piece: BoardPiece): Boolean = when (piece.color) {
        Color.WHITE -> piece.pos.rank <= 1
        Color.BLACK -> piece.pos.rank >= 6
    }

    override fun getPieceMoves(piece: BoardPiece, board: ChessboardView): List<Move> = when (piece.type) {
        PieceType.PAWN -> pawnMovement(piece){ pawnCanDouble(it) }.promotions(Normal.PROMOTIONS)
        else -> Normal.getPieceMoves(piece, board)
    }

    override fun getLegality(move: Move, board: ChessboardView): MoveLegality = when(move.main.color) {
        Color.BLACK -> Normal.getLegality(move, board)
        Color.WHITE -> if (Normal.isValid(move, board)) MoveLegality.LEGAL else MoveLegality.INVALID
    }

    override fun isInCheck(king: BoardPiece, board: ChessboardView) =
        king.color == Color.BLACK && Normal.isInCheck(king, board)

    override fun checkForMatchEnd(match: ChessMatch) = with(match.board) {
        if (piecesOf(Color.WHITE).isEmpty())
            match.stop(blackWonBy(EndReason.ALL_PIECES_LOST))

        if (piecesOf(!match.board.currentTurn).all { it.getMoves(this).none { m -> match.variant.isLegal(m, this) } }) {
            if (isInCheck(this, Color.BLACK))
                match.stop(whiteWonBy(EndReason.CHECKMATE))
            else
                match.stop(drawBy(EndReason.STALEMATE))
        }

        checkForRepetition(match)
        checkForFiftyMoveRule(match)
    }

    override fun timeout(match: ChessMatch, color: Color) = match.stop(color.lostBy(EndReason.TIMEOUT))

    override fun genFEN(chess960: Boolean): FEN {
        val base = Normal.genFEN(chess960)
        val replacement = "///1PP2PP1/PPPPPPPP/PPPPPPPP/PPPPPPPP/PPPPPPPP".split("/")
        val state = base.boardState.split("/").mapIndexed { i, r -> replacement[i].ifEmpty { r } }.joinToString("/")
        return base.copy(boardState = state, castlingRights = base.castlingRights.copy(black = emptyList()))
    }
}