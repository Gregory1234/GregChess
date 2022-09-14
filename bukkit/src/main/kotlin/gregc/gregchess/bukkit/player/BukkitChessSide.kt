package gregc.gregchess.bukkit.player

import gregc.gregchess.*
import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.component.BukkitComponentType
import gregc.gregchess.bukkit.event.BukkitChessEventType
import gregc.gregchess.bukkit.event.PlayerDirection
import gregc.gregchess.bukkit.match.copyMessage
import gregc.gregchess.bukkit.move.formatLastMoves
import gregc.gregchess.bukkit.move.localMoveFormatter
import gregc.gregchess.bukkit.piece.item
import gregc.gregchess.bukkit.results.quick
import gregc.gregchess.bukkit.results.sendMatchResults
import gregc.gregchess.bukkitutils.*
import gregc.gregchess.event.*
import gregc.gregchess.match.ChessMatch
import gregc.gregchess.match.PGN
import gregc.gregchess.move.connector.checkExists
import gregc.gregchess.move.trait.promotionTrait
import gregc.gregchess.piece.BoardPiece
import gregc.gregchess.piece.Piece
import gregc.gregchess.player.*
import gregc.gregchess.results.EndReason
import gregc.gregchess.results.drawBy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.*
import kotlin.time.Duration.Companion.seconds

class PiecePlayerActionEvent(val piece: BoardPiece, val action: Type) : ChessEvent {
    enum class Type {
        PICK_UP, PLACE_DOWN
    }
    override val type get() = BukkitChessEventType.PIECE_PLAYER_ACTION
}

inline fun ChessSideManagerFacade.forEachReal(block: (BukkitPlayer) -> Unit) {
    toList().filterIsInstance<BukkitChessSideFacade>().map { it.player }.distinct().forEach(block)
}

fun ChessSideManagerFacade.isSamePlayer(): Boolean {
    val w = white
    val b = black
    return w is BukkitChessSideFacade && b is BukkitChessSideFacade && w.uuid == b.uuid
}

inline fun ChessSideManagerFacade.forEachUnique(block: (BukkitChessSideFacade) -> Unit) {
    val players = toList().filterIsInstance<BukkitChessSideFacade>()
    if (isSamePlayer())
        players.filter { it.hasTurn }.forEach(block)
    else
        players.forEach(block)
}

operator fun ChessSideManagerFacade.get(uuid: UUID): BukkitChessSideFacade? {
    var ret: BukkitChessSideFacade? = null
    forEachUnique {
        if (it.uuid == uuid)
            ret = it
    }
    return ret
}

@Serializable
class BukkitChessSide(val player: BukkitPlayer, override val color: Color) : ChessSide {

    private fun isSilent(match: ChessMatch): Boolean = match.sides.isSamePlayer()

    @Transient
    private var _held: BoardPiece? = null

    val held: BoardPiece? get() = _held

    private fun setHeld(match: ChessMatch, newHeld: BoardPiece?) {
        val oldHeld = _held
        _held = newHeld
        oldHeld?.let {
            match.board.checkExists(it)
            match.callEvent(PiecePlayerActionEvent(it, PiecePlayerActionEvent.Type.PLACE_DOWN))
        }
        newHeld?.let {
            match.board.checkExists(it)
            match.callEvent(PiecePlayerActionEvent(it, PiecePlayerActionEvent.Type.PICK_UP))
        }
    }

    val uuid: UUID get() = player.uuid
    override val name: String get() = player.name

    override val type get() = BukkitChessSideType.BUKKIT

    override fun createFacade(match: ChessMatch) = BukkitChessSideFacade(match, this)

    private inline fun oncePerPlayer(match: ChessMatch, callback: () -> Unit) {
        if (!match.sides.isSamePlayer() || color == match.currentColor) {
            callback()
        }
    }

    private fun onStart(match: ChessMatch) = oncePerPlayer(match) {
        player.registerMatch(match)
        player.joinMatch(match)
    }

