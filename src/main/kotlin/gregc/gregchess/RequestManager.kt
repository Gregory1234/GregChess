package gregc.gregchess

import gregc.gregchess.chess.HumanPlayer
import gregc.gregchess.chess.human
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin
import java.util.*
import kotlin.collections.set

interface RequestManager {
    fun <T> register(requestType: RequestType<T>): RequestType<T>
}

class BukkitRequestManager(private val plugin: Plugin) : Listener, RequestManager {
    private val requestTypes = mutableListOf<RequestType<*>>()

    fun start() {
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }

    override fun <T> register(requestType: RequestType<T>): RequestType<T> {
        requestTypes.add(requestType)
        return requestType
    }

    @EventHandler
    fun onPlayerQuit(e: PlayerQuitEvent) {
        requestTypes.forEach {
            it.quietRemove(e.player.human)
        }
    }

}

class RequestTypeBuilder<T> internal constructor() {
    lateinit var messages: RequestMessages
    var validateSender: (HumanPlayer) -> Boolean = { true }
    var printT: (T) -> String = { it.toString() }
    var onAccept: (Request<T>) -> Unit = {}
    var onCancel: (Request<T>) -> Unit = {}

    fun messagesSimple(
        view: Config.Request.RequestType,
        acceptCommand: String,
        cancelCommand: String
    ): RequestTypeBuilder<T> {
        messages = RequestMessages(view, acceptCommand, cancelCommand)
        return this
    }
}

fun <T> buildRequestType(
    t: TimeManager,
    m: RequestManager,
    f: RequestTypeBuilder<T>.() -> Unit
): RequestType<T> = RequestTypeBuilder<T>().run{
    f()
    m.register(RequestType(t, messages, validateSender, printT, onAccept, onCancel))
}


class RequestType<in T>(
    private val timeManager: TimeManager,
    private val messages: RequestMessages,
    private inline val validateSender: (HumanPlayer) -> Boolean = { true },
    private inline val printT: (T) -> String = { it.toString() },
    private inline val onAccept: (Request<T>) -> Unit,
    private inline val onCancel: (Request<T>) -> Unit
) {
    private val requests = mutableMapOf<UUID, Request<T>>()
    private val view get() = messages.view

    private fun call(request: Request<T>, simple: Boolean) {
        if (!validateSender(request.sender)) {
            request.sender.sendMessage(view.error.cannotSend)
            glog.mid("Invalid sender", request.uniqueId)
            return
        }
        if ((simple || Config.request.selfAccept)
            && request.sender == request.receiver
        ) {
            glog.mid("Self request", request.uniqueId)
            onAccept(request)
            return
        }
        requests[request.uniqueId] = request
        request.sender.sendCommandMessage(
            view.sent.request + " ",
            Config.request.cancel,
            if (simple) messages.cancelCommand else "${messages.cancelCommand} ${request.uniqueId}"
        )
        request.receiver.sendCommandMessage(
            view.received.getRequest(request.sender.name, printT(request.value)) + " ",
            Config.request.accept,
            if (simple) messages.acceptCommand else "${messages.acceptCommand} ${request.uniqueId}"
        )
        val duration = view.duration
        if (duration != null)
            timeManager.runTaskLater(duration) {
                if (request.uniqueId in requests)
                    expire(request)
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
        request.sender.sendMessage(view.sent.getAccept(request.receiver.name))
        request.receiver.sendMessage(view.received.getAccept(request.sender.name))
        requests.remove(request.uniqueId)
        onAccept(request)
        glog.mid("Accepted", request.uniqueId)
    }

    fun accept(p: HumanPlayer, uniqueId: UUID) {
        val request = requests[uniqueId]
        if (request == null || p != request.receiver)
            p.sendMessage(view.error.notFound)
        else
            accept(request)
    }

    private fun cancel(request: Request<T>) {
        request.sender.sendMessage(view.sent.getCancel(request.receiver.name))
        request.receiver.sendMessage(view.received.getCancel(request.sender.name))
        requests.remove(request.uniqueId)
        onCancel(request)
        glog.mid("Cancelled", request.uniqueId)
    }

    fun cancel(p: HumanPlayer, uniqueId: UUID) {
        val request = requests[uniqueId]
        if (request == null || p != request.sender)
            p.sendMessage(view.error.notFound)
        else
            cancel(request)
    }

    private fun expire(request: Request<T>) {
        request.sender.sendMessage(view.getExpired(request.receiver.name))
        request.receiver.sendMessage(view.getExpired(request.sender.name))
        requests.remove(request.uniqueId)
        onCancel(request)
        glog.mid("Expired", request.uniqueId)
    }

    fun quietRemove(p: HumanPlayer) = requests.values.filter { it.sender == p || it.receiver == p }
        .forEach { requests.remove(it.uniqueId) }

}

data class RequestMessages(val view: Config.Request.RequestType, val acceptCommand: String, val cancelCommand: String)

data class Request<out T>(val sender: HumanPlayer, val receiver: HumanPlayer, val value: T) {
    val uniqueId: UUID = UUID.randomUUID()

    init {
        glog.low("Created request", uniqueId, sender, receiver, value)
    }
}