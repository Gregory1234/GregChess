package gregc.gregchess.bukkit.match

import gregc.gregchess.board.FEN
import gregc.gregchess.bukkit.message
import gregc.gregchess.bukkitutils.textComponent
import gregc.gregchess.match.PGN

private val COPY_FEN = message("CopyFEN")

fun FEN.copyMessage() = textComponent(COPY_FEN.get()) {
    onClickCopy(this@copyMessage)
}

private val COPY_PGN = message("CopyPGN")

fun PGN.copyMessage() = textComponent(COPY_PGN.get()) {
    onClickCopy(this@copyMessage)
}