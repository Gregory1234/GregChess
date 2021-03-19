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

class RequestTypeBuilder<T>(private val config: ConfigManager, private val time: TimeManager) {
    private lateinit var messages: RequestMessages
    private var validateSender: (Player) -> Boolean = { true }
    private var printT: (T) -> String = { it.toString() }
    private var onAccept: (Request<T>) -> Unit = {}
    private var onCancel: (Request<T>) -> Unit = {}

    fun register(m: RequestManager) =
        m.register(RequestType(config, time, messages, validateSender, printT, onAccept, onCancel))

    fun messagesSimple(
        root: String,
        acceptCommand: String,
        cancelCommand: String
    ): RequestTypeBuilder<T> {
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

    fun onCancel(onCancel: (Request<T>) -> Unit): RequestTypeBuilder<T> {
        this.onCancel = onCancel
        return this
    }
}


class RequestType<in T>(
    private val config: ConfigManager,
    private val time: TimeManager,
    private val messages: RequestMessages,
    private inline val validateSender: (Player) -> Boolean = { true },
    private inline val printT: (T) -> String = { it.toString() },
    private inline val onAccept: (Request<T>) -> Unit,
    private inline val onCancel: (Request<T>) -> Unit
) {
    private val requests = mutableMapOf<UUID, Request<T>>()
    private val root = messages.name

    private fun getMessage(name: String) = config.getString("Request.$root.$name")
    private fun getFormatMessage(name: String, vararg args: Any?) =
        config.getFormatString("Request.$root.$name", *args)

    private fun call(request: Request<T>, simple: Boolean) {
        if (!validateSender(request.sender)) {
            request.sender.sendMessage(getMessage("Error.CannotSend"))
            glog.mid("Invalid sender", request.uniqueId)
            return
        }
        if ((simple || config.getBool("Request.SelfAccept", true))
            && request.sender == request.receiver
        ) {
            glog.mid("Self request", request.uniqueId)
            onAccept(request)
            return
        }
        requests[request.uniqueId] = request
        val messageCancel = TextComponent(config.getString("Request.Cancel"))
        messageCancel.clickEvent =
            ClickEvent(
                ClickEvent.Action.RUN_COMMAND,
                if (simple) messages.cancelCommand else "${messages.cancelCommand} ${request.uniqueId}"
            )
        val messageSender = TextComponent(getMessage("Sent.Request") + " ")
        request.sender.spigot().sendMessage(messageSender, messageCancel)

        val messageAccept = TextComponent(config.getString("Request.Accept"))
        messageAccept.clickEvent =
            ClickEvent(
                ClickEvent.Action.RUN_COMMAND,
                if (simple) messages.acceptCommand else "${messages.acceptCommand} ${request.uniqueId}"
            )
        val messageReceiver = TextComponent(
            getFormatMessage("Received.Request", request.sender.name, printT(request.value)) + " "
        )
        request.receiver.spigot().sendMessage(messageReceiver, messageAccept)
        val duration = config.getOptionalDuration("Request.$root.Duration")
        if (duration != null)
            time.runTaskLater(duration) {
                if (request.uniqueId in requests)
                    timeOut(request)
            }
        glog.mid("Sent", request.uniqueId)
    }

    operator fun plusAssign(request: Request<T>) = call(request, false)


    fun simpleCall(request: Request<T>) {
        requests.values.firstOrNull { it.sender == request.sender }?.let {
            cancel(request)
            return
        }
        requests.values.firstOrNull { it.sender == request.receiver && it.receiver == request.sender }
            ?.let {
                accept(request)
                return
            }
        call(request, true)
    }

    private fun accept(request: Request<T>) {
        request.sender.sendMessage(getFormatMessage("Sent.Accept", request.receiver.name))
        request.receiver.sendMessage(getFormatMessage("Received.Accept", request.sender.name))
        requests.remove(request.uniqueId)
        onAccept(request)
        glog.mid("Accepted", request.uniqueId)
    }

    fun accept(p: Player, uniqueId: UUID) {
        val request = requests[uniqueId]
        if (request == null || p != request.receiver)
            p.sendMessage(getMessage("Error.NotFound"))
        else
            accept(request)
    }

    private fun cancel(request: Request<T>) {
        request.sender.sendMessage(getFormatMessage("Sent.Cancel", request.receiver.name))
        request.receiver.sendMessage(getFormatMessage("Received.Cancel", request.sender.name))
        requests.remove(request.uniqueId)
        onCancel(request)
        glog.mid("Cancelled", request.uniqueId)
    }

    fun cancel(p: Player, uniqueId: UUID) {
        val request = requests[uniqueId]
        if (request == null || p != request.sender)
            p.sendMessage(getMessage("Error.NotFound"))
        else
            cancel(request)
    }

    private fun timeOut(request: Request<T>) {
        request.sender.sendMessage(getFormatMessage("TimedOut", request.receiver.name))
        request.receiver.sendMessage(getFormatMessage("TimedOut", request.sender.name))
        requests.remove(request.uniqueId)
        onCancel(request)
        glog.mid("Timed out", request.uniqueId)
    }

    fun quietRemove(p: Player) = requests.values.filter { it.sender == p || it.receiver == p }
        .forEach { requests.remove(it.uniqueId) }

}

data class RequestMessages(val name: String, val acceptCommand: String, val cancelCommand: String)

data class Request<out T>(val sender: Player, val receiver: Player, val value: T) {
    val uniqueId: UUID = UUID.randomUUID()

    init {
        glog.low("Created request", uniqueId, sender, receiver, value)
    }
}