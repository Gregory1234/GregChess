package gregc.gregchess.bukkit.match

import gregc.gregchess.board.FEN
import gregc.gregchess.bukkit.configName
import gregc.gregchess.bukkit.message
import gregc.gregchess.bukkit.renderer.arena
import gregc.gregchess.bukkitutils.textComponent
import gregc.gregchess.match.ChessMatch
import gregc.gregchess.match.PGN

fun ChessMatch.getInfo() = textComponent {
    text("UUID: $uuid\n") {
        onClickCopy(uuid)
    }
    text("Players: ${sides.toList().joinToString { "${it.name} as ${it.color.configName}" }}\n")
    text("Spectators: ${spectators.spectators.joinToString { it.name }}\n")
    text("Arena: ${arena.name}\n")
    text("Preset: $presetName\n")
    text("Variant: ${variant.key}\n")
    text("Components: ${components.joinToString { it.type.key.toString() }}")
}

private val COPY_FEN = message("CopyFEN")

fun FEN.copyMessage() = textComponent(COPY_FEN.get()) {
    onClickCopy(this)
}

private val COPY_PGN = message("CopyPGN")

fun PGN.copyMessage() = textComponent(COPY_PGN.get()) {
    onClickCopy(this)
}