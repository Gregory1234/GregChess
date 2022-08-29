package gregc.gregchess.bukkit.player

import gregc.gregchess.*
import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.match.BukkitChessEventType
import gregc.gregchess.bukkit.piece.item
import gregc.gregchess.bukkitutils.Message
import gregc.gregchess.match.*
import gregc.gregchess.move.connector.checkExists
import gregc.gregchess.move.trait.promotionTrait
import gregc.gregchess.piece.BoardPiece
import gregc.gregchess.player.ChessSide
import gregc.gregchess.player.ChessSideFacade
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.*

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

    override fun init(match: ChessMatch, eventManager: ChessEventManager) {
        eventManager.registerEventE(TurnEvent.START) {
            startTurn(match)
        }
    }

    companion object {
        private val IN_CHECK_MSG = message("InCheck")
        private val YOU_ARE_PLAYING_AS_MSG = byColor { message("YouArePlayingAs.${it.configName}") }

        private val IN_CHECK_TITLE = title("InCheck")
        private val YOU_ARE_PLAYING_AS_TITLE = byColor { title("YouArePlayingAs.${it.configName}") }
        private val YOUR_TURN = title("YourTurn")
    }

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
                promotion = player.openPawnPromotionMenu(promotions)
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

    fun sendStartMessage(match: ChessMatch) {
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
}

class BukkitChessSideFacade(match: ChessMatch, side: BukkitChessSide) : ChessSideFacade<BukkitChessSide>(match, side) {
    val uuid: UUID get() = side.uuid
    val player: BukkitPlayer get() = side.player

    val held: BoardPiece? get() = side.held

    fun pickUp(pos: Pos) = side.pickUp(match, pos)
    fun makeMove(pos: Pos) = side.makeMove(match, pos)
    fun sendStartMessage() = side.sendStartMessage(match)
}