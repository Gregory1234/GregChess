package gregc.gregchess

import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin

class RequestManager(private val plugin: JavaPlugin) : Listener {
    private val requestTypes = mutableListOf<RequestType<*>>()

    fun start() {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    fun <T> register(requestType: RequestType<T>): RequestType<T> {
        requestTypes.add(requestType)
        return requestType
    }

    @EventHandler
    fun onPlayerQuit(e: PlayerQuitEvent) {
        requestTypes.forEach {
            it.quietRemove(e.player)
        }
    }

}

class RequestTypeBuilder<T> {
    private lateinit var messages: RequestMessages
    private var validateSender: (Player) -> Boolean = { true }
    private var printT: (T) -> String = { it.toString() }
    private var onAccept: (Request<T>) -> Unit = {}

    fun register(m: RequestManager) = m.register(RequestType(messages, validateSender, printT, onAccept))

    fun messagesSimple(root: String, acceptCommand: String, cancelCommand: String): RequestTypeBuilder<T> {
        messages = RequestMessages(
            "$root.Sent.Request",
            "$root.Received.Request",
            "$root.Sent.Cancel",
            "$root.Received.Cancel",
            "$root.Sent.Accept",
            "$root.Received.Accept",
            "$root.CannotSend",
            "$root.NotFound",
            "$root.AlreadySent",
            acceptCommand,
            cancelCommand
        )
        return this
    }

    fun validate(validateSender: (Player) -> Boolean): RequestTypeBuilder<T> {
        this.validateSender = validateSender
        return this
    }

    fun print(printT: (T) -> String): RequestTypeBuilder<T> {
        this.printT = printT
        return this
    }

    fun onAccept(onAccept: (Request<T>) -> Unit): RequestTypeBuilder<T> {
        this.onAccept = onAccept
        return this
    }
}


class RequestType<in T>(
    private val messages: RequestMessages,
    private inline val validateSender: (Player) -> Boolean = { true },
    private inline val printT: (T) -> String = { it.toString() },
    private inline val onAccept: (Request<T>) -> Unit
) {
    private val requests = mutableListOf<Request<T>>()

    operator fun plusAssign(request: Request<T>) {
        if (!validateSender(request.sender)) {
            request.sender.sendMessage(string(messages.cannotSend))
            return
        }
        if (request.sender == request.receiver) {
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

    fun simpleCall(request: Request<T>) {
        requests.firstOrNull { it.sender == request.sender }?.let {
            cancel(it)
            return
        }
        requests.firstOrNull { it.receiver == request.receiver }?.let {
            request.sender.sendMessage(string(messages.alreadySentMessage))
            return
        }
        requests.firstOrNull { it.sender == request.receiver && it.receiver == request.sender }?.let {
            accept(it)
            return
        }
        plusAssign(request)
    }

    fun accept(request: Request<T>) {
        request.sender.sendMessage(string(messages.acceptMessageReceived))
        request.receiver.sendMessage(string(messages.acceptMessage))
        onAccept(request)
        requests -= request
    }

    fun accept(p: Player) {
        val request = requests.firstOrNull { it.receiver == p }
        if (request == null)
            p.sendMessage(string(messages.notFoundMessage))
        else
            accept(request)
    }

    fun cancel(request: Request<T>) {
        request.sender.sendMessage(string(messages.cancelMessage).replace("$1", request.receiver.name))
        request.receiver.sendMessage(string(messages.cancelMessageReceived).replace("$1", request.sender.name))
        requests -= request
    }

    fun cancel(p: Player) {
        val request = requests.firstOrNull { it.sender == p }
        if (request == null)
            p.sendMessage(string(messages.notFoundMessage))
        else
            cancel(request)
    }

    fun quietRemove(p: Player) = requests.removeIf { it.sender == p || it.receiver == p }

}

data class RequestMessages(
    val messageSent: String,
    val messageReceived: String,
    val cancelMessage: String,
    val cancelMessageReceived: String,
    val acceptMessage: String,
    val acceptMessageReceived: String,
    val cannotSend: String,
    val notFoundMessage: String,
    val alreadySentMessage: String,
    val acceptCommand: String,
    val cancelCommand: String
)

data class Request<out T>(val sender: Player, val receiver: Player, val value: T)