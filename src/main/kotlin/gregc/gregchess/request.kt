package gregc.gregchess

import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import java.util.*

object RequestManager : Listener {
    private val requestTypes = mutableListOf<RequestType<*>>()

    fun start() {
        GregInfo.registerListener(this)
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
    private var onCancel: (Request<T>) -> Unit = {}

    fun register() =
        RequestManager.register(RequestType(messages, validateSender, printT, onAccept, onCancel))

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
    private val messages: RequestMessages,
    private inline val validateSender: (Player) -> Boolean = { true },
    private inline val printT: (T) -> String = { it.toString() },
    private inline val onAccept: (Request<T>) -> Unit,
    private inline val onCancel: (Request<T>) -> Unit
) {
    private val requests = mutableMapOf<UUID, Request<T>>()
    private val root = messages.name

    private val view = ConfigManager.getView("Request.$root")

    private fun call(request: Request<T>, simple: Boolean) {
        if (!validateSender(request.sender)) {
            request.sender.sendMessage(view.getString("Error.CannotSend"))
            glog.mid("Invalid sender", request.uniqueId)
            return
        }
        if ((simple || ConfigManager.getBool("Request.SelfAccept", true))
            && request.sender == request.receiver
        ) {
            glog.mid("Self request", request.uniqueId)
            onAccept(request)
            return
        }
        requests[request.uniqueId] = request
        request.sender.spigot().sendMessage(buildTextComponent {
            append(view.getString("Sent.Request") + " ")
            append(
                ConfigManager.getString("Request.Cancel"), ClickEvent(
                    ClickEvent.Action.RUN_COMMAND,
                    if (simple) messages.cancelCommand else "${messages.cancelCommand} ${request.uniqueId}"
                )
            )
        })
        request.receiver.spigot().sendMessage(buildTextComponent {
            append(
                view.getFormatString(
                    "Received.Request",
                    request.sender.name,
                    printT(request.value)
                ) + " "
            )
            append(
                ClickEvent(
                    ClickEvent.Action.RUN_COMMAND,
                    if (simple) messages.acceptCommand else "${messages.acceptCommand} ${request.uniqueId}"
                )
            )
        })
        val duration = ConfigManager.getOptionalDuration("Request.$root.Duration")
        if (duration != null)
            TimeManager.runTaskLater(duration) {
                if (request.uniqueId in requests)
                    timeOut(request)
            }
        glog.mid("Sent", request.uniqueId)
    }

    operator fun plusAssign(request: Request<T>) = call(request, false)


    fun simpleCall(request: Request<T>) {
        requests.values.firstOrNull { it.sender == request.sender }?.let {
            cancel(it)
            return
        }
        requests.values.firstOrNull { it.sender == request.receiver && it.receiver == request.sender }
            ?.let {
                accept(it)
                return
            }
        call(request, true)
    }

    private fun accept(request: Request<T>) {
        request.sender.sendMessage(view.getFormatString("Sent.Accept", request.receiver.name))
        request.receiver.sendMessage(view.getFormatString("Received.Accept", request.sender.name))
        requests.remove(request.uniqueId)
        onAccept(request)
        glog.mid("Accepted", request.uniqueId)
    }

    fun accept(p: Player, uniqueId: UUID) {
        val request = requests[uniqueId]
        if (request == null || p != request.receiver)
            p.sendMessage(view.getString("Error.NotFound"))
        else
            accept(request)
    }

    private fun cancel(request: Request<T>) {
        request.sender.sendMessage(view.getFormatString("Sent.Cancel", request.receiver.name))
        request.receiver.sendMessage(view.getFormatString("Received.Cancel", request.sender.name))
        requests.remove(request.uniqueId)
        onCancel(request)
        glog.mid("Cancelled", request.uniqueId)
    }

    fun cancel(p: Player, uniqueId: UUID) {
        val request = requests[uniqueId]
        if (request == null || p != request.sender)
            p.sendMessage(view.getString("Error.NotFound"))
        else
            cancel(request)
    }

    private fun timeOut(request: Request<T>) {
        request.sender.sendMessage(view.getFormatString("TimedOut", request.receiver.name))
        request.receiver.sendMessage(view.getFormatString("TimedOut", request.sender.name))
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