    private fun onStop(match: ChessMatch) = oncePerPlayer(match) {
        if (player.currentMatch != match) {
            player.unregisterMatch(match)
            return
        }
        val results = match.results!!
        sendLastMoves(match, Color.WHITE)
        val pgn = PGN.generate(match)
        match.coroutineScope.launch {
            player.sendMatchResults(color, results)
            if (!results.endReason.quick)
                delay((if (player.quickLeave) 0 else 3).seconds)
            player.leaveMatchDirect(match, canRequestRematch = true)
            player.sendMessage(pgn.copyMessage())
        }
    }

    private fun onPanic(match: ChessMatch) = oncePerPlayer(match) {
        if (player.currentMatch == match) {
            val results = match.results!!
            val pgn = PGN.generate(match)
            player.sendMatchResults(color, results)
            player.leaveMatchDirect(match, canRequestRematch = true)
            player.sendMessage(pgn.copyMessage())
        }
        if (match in player.activeMatches)
            player.unregisterMatch(match)
    }

    private fun onClear(match: ChessMatch) = oncePerPlayer(match) {
        if (match in player.activeMatches)
            player.unregisterMatch(match)
    }

    override fun init(match: ChessMatch, events: ChessEventRegistry) {
        events.register(ChessEventType.BASE, ChessEventOrderConstraint(
            runBeforeAll = true,
            runAfter = setOf(ChessEventComponentOwner(BukkitComponentType.MATCH_CONTROLLER))
        )) {
            when(it) {
                ChessBaseEvent.START -> onStart(match)
                ChessBaseEvent.SYNC -> if (match.running) onStart(match)
                else -> {}
            }
        }
        events.register(ChessEventType.BASE, ChessEventOrderConstraint(
            runAfterAll = true,
            runBefore = setOf(ChessEventComponentOwner(BukkitComponentType.MATCH_CONTROLLER))
        )) {
            when(it) {
                ChessBaseEvent.STOP -> onStop(match)
                ChessBaseEvent.PANIC -> onPanic(match)
                ChessBaseEvent.CLEAR -> onClear(match)
                else -> {}
            }
        }
        events.register(ChessEventType.TURN) {
            when(it) {
                TurnEvent.START -> startTurn(match)
                TurnEvent.END -> endTurn(match)
                else -> {}
            }
        }
        events.register(BukkitChessEventType.PLAYER) {
            if (it.player == player) {
                when (it.dir) {
                    PlayerDirection.JOIN -> {
                        require(player.currentMatch == match)
                        sendStartMessage(match)
                    }
                    PlayerDirection.LEAVE -> {
                        require(player.currentMatch == null)
                        clearRequests(match)
                    }
                }
            }
        }
    }

    companion object {
        private val IN_CHECK_MSG = message("InCheck")
        private val YOU_ARE_PLAYING_AS_MSG = byColor { message("YouArePlayingAs.${it.configName}") }

        private val IN_CHECK_TITLE = title("InCheck")
        private val YOU_ARE_PLAYING_AS_TITLE = byColor { title("YouArePlayingAs.${it.configName}") }
        private val YOUR_TURN = title("YourTurn")

        private val PAWN_PROMOTION = message("PawnPromotion")

        private val DRAW_SENT = message("Draw.Sent.Request")
        private val DRAW_RECEIVED = message("Draw.Received.Request")
        private val DRAW_SENT_CANCEL = message("Draw.Sent.Cancel")
        private val DRAW_RECEIVED_CANCEL = message("Draw.Received.Cancel")
        private val DRAW_SENT_ACCEPT = message("Draw.Sent.Accept")
        private val DRAW_RECEIVED_ACCEPT = message("Draw.Received.Accept")
        private val DRAW_ACCEPT = message("Draw.Accept")
        private val DRAW_CANCEL = message("Draw.Cancel")

        private val UNDO_SENT = message("Takeback.Sent.Request")
        private val UNDO_RECEIVED = message("Takeback.Received.Request")
        private val UNDO_SENT_CANCEL = message("Takeback.Sent.Cancel")
        private val UNDO_RECEIVED_CANCEL = message("Takeback.Received.Cancel")
        private val UNDO_SENT_ACCEPT = message("Takeback.Sent.Accept")
        private val UNDO_RECEIVED_ACCEPT = message("Takeback.Received.Accept")
        private val UNDO_ACCEPT = message("Takeback.Accept")
        private val UNDO_CANCEL = message("Takeback.Cancel")

    }

