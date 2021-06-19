package gregc.gregchess

import gregc.gregchess.chess.HumanPlayer
import java.time.Duration
import java.util.*
import kotlin.collections.set
import kotlin.coroutines.*

interface RequestConfig {
    val accept: LocalizedString
    val cancel: LocalizedString
    val selfAccept: Boolean
    fun getRequestType(t: String): RequestTypeConfig
}

interface RequestTypeConfig {

    val name: String

    fun expired(a1: String): LocalizedString
    val duration: Duration?
    val sentRequest: LocalizedString
    fun sentCancel(a1: String): LocalizedString
    fun sentAccept(a1: String): LocalizedString
    fun receivedRequest(a1: String, a2: String): LocalizedString
    fun receivedCancel(a1: String): LocalizedString
    fun receivedAccept(a1: String): LocalizedString
    val notFound: LocalizedString
    val cannotSend: LocalizedString
}

val Config.request: RequestConfig by Config

interface RequestManager {
    fun register(c: RequestTypeConfig, accept: String, cancel: String): RequestType
}

fun RequestManager.register(c: String, accept: String, cancel: String): RequestType =
    register(Config.request.getRequestType(c), accept, cancel)


class RequestType(private val timeManager: TimeManager, private val data: RequestTypeData) {
    private val requests = mutableMapOf<UUID, Request>()
    private val config get() = data.config

    suspend fun invalidSender(s: HumanPlayer) {
        glog.mid("Invalid sender", s)
        s.sendMessage(config.cannotSend)
        return suspendCoroutine { }
    }

    suspend inline fun invalidSender(s: HumanPlayer, block: () -> Boolean) {
        if (block())
            invalidSender(s)
    }

    private fun call(request: Request, simple: Boolean) {
        if ((simple || Config.request.selfAccept) && request.sender == request.receiver) {
            glog.mid("Self request", request.uniqueId)
            request.cont.resume(RequestResponse.ACCEPT)
            return
        }
        requests[request.uniqueId] = request
        request.sender.sendCommandMessage(
            config.sentRequest,
            Config.request.cancel,
            if (simple) data.cancelCommand else "${data.cancelCommand} ${request.uniqueId}"
        )
        request.receiver.sendCommandMessage(
            config.receivedRequest(request.sender.name, request.value),
            Config.request.accept,
            if (simple) data.acceptCommand else "${data.acceptCommand} ${request.uniqueId}"
        )
        val duration = config.duration
        if (duration != null)
            timeManager.runTaskLater(duration) {
                if (request.uniqueId in requests)
                    expire(request)
            }
        glog.mid("Sent", request.uniqueId)
    }

    private operator fun plusAssign(request: Request) = call(request, false)

    suspend fun call(request: RequestData, simple: Boolean = false): RequestResponse = suspendCoroutine {
        val req = Request(request.sender, request.receiver, request.value, it)
        if (simple) {
            simpleCall(req)
        } else {
            this += req
        }
    }

    private fun simpleCall(request: Request) {
        requests.values.firstOrNull { it.sender == request.sender }?.let {
            cancel(it)
            return
        }
        requests.values.firstOrNull { it.sender == request.receiver && it.receiver == request.sender }?.let {
            accept(it)
            return
        }
        call(request, true)
    }

    private fun accept(request: Request) {
        request.sender.sendMessage(config.sentAccept(request.receiver.name))
        request.receiver.sendMessage(config.receivedAccept(request.sender.name))
        requests.remove(request.uniqueId)
        glog.mid("Accepted", request.uniqueId)
        request.cont.resume(RequestResponse.ACCEPT)
    }

    fun accept(p: HumanPlayer, uniqueId: UUID) {
        val request = requests[uniqueId]
        if (request == null || p != request.receiver)
            p.sendMessage(config.notFound)
        else
            accept(request)
    }

    private fun cancel(request: Request) {
        request.sender.sendMessage(config.sentCancel(request.receiver.name))
        request.receiver.sendMessage(config.receivedCancel(request.sender.name))
        requests.remove(request.uniqueId)
        glog.mid("Cancelled", request.uniqueId)
        request.cont.resume(RequestResponse.CANCEL)
    }

    fun cancel(p: HumanPlayer, uniqueId: UUID) {
        val request = requests[uniqueId]
        if (request == null || p != request.sender)
            p.sendMessage(config.notFound)
        else
            cancel(request)
    }

    private fun expire(request: Request) {
        request.sender.sendMessage(config.expired(request.receiver.name))
        request.receiver.sendMessage(config.expired(request.sender.name))
        requests.remove(request.uniqueId)
        glog.mid("Expired", request.uniqueId)
        request.cont.resume(RequestResponse.EXPIRED)
    }

    fun quietRemove(p: HumanPlayer) = requests.values.filter { it.sender == p || it.receiver == p }.forEach {
        requests.remove(it.uniqueId)
        glog.mid("Quit", it.uniqueId)
        it.cont.resume(RequestResponse.QUIT)
    }

}

data class RequestTypeData(val config: RequestTypeConfig, val acceptCommand: String, val cancelCommand: String)

enum class RequestResponse {
    ACCEPT, CANCEL, EXPIRED, QUIT
}

data class RequestData(val sender: HumanPlayer, val receiver: HumanPlayer, val value: String)

class Request(
    val sender: HumanPlayer, val receiver: HumanPlayer, val value: String, val cont: Continuation<RequestResponse>
) {
    val uniqueId: UUID = UUID.randomUUID()

    init {
        glog.low("Created request", uniqueId, sender, receiver, value)
    }
}