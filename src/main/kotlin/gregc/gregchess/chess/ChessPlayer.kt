package gregc.gregchess.chess

import gregc.gregchess.ConfigManager


abstract class ChessPlayer(val side: Side, private val silent: Boolean, val game: ChessGame) {

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

    abstract val name: String

    abstract fun sendMessage(msg: String)

    abstract fun sendTitle(title: String, subtitle: String = "")

    val opponent
        get() = game[!side]

    val hasTurn
        get() = game.currentTurn == side

    val pieces
        get() = game.board.piecesOf(side)

    val king
        get() = game.board.kingOf(side)

    private fun announceInCheck() {
        if (!silent) {
            sendTitle(ConfigManager.getString("Title.YourTurn"), ConfigManager.getString("Title.InCheck"))
            sendMessage(ConfigManager.getString("Message.InCheck"))
        } else {
            sendTitle(ConfigManager.getString("Title.InCheck"))
            sendMessage(ConfigManager.getString("Message.InCheck"))
        }
    }

    open fun stop() {}

    open fun startTurn() {
        if (!silent) {
            sendTitle(ConfigManager.getString("Title.YourTurn"))
        }
        if (king?.let { game.variant.isInCheck(it) } == true)
            announceInCheck()
    }

}

class HumanChessPlayer(val player: HumanPlayer, side: Side, silent: Boolean, game: ChessGame) :
    ChessPlayer(side, silent, game) {

    override val name = player.name

    override fun toString() =
        "BukkitChessPlayer(name = $name, side = $side, game.uniqueId = ${game.uniqueId})"

    override fun sendMessage(msg: String) = player.sendMessage(msg)
    override fun sendTitle(title: String, subtitle: String) = player.sendTitle(title, subtitle)

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
        if (chosenMoves.size != 1)
            player.pawnPromotionScreen(chosenMoves.mapNotNull { m -> m.promotion?.let { it to m } })
        else
            game.finishMove(chosenMoves.first())
    }
}

class EnginePlayer(val engine: ChessEngine, side: Side, game: ChessGame) : ChessPlayer(side, true, game) {

    override val name = engine.name

    override fun toString() = "EnginePlayer(name = $name, side = $side)"

    override fun stop() = engine.stop()

    override fun sendMessage(msg: String) {}

    override fun sendTitle(title: String, subtitle: String) {}

    override fun startTurn() {
        super.startTurn()
        engine.getMove(game.board.getFEN(), { str ->
            val origin = Pos.parseFromString(str.take(2))
            val target = Pos.parseFromString(str.drop(2).take(2))
            val promotion =
                str.drop(4).firstOrNull()?.let { PieceType.parseFromStandardChar(it) }
            val move = game.board.getMoves(origin)
                .first { it.display.pos == target && it.promotion?.type == promotion }
            game.finishMove(move)
        }, { game.stop(ChessGame.EndReason.Error(it)) })

    }
}