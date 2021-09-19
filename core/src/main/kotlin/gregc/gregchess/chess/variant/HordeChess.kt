package gregc.gregchess.chess.variant

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Chessboard

object HordeChess : ChessVariant() {

    override fun chessboardSetup(board: Chessboard) {
        for (it in board.piecesOf(Side.WHITE, PieceType.PAWN)) {
            it.copyInPlace(board, hasMoved = false)
        }
    }

    private fun pawnCanDouble(piece: BoardPiece): Boolean = when (piece.side) {
        Side.WHITE -> piece.pos.rank <= 1
        Side.BLACK -> piece.pos.rank >= 6
    }

    override fun getPieceMoves(piece: BoardPiece, board: Chessboard): List<Move> = when (piece.type) {
        PieceType.PAWN -> PawnMovement(canDouble = { pawnCanDouble(it) }).generate(piece, board)
        else -> Normal.getPieceMoves(piece, board)
    }

    override fun getLegality(move: Move, game: ChessGame): MoveLegality = when {
        move.piece.side == black -> Normal.getLegality(move, game)
        Normal.isValid(move, game) -> MoveLegality.LEGAL
        else -> MoveLegality.INVALID
    }

    override fun isInCheck(king: BoardPiece, board: Chessboard) = king.side == black && Normal.isInCheck(king, board)

    override fun checkForGameEnd(game: ChessGame) = with(game.board) {
        if (piecesOf(Side.BLACK).all { it.getMoves(this).none { m -> game.variant.isLegal(m, game) } }) {
            if (isInCheck(game, Side.BLACK))
                game.stop(whiteWonBy(EndReason.CHECKMATE))
            else
                game.stop(drawBy(EndReason.STALEMATE))
        }
        if (piecesOf(Side.WHITE).isEmpty())
            game.stop(blackWonBy(EndReason.ALL_PIECES_LOST))
        checkForRepetition()
        checkForFiftyMoveRule()
    }

    override fun timeout(game: ChessGame, side: Side) = game.stop(side.lostBy(EndReason.TIMEOUT))

    override fun genFEN(chess960: Boolean): FEN {
        val base = Normal.genFEN(chess960)
        val replacement = "///1PP2PP1/PPPPPPPP/PPPPPPPP/PPPPPPPP/PPPPPPPP".split("/")
        val state = base.boardState.split("/").mapIndexed { i,r -> replacement[i].ifEmpty { r } }.joinToString("/")
        return base.copy(boardState = state, castlingRights = base.castlingRights.copy(black = emptyList()))
    }
}