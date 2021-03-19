package gregc.gregchess

import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

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

class RequestTypeBuilder<T>(private val configManager: ConfigManager) {
    private lateinit var messages: RequestMessages
    private var validateSender: (Player) -> Boolean = { true }
    private var printT: (T) -> String = { it.toString() }
    private var onAccept: (Request<T>) -> Unit = {}

    fun register(m: RequestManager) = m.register(RequestType(configManager, messages, validateSender, printT, onAccept))

    fun messagesSimple(root: String, acceptCommand: String, cancelCommand: String): RequestTypeBuilder<T> {
        messages = RequestMessages(root, acceptCommand, cancelCommand)
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
    private val config: ConfigManager,
    private val messages: RequestMessages,
    private inline val validateSender: (Player) -> Boolean = { true },
    private inline val printT: (T) -> String = { it.toString() },
    private inline val onAccept: (Request<T>) -> Unit
) {
    private val requests = mutableListOf<Request<T>>()
    private val root = messages.name

    private fun getError(name: String) = config.getError("$root.$name")
    private fun getMessage(name: String) = config.getString("Message.Request.$root.$name")

    operator fun plusAssign(request: Request<T>) {
        if (!validateSender(request.sender)) {
            request.sender.sendMessage(getError("CannotSend"))
            glog.mid("Invalid sender", request.uuid)
            return
        }
        if (request.sender == request.receiver) {
            glog.mid("Self request", request.uuid)
            onAccept(request)
            return
        }
        requests.firstOrNull { it.sender == request.sender }?.let {
            glog.mid("Already sent", request.uuid)
            request.sender.sendMessage(getError("AlreadySent"))
            return
        }
        requests.firstOrNull { it.receiver == request.sender || it.sender == request.receiver }?.let {
            glog.mid("Already sent", request.uuid)
            request.sender.sendMessage(getError("AlreadySent"))
            return
        }
        requests += request
        val messageCancel = TextComponent(config.getString("Message.Request.Cancel"))
        messageCancel.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, messages.cancelCommand)
        val messageSender = TextComponent(getMessage("Sent.Request") + " ")
        request.sender.spigot().sendMessage(messageSender, messageCancel)

        val messageAccept = TextComponent(config.getString("Message.Request.Accept"))
        messageAccept.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, messages.acceptCommand)
        val messageReceiver = TextComponent(
            getMessage("Received.Request").replace("$1", request.sender.name)
                .replace("$2", printT(request.value)) + " "
        )
        request.receiver.spigot().sendMessage(messageReceiver, messageAccept)
        glog.mid("Sent", request.uuid)
    }

    fun simpleCall(request: Request<T>) {
        requests.firstOrNull { it.sender == request.sender }?.let {
            cancel(it)
            return
        }
        requests.firstOrNull { it.receiver == request.receiver }?.let {
            glog.mid("Already sent", request.uuid)
            request.sender.sendMessage(getError("AlreadySent"))
            return
        }
        requests.firstOrNull { it.sender == request.receiver && it.receiver == request.sender }?.let {
            accept(it)
            return
        }
        plusAssign(request)
    }

    fun accept(request: Request<T>) {
        request.sender.sendMessage(getMessage("Sent.Accept"))
        request.receiver.sendMessage(getMessage("Received.Accept"))
        onAccept(request)
        requests -= request
        glog.mid("Accepted", request.uuid)
    }

    fun accept(p: Player) {
        val request = requests.firstOrNull { it.receiver == p }
        if (request == null)
            p.sendMessage(getError("NotFound"))
        else
            accept(request)
    }

    fun cancel(request: Request<T>) {
        request.sender.sendMessage(getMessage("Sent.Cancel").replace("$1", request.receiver.name))
        request.receiver.sendMessage(getMessage("Received.Cancel").replace("$1", request.sender.name))
        requests -= request
        glog.mid("Cancelled", request.uuid)
    }

    fun cancel(p: Player) {
        val request = requests.firstOrNull { it.sender == p }
        if (request == null)
            p.sendMessage(getError("NotFound"))
        else
            cancel(request)
    }

    fun quietRemove(p: Player) = requests.removeIf { it.sender == p || it.receiver == p }

}

data class RequestMessages(val name: String, val acceptCommand: String, val cancelCommand: String)

data class Request<out T>(val sender: Player, val receiver: Player, val value: T){
    val uuid: UUID = UUID.randomUUID()
    init {
        glog.low("Created request", uuid, sender, receiver, value)
    }
}