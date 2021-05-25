package gregc.gregchess.chess

import gregc.gregchess.*
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.Player


abstract class ChessPlayer(val side: Side, private val silent: Boolean, val game: ChessGame) {

    var held: Piece? = null
        set(v) {
            v?.let {
                it.square.moveMarker = Material.YELLOW_CONCRETE
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

class BukkitChessPlayer(val player: Player, side: Side, silent: Boolean, game: ChessGame) :
    ChessPlayer(side, silent, game) {

    class PawnPromotionScreen(
        private val pawn: Piece,
        private val moves: List<Pair<PieceType, MoveCandidate>>,
        private val player: ChessPlayer
    ) : Screen<MoveCandidate>("Message.PawnPromotion") {
        override fun getContent() = moves.mapIndexed { i, (t, m) ->
            ScreenOption(t.getItem(pawn.side), m, InventoryPosition.fromIndex(i))
        }

        override fun onClick(v: MoveCandidate) = player.game.finishMove(v)

        override fun onCancel() = player.game.finishMove(moves.first().second)

    }

    var isAdmin = false
        set(value) {
            if (!value) {
                val loc = player.location
                game.renderer.resetPlayer(player)
                player.teleport(loc)
            } else {
                player.gameMode = GameMode.CREATIVE
            }
            field = value
        }

    override val name = player.name

    override fun toString() =
        "BukkitChessPlayer(name = $name, side = $side, game.uniqueId = ${game.uniqueId})"

    override fun sendMessage(msg: String) = player.sendMessage(msg)
    override fun sendTitle(title: String, subtitle: String) = player.sendDefTitle(title, subtitle)

    fun pickUp(pos: Pos) {
        if (!game.running) return
        val piece = game.board[pos]?.piece ?: return
        if (piece.side != side) return
        held = piece
        player.inventory.setItem(0, piece.item)
    }

    fun makeMove(pos: Pos) {
        if (!game.running) return
        val newSquare = game.board[pos] ?: return
        val piece = held ?: return
        val moves = piece.square.bakedLegalMoves ?: return
        if (newSquare != piece.square && newSquare !in moves.map { it.display }) return
        held = null
        player.inventory.setItem(0, null)
        if (newSquare == piece.square) return
        val chosenMoves = moves.filter { it.display == newSquare }
        if (chosenMoves.size != 1) {
            val promotingMoves = chosenMoves.mapNotNull { m -> m.promotion?.let { it to m } }
            player.openScreen(PawnPromotionScreen(piece, promotingMoves, this))
        } else {
            game.finishMove(chosenMoves.first())
        }
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
                .first { it.display.pos == target && it.promotion == promotion }
            game.finishMove(move)
        }, { game.stop(ChessGame.EndReason.Error(it)) })

    }
}