package gregc.gregchess.bukkitutils.requests

import gregc.gregchess.bukkitutils.*
import kotlinx.coroutines.*
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.command.CommandSender
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin
import java.util.*
import kotlin.coroutines.*
import kotlin.time.ExperimentalTime


class RequestManager(private val plugin: Plugin, private val coroutineScope: CoroutineScope) : Listener {

    private val requestTypes = mutableListOf<RequestType>()

    fun start() {
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }

    fun register(name: String, accept: String, cancel: String): RequestType {
        val requestType = RequestType(plugin.config, coroutineScope, name, accept, cancel)
        requestTypes.add(requestType)
        return requestType
    }

    @EventHandler
    fun onPlayerQuit(e: PlayerQuitEvent) {
        for (type in requestTypes)
            type.quietRemove(e.player)
    }
}

class RequestType internal constructor(
    private val config: ConfigurationSection,
    private val coroutineScope: CoroutineScope,
    val name: String,
    private val acceptCommand: String,
    private val cancelCommand: String
) {
    private val requests = mutableMapOf<UUID, Request>()
    private val section get() = config.getConfigurationSection("Request.$name")!!

    suspend fun invalidSender(s: Player) {
        s.sendMessage(section.getPathString("CannotSend"))
        return suspendCoroutine { }
    }

    suspend inline fun invalidSender(s: Player, block: () -> Boolean) {
        if (block())
            invalidSender(s)
    }

    private fun CommandSender.sendCommandMessage(msg: String, action: String, command: String) {
        spigot().sendMessage(textComponent {
            text(msg.chatColor())
            text(" ")
            text(action.chatColor()) {
                onClickCommand(command)
            }
        })
    }

    @OptIn(ExperimentalTime::class)
    private fun call(request: Request, simple: Boolean) {
        if ((simple || config.getBoolean("Request.SelfAccept", true)) && request.senderUUID == request.receiverUUID) {
            request.cont.resume(RequestResponse.ACCEPT)
            return
        }
        requests[request.uuid] = request
        request.sender?.sendCommandMessage(
            section.getPathString("Sent.Request"),
            config.getPathString("Request.Cancel"),
            if (simple) cancelCommand else "$cancelCommand ${request.uuid}"
        )
        request.receiver?.sendCommandMessage(
            section.getPathString("Received.Request", request.senderName, request.value),
            config.getPathString("Request.Accept"),
            if (simple) acceptCommand else "$acceptCommand ${request.uuid}"
        )
        val duration = section.getString("Duration")?.toDuration()
        if (duration != null)
            coroutineScope.launch {
                delay(duration)
                if (request.uuid in requests)
                    expire(request)
            }
    }

    private operator fun plusAssign(request: Request) = call(request, false)

    suspend fun call(request: RequestData, simple: Boolean = false): RequestResponse = suspendCoroutine {
        val req = Request(request.senderUUID, request.receiverUUID, request.value, it)
        if (simple) {
            simpleCall(req)
        } else {
            this += req
        }
    }

    private fun simpleCall(request: Request) {
        requests.values.firstOrNull { it.senderUUID == request.senderUUID }?.let {
            cancel(it)
            return
        }
        requests.values.firstOrNull { it.senderUUID == request.receiverUUID && it.receiverUUID == request.senderUUID }?.let {
            accept(it)
            return
        }
        call(request, true)
    }

    private fun accept(request: Request) {
        request.sender?.sendMessage(section.getPathString("Sent.Accept", request.receiverName))
        request.receiver?.sendMessage(section.getPathString("Received.Accept", request.senderName))
        requests.remove(request.uuid)
        request.cont.resume(RequestResponse.ACCEPT)
    }

    fun accept(p: UUID, uuid: UUID) {
        val request = requests[uuid]
        if (request == null || p != request.receiverUUID)
            Bukkit.getPlayer(p)?.sendMessage(section.getPathString("Error.NotFound"))
        else
            accept(request)
    }

    fun accept(p: OfflinePlayer, uuid: UUID) = accept(p.uniqueId, uuid)

    private fun cancel(request: Request) {
        request.sender?.sendMessage(section.getPathString("Sent.Cancel", request.receiverName))
        request.receiver?.sendMessage(section.getPathString("Received.Cancel", request.senderName))
        requests.remove(request.uuid)
        request.cont.resume(RequestResponse.CANCEL)
    }

    fun cancel(p: UUID, uuid: UUID) {
        val request = requests[uuid]
        if (request == null || p != request.senderUUID)
            Bukkit.getPlayer(p)?.sendMessage(section.getPathString("Error.NotFound"))
        else
            cancel(request)
    }

    fun cancel(p: OfflinePlayer, uuid: UUID) = cancel(p.uniqueId, uuid)

    private fun expire(request: Request) {
        request.sender?.sendMessage(section.getPathString("Expired", request.receiverName))
        request.receiver?.sendMessage(section.getPathString("Expired", request.senderName))
        requests.remove(request.uuid)
        request.cont.resume(RequestResponse.EXPIRED)
    }

    fun quietRemove(p: UUID) {
        for (r in requests.values) {
            if (r.senderUUID == p || r.receiverUUID == p) {
                requests.remove(r.uuid)
                r.cont.resume(RequestResponse.QUIT)
            }
        }
    }

    fun quietRemove(p: OfflinePlayer) = quietRemove(p.uniqueId)
}

enum class RequestResponse {
    ACCEPT, CANCEL, EXPIRED, QUIT
}

data class RequestData(val senderUUID: UUID, val receiverUUID: UUID, val value: String)

class Request(val senderUUID: UUID, val receiverUUID: UUID, val value: String, val cont: Continuation<RequestResponse>) {
    val uuid: UUID = UUID.randomUUID()
    val senderName get() = Bukkit.getOfflinePlayer(senderUUID).name!!
    val receiverName get() = Bukkit.getOfflinePlayer(receiverUUID).name!!
    val sender get() = Bukkit.getPlayer(senderUUID)
    val receiver get() = Bukkit.getPlayer(receiverUUID)
}