package gregc.gregchess

import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.entity.Player

class SimpleRequestType<in T : Any>(
    private val messages: SimpleRequestMessages,
    private inline val onAccept: (Request<T>) -> Unit
) {
    private val requests = mutableListOf<Request<T>>()

    operator fun plusAssign(request: Request<T>) {
        if (request.sender == request.receiver) {
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
        val messageSender = TextComponent(string(messages.messageSent) + " ")
        request.sender.spigot().sendMessage(messageSender, messageCancel)

        val messageAccept = TextComponent(string("Message.Request.Accept"))
        messageAccept.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, messages.command)
        val messageReceiver = TextComponent(string(messages.messageReceived) + " ")
        request.receiver.spigot().sendMessage(messageReceiver, messageAccept)
    }

    fun quietRemove(p: Player) = requests.removeIf { it.sender == p || it.receiver == p }

}

data class SimpleRequestMessages(
    val messageSent: String,
    val messageReceived: String,
    val cancelMessage: String,
    val acceptMessage: String,
    val command: String
)

class RequestType<in T : Any>(
    private val messages: RequestMessages,
    private inline val printT: (T) -> String = { it.toString() },
    private inline val onAccept: (Request<T>) -> Unit
) {
    private val requests = mutableListOf<Request<T>>()

    operator fun plusAssign(request: Request<T>) {
        if (request.sender == request.receiver){
            onAccept(request)
            return
        }
        requests.firstOrNull { it.sender == request.sender }?.let {
            request.sender.sendMessage(string(messages.alreadySentMessage))
            return
        }
        requests.firstOrNull { it.receiver == request.receiver || it.sender == request.receiver }?.let {
            request.sender.sendMessage(string(messages.alreadySentMessage))
            return
        }
        requests += request
        val messageCancel = TextComponent(string("Message.Request.Cancel"))
        messageCancel.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, messages.cancelCommand)
        val messageSender = TextComponent(string(messages.messageSent) + " ")
        request.sender.spigot().sendMessage(messageSender, messageCancel)

        val messageAccept = TextComponent(string("Message.Request.Accept"))
        messageAccept.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, messages.acceptCommand)
        val messageReceiver = TextComponent(
            string(messages.messageReceived).replace("$1", request.sender.name)
                .replace("$2", printT(request.value)) + " "
        )
        request.receiver.spigot().sendMessage(messageReceiver, messageAccept)
    }

    fun accept(p: Player) {
        val request = requests.firstOrNull { it.receiver == p }
        if (request == null) {
            p.sendMessage(string(messages.notFoundMessage))
            return
        }
        request.sender.sendMessage(string(messages.acceptMessage))
        request.receiver.sendMessage(string(messages.acceptMessage))
        onAccept(request)
        requests -= request
    }

    fun cancel(p: Player) {
        val request = requests.firstOrNull { it.sender == p }
        if (request == null) {
            p.sendMessage(string(messages.notFoundMessage))
            return
        }
        request.sender.sendMessage(string(messages.cancelMessage).replace("$1", request.receiver.name))
        request.receiver.sendMessage(string(messages.cancelMessage).replace("$1", request.sender.name))
        requests -= request
    }

    fun quietRemove(p: Player) = requests.removeIf { it.sender == p || it.receiver == p }

}

data class RequestMessages(
    val messageSent: String,
    val messageReceived: String,
    val cancelMessage: String,
    val acceptMessage: String,
    val notFoundMessage: String,
    val alreadySentMessage: String,
    val acceptCommand: String,
    val cancelCommand: String
)

data class Request<out T>(val sender: Player, val receiver: Player, val value: T)