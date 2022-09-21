package gregc.gregchess.bukkit.match

import gregc.gregchess.bukkit.configName
import gregc.gregchess.bukkit.event.BukkitChessEventType
import gregc.gregchess.bukkitutils.TextComponentBuilder
import gregc.gregchess.bukkitutils.textComponent
import gregc.gregchess.event.ChessEvent
import gregc.gregchess.match.ChessMatch
import net.md_5.bungee.api.chat.TextComponent

// TODO: add a way to order the info better
// TODO: make sure the info is in the correct format
class MatchInfoEvent(private val textComponent: TextComponentBuilder) : ChessEvent {
    override val type get() = BukkitChessEventType.MATCH_INFO

    fun text(text: String) = textComponent.text(text)

    fun text(text: TextComponent) = textComponent.text(text)

    fun text(value: Any?) = textComponent.text(value)

    inline fun text(text: String, builder: TextComponentBuilder.() -> Unit) = text(TextComponent(text), builder)

    inline fun text(text: TextComponent, builder: TextComponentBuilder.() -> Unit) {
        text(TextComponentBuilder(text).apply(builder).build())
    }

    inline fun text(value: Any?, builder: TextComponentBuilder.() -> Unit) = text(value.toString(), builder)
}

fun ChessMatch.getInfo() = textComponent {
    text("UUID: $uuid\n") {
        onClickCopy(uuid)
    }
    text("Players: ${sides.toList().joinToString { "${it.name} as ${it.color.configName}" }}\n")
    callEvent(MatchInfoEvent(this))
    text("Preset: $presetName\n")
    text("Variant: ${variant.key}\n")
    text("Components: ${components.joinToString { it.type.key.toString() }}")
}