package gregc.gregchess

import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.generator.ChunkGenerator
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.scoreboard.Scoreboard
import java.lang.IllegalArgumentException
import java.time.Duration
import java.util.*
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.math.floor
import kotlin.math.roundToLong

data class PlayerData(
    val location: Location? = null,
    val inventory: List<ItemStack?> = List(41) { null },
    val gameMode: GameMode = GameMode.SURVIVAL,
    val health: Double = 20.0,
    val foodLevel: Int = 20,
    val saturation: Float = 20.0F,
    val level: Int = 0,
    val exp: Float = 0.0F,
    val allowFlight: Boolean = false,
    val isFlying: Boolean = false,
    val effects: List<PotionEffect> = emptyList(),
    val scoreboard: Scoreboard = Bukkit.getScoreboardManager()!!.mainScoreboard
)

var Player.playerData: PlayerData
    get() = PlayerData(
        location.clone(),
        inventory.contents.toList(),
        gameMode,
        health,
        foodLevel,
        saturation,
        level,
        exp,
        allowFlight,
        isFlying,
        activePotionEffects.toList(),
        scoreboard
    )
    set(d) {
        inventory.contents = d.inventory.toTypedArray()
        gameMode = d.gameMode
        health = d.health
        foodLevel = d.foodLevel
        saturation = d.saturation
        level = level
        exp = d.exp
        allowFlight = d.allowFlight
        isFlying = d.isFlying
        activePotionEffects.forEach { removePotionEffect(it.type) }
        d.effects.forEach(::addPotionEffect)
        d.location?.let(::teleport)
        scoreboard = d.scoreboard
    }

fun Location.playSound(s: Sound, volume: Float = 3.0f, pitch: Float = 1.0f) =
    world?.playSound(this, s, volume, pitch)


abstract class Arena(
    val config: ConfigManager,
    val name: String,
    val resourcePackPath: String? = null
) {
    abstract val defaultData: PlayerData
    abstract val spectatorData: PlayerData
    abstract val worldGen: ChunkGenerator
    abstract val setSettings: World.() -> Unit

    val scoreboard by lazy {
        Bukkit.getScoreboardManager()!!.newScoreboard
    }

    private val data: MutableMap<UUID, PlayerData> = mutableMapOf()

    private var worldCreated: Boolean = false
    val world by lazy {
        worldCreated = true
        if (GregChessInfo.server.getWorld(name) != null) {
            Bukkit.getLogger().warning("World already exists!")
            return@lazy GregChessInfo.server.getWorld(name)!!
        }

        GregChessInfo.server.createWorld(WorldCreator(name).generator(worldGen))

        GregChessInfo.server.getWorld(name)!!.apply(setSettings)
    }

    fun teleport(p: Player) {
        data[p.uniqueId] = p.playerData
        p.playerData = defaultData
        p.teleport(world.spawnLocation)
        p.scoreboard = scoreboard
        p.sendMessage(config.getString("Message.Teleported").replace("$1", name))
        setResourcePack(p)
    }

    fun teleportSpectator(p: Player) {
        data[p.uniqueId] = p.playerData
        p.playerData = spectatorData
        p.teleport(world.spawnLocation)
        p.scoreboard = scoreboard
        p.sendMessage(config.getString("Message.Teleported").replace("$1", name))
        setResourcePack(p)
    }

    private fun setResourcePack(p: Player) {
        resourcePackPath?.let {
            glog.io(config.getOptionalString(resourcePackPath))
            config.getOptionalString(resourcePackPath)?.let {
                val h = config.getHexString(resourcePackPath + "Hash")
                if (h == null)
                    p.setResourcePack(it)
                else
                    p.setResourcePack(it, h)
            }
        }
    }

    fun exit(p: Player) {
        p.playerData = data[p.uniqueId]!!
        data.remove(p.uniqueId)
        resourcePackPath?.let {
            glog.io(config.getString("EmptyResourcePack"))
            p.setResourcePack(
                config.getString("EmptyResourcePack"),
                hexToBytes("6202c61ae5d659ea7a9772aa1cde15cc3614494d")!!
            )
        }
    }

    fun isEmpty() = world.players.isEmpty()
    fun delete() {
        if (!worldCreated)
            return
        val folder = world.worldFolder
        Bukkit.unloadWorld(world, false)
        folder.deleteRecursively()
    }

    override fun toString() = "Arena(name = ${world.name})"
    fun clearScoreboard() {
        scoreboard.teams.forEach { it.unregister() }
        scoreboard.objectives.forEach { it.unregister() }
    }
}

fun JavaPlugin.addCommand(
    config: ConfigManager,
    name: String,
    command: (CommandSender, Array<String>) -> Unit
) {
    getCommand(name)?.setExecutor { sender, _, _, args ->
        try {
            command(sender, args)
        } catch (e: CommandException) {
            sender.sendMessage(config.getError(e.playerMsg))
            sender.sendMessage(config.getError(e.playerMsg))
        }
        true
    }
}

fun JavaPlugin.addCommandTab(
    name: String,
    tabCompleter: (CommandSender, Array<String>) -> List<String>?
) {
    getCommand(name)?.setTabCompleter { sender, _, _, args ->
        tabCompleter(sender, args)?.toMutableList()
    }
}

