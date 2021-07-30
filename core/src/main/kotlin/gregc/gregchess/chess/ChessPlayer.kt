package gregc.gregchess.chess

import gregc.gregchess.interact


abstract class ChessPlayer(val name: String, val side: Side, protected val silent: Boolean, val game: ChessGame) {

    var held: BoardPiece? = null
        set(v) {
            v?.let {
                it.square.moveMarker = Floor.NOTHING
                it.square.bakedLegalMoves?.forEach(MoveCandidate::render)
                it.pickUp()
            }
            field?.let {
                it.square.moveMarker = null
                it.square.bakedLegalMoves?.forEach(MoveCandidate::clear)
                it.placeDown()
            }
            field = v
        }

    val opponent
        get() = game[!side]

    val hasTurn
        get() = game.currentTurn == side

    val pieces
        get() = game.board.piecesOf(side)

    val king
        get() = game.board.kingOf(side)

    open fun init() {}
    open fun stop() {}
    open fun startTurn() {}

}

class HumanChessPlayer(val player: HumanPlayer, side: Side, silent: Boolean, game: ChessGame) :
    ChessPlayer(player.name, side, silent, game) {

    override fun toString() = "BukkitChessPlayer(name=$name, side=$side, game.uuid=${game.uuid})"

    fun pickUp(pos: Pos) {
        if (!game.running) return
        val piece = game.board[pos]?.piece ?: return
        if (piece.side != side) return
        held = piece
        player.setItem(0, piece.piece)
    }

    fun makeMove(pos: Pos) {
        if (!game.running) return
        val newSquare = game.board[pos] ?: return
        val piece = held ?: return
        val moves = piece.square.bakedLegalMoves ?: return
        if (newSquare != piece.square && newSquare !in moves.map { it.display }) return
        held = null
        player.setItem(0, null)
        if (newSquare == piece.square) return
        val chosenMoves = moves.filter { it.display == newSquare }
        val move = chosenMoves.first()
        interact {
            game.finishMove(move, move.promotions?.let { pawnPromotionScreen(it) })
        }
    }

    private var firstTurn = true

    override fun startTurn() {
        if (firstTurn) {
            firstTurn = false
            return
        }
        player.sendGameUpdate(side, buildList {
            if (king?.let { game.variant.isInCheck(it) } == true)
                this += GamePlayerStatus.IN_CHECK
            if (!silent)
                this += GamePlayerStatus.TURN
        })
    }

    override fun init() {
        player.sendGameUpdate(side, buildList {
            this += GamePlayerStatus.START
            if (hasTurn)
                this += GamePlayerStatus.TURN
        })
    }

    private suspend fun pawnPromotionScreen(promotions: Collection<Piece>) = player.openPawnPromotionMenu(promotions)
}

class EnginePlayer(val engine: ChessEngine, side: Side, game: ChessGame) :
    ChessPlayer(engine.name, side, true, game) {

    override fun toString() = "EnginePlayer(name=$name, side=$side)"

    override fun stop() = engine.stop()

    override fun startTurn() {
        interact {
            try {
                val str = engine.getMove(game.board.getFEN())
                val origin = Pos.parseFromString(str.take(2))
                val target = Pos.parseFromString(str.drop(2).take(2))
                val promotion = str.drop(4).firstOrNull()?.let { PieceType.chooseByChar(game.variant.pieceTypes, it) }
                val move = game.board.getMoves(origin).first { it.display.pos == target }
                game.finishMove(move, promotion?.of(side))
            } catch(e: Exception) {
                e.printStackTrace()
                game.stop(drawBy(EndReason.ERROR))
            }
        }
    }
}