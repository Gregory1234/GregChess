package gregc.gregchess.bukkitutils

import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.TextComponent

inline fun textComponent(builder: TextComponentBuilder.() -> Unit): TextComponent =
    TextComponentBuilder().apply(builder).build()

inline fun textComponent(base: String, builder: TextComponentBuilder.() -> Unit): TextComponent =
    TextComponentBuilder(TextComponent(base)).apply(builder).build()

class TextComponentBuilder(private val textComponent: TextComponent = TextComponent()) {

    var clickEvent: ClickEvent
        get() = textComponent.clickEvent
        set(v) { textComponent.clickEvent = v }

    fun build() = textComponent

    fun text(text: String) = textComponent.addExtra(text)

    fun text(text: TextComponent) = textComponent.addExtra(text)

    fun text(value: Any?) = text(value.toString())

    inline fun text(text: String, builder: TextComponentBuilder.() -> Unit) = text(TextComponent(text), builder)

    inline fun text(text: TextComponent, builder: TextComponentBuilder.() -> Unit) {
        text(TextComponentBuilder(text).apply(builder).build())
    }

    inline fun text(value: Any?, builder: TextComponentBuilder.() -> Unit) = text(value.toString(), builder)

    fun onClickCopy(text: String) {
        clickEvent = ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, text)
    }

    fun onClickCopy(value: Any?) = onClickCopy(value.toString())

    fun onClickCommand(command: String) {
        clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, command)
    }
}