    private suspend fun openPawnPromotionMenu(promotions: Collection<Piece>) =
        player.openMenu(PAWN_PROMOTION, promotions.mapIndexed { i, p ->
            ScreenOption(p.item, p, i.toInvPos())
        }) ?: promotions.first()

    override fun toString() = "BukkitChessSide(uuid=$uuid, name=$name, color=$color)"

    fun pickUp(match: ChessMatch, pos: Pos) {
        if (!match.running) return
        if (player.currentMatch != match) return
        val piece = match.board[pos] ?: return
        if (piece.color != color) return
        setHeld(match, piece)
        player.entity?.inventory?.setItem(0, piece.piece.item)
    }

    fun makeMove(match: ChessMatch, pos: Pos) {
        if (!match.running) return
        if (player.currentMatch != match) return
        val piece = held ?: return
        val moves = piece.getLegalMoves(match.board)
        if (pos != piece.pos && pos !in moves.map { it.display }) return
        setHeld(match, null)
        player.entity?.inventory?.setItem(0, null)
        if (pos == piece.pos) return
        val chosenMoves = moves.filter { it.display == pos }
        val move = chosenMoves.first()
        match.coroutineScope.launch {
            move.promotionTrait?.apply {
                promotion = openPawnPromotionMenu(promotions)
            }
            match.finishMove(move)
        }
    }

    @Transient
    private var firstTurn = true

    private fun sendTitleList(titles: List<Pair<Message, Boolean>>) {
        val title = titles.firstOrNull { it.second }
        val subtitle = titles.firstOrNull { it != title }
        player.sendTitle(title?.first?.get() ?: "", subtitle?.first?.get() ?: "")
    }

    private fun startTurn(match: ChessMatch) {
        if (firstTurn) {
            firstTurn = false
            return
        }
        if (player.currentMatch != match) return
        val inCheck = match.variant.isInCheck(match.board, color)
        sendTitleList(buildList {
            if (inCheck)
                this += IN_CHECK_TITLE to true
            if (!isSilent(match))
                this += YOUR_TURN to true
        })
        if (inCheck)
            player.sendMessage(IN_CHECK_MSG)
    }

    private fun endTurn(match: ChessMatch) = oncePerPlayer(match) {
        if (player.currentMatch != match) return
        sendLastMoves(match, Color.BLACK)
        if (color == match.currentColor) clearRequests(match)
    }

    private fun sendStartMessage(match: ChessMatch) {
        val facade = match.sides[color] as BukkitChessSideFacade
        if (facade.hasTurn || !isSilent(match)) {
            sendTitleList(buildList {
                this += YOU_ARE_PLAYING_AS_TITLE[color] to false
                if (facade.hasTurn)
                    this += YOUR_TURN to true
            })
            player.sendMessage(YOU_ARE_PLAYING_AS_MSG[color])
        }
    }

    private fun sendLastMoves(match: ChessMatch, checkLastMoveColor: Color? = null) {
        val normalMoves = match.board.moveHistory.filter { !it.isPhantomMove }
        val lastMoveColor = if (normalMoves.size % 2 == 0) !match.board.initialFEN.currentColor else match.board.initialFEN.currentColor
        if (checkLastMoveColor != lastMoveColor && checkLastMoveColor != null) return
        val wLastIndex = if (lastMoveColor == Color.WHITE) normalMoves.size - 1 else normalMoves.size - 2
        val wLast = if (wLastIndex < 0) null else normalMoves[wLastIndex]
        val bLast = if (lastMoveColor == Color.BLACK) normalMoves.lastOrNull() else null
        player.sendMessage(match.variant.localMoveFormatter.formatLastMoves(match.board.fullmoveCounter + if (lastMoveColor == Color.WHITE) 1 else 0, wLast, bLast))
    }

