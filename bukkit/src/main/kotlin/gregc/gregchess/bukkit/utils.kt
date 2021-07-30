package gregc.gregchess.bukkit

import gregc.gregchess.Loc
import gregc.gregchess.bukkit.chess.human
import gregc.gregchess.minutes
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.command.CommandSender
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.contracts.contract
import kotlin.math.*

const val DEFAULT_LANG = "en_US"

class CommandArgs(val player: CommandSender, val args: Array<String>) {
    private var index = 0

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

inline fun cTry(p: CommandSender, err: (Exception) -> Unit = {}, f: () -> Unit) = try {
    f()
} catch (e: CommandException) {
    p.sendMessage(e.error.get(p.lang).chatColor())
    err(e)
}

inline fun JavaPlugin.addCommand(name: String, crossinline command: CommandArgs.() -> Unit) {
    getCommand(name)?.setExecutor { sender, _, _, args ->
        cTry(sender) {
            command(CommandArgs(sender, args))
        }
        true
    }
}

inline fun JavaPlugin.addCommandTab(name: String, crossinline tabCompleter: CommandArgs.() -> List<String>?) {
    getCommand(name)?.setTabCompleter { sender, _, _, args ->
        tabCompleter(CommandArgs(sender, args))?.toMutableList()
    }
}

fun Loc.toLocation(w: World) = Location(w, x.toDouble(), y.toDouble(), z.toDouble())
fun Location.toLoc() = Loc(x.toInt(), y.toInt(), z.toInt())

val Block.loc: Loc
    get() = Loc(x, y, z)

@JvmField
val WRONG_ARGUMENTS_NUMBER = err("WrongArgumentsNumber")
@JvmField
val WRONG_ARGUMENT = err("WrongArgument")
@JvmField
val NO_PERMISSION = err("NoPermission")
@JvmField
val NOT_PLAYER = err("NotPlayer")
@JvmField
val PLAYER_NOT_FOUND = err("PlayerNotFound")
@JvmField
val WRONG_DURATION_FORMAT = err("WrongDurationFormat")
@JvmField
val YOU_IN_GAME = err("InGame.You")
@JvmField
val OPPONENT_IN_GAME = err("InGame.Opponent")
@JvmField
val YOU_NOT_IN_GAME = err("NotInGame.You")
@JvmField
val PLAYER_NOT_IN_GAME = err("NotInGame.Player")
@JvmField
val OPPONENT_NOT_HUMAN = err("NotHuman.Opponent")

fun message(n: String) = config.getLocalizedString( "Message.$n")
fun title(n: String) = config.getLocalizedString( "Title.$n")
fun err(n: String) = config.getLocalizedString("Message.Error.$n")


class CommandException(val error: LocalizedString, cause: Throwable? = null) : Exception(cause) {

    override val message: String
        get() = "Uncaught command error: ${error.path}"
}

fun cRequire(e: Boolean, msg: LocalizedString) {
    contract {
        returns() implies e
    }
    if (!e) throw CommandException(msg)
}

fun <T> T?.cNotNull(msg: LocalizedString): T = this ?: throw CommandException(msg)

inline fun <reified T, reified R : T> T.cCast(msg: LocalizedString): R = (this as? R).cNotNull(msg)


fun cPerms(p: CommandSender, perm: String) {
    cRequire(p.hasPermission(perm), NO_PERMISSION)
}

fun cPlayer(p: CommandSender) {
    contract {
        returns() implies (p is Player)
    }
    cRequire(p is Player, NOT_PLAYER)
}

fun cServerPlayer(name: String) = Bukkit.getPlayer(name).cNotNull(PLAYER_NOT_FOUND)

fun cArgs(args: Array<String>, min: Int = 0, max: Int = Int.MAX_VALUE) {
    cRequire(args.size in min..max, WRONG_ARGUMENTS_NUMBER)
}

inline fun <T> cWrongArgument(block: () -> T): T = try {
    block()
} catch (e: IllegalArgumentException) {
    throw CommandException(WRONG_ARGUMENT, e)
}

fun cWrongArgument(): Nothing = throw CommandException(WRONG_ARGUMENT)

fun String.chatColor(): String = ChatColor.translateAlternateColorCodes('&', this)


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

val CommandSender.lang get() = (this as? Player)?.human?.lang ?: DEFAULT_LANG
fun CommandSender.sendMessage(s: LocalizedString) = sendMessage(s.get(lang).chatColor())
fun CommandSender.sendCommandMessage(msg: String, action: String, command: String) {
    spigot().sendMessage(buildTextComponent {
        append(msg.chatColor())
        append(" ")
        append(action.chatColor(), ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
    })
}
fun CommandSender.sendCommandMessage(msg: LocalizedString, action: LocalizedString, command: String) =
    sendCommandMessage(msg.get(lang), action.get(lang), command)

fun Listener.registerEvents() = Bukkit.getPluginManager().registerEvents(this, GregChess.plugin)

fun Duration.toTicks(): Long = toMillis() / 50

val Int.ticks: Duration
    get() = Duration.ofMillis(toLong() * 50)
val Long.ticks: Duration
    get() = Duration.ofMillis(this * 50)
val Double.seconds: Duration
    get() = Duration.ofNanos(floor(this * 1000000000L).toLong())
val Double.milliseconds: Duration
    get() = Duration.ofNanos(floor(this * 1000000L).toLong())
val Double.minutes: Duration
    get() = Duration.ofNanos(floor(this * 60000000000L).toLong())

fun String.asDurationOrNull(): Duration? {
    val match1 = Regex("""^(-|\+|)(\d+(?:\.\d+)?)(s|ms|t|m)$""").find(this)
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
    val match2 = Regex("""^(-)?(\d+):(\d{2,}(?:\.\d)?)$""").find(this)
    if (match2 != null) {
        val sign = (if (match2.groupValues[1] == "-") -1 else 1)
        val minutes = (match2.groupValues[2].toLongOrNull() ?: return null) * sign
        val seconds = (match2.groupValues[3].toDoubleOrNull() ?: return null) * sign
        return minutes.minutes + seconds.seconds
    }
    return null
}

fun Duration.toLocalTime(): LocalTime =
    LocalTime.ofNanoOfDay(max(ceil(toNanos().toDouble() / 1000000.0).toLong() * 1000000, 0))

fun Duration.format(formatString: String): String? = try {
    DateTimeFormatter.ofPattern(formatString).format(toLocalTime())
} catch (e: DateTimeException) {
    null
}

internal fun String.toKey(): NamespacedKey = NamespacedKey.fromString(this, GregChess.plugin)!!

val config: ConfigurationSection get() = GregChess.plugin.config

fun ConfigurationSection.getLocalizedString(path: String, vararg args: Any?) = LocalizedString(this, path, *args)

class LocalizedString(private val section: ConfigurationSection, val path: String, private vararg val args: Any?) {
    fun get(lang: String): String =
        (section.getString(path) ?: throw IllegalArgumentException(lang + "/" + section.currentPath + "." + path))
            .format(*args.map { if (it is LocalizedString) it.get(lang) else it }.toTypedArray())
}