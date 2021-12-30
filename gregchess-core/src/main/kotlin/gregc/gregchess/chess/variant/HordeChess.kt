package gregc.gregchess.chess.variant

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Chessboard
import gregc.gregchess.chess.move.*
import gregc.gregchess.chess.piece.BoardPiece
import gregc.gregchess.chess.piece.PieceType

object HordeChess : ChessVariant() {

    private fun pawnCanDouble(piece: BoardPiece): Boolean = when (piece.color) {
        Color.WHITE -> piece.pos.rank <= 1
        Color.BLACK -> piece.pos.rank >= 6
    }

    override fun getPieceMoves(piece: BoardPiece, board: Chessboard): List<Move> = when (piece.type) {
        PieceType.PAWN -> pawnMovement(piece){ pawnCanDouble(it) }.promotions(Normal.PROMOTIONS)
        else -> Normal.getPieceMoves(piece, board)
    }

    override fun getLegality(move: Move, game: ChessGame): MoveLegality = when(move.main.color) {
        Color.BLACK -> Normal.getLegality(move, game)
        Color.WHITE -> if (Normal.isValid(move, game)) MoveLegality.LEGAL else MoveLegality.INVALID
    }

    override fun isInCheck(king: BoardPiece, board: Chessboard) =
        king.color == Color.BLACK && Normal.isInCheck(king, board)

    override fun checkForGameEnd(game: ChessGame) = with(game.board) {
        if (piecesOf(Color.WHITE).isEmpty())
            game.stop(blackWonBy(EndReason.ALL_PIECES_LOST))

        if (piecesOf(!game.currentTurn).all { it.getMoves(this).none { m -> game.variant.isLegal(m, game) } }) {
            if (isInCheck(game, Color.BLACK))
                game.stop(whiteWonBy(EndReason.CHECKMATE))
            else
                game.stop(drawBy(EndReason.STALEMATE))
        }

        checkForRepetition()
        checkForFiftyMoveRule()
    }

    override fun timeout(game: ChessGame, color: Color) = game.stop(color.lostBy(EndReason.TIMEOUT))

    override fun genFEN(chess960: Boolean): FEN {
        val base = Normal.genFEN(chess960)
        val replacement = "///1PP2PP1/PPPPPPPP/PPPPPPPP/PPPPPPPP/PPPPPPPP".split("/")
        val state = base.boardState.split("/").mapIndexed { i, r -> replacement[i].ifEmpty { r } }.joinToString("/")
        return base.copy(boardState = state, castlingRights = base.castlingRights.copy(black = emptyList()))
    }
}