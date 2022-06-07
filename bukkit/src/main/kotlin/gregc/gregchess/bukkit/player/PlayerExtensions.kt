package gregc.gregchess.bukkit.player

import gregc.gregchess.Color
import gregc.gregchess.board.FEN
import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.game.*
import gregc.gregchess.bukkit.piece.item
import gregc.gregchess.bukkit.results.message
import gregc.gregchess.bukkit.results.name
import gregc.gregchess.bukkitutils.*
import gregc.gregchess.byColor
import gregc.gregchess.game.ChessGame
import gregc.gregchess.game.PGN
import gregc.gregchess.move.Move
import gregc.gregchess.move.MoveFormatter
import gregc.gregchess.piece.Piece
import gregc.gregchess.player.ChessPlayer
import gregc.gregchess.results.*
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player

fun OfflinePlayer.toChessPlayer(): ChessPlayer = BukkitPlayerType.BUKKIT.of(uniqueId)

var Player.currentChessGame: ChessGame?
    get() = ChessGameManager.currentGameOf(uniqueId)
    set(game) { ChessGameManager.setCurrentGame(uniqueId, game?.uuid) }
val Player.isInChessGame: Boolean get() = currentChessGame != null
val Player.currentChessSide: BukkitChessSide? get() = ChessGameManager.currentSideOf(uniqueId)
val Player.activeChessGames: Set<ChessGame> get() = ChessGameManager.activeGamesOf(uniqueId)
val Player.currentSpectatedChessGame: ChessGame? get() = ChessGameManager.currentSpectatedGameOf(uniqueId)
val Player.isSpectatingChessGame: Boolean get() = currentSpectatedChessGame != null

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

fun Player.sendLastMoves(num: Int, wLast: Move?, bLast: Move?, formatter: MoveFormatter) {
    sendMessage(buildString {
        append(num - 1)
        append(". ")
        wLast?.let { append(formatter.format(it)) }
        append("  | ")
        bLast?.let { append(formatter.format(it)) }
    })
}

private val PAWN_PROMOTION = message("PawnPromotion")

suspend fun Player.openPawnPromotionMenu(promotions: Collection<Piece>) =
    openMenu(PAWN_PROMOTION, promotions.mapIndexed { i, p ->
        ScreenOption(p.item, p, i.toInvPos())
    }) ?: promotions.first()

private val allowRejoining get() = config.getBoolean("Rejoin.AllowRejoining")

private val REJOIN_REMINDER = message("RejoinReminder")

fun Player.sendRejoinReminder() {
    if (allowRejoining && config.getBoolean("Rejoin.SendReminder") && activeChessGames.isNotEmpty()) {
        spigot().sendMessage(textComponent(REJOIN_REMINDER.get()) {
            onClickCommand("/chess rejoin")
        })
    }
}

fun Player.leaveGame() {
    // TODO: add a time limit for rejoining
    currentSpectatedChessGame?.spectators?.minusAssign(this)
    currentChessGame?.let { game ->
        if (allowRejoining) {
            game.callEvent(PlayerEvent(this, PlayerDirection.LEAVE))
        } else {
            val color = game[uniqueId]!!.color
            game.stop(color.lostBy(EndReason.WALKOVER), byColor { it == color })
        }
    }
    currentChessGame = null
    sendRejoinReminder()
}

fun Player.rejoinGame() {
    activeChessGames.firstOrNull()?.let { game ->
        currentChessGame = game
        game.callEvent(PlayerEvent(this, PlayerDirection.JOIN))
    }
}