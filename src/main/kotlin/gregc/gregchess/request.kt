package gregc.gregchess

import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.entity.Player

class RequestType<in T : Any>(
    private val messages: RequestMessages,
    private inline val onAccept: (Request<T>) -> Unit
) {
    private val requests = mutableListOf<Request<T>>()

    operator fun plusAssign(request: Request<T>) {
        if (request.sender == request.receiver){
            onAccept(request)
            return
        }

        requests.firstOrNull { it.receiver == request.sender && it.sender == request.receiver }?.let {
            request.sender.sendMessage(string(messages.acceptMessage))
            request.receiver.sendMessage(string(messages.acceptMessage))
            onAccept(it)
            requests -= it
            return
        }
        requests.firstOrNull { it.receiver == request.receiver && it.sender == request.sender }?.let {
            request.sender.sendMessage(string(messages.cancelMessage))
            request.receiver.sendMessage(string(messages.cancelMessage))
            requests -= it
            return
        }
        requests += request
        val messageCancel = TextComponent(string("Message.Request.Cancel"))
        messageCancel.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, messages.command)
        val messageSender = TextComponent(string(messages.message) + " ")
        request.sender.spigot().sendMessage(messageSender, messageCancel)

        val messageAccept = TextComponent(string("Message.Request.Accept"))
        messageAccept.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, messages.command)
        val messageReceiver = TextComponent(string(messages.message) + " ")
        request.receiver.spigot().sendMessage(messageReceiver, messageAccept)
    }

    fun quietRemove(p: Player) {
        val cancelled = requests.firstOrNull { it.sender == p }
        requests.remove(cancelled)
    }

}

data class RequestMessages(
    val message: String,
    val cancelMessage: String,
    val acceptMessage: String,
    val command: String
)

data class Request<out T>(val sender: Player, val receiver: Player, val value: T)