package gregc.gregchess.bukkit

import gregc.gregchess.asDurationOrNull
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import java.util.*
import kotlin.collections.set
import kotlin.coroutines.*

object RequestManager : Listener {

    private val requestTypes = mutableListOf<RequestType>()

    fun start() {
        registerEvents()
    }

    fun register(name: String, accept: String, cancel: String): RequestType {
        val requestType = RequestType(name, accept, cancel)
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

class RequestType(val name: String, private val acceptCommand: String, private val cancelCommand: String) {
    private val requests = mutableMapOf<UUID, Request>()
    private val section get() = config.getConfigurationSection("Request.$name")!!

    suspend fun invalidSender(s: Player) {
        s.sendMessage(section.getLocalizedString("CannotSend"))
        return suspendCoroutine { }
    }

    suspend inline fun invalidSender(s: Player, block: () -> Boolean) {
        if (block())
            invalidSender(s)
    }

    private fun call(request: Request, simple: Boolean) {
        if ((simple || config.getBoolean("Request.SelfAccept", true)) && request.sender == request.receiver) {
            request.cont.resume(RequestResponse.ACCEPT)
            return
        }
        requests[request.uuid] = request
        request.sender.sendCommandMessage(
            section.getLocalizedString("Sent.Request"),
            config.getLocalizedString("Request.Cancel"),
            if (simple) cancelCommand else "$cancelCommand ${request.uuid}"
        )
        request.receiver.sendCommandMessage(
            section.getLocalizedString("Received.Request", request.sender.name, request.value),
            config.getLocalizedString("Request.Accept"),
            if (simple) acceptCommand else "$acceptCommand ${request.uuid}"
        )
        val duration = section.getString("Duration")?.asDurationOrNull()
        if (duration != null)
            BukkitTimeManager.runTaskLater(duration) {
                if (request.uuid in requests)
                    expire(request)
            }
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
        request.sender.sendMessage(section.getLocalizedString("Sent.Accept", request.receiver.name))
        request.receiver.sendMessage(section.getLocalizedString("Received.Accept", request.sender.name))
        requests.remove(request.uuid)
        request.cont.resume(RequestResponse.ACCEPT)
    }

    fun accept(p: Player, uuid: UUID) {
        val request = requests[uuid]
        if (request == null || p != request.receiver)
            p.sendMessage(section.getLocalizedString("Error.NotFound"))
        else
            accept(request)
    }

    private fun cancel(request: Request) {
        request.sender.sendMessage(section.getLocalizedString("Sent.Cancel", request.receiver.name))
        request.receiver.sendMessage(section.getLocalizedString("Received.Cancel", request.sender.name))
        requests.remove(request.uuid)
        request.cont.resume(RequestResponse.CANCEL)
    }

    fun cancel(p: Player, uuid: UUID) {
        val request = requests[uuid]
        if (request == null || p != request.sender)
            p.sendMessage(section.getLocalizedString("Error.NotFound"))
        else
            cancel(request)
    }

    private fun expire(request: Request) {
        request.sender.sendMessage(section.getLocalizedString("Expired", request.receiver.name))
        request.receiver.sendMessage(section.getLocalizedString("Expired", request.sender.name))
        requests.remove(request.uuid)
        request.cont.resume(RequestResponse.EXPIRED)
    }

    fun quietRemove(p: Player) = requests.values.filter { it.sender == p || it.receiver == p }.forEach {
        requests.remove(it.uuid)
        it.cont.resume(RequestResponse.QUIT)
    }

}

enum class RequestResponse {
    ACCEPT, CANCEL, EXPIRED, QUIT
}

data class RequestData(val sender: Player, val receiver: Player, val value: String)

class Request(val sender: Player, val receiver: Player, val value: String, val cont: Continuation<RequestResponse>) {
    val uuid: UUID = UUID.randomUUID()
}