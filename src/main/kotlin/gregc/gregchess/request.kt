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
        requests.firstOrNull { it.receiver == request.receiver && it.sender == request.sender }?.let {
            request.sender.sendMessage(string(messages.requestAlreadySent))
            return
        }
        requests.firstOrNull { it.receiver == request.sender && it.sender == request.receiver }?.let {
            this += it.sender
            return
        }
        requests += request
        val messageCancel = TextComponent(string("Message.Request.Cancel"))
        messageCancel.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, messages.cancelCommand)
        val messageSender = TextComponent(string(messages.message) + " ")
        request.sender.spigot().sendMessage(messageSender, messageCancel)

        val messageAccept = TextComponent(string("Message.Request.Accept"))
        messageAccept.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, messages.acceptCommand)
        val messageReceiver = TextComponent(string(messages.message) + " ")
        request.receiver.spigot().sendMessage(messageReceiver, messageAccept)
    }

    operator fun minusAssign(sender: Player) {
        val cancelled = requests.firstOrNull { it.sender == sender }
        if (cancelled == null) {
            sender.sendMessage(string(messages.requestNotFound))
            return
        }
        cancelled.sender.sendMessage(string(messages.cancelMessage))
        cancelled.receiver.sendMessage(string(messages.cancelMessage))
        requests.remove(cancelled)
    }

    operator fun plusAssign(receiver: Player) {
        val completed = requests.firstOrNull { it.receiver == receiver }
        if (completed == null) {
            receiver.sendMessage(string(messages.requestNotFound))
            return
        }
        completed.sender.sendMessage(string(messages.acceptCommand))
        completed.receiver.sendMessage(string(messages.acceptCommand))
        onAccept(completed)
        requests.remove(completed)
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
    val requestNotFound: String,
    val requestAlreadySent: String,
    val acceptCommand: String,
    val cancelCommand: String
)

data class Request<out T>(val sender: Player, val receiver: Player, val value: T)