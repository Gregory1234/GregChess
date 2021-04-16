package gregc.gregchess.chess

import gregc.gregchess.*
import org.bukkit.Material
import org.bukkit.entity.Player


sealed class ChessPlayer(val side: ChessSide, private val silent: Boolean, val game: ChessGame) {
    class Human(val player: Player, side: ChessSide, silent: Boolean, game: ChessGame) :
        ChessPlayer(side, silent, game) {

        override val name = player.name

        override fun toString() =
            "ChessPlayer.Human(name = $name, side = $side, game.uniqueId = ${game.uniqueId})"

        override fun sendMessage(msg: String) = player.sendMessage(msg)
        override fun sendTitle(title: String, subtitle: String) =
            player.sendTitle(title, subtitle, 10, 70, 20)

        fun pickUp(loc: Loc) {
            if (!game.board.renderer.getPos(loc).isValid()) return
            val piece = game.board[loc]?.piece ?: return
            if (piece.side != side) return
            piece.square.moveMarker = Material.YELLOW_CONCRETE
            piece.square.render()
            heldMoves = piece.square.bakedMoves.orEmpty().filter { game.variant.isLegal(it) }
            heldMoves?.forEach { it.render() }
            held = piece
            piece.pickUp()
            player.inventory.setItem(0, piece.type.getItem(piece.side))
        }

        fun makeMove(loc: Loc) {
            val newSquare = game.board[loc] ?: return
            val piece = held ?: return
            val moves = heldMoves ?: return
            if (newSquare != piece.square && newSquare !in moves.map { it.display }) return
            piece.square.moveMarker = null
            piece.square.render()
            moves.forEach { it.clear() }
            held = null
            heldMoves = null
            player.inventory.setItem(0, null)
            if (newSquare == piece.square) {
                piece.placeDown()
                return
            }
            val chosenMoves = moves.filter { it.display == newSquare }
            if (chosenMoves.size != 1) {
                val promotingMoves = chosenMoves.mapNotNull { m -> m.promotion?.let { it to m } }
                player.openScreen(PawnPromotionScreen(piece, promotingMoves, this))
            } else {
                game.variant.finishMove(chosenMoves.first())
            }
        }
    }

    class Engine(val engine: ChessEngine, side: ChessSide, game: ChessGame) :
        ChessPlayer(side, true, game) {

        override val name = engine.name

        override fun toString() = "ChessPlayer.Engine(name = $name, side = $side)"

        override fun stop() = engine.stop()

        override fun sendMessage(msg: String) {}

        override fun sendTitle(title: String, subtitle: String) {}

        override fun startTurn() {
            super.startTurn()
            engine.getMove(game.board.getFEN(), { str ->
                val origin = ChessPosition.parseFromString(str.take(2))
                val target = ChessPosition.parseFromString(str.drop(2).take(2))
                val promotion =
                    str.drop(4).firstOrNull()?.let { ChessType.parseFromStandardChar(it) }
                val move = game.board.getMoves(origin)
                    .first { it.display.pos == target && it.promotion == promotion }
                game.variant.finishMove(move)
            }, { game.stop(ChessGame.EndReason.Error(it)) })

        }
    }

    var held: ChessPiece? = null
    protected var heldMoves: List<MoveCandidate>? = null

    abstract val name: String

    abstract fun sendMessage(msg: String)

    abstract fun sendTitle(title: String, subtitle: String = "")

    val opponent
        get() = game[!side]

    fun hasTurn(): Boolean = game.currentTurn == side

    class PawnPromotionScreen(
        private val pawn: ChessPiece,
        private val moves: List<Pair<ChessType, MoveCandidate>>,
        private val player: ChessPlayer
    ) : Screen<MoveCandidate>("Message.PawnPromotion") {
        override fun getContent() = moves.mapIndexed { i, (t, m) ->
            ScreenOption(t.getItem(pawn.side), m, InventoryPosition.fromIndex(i))
        }

        override fun onClick(v: MoveCandidate) = player.game.variant.finishMove(v)

        override fun onCancel() = player.game.variant.finishMove(moves.first().second)

    }

    open fun stop() {}

    fun announceInCheck() {
        if (!silent) {
            sendTitle(
                ConfigManager.getString("Title.YourTurn"),
                ConfigManager.getString("Title.InCheck")
            )
            sendMessage(ConfigManager.getString("Message.InCheck"))
        } else {
            sendTitle(ConfigManager.getString("Title.InCheck"))
            sendMessage(ConfigManager.getString("Message.InCheck"))
        }
    }

    open fun startTurn() {
        if (!silent) {
            sendTitle(ConfigManager.getString("Title.YourTurn"))
        }
        val king = game.board.kingOf(side)
        if(king != null && game.variant.isInCheck(king))
            announceInCheck()
    }

}