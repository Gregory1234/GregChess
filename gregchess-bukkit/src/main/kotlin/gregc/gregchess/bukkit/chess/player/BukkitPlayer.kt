package gregc.gregchess.bukkit.chess.player

import gregc.gregchess.bukkit.chess.*
import gregc.gregchess.bukkit.chess.component.spectators
import gregc.gregchess.bukkit.message
import gregc.gregchess.bukkit.title
import gregc.gregchess.bukkitutils.*
import gregc.gregchess.chess.*
import gregc.gregchess.chess.move.*
import gregc.gregchess.chess.piece.BoardPiece
import gregc.gregchess.chess.piece.Piece
import gregc.gregchess.chess.player.ChessPlayer
import kotlinx.coroutines.launch
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

private class ExtraPlayerInfo(val player: Player) {
    companion object {
        private val infos = mutableMapOf<Player, ExtraPlayerInfo>()
        fun of(p: Player) = infos.getOrPut(p) { ExtraPlayerInfo(p) }
    }

    var currentGame: ChessGame? = null
    val games = mutableListOf<ChessGame>()

    var isAdmin = false
        set(value) {
            field = value
            val loc = player.location
            currentGame?.callEvent(ResetPlayerEvent(player))
            player.teleport(loc)
        }

    var spectatedGame: ChessGame? = null
        set(v) {
            field?.let { it.spectators -= player }
            field = v
            field?.let { it.spectators += player }
        }
}

private val Player.extra get() = ExtraPlayerInfo.of(this)
val Player.chess: BukkitPlayer?
    get() {
        val players = currentGame?.players?.toList().orEmpty()
            .filterIsInstance<BukkitPlayer>().filter { it.player == this }
        return if (players.size == 2)
            players.firstOrNull { it.hasTurn }
        else
            players.singleOrNull()
    }
var Player.currentGame
    get() = extra.currentGame
    set(v) {
        extra.currentGame = v
    }
val Player.games get() = extra.games
val Player.isInGame get() = currentGame != null
var Player.isAdmin
    get() = extra.isAdmin
    set(v) {
        extra.isAdmin = v
    }
var Player.spectatedGame
    get() = extra.spectatedGame
    set(v) {
        extra.spectatedGame = v
    }
val Player.isSpectating get() = spectatedGame != null

fun CommandSender.sendMessage(msg: Message) = sendMessage(msg.get())

fun Player.sendTitleFull(title: String?, subtitle: String?) = sendTitle(title, subtitle, 10, 70, 20)

private val SPECTATOR_WINNER = byColor { title("Spectator.${it.configName}Won") }
private val SPECTATOR_DRAW = title("Spectator.ItWasADraw")

fun Player.showGameResults(results: GameResults) {
    sendTitleFull(
        results.score.let { if (it is GameScore.Victory) SPECTATOR_WINNER[it.winner] else SPECTATOR_DRAW }.get(),
        results.name
    )
    sendMessage(results.message)
}

private val YOU_WON = title("Player.YouWon")
private val YOU_LOST = title("Player.YouLost")
private val YOU_DREW = title("Player.YouDrew")

fun Player.showGameResults(color: Color, results: GameResults) {
    val wld = when (results.score) {
        GameScore.Victory(color) -> YOU_WON
        GameScore.Draw -> YOU_DREW
        else -> YOU_LOST
    }
    sendTitleFull(wld.get(), results.name)
    sendMessage(results.message)
}

private val COPY_FEN = message("CopyFEN")

fun Player.sendFEN(fen: FEN) {
    spigot().sendMessage(textComponent(COPY_FEN.get()) {
        onClickCopy(fen)
    })
}

private val COPY_PGN = message("CopyPGN")

fun Player.sendPGN(pgn: PGN) {
    spigot().sendMessage(textComponent(COPY_PGN.get()) {
        onClickCopy(pgn)
    })
}

fun Player.sendLastMoves(num: UInt, wLast: Move?, bLast: Move?, formatter: MoveNameFormatter) {
    sendMessage(buildString {
        append(num - 1u)
        append(". ")
        wLast?.let { append(it.name.format(formatter)) }
        append("  | ")
        bLast?.let { append(it.name.format(formatter)) }
    })
}

private val PAWN_PROMOTION = message("PawnPromotion")

suspend fun Player.openPawnPromotionMenu(promotions: Collection<Piece>) =
    openMenu(PAWN_PROMOTION, promotions.mapIndexed { i, p ->
        ScreenOption(p.item, p, i.toInvPos())
    }) ?: promotions.first()

class PiecePlayerActionEvent(val piece: BoardPiece, val type: Type) : ChessEvent {
    enum class Type {
        PICK_UP, PLACE_DOWN
    }
}

class BukkitPlayer(player: Player, color: Color, game: ChessGame) : ChessPlayer<Player>(player, color, player.name, game) {

    private val silent get() = this.player == opponent.player


    var held: BoardPiece? = null
        private set(v) {
            field?.let {
                it.checkExists(game.board)
                game.callEvent(PiecePlayerActionEvent(it, PiecePlayerActionEvent.Type.PLACE_DOWN))
            }
            field = v
            v?.let {
                it.checkExists(game.board)
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

    override fun toString() = "BukkitPlayer(name=$name)"

    fun pickUp(pos: Pos) {
        if (!game.running) return
        val piece = game.board[pos] ?: return
        if (piece.color != color) return
        held = piece
        player.inventory.setItem(0, piece.piece.item)
    }

    fun makeMove(pos: Pos) {
        if (!game.running) return
        val piece = held ?: return
        val moves = piece.getLegalMoves(game.board)
        if (pos != piece.pos && pos !in moves.map { it.display }) return
        held = null
        player.inventory.setItem(0, null)
        if (pos == piece.pos) return
        val chosenMoves = moves.filter { it.display == pos }
        val move = chosenMoves.first()
        game.coroutineScope.launch {
            move.getTrait<PromotionTrait>()?.apply {
                promotion = player.openPawnPromotionMenu(promotions)
            }
            game.finishMove(move)
        }
    }

    private var firstTurn = true

    private fun sendTitleList(titles: List<Pair<Message, Boolean>>) {
        val title = titles.firstOrNull { it.second }
        val subtitle = titles.firstOrNull { it != title }
        player.sendTitleFull(title?.first?.get() ?: "", subtitle?.first?.get() ?: "")
    }

    override fun startTurn() {
        if (firstTurn) {
            firstTurn = false
            return
        }
        val inCheck = king?.let { game.variant.isInCheck(it, game.board) } == true
        sendTitleList(buildList {
            if (inCheck)
                this += IN_CHECK_TITLE to true
            if (!silent)
                this += YOUR_TURN to true
        })
        if (inCheck)
            player.sendMessage(IN_CHECK_MSG)
    }

    override fun init() {
        sendTitleList(buildList {
            this += YOU_ARE_PLAYING_AS_TITLE[color] to false
            if (hasTurn)
                this += YOUR_TURN to true
        })
        player.sendMessage(YOU_ARE_PLAYING_AS_MSG[color])
    }
}

inline fun ByColor<ChessPlayer<*>>.forEachReal(block: (Player) -> Unit) {
    toList().filterIsInstance<BukkitPlayer>().map { it.player }.distinct().forEach(block)
}

inline fun ByColor<ChessPlayer<*>>.forEachUnique(block: (BukkitPlayer) -> Unit) {
    val players = toList().filterIsInstance<BukkitPlayer>()
    if (players.size == 2 && players.all { it.player == players[0].player })
        players.filter { it.hasTurn }.forEach(block)
    else
        players.forEach(block)
}
