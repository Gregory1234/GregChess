package gregc.gregchess.bukkit.player

import gregc.gregchess.*
import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.piece.item
import gregc.gregchess.bukkitutils.*
import gregc.gregchess.game.ChessEvent
import gregc.gregchess.game.ChessGame
import gregc.gregchess.move.trait.promotionTrait
import gregc.gregchess.piece.BoardPiece
import gregc.gregchess.player.ChessSide
import kotlinx.coroutines.launch
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import java.util.*

class PiecePlayerActionEvent(val piece: BoardPiece, val type: Type) : ChessEvent {
    enum class Type {
        PICK_UP, PLACE_DOWN
    }
}

inline fun ByColor<ChessSide<*>>.forEachRealOffline(block: (OfflinePlayer) -> Unit) {
    toList().filterIsInstance<BukkitChessSide>().map { it.bukkitOffline }.distinct().forEach(block)
}

inline fun ByColor<ChessSide<*>>.forEachReal(block: (Player) -> Unit) = forEachRealOffline { it.player?.let(block) }

inline fun ByColor<ChessSide<*>>.forEachUnique(block: (BukkitChessSide) -> Unit) {
    val players = toList().filterIsInstance<BukkitChessSide>()
    if (players.size == 2 && players.all { it.player == players[0].player })
        players.filter { it.hasTurn }.forEach(block)
    else
        players.forEach(block)
}

inline fun ByColor<ChessSide<*>>.forEachUniqueBukkit(block: (Player, Color) -> Unit) = forEachUnique {
    it.bukkit?.let { player ->
        block(player, it.color)
    }
}

class BukkitChessSide(val uuid: UUID, color: Color, game: ChessGame) : ChessSide<UUID>(BukkitPlayerType.BUKKIT, uuid, color, game) {

    val bukkitOffline: OfflinePlayer get() = Bukkit.getOfflinePlayer(uuid)

    val bukkit: Player? get() = Bukkit.getPlayer(uuid)

    private val silent get() = this.player == opponent.player

    var held: BoardPiece? = null
        private set(v) {
            val oldHeld = field
            field = v
            oldHeld?.let {
                game.board.checkExists(it)
                game.callEvent(PiecePlayerActionEvent(it, PiecePlayerActionEvent.Type.PLACE_DOWN))
            }
            v?.let {
                game.board.checkExists(it)
                game.callEvent(PiecePlayerActionEvent(it, PiecePlayerActionEvent.Type.PICK_UP))
            }
        }

    companion object {
        private val IN_CHECK_MSG = message("InCheck")
        private val YOU_ARE_PLAYING_AS_MSG = byColor { message("YouArePlayingAs.${it.configName}") }

        private val IN_CHECK_TITLE = title("InCheck")
        private val YOU_ARE_PLAYING_AS_TITLE = byColor { title("YouArePlayingAs.${it.configName}") }
        private val YOUR_TURN = title("YourTurn")
    }

    override fun toString() = "BukkitChessSide(name=$name)"

    fun pickUp(pos: Pos) {
        if (!game.running) return
        val piece = game.board[pos] ?: return
        if (piece.color != color) return
        held = piece
        bukkit?.inventory?.setItem(0, piece.piece.item)
    }

    fun makeMove(pos: Pos) {
        if (!game.running) return
        val piece = held ?: return
        val moves = piece.getLegalMoves(game.board)
        if (pos != piece.pos && pos !in moves.map { it.display }) return
        held = null
        bukkit?.inventory?.setItem(0, null)
        if (pos == piece.pos) return
        val chosenMoves = moves.filter { it.display == pos }
        val move = chosenMoves.first()
        game.coroutineScope.launch {
            move.promotionTrait?.apply {
                promotion = bukkit?.openPawnPromotionMenu(promotions) ?: promotions.first()
            }
            game.finishMove(move)
        }
    }

    private var firstTurn = true

    private fun sendTitleList(titles: List<Pair<Message, Boolean>>) {
        val title = titles.firstOrNull { it.second }
        val subtitle = titles.firstOrNull { it != title }
        bukkit?.sendTitleFull(title?.first?.get() ?: "", subtitle?.first?.get() ?: "")
    }

    override fun startTurn() {
        if (firstTurn) {
            firstTurn = false
            return
        }
        val inCheck = game.variant.isInCheck(game.board, color)
        sendTitleList(buildList {
            if (inCheck)
                this += IN_CHECK_TITLE to true
            if (!silent)
                this += YOUR_TURN to true
        })
        if (inCheck)
            bukkit?.sendMessage(IN_CHECK_MSG)
    }

    override fun start() {
        if (hasTurn || !silent) {
            sendTitleList(buildList {
                this += YOU_ARE_PLAYING_AS_TITLE[color] to false
                if (hasTurn)
                    this += YOUR_TURN to true
            })
            bukkit?.sendMessage(YOU_ARE_PLAYING_AS_MSG[color])
        }
    }
}