    var requestsDraw: Boolean = false
        private set
    var requestsUndo: Boolean = false
        private set

    private fun setRequestsDraw(match: ChessMatch, value: Boolean) {
        requestsDraw = value
        val opponent = match.sides[!color] as? BukkitChessSideFacade
        if (opponent?.player == player) {
            opponent.side.requestsDraw = value
        }
    }

    private fun setRequestsUndo(match: ChessMatch, value: Boolean) {
        requestsUndo = value
        val opponent = match.sides[!color] as? BukkitChessSideFacade
        if (opponent?.player == player) {
            opponent.side.requestsUndo = value
        }
    }

    private fun clearRequests(match: ChessMatch) {
        setRequestsDraw(match, false)
        setRequestsUndo(match, false)
    }

    private fun sendMessagePair(match: ChessMatch, msgSelf: Message, msgOpponent: Message) {
        val opponent = match.sides[!color] as BukkitChessSideFacade
        if (opponent.player != player) {
            player.sendMessage(msgSelf)
            opponent.player.sendMessage(msgOpponent)
        }
    }

    private fun sendMessagePairWithCommand(match: ChessMatch, msgSelf: Message, buttonSelf: Message, msgOpponent: Message, buttonOpponent: Message, command: String) {
        val opponent = match.sides[!color] as BukkitChessSideFacade
        if (opponent.player != player) {
            player.sendMessage(textComponent(msgSelf.get()) { text(" "); text(buttonSelf.get()) { onClickCommand(command) } })
            opponent.player.sendMessage(textComponent(msgOpponent.get()) { text(" "); text(buttonOpponent.get()) { onClickCommand(command) } })
        }
    }

    fun requestDraw(match: ChessMatch) {
        setRequestsDraw(match, !requestsDraw)
        if (!requestsDraw) {
            sendMessagePair(match, DRAW_SENT_CANCEL, DRAW_RECEIVED_CANCEL)
        } else if ((match.sides[!color] as BukkitChessSideFacade).requestsDraw) {
            sendMessagePair(match, DRAW_SENT_ACCEPT, DRAW_RECEIVED_ACCEPT)
            match.stop(drawBy(EndReason.DRAW_AGREEMENT))
        } else {
            sendMessagePairWithCommand(match, DRAW_SENT, DRAW_CANCEL, DRAW_RECEIVED, DRAW_ACCEPT, "/chess draw")
        }
    }

    fun requestUndo(match: ChessMatch) {
        setRequestsUndo(match, !requestsUndo)
        if (!requestsUndo) {
            sendMessagePair(match, UNDO_SENT_CANCEL, UNDO_RECEIVED_CANCEL)
        } else if ((match.sides[!color] as BukkitChessSideFacade).requestsUndo) {
            sendMessagePair(match, UNDO_SENT_ACCEPT, UNDO_RECEIVED_ACCEPT)
            match.board.undoLastMove()
            setRequestsUndo(match, false)
            (match.sides[!color] as BukkitChessSideFacade).side.setRequestsUndo(match, false)
        } else {
            sendMessagePairWithCommand(match, UNDO_SENT, UNDO_CANCEL, UNDO_RECEIVED, UNDO_ACCEPT, "/chess undo")
        }
    }
}

class BukkitChessSideFacade(match: ChessMatch, side: BukkitChessSide) : ChessSideFacade<BukkitChessSide>(match, side) {
    val uuid: UUID get() = side.uuid
    val player: BukkitPlayer get() = side.player

    val held: BoardPiece? get() = side.held

    fun pickUp(pos: Pos) = side.pickUp(match, pos)
    fun makeMove(pos: Pos) = side.makeMove(match, pos)

    val requestsDraw get() = side.requestsDraw
    val requestsUndo get() = side.requestsUndo

    fun requestDraw() = side.requestDraw(match)
    fun requestUndo() = side.requestUndo(match)
}