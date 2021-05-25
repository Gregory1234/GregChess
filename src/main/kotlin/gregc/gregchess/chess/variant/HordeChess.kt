package gregc.gregchess.chess.variant

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Chessboard

object HordeChess : ChessVariant("Horde") {
    override fun chessboardSetup(board: Chessboard) {
        board.piecesOf(ChessSide.WHITE).filter { it.pos.rank == 0 }.forEach {
            it.force(false)
        }
    }

    override fun finishMove(move: MoveCandidate) {
        if (move.piece.type == ChessType.PAWN && move.target.pos.rank == 1) {
            move.piece.force(false)
        }
    }

    override fun getLegality(move: MoveCandidate): MoveLegality =
        when {
            move.piece.side == ChessSide.BLACK -> Normal.getLegality(move)
            Normal.isValid(move) -> MoveLegality.LEGAL
            else -> MoveLegality.INVALID
        }

    override fun isInCheck(king: ChessPiece) =
        king.side == ChessSide.BLACK && Normal.isInCheck(king)

    override fun checkForGameEnd(game: ChessGame) {
        if (game.board.piecesOf(ChessSide.BLACK)
                .all { game.board.getMoves(it.pos).none(game.variant::isLegal) }
        ) {
            if (isInCheck(game, ChessSide.BLACK))
                game.stop(ChessGame.EndReason.Checkmate(ChessSide.WHITE))
            else
                game.stop(ChessGame.EndReason.Stalemate())
        }
        if (game.board.piecesOf(ChessSide.WHITE).isEmpty())
            game.stop(ChessGame.EndReason.AllPiecesLost(ChessSide.BLACK))
        game.board.checkForRepetition()
        game.board.checkForFiftyMoveRule()
    }

    override fun timeout(game: ChessGame, side: ChessSide) =
        game.stop(ChessGame.EndReason.Timeout(side))

    override fun genFEN(chess960: Boolean): FEN {
        val base = Normal.genFEN(chess960)
        val replacement = "///1PP2PP1/PPPPPPPP/PPPPPPPP/PPPPPPPP/PPPPPPPP".split("/")
        val state = base.boardState.mapIndexed { i, r -> replacement[i].ifEmpty { r }}
        return base.copy(boardState = state, castlingRightsBlack = emptyList())
    }
}