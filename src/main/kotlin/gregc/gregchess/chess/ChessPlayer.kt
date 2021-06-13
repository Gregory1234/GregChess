package gregc.gregchess.chess

import gregc.gregchess.*


abstract class ChessPlayer(val side: Side, protected val silent: Boolean, val game: ChessGame) {

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

    val opponent
        get() = game[!side]

    val hasTurn
        get() = game.currentTurn == side

    val pieces
        get() = game.board.piecesOf(side)

    val king
        get() = game.board.kingOf(side)

    open fun stop() {}

    abstract fun startTurn()

}

class PawnPromotionScreen(
    private val moves: List<MoveCandidate>,
    private val player: ChessPlayer
) : Screen<MoveCandidate>(MoveCandidate::class, Config.Message.pawnPromotion) {
    override fun getContent(config: Configurator) = moves.mapIndexed { i, m ->
        ScreenOption(m, InventoryPosition.fromIndex(i))
    }

    override fun onClick(v: MoveCandidate) {
        player.game.finishMove(v)
    }

    override fun onCancel() {
        player.game.finishMove(moves.first())
    }

}

class HumanChessPlayer(val player: HumanPlayer, side: Side, silent: Boolean, game: ChessGame) :
    ChessPlayer(side, silent, game) {

    override val name = player.name

    override fun toString() = "BukkitChessPlayer(name=$name, side=$side, game.uniqueId=${game.uniqueId})"

    override fun sendMessage(msg: String) = player.sendMessage(msg)
    fun sendMessage(msg: ConfigVal<String>) = player.sendMessage(msg)
    fun sendTitle(title: String, subtitle: String = "") = player.sendTitle(title, subtitle)
    fun sendTitle(title: ConfigVal<String>, subtitle: ConfigVal<String>) = player.sendTitle(title, subtitle)
    fun sendTitle(title: String, subtitle: ConfigVal<String>) = player.sendTitle(title, subtitle)
    fun sendTitle(title: ConfigVal<String>, subtitle: String = "") = player.sendTitle(title, subtitle)

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
            pawnPromotionScreen(chosenMoves.filter { it.promotion != null })
        else
            game.finishMove(chosenMoves.first())
    }

    private fun announceInCheck() {
        if (!silent) {
            sendTitle(Config.Title.yourTurn, Config.Title.inCheck)
            sendMessage(Config.Message.inCheck)
        } else {
            sendTitle(Config.Title.inCheck)
            sendMessage(Config.Message.inCheck)
        }
    }

    override fun startTurn() {
        if (!silent) {
            sendTitle(Config.Title.yourTurn)
        }
        if (king?.let { game.variant.isInCheck(it) } == true)
            announceInCheck()
    }

    fun pawnPromotionScreen(moves: List<MoveCandidate>) =
        player.openScreen(PawnPromotionScreen(moves, this))
}

class EnginePlayer(val engine: ChessEngine, side: Side, game: ChessGame) : ChessPlayer(side, true, game) {

    override val name = engine.name

    override fun toString() = "EnginePlayer(name=$name, side=$side)"

    override fun stop() = engine.stop()

    override fun sendMessage(msg: String) {}

    override fun startTurn() {
        engine.getMove(game.board.getFEN(), { str ->
            val origin = Pos.parseFromString(str.take(2))
            val target = Pos.parseFromString(str.drop(2).take(2))
            val promotion = str.drop(4).firstOrNull()?.let { PieceType.parseFromStandardChar(it) }
            val move = game.board.getMoves(origin).first { it.display.pos == target && it.promotion?.type == promotion }
            game.finishMove(move)
        }, { game.stop(ChessGame.EndReason.Error(it)) })
    }
}