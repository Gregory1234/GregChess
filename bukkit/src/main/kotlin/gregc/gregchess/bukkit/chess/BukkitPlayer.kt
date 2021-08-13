package gregc.gregchess.bukkit.chess

import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.chess.component.spectators
import gregc.gregchess.chess.*
import gregc.gregchess.interact
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.TextComponent
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

fun Player.sendMessage(msg: Message) = sendMessage(msg.get())

fun Player.sendTitleFull(title: String?, subtitle: String?) = sendTitle(title, subtitle, 10, 70, 20)

private val SPECTATOR_WINNER = bySides { title("Spectator.${it.configName}Won") }
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

fun Player.showGameResults(side: Side, results: GameResults) {
    val wld = when (results.score) {
        GameScore.Victory(side) -> YOU_WON
        GameScore.Draw -> YOU_DREW
        else -> YOU_LOST
    }
    sendTitleFull(wld.get(), results.name)
    sendMessage(results.message)
}

private val COPY_FEN = message("CopyFEN")

fun Player.sendFEN(fen: FEN) {
    val message = TextComponent(COPY_FEN.get())
    message.clickEvent = ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, fen.toString())
    spigot().sendMessage(message)
}

private val COPY_PGN = message("CopyPGN")

fun Player.sendPGN(pgn: PGN) {
    val message = TextComponent(COPY_PGN.get())
    message.clickEvent = ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, pgn.toString())
    spigot().sendMessage(message)
}

fun Player.sendLastMoves(num: UInt, wLast: MoveData?, bLast: MoveData?) {
    sendMessage(buildString {
        append(num - 1u)
        append(". ")
        wLast?.let { append(it.name.localName) }
        append("  | ")
        bLast?.let { append(it.name.localName) }
    })
}

private val PAWN_PROMOTION = message("PawnPromotion")

suspend fun Player.openPawnPromotionMenu(promotions: Collection<Piece>) =
    openMenu(PAWN_PROMOTION, promotions.mapIndexed { i, p ->
        ScreenOption(p.item, p, i.toInvPos())
    }) ?: promotions.first()

class BukkitPlayer(val player: Player, side: Side, val silent: Boolean, game: ChessGame):
    ChessPlayer(player.name, side, game) {

    companion object {
        private val IN_CHECK_MSG = message("InCheck")
        private val YOU_ARE_PLAYING_AS_MSG = bySides { message("YouArePlayingAs.${it.configName}") }

        private val IN_CHECK_TITLE = title("InCheck")
        private val YOU_ARE_PLAYING_AS_TITLE = bySides { title("YouArePlayingAs.${it.configName}") }
        private val YOUR_TURN = title("YourTurn")
    }

    override fun toString() = "BukkitPlayer(name=$name)"

    fun pickUp(pos: Pos) {
        if (!game.running) return
        val piece = game.board[pos]?.piece ?: return
        if (piece.side != side) return
        held = piece
        player.inventory.setItem(0, piece.piece.item)
    }

    fun makeMove(pos: Pos) {
        if (!game.running) return
        val newSquare = game.board[pos] ?: return
        val piece = held ?: return
        val moves = piece.square.bakedLegalMoves ?: return
        println(moves.map { it.display.pos })
        if (newSquare != piece.square && newSquare !in moves.map { it.display }) return
        held = null
        player.inventory.setItem(0, null)
        if (newSquare == piece.square) return
        val chosenMoves = moves.filter { it.display == newSquare }
        val move = chosenMoves.first()
        interact {
            game.finishMove(move, move.promotions?.let { player.openPawnPromotionMenu(it) })
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
        val inCheck = king?.let { game.variant.isInCheck(it) } == true
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
            this += YOU_ARE_PLAYING_AS_TITLE[side] to false
            if (hasTurn)
                this += YOUR_TURN to true
        })
        player.sendMessage(YOU_ARE_PLAYING_AS_MSG[side])
    }
}

fun ChessGame.AddPlayersScope.bukkit(player: Player, side: Side, silent: Boolean) {
    addPlayer(BukkitPlayer(player, side, silent, game))
}

inline fun BySides<ChessPlayer>.forEachReal(block: (Player) -> Unit) {
    toList().filterIsInstance<BukkitPlayer>().map { it.player }.distinct().forEach(block)
}

inline fun BySides<ChessPlayer>.forEachUnique(block: (BukkitPlayer) -> Unit) {
    val players = toList().filterIsInstance<BukkitPlayer>()
    if (players.size == 2 && players.all {it.player == players[0].player})
        players.filter { it.hasTurn }.forEach(block)
    else
        players.forEach(block)
}
