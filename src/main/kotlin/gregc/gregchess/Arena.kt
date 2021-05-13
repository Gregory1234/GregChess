package gregc.gregchess

import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.entity.Player
import org.bukkit.generator.ChunkGenerator
import java.util.*

abstract class Arena(val name: String, private val resourcePackPath: String? = null) {
    abstract val defaultData: PlayerData
    abstract val spectatorData: PlayerData
    abstract val worldGen: ChunkGenerator
    abstract val setSettings: World.() -> Unit
    private var reserved = false

    private val data: MutableMap<UUID, PlayerData> = mutableMapOf()

    private var worldCreated: Boolean = false
    val world by lazy {
        worldCreated = true
        if (GregInfo.server.getWorld(name) != null) {
            //glog.warn("World already exists!", name)
            return@lazy GregInfo.server.getWorld(name)!!
        }

        GregInfo.server.createWorld(WorldCreator(name).generator(worldGen))
        glog.io("Created arena", name)
        GregInfo.server.getWorld(name)!!.apply(setSettings)
    }

    fun teleport(p: Player) {
        if (p.uniqueId !in data)
            data[p.uniqueId] = p.playerData
        p.playerData = defaultData
        p.teleport(world.spawnLocation)
        p.sendMessage(ConfigManager.getFormatString("Message.Teleported", name))
        glog.mid("Teleported", p.name, "to arena", name)
        setResourcePack(p)
    }

    fun teleportSpectator(p: Player) {
        if (p.uniqueId !in data)
            data[p.uniqueId] = p.playerData
        p.playerData = spectatorData
        p.teleport(world.spawnLocation)
        p.sendMessage(ConfigManager.getFormatString("Message.Teleported", name))
        glog.mid("Teleported spectator", p.name, "to arena", name)
        setResourcePack(p)
    }

    private fun setResourcePack(p: Player) {
        resourcePackPath?.let {
            glog.io(ConfigManager.getOptionalString(resourcePackPath))
            ConfigManager.getOptionalString(resourcePackPath)?.let {
                val h = ConfigManager.getHexString(resourcePackPath + "Hash")
                if (h == null)
                    p.setResourcePack(it)
                else
                    p.setResourcePack(it, h)
            }
        }
    }

    fun exit(p: Player) {
        try {
            p.playerData = data[p.uniqueId]!!
            data.remove(p.uniqueId)
            resourcePackPath?.let {
                ConfigManager.getOptionalString(resourcePackPath)?.let {
                    glog.io(ConfigManager.getString("EmptyResourcePack"))
                    p.setResourcePack(
                        ConfigManager.getString("EmptyResourcePack"),
                        hexToBytes("6202c61ae5d659ea7a9772aa1cde15cc3614494d")!!
                    )
                }
            }
            glog.mid("Teleported", p.name, "out of arena", name)
        } catch (e: NullPointerException) {
            p.sendMessage(ConfigManager.getError("TeleportFailed"))
            p.teleport(Bukkit.getServer().getWorld("world")?.spawnLocation ?: p.world.spawnLocation)
            e.printStackTrace()
        }
    }

    fun isEmpty() = world.players.isEmpty()
    fun isAvailable() = isEmpty() && !reserved

    fun reserve() {
        reserved = true
        glog.low("Reserved", name)
    }

    fun clear() {
        world.players.forEach(::exit)
        reserved = false
        glog.low("Cleared", name)
    }

    override fun toString() = "Arena(name = ${world.name})"

    fun safeExit(p: Player) {
        p.playerData = data[p.uniqueId]!!
        data.remove(p.uniqueId)
        resourcePackPath?.let {
            p.setResourcePack(
                ConfigManager.getString("EmptyResourcePack"),
                hexToBytes("6202c61ae5d659ea7a9772aa1cde15cc3614494d")!!
            )
        }
    }

}