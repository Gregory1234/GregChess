package gregc.gregchess.bukkit.match

import gregc.gregchess.bukkit.configName
import gregc.gregchess.bukkit.renderer.arena
import gregc.gregchess.bukkitutils.textComponent
import gregc.gregchess.match.ChessMatch

fun ChessMatch.getInfo() = textComponent {
    text("UUID: $uuid\n") {
        onClickCopy(uuid)
    }
    text("Players: ${sides.toList().joinToString { "${it.name} as ${it.color.configName}" }}\n")
    text("Spectators: ${spectators.spectators.joinToString { it.name }}\n")
    text("Arena: ${arena.name}\n")
    text("Preset: ${matchController.presetName}\n")
    text("Variant: ${variant.key}\n")
    text("Components: ${components.joinToString { it.type.key.toString() }}")
}