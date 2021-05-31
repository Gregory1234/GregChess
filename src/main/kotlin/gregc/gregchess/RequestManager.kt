package gregc.gregchess

import gregc.gregchess.chess.HumanPlayer
import gregc.gregchess.chess.human
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import java.util.*
import kotlin.collections.set

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
        root: String,
        acceptCommand: String,
        cancelCommand: String
    ): RequestTypeBuilder<T> {
        messages = RequestMessages(root, acceptCommand, cancelCommand)
        return this
    }
}

fun <T> buildRequestType(f: RequestTypeBuilder<T>.() -> Unit): RequestType<T> = RequestTypeBuilder<T>().run{
    f()
    RequestManager.register(RequestType(messages, validateSender, printT, onAccept, onCancel))
}


class RequestType<in T>(
    private val messages: RequestMessages,
    private inline val validateSender: (HumanPlayer) -> Boolean = { true },
    private inline val printT: (T) -> String = { it.toString() },
    private inline val onAccept: (Request<T>) -> Unit,
    private inline val onCancel: (Request<T>) -> Unit
) {
    private val requests = mutableMapOf<UUID, Request<T>>()
    private val root = messages.name

    private val view get() = Config.request.requestTypes[root]!!

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
            view.getFormatString("Received.Request", request.sender.name, printT(request.value)) + " ",
            Config.request.accept,
            if (simple) messages.acceptCommand else "${messages.acceptCommand} ${request.uniqueId}"
        )
        val duration = view.duration
        if (duration != null)
            TimeManager.runTaskLater(duration) {
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
        request.sender.sendMessage(view.getFormatString("Sent.Accept", request.receiver.name))
        request.receiver.sendMessage(view.getFormatString("Received.Accept", request.sender.name))
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
        request.sender.sendMessage(view.getFormatString("Sent.Cancel", request.receiver.name))
        request.receiver.sendMessage(view.getFormatString("Received.Cancel", request.sender.name))
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
        request.sender.sendMessage(view.getFormatString("Expired", request.receiver.name))
        request.receiver.sendMessage(view.getFormatString("Expired", request.sender.name))
        requests.remove(request.uniqueId)
        onCancel(request)
        glog.mid("Expired", request.uniqueId)
    }

    fun quietRemove(p: HumanPlayer) = requests.values.filter { it.sender == p || it.receiver == p }
        .forEach { requests.remove(it.uniqueId) }

}

data class RequestMessages(val name: String, val acceptCommand: String, val cancelCommand: String)

data class Request<out T>(val sender: HumanPlayer, val receiver: HumanPlayer, val value: T) {
    val uniqueId: UUID = UUID.randomUUID()

    init {
        glog.low("Created request", uniqueId, sender, receiver, value)
    }
}