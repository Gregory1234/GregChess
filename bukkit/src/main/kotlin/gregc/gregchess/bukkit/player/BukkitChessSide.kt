package gregc.gregchess.bukkit.player

import gregc.gregchess.*
import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.match.*
import gregc.gregchess.bukkit.move.formatLastMoves
import gregc.gregchess.bukkit.move.localMoveFormatter
import gregc.gregchess.bukkit.piece.item
import gregc.gregchess.bukkit.results.quick
import gregc.gregchess.bukkit.results.sendMatchResults
import gregc.gregchess.bukkitutils.*
import gregc.gregchess.match.*
import gregc.gregchess.move.connector.checkExists
import gregc.gregchess.move.trait.promotionTrait
import gregc.gregchess.piece.BoardPiece
import gregc.gregchess.piece.Piece
import gregc.gregchess.player.ChessSide
import gregc.gregchess.player.ChessSideFacade
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

inline fun ByColor<ChessSideFacade<*>>.forEachReal(block: (BukkitPlayer) -> Unit) {
    toList().filterIsInstance<BukkitChessSideFacade>().map { it.player }.distinct().forEach(block)
}

fun ByColor<ChessSideFacade<*>>.isSamePlayer(): Boolean {
    val w = white
    val b = black
    return w is BukkitChessSideFacade && b is BukkitChessSideFacade && w.uuid == b.uuid
}

inline fun ByColor<ChessSideFacade<*>>.forEachUnique(block: (BukkitChessSideFacade) -> Unit) {
    val players = toList().filterIsInstance<BukkitChessSideFacade>()
    if (isSamePlayer())
        players.filter { it.hasTurn }.forEach(block)
    else
        players.forEach(block)
}

operator fun ChessMatch.get(uuid: UUID): BukkitChessSideFacade? {
    var ret: BukkitChessSideFacade? = null
    sideFacades.forEachUnique {
        if (it.uuid == uuid)
            ret = it
    }
    return ret
}

@Serializable
class BukkitChessSide(val player: BukkitPlayer, override val color: Color) : ChessSide {

    private fun isSilent(match: ChessMatch): Boolean = match.sideFacades.isSamePlayer()

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
        if (!match.sideFacades.isSamePlayer() || color == match.board.currentTurn) {
            callback()
        }
    }

    private fun onStart(match: ChessMatch) = oncePerPlayer(match) { match.callEvent(PlayerEvent(player, PlayerDirection.JOIN)) }

    private fun onStop(match: ChessMatch) = oncePerPlayer(match) {
        val results = match.results!!
        sendLastMoves(match, Color.WHITE)
        val pgn = PGN.generate(match)
        match.coroutineScope.launch {
            player.sendMatchResults(color, results)
            if (!results.endReason.quick)
                delay((if (player.quickLeave) 0 else 3).seconds)
            match.callEvent(PlayerEvent(player, PlayerDirection.LEAVE))
            player.sendMessage(pgn.copyMessage())
            player.currentChessMatch = null
        }
    }

    private fun onPanic(match: ChessMatch) = oncePerPlayer(match) {
        val results = match.results!!
        val pgn = PGN.generate(match)
        player.sendMatchResults(color, results)
        player.sendMessage(pgn.copyMessage())
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
            when(it.dir) {
                PlayerDirection.JOIN -> if (it.player == player) sendStartMessage(match)
                PlayerDirection.LEAVE -> {}
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
    }

    private suspend fun openPawnPromotionMenu(promotions: Collection<Piece>) =
        player.openMenu(PAWN_PROMOTION, promotions.mapIndexed { i, p ->
            ScreenOption(p.item, p, i.toInvPos())
        }) ?: promotions.first()

    override fun toString() = "BukkitChessSide(uuid=$uuid, name=$name, color=$color)"

    fun pickUp(match: ChessMatch, pos: Pos) {
        if (!match.running) return
        val piece = match.board[pos] ?: return
        if (piece.color != color) return
        setHeld(match, piece)
        player.entity?.inventory?.setItem(0, piece.piece.item)
    }

    fun makeMove(match: ChessMatch, pos: Pos) {
        if (!match.running) return
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
        sendLastMoves(match, Color.BLACK)
        if (color == match.board.currentTurn)
            GregChessPlugin.clearRequests(player)
    }

    private fun sendStartMessage(match: ChessMatch) {
        val facade = match[color] as BukkitChessSideFacade
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
        val lastMoveColor = if (normalMoves.size % 2 == 0) !match.board.initialFEN.currentTurn else match.board.initialFEN.currentTurn
        if (checkLastMoveColor != lastMoveColor && checkLastMoveColor != null) return
        val wLastIndex = if (lastMoveColor == Color.WHITE) normalMoves.size - 1 else normalMoves.size - 2
        val wLast = if (wLastIndex < 0) null else normalMoves[wLastIndex]
        val bLast = if (lastMoveColor == Color.BLACK) normalMoves.lastOrNull() else null
        player.sendMessage(match.variant.localMoveFormatter.formatLastMoves(match.board.fullmoveCounter + if (lastMoveColor == Color.WHITE) 1 else 0, wLast, bLast))
    }
}

class BukkitChessSideFacade(match: ChessMatch, side: BukkitChessSide) : ChessSideFacade<BukkitChessSide>(match, side) {
    val uuid: UUID get() = side.uuid
    val player: BukkitPlayer get() = side.player

    val held: BoardPiece? get() = side.held

    fun pickUp(pos: Pos) = side.pickUp(match, pos)
    fun makeMove(pos: Pos) = side.makeMove(match, pos)
}