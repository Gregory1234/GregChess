package gregc.gregchess.chess

import gregc.gregchess.Loc
import gregc.gregchess.glog
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.InventoryHolder
import java.lang.NullPointerException


sealed class ChessPlayer(val side: ChessSide, private val silent: Boolean) {
    class Human(val player: Player, side: ChessSide, silent: Boolean) :
        ChessPlayer(side, silent) {

        override val name = player.name

        override fun toString() = "ChessPlayer.Human(name = $name, side = $side)"

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
            player.inventory.setItem(0, piece.type.getItem(game.config, piece.side))
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
                player.openInventory(PawnPromotionScreen(piece, this, chosenMoves.mapNotNull {
                    val p = (it as? ChessMove.Promoting)?.promotion
                    if (p != null)
                        p to it
                    else
                        null
                }).inventory)
            } else {
                finishMove(chosenMoves.first())
            }
        }
    }

    class Engine(val engine: ChessEngine, side: ChessSide) : ChessPlayer(side, true) {

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

    private fun getString(path: String) = game.config.getString(path)

    var held: ChessPiece? = null
    protected var heldMoves: List<ChessMove>? = null

    lateinit var game: ChessGame

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
        game.board.getMoves(piece.pos).filter { game.board.run { it.isLegal } }

    fun finishMove(move: ChessMove) {
        val data = move.execute(game.config)
        game.board.lastMove?.clear()
        game.board.lastMove = data
        game.board.lastMove?.render()
        glog.low("Finished move", data)
        game.nextTurn()
    }

    fun hasTurn(): Boolean = game.currentTurn == side

    class PawnPromotionScreen(
        pawn: ChessPiece,
        private val player: ChessPlayer,
        private val moves: List<Pair<ChessType, ChessMove>>
    ) : InventoryHolder {
        var finished: Boolean = false
        private val inv =
            Bukkit.createInventory(this, 9, player.game.config.getString("Message.PawnPromotion"))

        private val typesTmp = mutableMapOf<Material, ChessType>()

        init {
            for ((p, _) in moves) {
                inv.addItem(p.getItem(player.game.config, pawn.side))
                typesTmp[p.getMaterial(player.game.config, pawn.side)] = p
            }
        }

        override fun getInventory() = inv

        fun applyEvent(choice: Material?) {
            val m = moves.find { it.first == typesTmp[choice] }?.second ?: moves.first().second
            player.finishMove(m)
            finished = true
        }
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
                sendTitle(getString("Title.YourTurn"), getString("Title.InCheck"))
                sendMessage(getString("Message.InCheck"))
            } else {
                sendTitle(getString("Title.InCheck"))
                sendMessage(getString("Message.InCheck"))
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
                sendTitle(getString("Title.YourTurn"))
            }
        }

    }

}