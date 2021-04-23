package gregc.gregchess

import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.generator.ChunkGenerator
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.scoreboard.Scoreboard
import java.io.File
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.contracts.contract
import kotlin.math.floor
import kotlin.math.roundToLong

data class PlayerData(
    val location: Location? = null,
    val inventory: List<ItemStack?> = List(41) { null },
    val gameMode: GameMode = GameMode.SURVIVAL,
    val health: Double = 20.0,
    val foodLevel: Int = 20, val saturation: Float = 20.0F,
    val level: Int = 0, val exp: Float = 0.0F,
    val allowFlight: Boolean = false, val isFlying: Boolean = false,
    val effects: List<PotionEffect> = emptyList(),
    val scoreboard: Scoreboard = Bukkit.getScoreboardManager()!!.mainScoreboard
)

var Player.playerData: PlayerData
    get() = PlayerData(
        location.clone(),
        inventory.contents.toList(),
        gameMode,
        health,
        foodLevel, saturation,
        level, exp,
        allowFlight, isFlying,
        activePotionEffects.toList(),
        scoreboard
    )
    set(d) {
        inventory.contents = d.inventory.toTypedArray()
        gameMode = d.gameMode
        health = d.health
        foodLevel = d.foodLevel; saturation = d.saturation
        level = d.level; exp = d.exp
        allowFlight = d.allowFlight; isFlying = d.isFlying
        activePotionEffects.forEach { removePotionEffect(it.type) }
        d.effects.forEach(::addPotionEffect)
        d.location?.let(::teleport)
        scoreboard = d.scoreboard
    }

fun World.playSound(l: Location, s: Sound) = playSound(l, s, 3.0f, 1.0f)

fun <R> Loc.doIn(w: World, f: (World, Location) -> R) = f(w, toLocation(w))


abstract class Arena(val name: String, private val resourcePackPath: String? = null) {
    abstract val defaultData: PlayerData
    abstract val spectatorData: PlayerData
    abstract val worldGen: ChunkGenerator
    abstract val setSettings: World.() -> Unit
    private var reserved = false

