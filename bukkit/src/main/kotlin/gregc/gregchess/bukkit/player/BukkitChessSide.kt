package gregc.gregchess.bukkit.player

import gregc.gregchess.*
import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.component.BukkitComponentType
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
class BukkitChessSide(val player: BukkitPlayer, override val color: Color) : HumanChessSide {

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

    override fun init(match: ChessMatch, events: EventListenerRegistry) {
        events.register<ChessBaseEvent>(OrderConstraint(
            runBeforeAll = true,
            runAfter = setOf(BukkitComponentType.MATCH_CONTROLLER)
        )) {
            when(it) {
                ChessBaseEvent.START -> onStart(match)
                ChessBaseEvent.SYNC -> if (match.running) onStart(match)
                else -> {}
            }
        }
        events.register<ChessBaseEvent>(OrderConstraint(
            runAfterAll = true,
            runBefore = setOf(BukkitComponentType.MATCH_CONTROLLER)
        )) {
            when(it) {
                ChessBaseEvent.STOP -> onStop(match)
                ChessBaseEvent.PANIC -> onPanic(match)
                ChessBaseEvent.CLEAR -> onClear(match)
                else -> {}
            }
        }
        events.register<TurnEvent> {
            when(it) {
                TurnEvent.START -> startTurn(match)
                TurnEvent.END -> endTurn(match)
                else -> {}
            }
        }
        events.register<PlayerEvent> {
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
        events.register<HumanRequestEvent> {
            onHumanRequest(match, it.request, it.color, it.value)
        }
    }

    companion object {
        private val IN_CHECK_MSG = message("InCheck")
        private val YOU_ARE_PLAYING_AS_MSG = byColor { message("YouArePlayingAs.${it.configName}") }

        private val IN_CHECK_TITLE = title("InCheck")
        private val YOU_ARE_PLAYING_AS_TITLE = byColor { title("YouArePlayingAs.${it.configName}") }
        private val YOUR_TURN = title("YourTurn")

        private val PAWN_PROMOTION = message("PawnPromotion")

        private val DRAW_MESSAGES = HumanRequestMessages("Draw", "/chess draw")
        private val UNDO_MESSAGES = HumanRequestMessages("Takeback", "/chess undo")

    }

    private class HumanRequestMessages(name: String, val command: String) {
        private val sentRequest = message("$name.Sent.Request")
        private val receivedRequest = message("$name.Received.Request")
        private val sentCancel = message("$name.Sent.Cancel")
        private val receivedCancel = message("$name.Received.Cancel")
        private val sentAccept = message("$name.Sent.Accept")
        private val receivedAccept = message("$name.Received.Accept")
        private val cancelButton = message("$name.Cancel")
        private val acceptButton = message("$name.Accept")
        fun request(color: Color, sender: Color) = if (color == sender) sentRequest else receivedRequest
        fun cancel(color: Color, sender: Color) = if (color == sender) sentCancel else receivedCancel
        fun accept(color: Color, sender: Color) = if (color == sender) sentAccept else receivedAccept
        fun button(color: Color, sender: Color) = if (color == sender) cancelButton else acceptButton
        fun fullRequest(color: Color, sender: Color) = textComponent(request(color, sender).get()) {
            text(" ")
            text(button(color, sender).get()) {
                onClickCommand(command)
            }
        }
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
            if (color == match.currentColor && !isSilent(match))
                this += YOUR_TURN to true
        })
        if (inCheck)
            player.sendMessage(IN_CHECK_MSG)
    }

    private fun endTurn(match: ChessMatch) = oncePerPlayer(match) {
        if (player.currentMatch != match) return
        sendLastMoves(match, Color.BLACK)
        clearRequests(match)
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

    private val requested = mutableSetOf<HumanRequest>()

    override fun isRequesting(request: HumanRequest): Boolean = request in requested

    override fun toggleRequest(match: ChessMatch, request: HumanRequest) {
        val opponent = match.sides[!color]
        check(opponent is HumanChessSideFacade)
        if (request in requested) {
            requested -= request
            if (opponent is BukkitChessSideFacade && opponent.player == player) opponent.side.requested -= request
            match.callEvent(HumanRequestEvent(request, color, false))
        } else {
            requested += request
            if (opponent is BukkitChessSideFacade && opponent.player == player) opponent.side.requested += request
            match.callEvent(HumanRequestEvent(request, color, true))
        }
    }

    override fun clearRequest(request: HumanRequest) {
        check(request in requested)
        requested -= request
    }

    private fun clearRequests(match: ChessMatch) {
        requested.clear()
        val opponent = match.sides[!color]
        if (opponent is BukkitChessSideFacade && opponent.player == player) opponent.side.requested.clear()
    }

    private fun onHumanRequest(match: ChessMatch, request: HumanRequest, changeColor: Color, value: Boolean) {
        if (color == changeColor)
            check(isRequesting(request) == value)

        if (!isSilent(match)) {
            val messages = when (request) {
                HumanRequest.DRAW -> DRAW_MESSAGES
                HumanRequest.UNDO -> UNDO_MESSAGES
            }
            if (!value) {
                player.sendMessage(messages.cancel(color, changeColor))
            } else if (isRequesting(request) && (match.sides[!color] as HumanChessSideFacade).isRequesting(request)) {
                player.sendMessage(messages.accept(color, changeColor))
            } else {
                player.sendMessage(messages.fullRequest(color, changeColor))
            }
        }
    }
}

class BukkitChessSideFacade(match: ChessMatch, side: BukkitChessSide) : HumanChessSideFacade<BukkitChessSide>(match, side) {
    val uuid: UUID get() = side.uuid
    val player: BukkitPlayer get() = side.player

    val held: BoardPiece? get() = side.held

    fun pickUp(pos: Pos) = side.pickUp(match, pos)
    fun makeMove(pos: Pos) = side.makeMove(match, pos)
}