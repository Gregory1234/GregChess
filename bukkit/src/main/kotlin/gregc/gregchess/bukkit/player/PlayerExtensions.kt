package gregc.gregchess.bukkit.player

import gregc.gregchess.Color
import gregc.gregchess.board.FEN
import gregc.gregchess.bukkit.*
import gregc.gregchess.bukkit.match.*
import gregc.gregchess.bukkit.piece.item
import gregc.gregchess.bukkit.results.message
import gregc.gregchess.bukkit.results.name
import gregc.gregchess.bukkitutils.*
import gregc.gregchess.bukkitutils.player.BukkitPlayer
import gregc.gregchess.bukkitutils.player.DefaultBukkitPlayer
import gregc.gregchess.byColor
import gregc.gregchess.match.ChessMatch
import gregc.gregchess.match.PGN
import gregc.gregchess.move.Move
import gregc.gregchess.move.MoveFormatter
import gregc.gregchess.piece.Piece
import gregc.gregchess.results.*
import org.bukkit.entity.Player

fun BukkitPlayer.toChessSide(color: Color) = BukkitChessSide(uuid, color)

var BukkitPlayer.currentChessMatch: ChessMatch?
    get() = ChessMatchManager.currentMatchOf(uuid)
    set(match) { ChessMatchManager.setCurrentMatch(uuid, match?.uuid) }
val BukkitPlayer.isInChessMatch: Boolean get() = currentChessMatch != null
val BukkitPlayer.currentChessSide: BukkitChessSideFacade? get() = ChessMatchManager.currentSideOf(uuid)
val BukkitPlayer.activeChessMatches: Set<ChessMatch> get() = ChessMatchManager.activeMatchesOf(uuid)
val BukkitPlayer.currentSpectatedChessMatch: ChessMatch? get() = ChessMatchManager.currentSpectatedMatchOf(uuid)
val BukkitPlayer.isSpectatingChessMatch: Boolean get() = currentSpectatedChessMatch != null

private val SPECTATOR_WINNER = byColor { title("Spectator.${it.configName}Won") }
private val SPECTATOR_DRAW = title("Spectator.ItWasADraw")

fun BukkitPlayer.showMatchResults(results: MatchResults) {
    sendTitle(
        results.score.let { if (it is MatchScore.Victory) SPECTATOR_WINNER[it.winner] else SPECTATOR_DRAW }.get(),
        results.name
    )
    sendMessage(results.message)
}

private val YOU_WON = title("Player.YouWon")
private val YOU_LOST = title("Player.YouLost")
private val YOU_DREW = title("Player.YouDrew")

fun BukkitPlayer.showMatchResults(color: Color, results: MatchResults) {
    val wld = when (results.score) {
        MatchScore.Victory(color) -> YOU_WON
        MatchScore.Draw -> YOU_DREW
        else -> YOU_LOST
    }
    sendTitle(wld.get(), results.name)
    sendMessage(results.message)
}

private val COPY_FEN = message("CopyFEN")

fun BukkitPlayer.sendFEN(fen: FEN) {
    sendMessage(textComponent(COPY_FEN.get()) {
        onClickCopy(fen)
    })
}

private val COPY_PGN = message("CopyPGN")

fun BukkitPlayer.sendPGN(pgn: PGN) {
    sendMessage(textComponent(COPY_PGN.get()) {
        onClickCopy(pgn)
    })
}

fun BukkitPlayer.sendLastMoves(num: Int, wLast: Move?, bLast: Move?, formatter: MoveFormatter) {
    sendMessage(buildString {
        append(num - 1)
        append(". ")
        wLast?.let { append(formatter.format(it)) }
        append("  | ")
        bLast?.let { append(formatter.format(it)) }
    })
}

private val PAWN_PROMOTION = message("PawnPromotion")

suspend fun BukkitPlayer.openPawnPromotionMenu(promotions: Collection<Piece>) =
    openMenu(PAWN_PROMOTION, promotions.mapIndexed { i, p ->
        ScreenOption(p.item, p, i.toInvPos())
    }) ?: promotions.first()

private val allowRejoining get() = config.getBoolean("Rejoin.AllowRejoining")

private val REJOIN_REMINDER = message("RejoinReminder")

fun BukkitPlayer.sendRejoinReminder() {
    if (allowRejoining && config.getBoolean("Rejoin.SendReminder") && activeChessMatches.isNotEmpty()) {
        sendMessage(textComponent(REJOIN_REMINDER.get()) {
            onClickCommand("/chess rejoin")
        })
    }
}

fun BukkitPlayer.leaveMatch() {
    // TODO: add a time limit for rejoining
    currentSpectatedChessMatch?.spectators?.minusAssign(this)
    currentChessMatch?.let { match ->
        if (allowRejoining) {
            match.callEvent(PlayerEvent(this, PlayerDirection.LEAVE))
        } else {
            val color = match[uuid]!!.color
            match.stop(color.lostBy(EndReason.WALKOVER), byColor { it == color })
        }
    }
    currentChessMatch = null
    sendRejoinReminder()
}

fun BukkitPlayer.rejoinMatch() {
    activeChessMatches.firstOrNull()?.let { match ->
        currentChessMatch = match
        match.callEvent(PlayerEvent(this, PlayerDirection.JOIN))
    }
}

val org.bukkit.event.player.PlayerEvent.gregchessPlayer get() = DefaultBukkitPlayer(player)
val org.bukkit.event.block.BlockBreakEvent.gregchessPlayer get() = DefaultBukkitPlayer(player)
val org.bukkit.event.entity.EntityEvent.gregchessPlayer get() = (entity as? Player)?.let(::DefaultBukkitPlayer)
val org.bukkit.event.inventory.InventoryInteractEvent.gregchessPlayer get() = (whoClicked as? Player)?.let(::DefaultBukkitPlayer)