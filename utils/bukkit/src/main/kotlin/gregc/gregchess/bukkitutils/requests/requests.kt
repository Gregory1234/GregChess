package gregc.gregchess.bukkitutils.requests

import gregc.gregchess.bukkitutils.*
import gregc.gregchess.bukkitutils.player.BukkitPlayer
import gregc.gregchess.bukkitutils.player.DefaultBukkitPlayer
import kotlinx.coroutines.*
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin
import java.util.*
import kotlin.coroutines.*


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
) { // TODO: redesign this api
    private val requests = mutableMapOf<UUID, Request>()
    private val section get() = config.getConfigurationSection("Request.$name")!!

    suspend fun invalidSender(s: Player) = invalidSender(DefaultBukkitPlayer(s))

    suspend inline fun invalidSender(s: Player, block: () -> Boolean) = invalidSender(DefaultBukkitPlayer(s), block)

    suspend fun invalidSender(s: BukkitPlayer) {
        s.sendMessage(section.getPathString("CannotSend"))
        return suspendCoroutine { }
    }

    suspend inline fun invalidSender(s: BukkitPlayer, block: () -> Boolean) {
        if (block())
            invalidSender(s)
    }

    private fun BukkitPlayer.sendCommandMessage(msg: String, action: String, command: String) {
        sendMessage(textComponent {
            text(msg.chatColor())
            text(" ")
            text(action.chatColor()) {
                onClickCommand(command)
            }
        })
    }

    private fun call(request: Request, simple: Boolean) {
        if ((simple || config.getBoolean("Request.SelfAccept", true)) && request.sender.uuid == request.receiver.uuid) {
            request.cont.resume(RequestResponse.ACCEPT)
            return
        }
        requests[request.uuid] = request
        request.sender.sendCommandMessage(
            section.getPathString("Sent.Request"),
            config.getPathString("Request.Cancel"),
            if (simple) cancelCommand else "$cancelCommand ${request.uuid}"
        )
        request.receiver.sendCommandMessage(
            section.getPathString("Received.Request", request.sender.name, request.value),
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
        val req = Request(request.sender, request.receiver, request.value, it)
        if (simple) {
            simpleCall(req)
        } else {
            this += req
        }
    }

    private fun simpleCall(request: Request) {
        requests.values.firstOrNull { it.sender.uuid == request.sender.uuid }?.let {
            cancel(it)
            return
        }
        requests.values.firstOrNull { it.sender.uuid == request.receiver.uuid && it.receiver.uuid == request.sender.uuid }?.let {
            accept(it)
            return
        }
        call(request, true)
    }

    private fun accept(request: Request) {
        request.sender.sendMessage(section.getPathString("Sent.Accept", request.receiver.name))
        request.receiver.sendMessage(section.getPathString("Received.Accept", request.sender.name))
        requests.remove(request.uuid)
        request.cont.resume(RequestResponse.ACCEPT)
    }

    fun accept(p: UUID, uuid: UUID) {
        val request = requests[uuid]
        if (request == null || p != request.receiver.uuid)
            Bukkit.getPlayer(p)?.sendMessage(section.getPathString("Error.NotFound"))
        else
            accept(request)
    }

    fun accept(p: BukkitPlayer, uuid: UUID) = accept(p.uuid, uuid)
    fun accept(p: OfflinePlayer, uuid: UUID) = accept(p.uniqueId, uuid)

    private fun cancel(request: Request) {
        request.sender.sendMessage(section.getPathString("Sent.Cancel", request.receiver.name))
        request.receiver.sendMessage(section.getPathString("Received.Cancel", request.sender.name))
        requests.remove(request.uuid)
        request.cont.resume(RequestResponse.CANCEL)
    }

    fun cancel(p: UUID, uuid: UUID) {
        val request = requests[uuid]
        if (request == null || p != request.sender.uuid)
            Bukkit.getPlayer(p)?.sendMessage(section.getPathString("Error.NotFound"))
        else
            cancel(request)
    }

    fun cancel(p: BukkitPlayer, uuid: UUID) = cancel(p.uuid, uuid)
    fun cancel(p: OfflinePlayer, uuid: UUID) = cancel(p.uniqueId, uuid)

    private fun expire(request: Request) {
        request.sender.sendMessage(section.getPathString("Expired", request.receiver.name))
        request.receiver.sendMessage(section.getPathString("Expired", request.sender.name))
        requests.remove(request.uuid)
        request.cont.resume(RequestResponse.EXPIRED)
    }

    fun quietRemove(p: UUID) {
        for (r in requests.values) {
            if (r.sender.uuid == p || r.receiver.uuid == p) {
                requests.remove(r.uuid)
                r.cont.resume(RequestResponse.QUIT)
            }
        }
    }

    fun quietRemove(p: BukkitPlayer) = quietRemove(p.uuid)
    fun quietRemove(p: OfflinePlayer) = quietRemove(p.uniqueId)
}

enum class RequestResponse {
    ACCEPT, CANCEL, EXPIRED, QUIT
}

data class RequestData(val sender: BukkitPlayer, val receiver: BukkitPlayer, val value: String)

class Request(val sender: BukkitPlayer, val receiver: BukkitPlayer, val value: String, val cont: Continuation<RequestResponse>) {
    val uuid: UUID = UUID.randomUUID()
}