package gregc.gregchess.chess.variant

import gregc.gregchess.GregChessModule
import gregc.gregchess.chess.*
import gregc.gregchess.register

object Antichess : ChessVariant() {
    @JvmField
    val STALEMATE_VICTORY = GregChessModule.register("stalemate_victory", DetEndReason(EndReason.Type.NORMAL))

    object AntichessPawnConfig : PawnMovementConfig() {
        override fun promotions(piece: PieceInfo): List<Piece> =
            listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT, PieceType.KING)
                .map { it.of(piece.side) }
    }

    override fun getPieceMoves(piece: BoardPiece): List<Move> = when (piece.type) {
        PieceType.PAWN -> PawnMovement(AntichessPawnConfig).generate(piece)
        else -> Normal.getPieceMoves(piece)
    }

    override fun getLegality(move: Move, game: ChessGame): MoveLegality {
        if (!Normal.isValid(move, game))
            return MoveLegality.INVALID
        // TODO: block castling
        if (move.getTrait<CaptureTrait>()?.capture?.let { game.board[it]?.piece } != null)
            return MoveLegality.LEGAL
        return if (game.board.piecesOf(move.piece.side).none { m ->
                m.square.bakedMoves.orEmpty().filter { Normal.isValid(it, game) }
                    .any { mv -> mv.getTrait<CaptureTrait>()?.capture?.let { game.board[it]?.piece } != null }
            }) MoveLegality.LEGAL else MoveLegality.SPECIAL
    }

    override fun isInCheck(king: BoardPiece) = false

    override fun isInCheck(game: ChessGame, side: Side) = false

    override fun checkForGameEnd(game: ChessGame) = with(game.board) {
        if (piecesOf(!game.currentTurn).isEmpty())
            game.stop(game.currentTurn.lostBy(EndReason.ALL_PIECES_LOST))
        if (piecesOf(!game.currentTurn).all { getMoves(it.pos).none { m -> game.variant.isLegal(m, game) } })
            game.stop(game.currentTurn.lostBy(STALEMATE_VICTORY))
        checkForRepetition()
        checkForFiftyMoveRule()
    }

    override fun timeout(game: ChessGame, side: Side) = game.stop(side.wonBy(EndReason.TIMEOUT))
}