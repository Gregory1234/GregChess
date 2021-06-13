package gregc.gregchess.chess.variant

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Chessboard

object HordeChess : ChessVariant("Horde") {
    override fun chessboardSetup(board: Chessboard) {
        board.piecesOf(Side.WHITE).filter { it.pos.rank == 0 }.forEach {
            it.force(false)
        }
    }

    override fun finishMove(move: MoveCandidate) {
        if (move.piece.type == PieceType.PAWN && move.target.pos.rank == 1) {
            move.piece.force(false)
        }
    }

    override fun getLegality(move: MoveCandidate): MoveLegality =
        when {
            move.piece.side == Side.BLACK -> Normal.getLegality(move)
            Normal.isValid(move) -> MoveLegality.LEGAL
            else -> MoveLegality.INVALID
        }

    override fun isInCheck(king: BoardPiece) = king.side == Side.BLACK && Normal.isInCheck(king)

    override fun checkForGameEnd(game: ChessGame) {
        if (game.board.piecesOf(Side.BLACK).all { game.board.getMoves(it.pos).none(game.variant::isLegal) }) {
            if (isInCheck(game, Side.BLACK))
                game.stop(ChessGame.EndReason.Checkmate(Side.WHITE))
            else
                game.stop(ChessGame.EndReason.Stalemate())
        }
        if (game.board.piecesOf(Side.WHITE).isEmpty())
            game.stop(ChessGame.EndReason.AllPiecesLost(Side.BLACK))
        game.board.checkForRepetition()
        game.board.checkForFiftyMoveRule()
    }

    override fun timeout(game: ChessGame, side: Side) =
        game.stop(ChessGame.EndReason.Timeout(side))

    override fun genFEN(chess960: Boolean): FEN {
        val base = Normal.genFEN(chess960)
        val replacement = "///1PP2PP1/PPPPPPPP/PPPPPPPP/PPPPPPPP/PPPPPPPP".split("/")
        val state = base.boardState.mapIndexed { i, r -> replacement[i].ifEmpty { r } }
        return base.copy(boardState = state, castlingRights = base.castlingRights.copy(black = emptyList()))
    }
}