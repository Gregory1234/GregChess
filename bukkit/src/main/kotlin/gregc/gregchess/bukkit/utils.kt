package gregc.gregchess.bukkit

import gregc.gregchess.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.command.CommandSender
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.logging.Logger
import kotlin.contracts.contract
import kotlin.math.*

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
    p.sendMessage(e.error.get())
    err(e)
}

fun JavaPlugin.addCommand(name: String, command: CommandArgs.() -> Unit) {
    getCommand(name)?.setExecutor { sender, _, _, args ->
        cTry(sender) {
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

class Message(val config: ConfigurationSection, val path: String) {
    fun get() = config.getPathString(path)
}

fun message(n: String) = Message(config, "Message.$n")
fun title(n: String) = Message(config, "Title.$n")
fun err(n: String) = Message(config, "Message.Error.$n")


class CommandException(val error: Message, cause: Throwable? = null) : Exception(cause) {

    override val message: String get() = "Uncaught command error: ${error.get()}"
}

fun cRequire(e: Boolean, msg: Message) {
    contract {
        returns() implies e
    }
    if (!e) throw CommandException(msg)
}

fun <T> T?.cNotNull(msg: Message): T = this ?: throw CommandException(msg)

inline fun <reified T, reified R : T> T.cCast(msg: Message): R = (this as? R).cNotNull(msg)

fun CommandSender.cPerms(perm: String) {
    cRequire(hasPermission(perm), NO_PERMISSION)
}

fun cPlayer(p: CommandSender) {
    contract {
        returns() implies (p is Player)
    }
    cRequire(p is Player, NOT_PLAYER)
}

fun String.cPlayer() = Bukkit.getPlayer(this).cNotNull(PLAYER_NOT_FOUND)

fun cArgs(args: Array<String>, min: Int = 0, max: Int = Int.MAX_VALUE) {
    cRequire(args.size in min..max, WRONG_ARGUMENTS_NUMBER)
}

inline fun <T> cWrongArgument(block: () -> T): T = try {
    block()
} catch (e: DurationFormatException) {
    throw CommandException(WRONG_DURATION_FORMAT, e)
} catch (e: IllegalArgumentException) {
    throw CommandException(WRONG_ARGUMENT, e)
}

fun cWrongArgument(): Nothing = throw CommandException(WRONG_ARGUMENT)

fun String.chatColor(): String = ChatColor.translateAlternateColorCodes('&', this)

internal fun Listener.registerEvents() = Bukkit.getPluginManager().registerEvents(this, GregChess.plugin)

val Int.ticks: Duration get() = Duration.ofMillis(toLong() * 50)
val Long.ticks: Duration get() = Duration.ofMillis(this * 50)
val Double.seconds: Duration get() = Duration.ofNanos(floor(this * 1000000000L).toLong())
val Double.milliseconds: Duration get() = Duration.ofNanos(floor(this * 1000000L).toLong())
val Double.minutes: Duration get() = Duration.ofNanos(floor(this * 60000000000L).toLong())

class DurationFormatException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

fun String.toDuration(): Duration = toDurationOrNull() ?: throw DurationFormatException(this)

fun String.toDurationOrNull(): Duration? {
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

fun Duration.format(formatString: String): String = try {
    DateTimeFormatter.ofPattern(formatString).format(toLocalTime())
} catch (e: DateTimeException) {
    throw DurationFormatException(formatString, e)
}

internal val config: ConfigurationSection get() = GregChess.plugin.config

fun ConfigurationSection.getPathString(path: String, vararg args: String) =
    getString(path)?.format(*args)?.chatColor() ?: ((currentPath ?: "") + "-" + path)

class JavaGregLogger(private val logger: Logger) : GregLogger {
    override fun info(msg: String) = logger.info(msg)
    override fun warn(msg: String) = logger.warning(msg)
    override fun err(msg: String) = logger.severe(msg)
}

object UUIDAsStringSerializer : KSerializer<UUID> {
    override val descriptor = PrimitiveSerialDescriptor("UUIDAsString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
}

fun defaultModule() = SerializersModule {
    contextual(UUID::class, UUIDAsStringSerializer)
}