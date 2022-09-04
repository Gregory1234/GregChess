package gregc.gregchess.bukkit.player

import gregc.gregchess.bukkit.config
import gregc.gregchess.bukkit.match.*
import gregc.gregchess.bukkit.message
import gregc.gregchess.bukkitutils.textComponent
import gregc.gregchess.match.ChessMatch
import gregc.gregchess.results.EndReason
import gregc.gregchess.results.lostBy

var BukkitPlayer.currentChessMatch: ChessMatch?
    get() = ChessMatchManager.currentMatchOf(uuid)
    set(match) { ChessMatchManager.setCurrentMatch(uuid, match?.uuid) }
val BukkitPlayer.isInChessMatch: Boolean get() = currentChessMatch != null
val BukkitPlayer.currentChessSide: BukkitChessSideFacade? get() = ChessMatchManager.currentSideOf(uuid)
val BukkitPlayer.activeChessMatches: Set<ChessMatch> get() = ChessMatchManager.activeMatchesOf(uuid)
val BukkitPlayer.currentSpectatedChessMatch: ChessMatch? get() = ChessMatchManager.currentSpectatedMatchOf(uuid)
val BukkitPlayer.isSpectatingChessMatch: Boolean get() = currentSpectatedChessMatch != null

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
            quickLeave = true
            match.stop(color.lostBy(EndReason.WALKOVER))
            quickLeave = false
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
