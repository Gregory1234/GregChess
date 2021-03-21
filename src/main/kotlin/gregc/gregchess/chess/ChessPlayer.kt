package gregc.gregchess.chess

import gregc.gregchess.*
import org.bukkit.Material
import org.bukkit.entity.Player
import java.lang.NullPointerException
import java.util.*


sealed class ChessPlayer(
    val side: ChessSide,
    private val silent: Boolean,
    protected val gameUniqueId: UUID
) {
    class Human(val player: Player, side: ChessSide, silent: Boolean, gameUniqueId: UUID) :
        ChessPlayer(side, silent, gameUniqueId) {

        override val name = player.name

        override fun toString() = "ChessPlayer.Human(name = $name, side = $side, game.uniqueId = $gameUniqueId)"

        override fun sendMessage(msg: String) = player.sendMessage(msg)
        override fun sendTitle(title: String, subtitle: String) =
            player.sendTitle(title, subtitle, 10, 70, 20)

        fun pickUp(loc: Loc) {
            if (!game.board.renderer.getPos(loc).isValid()) return
            val piece = game.board[loc] ?: return
            if (piece.side != side) return
            piece.square.moveMarker = Material.YELLOW_CONCRETE
            piece.square.render()
            heldMoves = getAllowedMoves(piece)
            heldMoves?.forEach { it.render() }
            held = piece
            piece.pickUp()
            player.inventory.setItem(0, piece.type.getItem(piece.side))
        }

        fun makeMove(loc: Loc) {
            val newSquare = game.board.getSquare(loc) ?: return
            val piece = held ?: return
            val moves = heldMoves ?: return
            if (newSquare != piece.square && newSquare !in moves.map { it.display }) return
            piece.square.moveMarker = null
            piece.square.render()
            moves.forEach { it.clear() }
            held = null
            player.inventory.setItem(0, null)
            if (newSquare == piece.square) {
                piece.placeDown()
                return
            }
            val chosenMoves = moves.filter { it.display == newSquare }
            if (chosenMoves.size != 1) {
                val promotingMoves =
                    chosenMoves.mapNotNull { m -> (m as? ChessMove.Promoting)?.promotion?.let { it to m } }
                player.openScreen(PawnPromotionScreen(piece, promotingMoves, this))
            } else {
                finishMove(chosenMoves.first())
            }
        }
    }

    class Engine(val engine: ChessEngine, side: ChessSide, gameUniqueId: UUID) :
        ChessPlayer(side, true, gameUniqueId) {

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
                    .first { it.display.pos == target && if (it is ChessMove.Promoting) (it.promotion == promotion) else true }
                finishMove(move)
            }, { game.stop(ChessGame.EndReason.Error(it)) })

        }
    }

    val game: ChessGame
        get() = ChessManager[gameUniqueId]!!

    var held: ChessPiece? = null
    protected var heldMoves: List<ChessMove>? = null

    abstract val name: String

    abstract fun sendMessage(msg: String)

    abstract fun sendTitle(title: String, subtitle: String = "")

    private val pieces
        get() = game.board.piecesOf(side)
    private val king
        get() = try {
            pieces.find { it.type == ChessType.KING }!!
        } catch (e: NullPointerException) {
            game.stop(ChessGame.EndReason.Error(e))
            throw e
        }

    protected fun getAllowedMoves(piece: ChessPiece): List<ChessMove> =
        game.board.getMoves(piece.pos).filter(game.board::isLegal)

    fun finishMove(move: ChessMove) {
        val data = move.execute()
        game.board.lastMove?.clear()
        game.board.lastMove = data
        game.board.lastMove?.render()
        glog.low("Finished move", data)
        game.nextTurn()
    }

    fun hasTurn(): Boolean = game.currentTurn == side

    class PawnPromotionScreen(
        private val pawn: ChessPiece,
        private val moves: List<Pair<ChessType, ChessMove>>,
        private val player: ChessPlayer
    ) : Screen<ChessMove>("Message.PawnPromotion") {
        override fun getContent() = moves.mapIndexed { i, (t, m) ->
            ScreenOption(t.getItem(pawn.side), m, InventoryPosition.fromIndex(i))
        }

        override fun onClick(v: ChessMove) = player.finishMove(v)

        override fun onCancel() = player.finishMove(moves.first().second)

    }

    open fun stop() {}

    open fun startTurn() {
        val checkingMoves = game.board.checkingMoves(!side, king.square)
        if (checkingMoves.isNotEmpty()) {
            var inMate = true
            for (p in pieces) {
                if (getAllowedMoves(p).isNotEmpty()) {
                    inMate = false
                    break
                }
            }
            if (inMate) {
                game.stop(ChessGame.EndReason.Checkmate(!side))
            } else if (!silent) {
                //player.spigot().sendMessage(ChatMessageType.ACTION_BAR, *TextComponent.fromLegacyText(chatColor("&cYou are in check!")))
                sendTitle(
                    ConfigManager.getString("Title.YourTurn"),
                    ConfigManager.getString("Title.InCheck")
                )
                sendMessage(ConfigManager.getString("Message.InCheck"))
            } else {
                sendTitle(ConfigManager.getString("Title.InCheck"))
                sendMessage(ConfigManager.getString("Message.InCheck"))
            }
        } else {
            var inStalemate = true
            for (p in pieces) {
                if (getAllowedMoves(p).isNotEmpty()) {
                    inStalemate = false
                    break
                }
            }
            if (inStalemate) {
                game.stop(ChessGame.EndReason.Stalemate())
            } else if (!silent) {
                sendTitle(ConfigManager.getString("Title.YourTurn"))
            }
        }

    }

}