    val scoreboard by lazy {
        Bukkit.getScoreboardManager()!!.newScoreboard
    }

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
        p.scoreboard = scoreboard
        p.sendMessage(ConfigManager.getFormatString("Message.Teleported", name))
        glog.mid("Teleported", p.name, "to arena", name)
        setResourcePack(p)
    }

    fun teleportSpectator(p: Player) {
        if (p.uniqueId !in data)
            data[p.uniqueId] = p.playerData
        p.playerData = spectatorData
        p.teleport(world.spawnLocation)
        p.scoreboard = scoreboard
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
    fun clearScoreboard() {
        scoreboard.teams.forEach { it.unregister() }
        scoreboard.objectives.forEach { it.unregister() }
    }

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

class CommandArgs(val player: CommandSender, val args: Array<String>) {
    var index = 0
    val size = args.size

    fun nextArg(): String {
        cArgs(args, ++index)
        return args[index - 1]
    }

    operator fun get(n: Int): String {
        cArgs(args, index + n + 1)
        return args[index + n]
    }

    fun endArgs() {
        cArgs(args, max = index)
    }

    fun lastArg(): String {
        cArgs(args, ++index, index)
        return args[index - 1]
    }

    fun latestArg() = get(-1)

    fun rest() = args.drop(index)

    fun restString() = rest().joinToString(" ")

}

fun JavaPlugin.addCommand(name: String, command: CommandArgs.() -> Unit) {
    getCommand(name)?.setExecutor { sender, _, _, args ->
        try {
            command(CommandArgs(sender, args))
        } catch (e: CommandException) {
            sender.sendMessage(ConfigManager.getError(e.playerMsg))
        }
        true
    }
}

fun JavaPlugin.addCommandTab(name: String, tabCompleter: CommandArgs.() -> List<String>?) {
    getCommand(name)?.setTabCompleter { sender, _, _, args ->
        tabCompleter(CommandArgs(sender, args))?.toMutableList()
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

fun cRequire(e: Boolean, msg: String) {
    contract {
        returns() implies e
    }
    if (!e) throw CommandException(msg)
}

fun cArgs(args: Array<String>, min: Int = 0, max: Int = Int.MAX_VALUE) {
    cRequire(args.size in min..max, "WrongArgumentsNumber")
}

fun cPerms(p: CommandSender, perm: String) {
    cRequire(p.hasPermission(perm), "NoPerms")
}

fun cPlayer(p: CommandSender) {
    contract {
        returns() implies (p is Player)
    }
    cRequire(p is Player, "NotPlayer")
}

inline fun <T> cWrongArgument(block: () -> T): T = try {
    block()
} catch (e: IllegalArgumentException) {
    e.printStackTrace()
    cWrongArgument()
}

fun cWrongArgument(): Nothing = throw CommandException("WrongArgument")

fun <T> cNotNull(p: T?, msg: String): T = p ?: throw CommandException(msg)

inline fun <reified T, reified R : T> cCast(p: T, msg: String): R = cNotNull(p as? R, msg)

fun cServerPlayer(name: String) = cNotNull(GregInfo.server.getPlayer(name), "PlayerNotFound")

fun chatColor(s: String): String = ChatColor.translateAlternateColorCodes('&', s)

fun randomString(size: Int) =
    String(CharArray(size) { (('a'..'z') + ('A'..'Z') + ('0'..'9')).random() })

fun rotationsOf(x: Int, y: Int): List<Pair<Int, Int>> =
    listOf(x to y, x to -y, -x to y, -x to -y, y to x, -y to x, y to -x, -y to -x).distinct()

fun between(i: Int, j: Int): IntRange = if (i > j) (j + 1 until i) else (i + 1 until j)

operator fun <E> List<E>.component6(): E = this[5]

fun isValidUUID(s: String) =
    Regex("""^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}${'$'}""")
        .matches(s)

operator fun Pair<Int, Int>.rangeTo(other: Pair<Int, Int>) = (first..other.first).flatMap { i ->
    (second..other.second).map { j -> Pair(i, j) }
}

class BuildTextComponentScope {
    val returnValue = TextComponent()
    fun append(str: String) {
        returnValue.addExtra(str)
    }

    fun append(tc: TextComponent) {
        returnValue.addExtra(tc)
    }

    fun append(v: Any?, clickEvent: ClickEvent? = null) {
        val c = TextComponent(v.toString())
        if (clickEvent != null)
            c.clickEvent = clickEvent
        append(c)
    }

    fun appendCopy(v: Any?, copy: Any?) {
        append(v, ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, copy.toString()))
    }
}

inline fun buildTextComponent(f: BuildTextComponentScope.() -> Unit) =
    BuildTextComponentScope().apply(f).returnValue

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

fun numberedFormat(s: String, vararg args: Any?): String? {
    var retNull = false
    val ret = s.replace(Regex("""\$(?:(\d+)|\{(\d+)})""")) {
        val i = it.groupValues[1].toIntOrNull()
        if (i == null || i < 1 || i > args.size) {
            retNull = true
            ""
        } else
            args[i - 1].toString()
    }
    return if (retNull) null else ret
}

val glog: GregLogger = run {
    val file = File(
        GregInfo.plugin.dataFolder.absolutePath + "/GregChess-${
            DateTimeFormatter.ofPattern("uuuu-MM-dd-HH-mm-ss").format(LocalDateTime.now())
        }.log"
    )
    file.createNewFile()
    GregLogger(file)
}

object GregInfo {

    val server by lazy { Bukkit.getServer() }
    val logger
        get() = plugin.logger
    val plugin
        get() = Bukkit.getPluginManager().getPlugin("GregChess")!!

    fun registerListener(l: Listener) = server.pluginManager.registerEvents(l, plugin)
}