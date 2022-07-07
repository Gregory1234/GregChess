package gregc.gregchess.bukkit.player

import gregc.gregchess.Color
import gregc.gregchess.board.FEN
import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.match.*
import gregc.gregchess.bukkit.piece.item
import gregc.gregchess.bukkit.results.message
import gregc.gregchess.bukkit.results.name
import gregc.gregchess.bukkitutils.*
import gregc.gregchess.byColor
import gregc.gregchess.match.ChessMatch
import gregc.gregchess.match.PGN
import gregc.gregchess.move.Move
import gregc.gregchess.move.MoveFormatter
import gregc.gregchess.piece.Piece
import gregc.gregchess.player.ChessPlayer
import gregc.gregchess.results.*
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player

fun OfflinePlayer.toChessPlayer(): ChessPlayer = BukkitPlayerType.BUKKIT.of(uniqueId)

var Player.currentChessMatch: ChessMatch?
    get() = ChessMatchManager.currentMatchOf(uniqueId)
    set(match) { ChessMatchManager.setCurrentMatch(uniqueId, match?.uuid) }
val Player.isInChessMatch: Boolean get() = currentChessMatch != null
val Player.currentChessSide: BukkitChessSide? get() = ChessMatchManager.currentSideOf(uniqueId)
val Player.activeChessMatches: Set<ChessMatch> get() = ChessMatchManager.activeMatchesOf(uniqueId)
val Player.currentSpectatedChessMatch: ChessMatch? get() = ChessMatchManager.currentSpectatedMatchOf(uniqueId)
val Player.isSpectatingChessMatch: Boolean get() = currentSpectatedChessMatch != null

private val SPECTATOR_WINNER = byColor { title("Spectator.${it.configName}Won") }
private val SPECTATOR_DRAW = title("Spectator.ItWasADraw")

fun Player.showMatchResults(results: MatchResults) {
    sendTitleFull(
        results.score.let { if (it is MatchScore.Victory) SPECTATOR_WINNER[it.winner] else SPECTATOR_DRAW }.get(),
        results.name
    )
    sendMessage(results.message)
}

private val YOU_WON = title("Player.YouWon")
private val YOU_LOST = title("Player.YouLost")
private val YOU_DREW = title("Player.YouDrew")

fun Player.showMatchResults(color: Color, results: MatchResults) {
    val wld = when (results.score) {
        MatchScore.Victory(color) -> YOU_WON
        MatchScore.Draw -> YOU_DREW
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
    if (allowRejoining && config.getBoolean("Rejoin.SendReminder") && activeChessMatches.isNotEmpty()) {
        spigot().sendMessage(textComponent(REJOIN_REMINDER.get()) {
            onClickCommand("/chess rejoin")
        })
    }
}

fun Player.leaveMatch() {
    // TODO: add a time limit for rejoining
    currentSpectatedChessMatch?.minusAssign(this)
    currentChessMatch?.let { match ->
        if (allowRejoining) {
            match.callEvent(PlayerEvent(this, PlayerDirection.LEAVE))
        } else {
            val color = match[uniqueId]!!.color
            match.stop(color.lostBy(EndReason.WALKOVER), byColor { it == color })
        }
    }
    currentChessMatch = null
    sendRejoinReminder()
}

fun Player.rejoinMatch() {
    activeChessMatches.firstOrNull()?.let { match ->
        currentChessMatch = match
        match.callEvent(PlayerEvent(this, PlayerDirection.JOIN))
    }
}