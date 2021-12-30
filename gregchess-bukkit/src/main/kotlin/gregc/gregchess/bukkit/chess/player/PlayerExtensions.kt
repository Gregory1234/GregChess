package gregc.gregchess.bukkit.chess.player

import gregc.gregchess.bukkit.chess.*
import gregc.gregchess.bukkit.message
import gregc.gregchess.bukkit.title
import gregc.gregchess.bukkitutils.*
import gregc.gregchess.chess.*
import gregc.gregchess.chess.move.Move
import gregc.gregchess.chess.move.MoveNameFormatter
import gregc.gregchess.chess.piece.Piece
import org.bukkit.entity.Player

val Player.gregchess: BukkitPlayer get() = BukkitPlayer(uniqueId, name)

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