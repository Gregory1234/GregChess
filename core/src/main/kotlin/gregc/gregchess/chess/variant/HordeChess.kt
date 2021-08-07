package gregc.gregchess.chess.variant

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Chessboard

object HordeChess : ChessVariant() {

    object HordePawnConfig : PawnMovementConfig() {
        override fun canDouble(piece: PieceInfo): Boolean = when (piece.side) {
            Side.WHITE -> piece.pos.rank <= 1
            Side.BLACK -> piece.pos.rank >= 6
        }
    }

    override fun chessboardSetup(board: Chessboard) {
        for (it in board.piecesOf(white, PieceType.PAWN)) {
            it.force(false)
        }
    }

    override fun getPieceMoves(piece: BoardPiece): List<MoveCandidate> = when (piece.type) {
        PieceType.PAWN -> PawnMovement(HordePawnConfig).generate(piece)
        else -> Normal.getPieceMoves(piece)
    }

    override fun getLegality(move: MoveCandidate): MoveLegality = when {
        move.piece.side == black -> Normal.getLegality(move)
        Normal.isValid(move) -> MoveLegality.LEGAL
        else -> MoveLegality.INVALID
    }

    override fun isInCheck(king: BoardPiece) = king.side == black && Normal.isInCheck(king)

    override fun checkForGameEnd(game: ChessGame) = with(game.board) {
        if (piecesOf(black).all { getMoves(it.pos).none(game.variant::isLegal) }) {
            if (isInCheck(game, black))
                game.stop(white.wonBy(EndReason.CHECKMATE))
            else
                game.stop(drawBy(EndReason.STALEMATE))
        }
        if (piecesOf(white).isEmpty())
            game.stop(black.wonBy(EndReason.ALL_PIECES_LOST))
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