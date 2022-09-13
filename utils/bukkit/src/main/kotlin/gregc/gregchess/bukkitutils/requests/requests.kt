package gregc.gregchess.bukkitutils.requests

import gregc.gregchess.bukkitutils.*
import gregc.gregchess.bukkitutils.player.BukkitHuman
import kotlinx.coroutines.*
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.configuration.ConfigurationSection
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
) {
    private val requests = mutableMapOf<UUID, Request>()
    private val section get() = config.getConfigurationSection("Request.$name")!!

    private fun BukkitHuman.sendCommandMessage(msg: String, action: String, command: String) {
        sendMessage(textComponent {
            text(msg.chatColor())
            text(" ")
            text(action.chatColor()) {
                onClickCommand(command)
            }
        })
    }

    private fun call(request: Request) {
        if (config.getBoolean("Request.SelfAccept", true) && request.sender.uuid == request.receiver.uuid) {
            request.cont.resume(RequestResponse.ACCEPT)
            return
        }
        requests[request.uuid] = request
        request.sender.sendCommandMessage(
            section.getPathString("Sent.Request"),
            config.getPathString("Request.Cancel"),
            "$cancelCommand ${request.uuid}"
        )
        request.receiver.sendCommandMessage(
            section.getPathString("Received.Request", request.sender.name, request.value),
            config.getPathString("Request.Accept"),
            "$acceptCommand ${request.uuid}"
        )
        val duration = section.getString("Duration")?.toDuration()
        if (duration != null)
            coroutineScope.launch {
                delay(duration)
                if (request.uuid in requests)
                    expire(request)
            }
    }

    suspend fun call(request: RequestData): RequestResponse = suspendCoroutine {
        call(Request(request.sender, request.receiver, request.value, it))
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

    fun accept(p: BukkitHuman, uuid: UUID) = accept(p.uuid, uuid)

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

    fun cancel(p: BukkitHuman, uuid: UUID) = cancel(p.uuid, uuid)

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

    fun quietRemove(p: BukkitHuman) = quietRemove(p.uuid)
    fun quietRemove(p: OfflinePlayer) = quietRemove(p.uniqueId)
}

enum class RequestResponse {
    ACCEPT, CANCEL, EXPIRED, QUIT
}

data class RequestData(val sender: BukkitHuman, val receiver: BukkitHuman, val value: String)

class Request(val sender: BukkitHuman, val receiver: BukkitHuman, val value: String, val cont: Continuation<RequestResponse>) {
    val uuid: UUID = UUID.randomUUID()
}