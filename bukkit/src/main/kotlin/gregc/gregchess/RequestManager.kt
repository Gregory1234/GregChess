package gregc.gregchess

import gregc.gregchess.chess.HumanPlayer
import gregc.gregchess.chess.human
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin
import java.time.Duration
import java.util.*
import kotlin.collections.set
import kotlin.coroutines.*
import kotlin.reflect.KProperty

interface RequestConfig {
    val accept: String
    val cancel: String
    val selfAccept: Boolean
    fun getExpired(t: String): (String) -> String
    fun getRequestDuration(t: String): Duration?
    fun getSentRequest(t: String): String
    fun getSentCancel(t: String): (String) -> String
    fun getSentAccept(t: String): (String) -> String
    fun getReceivedRequest(t: String): (String, String) -> String
    fun getReceivedCancel(t: String): (String) -> String
    fun getReceivedAccept(t: String): (String) -> String
    fun getNotFound(t: String): String
    fun getCannotSend(t: String): String
}

operator fun RequestConfig.get(t: String) = RequestTypeConfig(t)

class RequestTypeConfig(val name: String) {

    private operator fun <R> (RequestConfig.(String) -> R).getValue(
        requestTypeConfig: RequestTypeConfig,
        property: KProperty<*>
    ): R = invoke(Config.request, name)

    val expired by RequestConfig::getExpired
    val duration by RequestConfig::getRequestDuration
    val sentRequest by RequestConfig::getSentRequest
    val sentCancel by RequestConfig::getSentCancel
    val sentAccept by RequestConfig::getSentAccept
    val receivedRequest by RequestConfig::getReceivedRequest
    val receivedCancel by RequestConfig::getReceivedCancel
    val receivedAccept by RequestConfig::getReceivedAccept
    val notFound by RequestConfig::getNotFound
    val cannotSend by RequestConfig::getCannotSend
}

val Config.request: RequestConfig by Config

interface RequestManager {
    fun register(c: RequestTypeConfig, accept: String, cancel: String): RequestType
}

fun RequestManager.register(c: String, accept: String, cancel: String): RequestType =
    register(Config.request[c], accept, cancel)

class BukkitRequestManager(private val plugin: Plugin, private val timeManager: TimeManager) : Listener,
    RequestManager {

    private val requestTypes = mutableListOf<RequestType>()

    fun start() {
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }

    override fun register(c: RequestTypeConfig, accept: String, cancel: String): RequestType {
        val requestType = RequestType(timeManager, RequestTypeData(c, accept, cancel))
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