data class Loc(val x: Int, val y: Int, val z: Int) {
    fun toLocation(w: World) = Location(w, x.toDouble(), y.toDouble(), z.toDouble())

    companion object {
        fun fromLocation(l: Location) = Loc(l.x.toInt(), l.y.toInt(), l.z.toInt())
    }
}

fun World.getBlockAt(l: Loc) = getBlockAt(l.x, l.y, l.z)
val Block.loc: Loc
    get() = Loc(x, y, z)

class CommandException(val playerMsg: String) : Exception() {
    override val message: String
        get() = "Uncaught command error: $playerMsg"
}

@ExperimentalContracts
fun <T> commandRequireNotNull(e: T?, msg: String) {
    contract {
        returns() implies (e != null)
    }
    if (e == null) throw CommandException(msg)
}

@ExperimentalContracts
fun commandRequirePlayer(e: CommandSender, msg: String = "NotPlayer") {
    contract {
        returns() implies (e is Player)
    }
    if (e !is Player) throw CommandException(msg)
}

fun commandRequireArgumentsGeneral(
    e: Array<String>,
    lower: Int = 0, upper: Int = Int.MAX_VALUE,
    msg: String = "WrongArgumentsNumber"
) {
    if (e.size !in lower..upper) throw CommandException(msg)
}

fun commandRequireArguments(e: Array<String>, num: Int, msg: String = "WrongArgumentsNumber") =
    commandRequireArgumentsGeneral(e, num, num, msg)

fun commandRequireArgumentsMin(e: Array<String>, min: Int, msg: String = "WrongArgumentsNumber") =
    commandRequireArgumentsGeneral(e, min, msg = msg)

fun commandRequirePermission(e: CommandSender, permission: String, msg: String = "NoPerms") {
    if (!e.hasPermission(permission)) throw CommandException(msg)
}

fun chatColor(s: String): String = ChatColor.translateAlternateColorCodes('&', s)


fun rotationsOf(x: Int, y: Int): List<Pair<Int, Int>> =
    listOf(x to y, x to -y, -x to y, -x to -y, y to x, -y to x, y to -x, -y to -x).distinct()

fun between(i: Int, j: Int): IntRange = if (i > j) (j + 1 until i) else (i + 1 until j)

fun Int.towards(goal: Int, amount: Int) =
    if (goal < this) this - amount
    else this + amount

fun <T, U, R> Iterable<T>.star(other: Iterable<U>, function: (T, U) -> R): List<R> =
    flatMap { x -> other.map { y -> function(x, y) } }

operator fun Pair<Int, Int>.times(m: Int) = Pair(m * first, m * second)

fun hexToBytes(hex: String): ByteArray? {
    if (hex.length % 2 == 1) return null
    val ret = ByteArray(hex.length / 2)
    try {
        for (i in hex.indices step 2) {
            ret[i / 2] = hex.substring(i..i + 1).toInt(16).toByte()
        }
    } catch (e: IllegalArgumentException) {
        return null
    }
    return ret
}

fun Duration.toTicks(): Long = toMillis() / 50

val Int.seconds: Duration
    get() = Duration.ofSeconds(toLong())
val Int.ticks: Duration
    get() = Duration.ofMillis(toLong() * 50)
val Long.minutes: Duration
    get() = Duration.ofMinutes(this)
val Long.ticks: Duration
    get() = Duration.ofMillis(this * 50)
val Double.seconds: Duration
    get() = Duration.ofNanos(floor(this * 1000000000L).toLong())
val Double.milliseconds: Duration
    get() = Duration.ofNanos(floor(this * 1000000L).toLong())
val Double.minutes: Duration
    get() = Duration.ofNanos(floor(this * 60000000000L).toLong())

fun parseDuration(s: String): Duration? {
    val match1 = Regex("""^(-|\+|)(\d+(?:\.\d+)?)(s|ms|t|m)$""").find(s)
    if (match1 != null) {
        val amount =
            (match1.groupValues[2].toDoubleOrNull()
                ?: return null) * (if (match1.groupValues[1] == "-") -1 else 1)
        return when (match1.groupValues[3]) {
            "s" -> amount.seconds
            "ms" -> amount.milliseconds
            "t" -> amount.roundToLong().ticks
            "m" -> amount.minutes
            else -> null
        }
    }
    val match2 = Regex("""^(-)?(\d+):(\d{2,}(?:\.\d)?)$""").find(s)
    if (match2 != null) {
        val sign = (if (match2.groupValues[1] == "-") -1 else 1)
        val minutes = (match2.groupValues[2].toLongOrNull() ?: return null) * sign
        val seconds = (match2.groupValues[3].toDoubleOrNull() ?: return null) * sign
        return minutes.minutes + seconds.seconds
    }
    return null
}

val glog = GregLogger(GregChessInfo.logger)

object GregChessInfo {
    val server by lazy { Bukkit.getServer() }
    val logger
        get() = Bukkit.getPluginManager().getPlugin("GregChess")?.logger!!
}