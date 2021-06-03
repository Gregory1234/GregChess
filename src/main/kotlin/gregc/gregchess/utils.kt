package gregc.gregchess

import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.scoreboard.Scoreboard
import java.io.File
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
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

fun Player.sendDefTitle(title: String, subtitle: String = "") = sendTitle(title, subtitle, 10, 70, 20)

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

inline fun cTry(p: CommandSender, c: Configurator, err: (Exception) -> Unit = {}, f: () -> Unit) = try {
    f()
} catch (e: CommandException) {
    p.sendMessage(e.playerMsg.get(c))
    err(e)
}

fun JavaPlugin.addCommand(name: String, c: Configurator, command: CommandArgs.() -> Unit) {
    getCommand(name)?.setExecutor { sender, _, _, args ->
        cTry(sender, c){
            command(CommandArgs(sender, args))
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
    operator fun plus(offset: Loc) = Loc(x + offset.x, y + offset.y, z + offset.z)

    companion object {
        fun fromLocation(l: Location) = Loc(l.x.toInt(), l.y.toInt(), l.z.toInt())
    }
}

fun World.getBlockAt(l: Loc) = getBlockAt(l.x, l.y, l.z)
val Block.loc: Loc
    get() = Loc(x, y, z)

class CommandException(val playerMsg: ConfigPath<String>) : Exception() {
    override val message: String
        get() = "Uncaught command error: ${playerMsg.path}"
}

val errorMsg get() = Config.message.error

fun cRequire(e: Boolean, msg: ConfigPath<String>) {
    contract {
        returns() implies e
    }
    if (!e) throw CommandException(msg)
}

fun cArgs(args: Array<String>, min: Int = 0, max: Int = Int.MAX_VALUE) {
    cRequire(args.size in min..max, errorMsg.wrongArgumentsNumber)
}

fun cPerms(p: CommandSender, perm: String) {
    cRequire(p.hasPermission(perm), errorMsg.noPermission)
}

fun cPlayer(p: CommandSender) {
    contract {
        returns() implies (p is Player)
    }
    cRequire(p is Player, errorMsg.notPlayer)
}

inline fun <T> cWrongArgument(block: () -> T): T = try {
    block()
} catch (e: IllegalArgumentException) {
    e.printStackTrace()
    cWrongArgument()
}

fun cWrongArgument(): Nothing = throw CommandException(errorMsg.wrongArgument)

fun <T> cNotNull(p: T?, msg: ConfigPath<String>): T = p ?: throw CommandException(msg)

inline fun <reified T, reified R : T> cCast(p: T, msg: ConfigPath<String>): R = cNotNull(p as? R, msg)

fun cServerPlayer(name: String) = cNotNull(Bukkit.getPlayer(name), errorMsg.playerNotFound)

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

fun String.upperFirst() = replaceFirstChar { it.uppercase() }
fun String.lowerFirst() = replaceFirstChar { it.lowercase() }

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
    val plugin = Bukkit.getPluginManager().getPlugin("GregChess")!!
    File(plugin.dataFolder.absolutePath + "/logs").mkdir()
    val file = File(
        plugin.dataFolder.absolutePath + "/logs/GregChess-${
            DateTimeFormatter.ofPattern("uuuu-MM-dd-HH-mm-ss").format(LocalDateTime.now())
        }.log"
    )
    file.createNewFile()
    GregLogger(file, Bukkit.getLogger())
}