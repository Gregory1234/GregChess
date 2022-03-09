package gregc.gregchess.bukkit.chess.player

import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.chess.*
import gregc.gregchess.bukkit.chess.component.*
import gregc.gregchess.bukkitutils.*
import gregc.gregchess.chess.*
import gregc.gregchess.chess.move.Move
import gregc.gregchess.chess.piece.Piece
import gregc.gregchess.chess.player.ChessPlayer
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player

val OfflinePlayer.gregchess: ChessPlayer get() = BukkitPlayerType.BUKKIT.of(uniqueId)

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

fun Player.sendLastMoves(num: UInt, wLast: Move?, bLast: Move?, formatter: MoveFormatter) {
    sendMessage(buildString {
        append(num - 1u)
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
    if (allowRejoining && config.getBoolean("Rejoin.SendReminder") && lastLeftGame != null) {
        spigot().sendMessage(textComponent(REJOIN_REMINDER.get()) {
            onClickCommand("/chess rejoin")
        })
    }
}

fun Player.leaveGame() {
    // TODO: add a time limit for rejoining
    val g = chess
    spectatedGame = null
    g?.game?.let { game ->
        lastLeftGame = game
        if (allowRejoining) {
            game.callEvent(PlayerEvent(this, PlayerDirection.LEAVE))
        } else {
            game.stop(g.color.lostBy(EndReason.WALKOVER), byColor { it == g.color })
        }
    }
    currentGame = null
    sendRejoinReminder()
}

fun Player.rejoinGame() {
    lastLeftGame?.let { game ->
        currentGame = game
        game.callEvent(PlayerEvent(this, PlayerDirection.JOIN))
        chess!!.start()
    }
}